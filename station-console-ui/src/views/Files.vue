<template>
  <div class="site-file">
    <div class="file-layout">
      <!--
        左侧目录树。
        ⚠ 前两项是虚拟节点，库里没有对应的行：
          folderId = ''      「全部文件」——不带 folderId 查，含未分类
          folderId = 'ROOT'  「未分类」——只看 folderid is null 的
        这两个必须分开。合成一个的话，点根节点会看到所有图，用户以为文件夹没生效。
      -->
      <aside class="folder-side">
        <div class="folder-side-head">
          <span class="folder-side-title">{{ $t('files.folders') }}</span>
          <el-button v-if="canFolder" link type="primary" size="small"
            :icon="FolderAdd" @click="openFolderAdd(null)">{{ $t('common.add') }}</el-button>
        </div>
        <el-tree
          ref="folderTreeRef"
          class="folder-tree"
          :data="folderTreeData"
          node-key="folderId"
          :props="{ label: 'folderName', children: 'children' }"
          :expand-on-click-node="false"
          :draggable="canFolder"
          :allow-drag="allowFolderDrag"
          :allow-drop="allowFolderDrop"
          @node-drop="onFolderDrop"
          :current-node-key="currentFolderId"
          highlight-current
          default-expand-all
          :empty-text="$t('files.noFolder')"
          @node-click="handleFolderClick">
          <template #default="{ data }">
            <!-- 整个节点是放置区：拖文件过来松手就归类到这个文件夹 -->
            <span class="folder-node" :class="{ 'drop-over': dropTargetId === data.folderId }"
              @dragover.prevent="onFolderDragOver(data)" @dragleave="onFolderDragLeave(data)"
              @drop.prevent="onDropToFolder(data)">
              <el-icon class="folder-node-icon"><component :is="data.virtual ? Files : Folder" /></el-icon>
              <span class="folder-node-name" :title="folderLabel(data)">{{ folderLabel(data) }}</span>
              <span v-if="data.fileCount" class="folder-node-count">{{ data.fileCount }}</span>
              <!-- 虚拟节点没有增删改：它们不是真的文件夹 -->
              <span v-if="canFolder && !data.virtual" class="folder-node-ops">
                <el-icon :title="$t('files.addSub')" @click.stop="openFolderAdd(data)"><Plus /></el-icon>
                <el-icon :title="$t('files.rename')" @click.stop="openFolderRename(data)"><EditPen /></el-icon>
                <el-icon :title="$t('common.delete')" class="is-danger" @click.stop="handleFolderRemove(data)"><Delete /></el-icon>
              </span>
            </span>
          </template>
        </el-tree>
      </aside>

      <div class="file-main">
    <div class="file-head">
      <el-radio-group v-model="activeType" @change="handleTypeChange">
        <el-radio-button value="image">{{ $t('files.type.image') }}</el-radio-button>
        <el-radio-button value="video">{{ $t('files.type.video') }}</el-radio-button>
        <el-radio-button value="model">{{ $t('files.type.model') }}</el-radio-button>
      </el-radio-group>

      <el-input v-model="queryName" class="file-search" clearable :placeholder="$t('files.searchName')"
        @keyup.enter="loadList" @clear="loadList">
        <template #append><el-button icon="Search" @click="loadList" /></template>
      </el-input>

      <div class="head-actions">
        <el-button v-hasPermi="['site:file:upload']" type="primary" icon="Upload"
          :loading="uploading" @click="pickFile">
          {{ $t('files.upload', { type: typeLabel }) }}
        </el-button>
        <!-- 用照片生成 3D（腾讯混元，2026-09-26）。只在「模型」标签下出现 -->
        <el-button v-if="isModel" v-hasPermi="['site:file:upload']" icon="MagicStick" @click="genPanel?.open()">
          {{ $t('files.gen.button') }}
        </el-button>
        <!--
          ⚠ 这里原来还有一个「存储设置」按钮，点开是「对象存储设置」弹窗。
            2026-09-07 整个搬到「站点概览」页的「文件存储」卡里了（那边有搬的理由）。
            这一页真正的内容是文件本身，一份一次性的站点配置不该挂在列表页顶上。

            下面那条 el-alert 里有去处的链接——**别把按钮加回来**：
            两个入口各改一半是这类配置最典型的坏法。
        -->
      </div>
    </div>

    <!--
      隐藏的原生 input：Element 的 el-upload 在这里帮不上忙——
      上传前要先把文件加载一遍量出宽高（视频还要量时长），量完才发请求，
      用 el-upload 的 before-upload 做异步测量要绕一圈 Promise，不如直接控制。
    -->
    <input ref="fileInput" type="file" class="hidden-input" :accept="acceptTypes" @change="handleFilePicked" />

    <el-alert v-if="storageMissing" class="storage-alert" type="warning" show-icon :closable="false"
      :title="storageMissingTitle">
      <template v-if="storageMissingReason === 'base-url'">
        <span v-html="$t('files.storage.noBaseUrl')"></span>
      </template>
      <template v-else>
        {{ $t('files.storage.noBucket1') }}
        <el-link type="primary" :underline="false" @click="goStorage">{{ $t('nav.overview') }}</el-link>
        {{ $t('files.storage.noBucket2') }}
      </template>
    </el-alert>

    <el-progress v-if="uploading" :percentage="uploadPercent" :stroke-width="4" class="upload-progress" />

    <!-- 生成任务列表 + 提交弹窗。任务完成时刷新模型列表 -->
    <model-gen-panel v-if="isModel" ref="genPanel" :folder-id="currentFolderId" @generated="loadList" />

        <!--
          多选工具条。只在选了东西时出现——常驻一条空工具条会白占一行，
          而这个页面的主体是图片网格，垂直空间很值钱。
        -->
        <div v-if="pickedKeys.length" class="pick-bar">
          <span class="pick-bar-text">{{ $t('files.picked', { n: pickedKeys.length }) }}</span>
          <el-select v-model="moveTarget" class="pick-bar-select" :placeholder="$t('files.moveTo')" size="small"
            @change="handleMoveSelected">
            <el-option :label="$t('files.unfiledRoot')" value="ROOT" />
            <el-option v-for="opt in flatFolderOptions" :key="opt.folderId"
              :label="opt.label" :value="opt.folderId" />
          </el-select>
          <el-button link size="small" @click="clearPicked">{{ $t('files.clearPick') }}</el-button>
          <span class="pick-bar-hint">{{ $t('files.dragHint') }}</span>
        </div>

    <div v-loading="loading" class="file-grid">
      <!--
        卡片可拖到左侧文件夹上完成归类。
        ⚠ draggable 放在卡片根节点而不是图片上：放在 <el-image> 上的话，
          浏览器会用「拖一张图片」的原生行为接管，拖出来的是图片本身而不是卡片，
          drop 到树上拿不到我们塞的 dataTransfer。
      -->
      <div v-for="item in list" :key="keyOf(item)" class="file-card"
        :class="{ picked: isPicked(item), dragging: draggingKeys.includes(keyOf(item)) }"
        :draggable="canFolder" @dragstart="onFileDragStart($event, item)" @dragend="onFileDragEnd">
        <!-- 复选框只在有文件夹权限时出现：没权限的人多选了也没法移动 -->
        <el-checkbox v-if="canFolder" class="card-check" :model-value="isPicked(item)"
          @change="togglePick(item)" @click.stop />
        <div class="card-media">
          <!--
            图片和视频卡片在同一个 grid 里，列宽相同，所以两种卡片天然等宽。
            媒体区固定成同一个高度、object-fit: contain，视频画面不会被拉伸，
            也不会因为视频比图片宽/窄而让这一行的卡片高低不齐。
          -->
          <el-image v-if="isImage" :src="item.thumbUrl || item.imageUrl"
            :preview-src-list="[item.imageUrl]" preview-teleported fit="contain" class="media-inner">
            <template #error><div class="media-error">{{ $t('files.loadFailed') }}</div></template>
          </el-image>
          <video v-else-if="isVideo" class="media-inner" preload="metadata" controls
            :src="item.videoUrl" />
          <!--
            模型没有封面，也不在卡片里直接起 WebGL：一页 20 张卡片 = 20 个上下文，
            浏览器会把最早的几个踢掉。立方体图标 + 「预览」按钮，点了再在弹窗里画。
          -->
          <div v-else class="media-inner media-model">
            <el-icon class="media-model-icon"><Box /></el-icon>
            <el-button link type="primary" size="small" @click="openPreview(item)">{{ $t('common.preview') }}</el-button>
          </div>
        </div>

        <div class="card-body">
          <div class="card-name" :title="nameOf(item)">{{ nameOf(item) }}</div>
          <div class="card-meta">
            <span>{{ formatSize(item.fileSize) }}</span>
            <span v-if="item.width && item.height">{{ item.width }}×{{ item.height }}</span>
            <span v-if="!isImage && item.duration">{{ formatDuration(item.duration) }}</span>
          </div>
          <div class="card-actions">
            <el-button link type="primary" size="small" @click="copyUrl(item)">{{ $t('files.copyUrl') }}</el-button>
            <el-button v-hasPermi="['site:file:edit']" link size="small" @click="openRename(item)">
              {{ $t('files.rename') }}
            </el-button>
            <el-button v-hasPermi="['site:file:remove']" link type="danger" size="small"
              @click="handleDelete(item)">
              {{ $t('common.delete') }}
            </el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !list.length" :description="$t('files.empty')" class="file-empty" />
    </div>

    <pagination v-show="total > 0" v-model:page="queryPage" v-model:limit="queryLimit"
      :total="total" @pagination="loadList" />

    <!-- 重命名 -->
    <el-dialog v-model="renameOpen" :title="$t('files.rename')" width="420px" append-to-body>
      <el-form label-width="70px">
        <el-form-item :label="$t('files.nameField')">
          <el-input v-model="renameForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('common.remark')">
          <el-input v-model="renameForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameOpen = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="renameSaving" @click="submitRename">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 3D 模型预览。传错文件（不是 glb、或者导出坏了）在这里就能看出来，不用等到前台 -->
    <el-dialog v-model="previewOpen" :title="$t('goods.media.modelPreview')" width="720px" append-to-body destroy-on-close>
      <model-preview v-if="previewOpen && previewUrl" :src="previewUrl" :height="420" />
      <p class="preview-hint">{{ $t('files.previewHint') }}</p>
    </el-dialog>
      </div>
    </div>

  </div>
</template>

<script setup name="ConsoleFiles">
import { ref, computed, onMounted, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, Files, Plus, EditPen, Delete, FolderAdd, Box } from '@element-plus/icons-vue'
// 异步：three.js 那个包只在点「预览」时才下，不进文件页的主 chunk
const ModelPreview = defineAsyncComponent(() => import('@station-shared/ModelPreview.vue'))
import ModelGenPanel from '@/components/ModelGenPanel.vue'
import { hasPermi } from '@/store/identity'
import { t } from '@/i18n'
import {
  listSiteImage, uploadSiteImage, updateSiteImage, delSiteImage,
  listSiteVideo, uploadSiteVideo, updateSiteVideo, delSiteVideo,
  listSiteModel, uploadSiteModel, updateSiteModel, delSiteModel,
  getShopStorage,
  listSiteFolder, addSiteFolder, updateSiteFolder, delSiteFolder, moveSiteFiles
} from '@/api/console'

// 这个页面没有 props：站点由服务端会话决定，前端传不了也不需要传。
//
// ⚠ 这里原来写着「文件库按商家隔离，同一个商家名下几个站看到的是同一批文件」。
//   那是 ERP 时代的形状，搬过来之后不成立了：u_s_siteimage / u_s_sitevideo 上的
//   shop_code 是【遗留列】，建库脚本里明写「新代码不得读它、不得按它过滤」——
//   一个部署一个站，一个库里只有一个站的数据，没有商家这个维度。
//   存储配置同理（u_s_storage 也没有商家列），这也是它能和站点设置摆在一起的前提。

const router = useRouter()

const activeType = ref('image')

// ---------------- 文件夹 ----------------
//
// currentFolderId 的三个状态和后端约定一致：
//   ''      全部（不带 folderId 查，含未分类）
//   'ROOT'  只看未分类
//   具体 id 只看那个文件夹
const folderTreeRef = ref(null)
const folders = ref([])
const currentFolderId = ref('')
const canFolder = computed(() => hasPermi('site:file:folder'))

// 虚拟节点拼在真实树前面。它们库里没有对应的行，所以带 virtual 标记，
// 模板里靠它决定不显示增删改按钮
const folderTreeData = computed(() => [
  /*
    ⚠ 这两个虚拟节点的 folderName 是**界面文字**，不是库里的名字（库里没有这两行）。
      所以显示时走 folderLabel() 翻译；真文件夹一律显示库里那个名字，不翻——
      那是商家自己起的名字，翻了反而认不出来。
  */
  { folderId: '', folderName: 'files.allFiles', virtual: true, children: [] },
  { folderId: 'ROOT', folderName: 'files.unfiled', virtual: true, children: [] },
  ...folders.value
])
const isVideo = computed(() => activeType.value === 'video')
const isModel = computed(() => activeType.value === 'model')
/** 「用照片生成」面板（ModelGenPanel），按钮在顶栏、面板在列表上方 */
const genPanel = ref(null)
const isImage = computed(() => !isVideo.value && !isModel.value)
const typeLabel = computed(() => isVideo.value
  ? t('files.type.video')
  : (isModel.value ? t('files.type.model') : t('files.type.image')))

/** 树节点显示名：虚拟节点翻译，真文件夹显示商家起的名字 */
const folderLabel = data => (data.virtual ? t(data.folderName) : data.folderName)

// 模型预览弹窗。destroy-on-close：关掉就释放 WebGL 上下文，连开几个不会攒
const previewOpen = ref(false)
const previewUrl = ref('')
function openPreview(item) {
  previewUrl.value = item.modelUrl
  previewOpen.value = true
}

const list = ref([])
const total = ref(0)
const loading = ref(false)
const queryName = ref('')
const queryPage = ref(1)
const queryLimit = ref(20)

const fileInput = ref(null)
const uploading = ref(false)
const uploadPercent = ref(0)

// '' = 存储可用；'secret' = 云厂商没填密钥；'base-url' = 本机磁盘但没配访问地址。
// 三种状态的处置完全不同（前两种去「站点概览」页的「文件存储」卡里填，
// 第三种只能找部署方改环境变量），
// 所以存的是原因不是一个 boolean
const storageMissingReason = ref('')
const storageMissing = computed(() => !!storageMissingReason.value)
const storageMissingTitle = computed(() => storageMissingReason.value === 'base-url'
  ? t('files.storage.noBaseUrlTitle')
  : t('files.storage.noBucketTitle'))

const renameOpen = ref(false)
const renameSaving = ref(false)
const renameForm = ref({})

/**
 * 选文件对话框里放哪些后缀。
 *
 * ⚠ 必须和后端白名单一致。真正的闸在后端（`SiteFileService.requireExtension`，
 * 白名单来自 `station.upload.image-ext` / `video-ext`），这里只是别让人白选一场。
 *
 * 这里原来多写了 `.svg` 和 `.ogg`——两个后端都会拒的格式。症状是「能选中、传上去才报错」。
 *
 * ⚠ **svg 不要加回来。** 这些文件由文件域名以原始 Content-Type 直接发出去，
 * 而 SVG 是一份可以内嵌 `<script>` 的 XML 文档——它是这份白名单里唯一一个
 * 能执行脚本的格式，后端是**故意**把它去掉的（理由写在 StationProperties.Upload.imageExt）。
 * 看到这一行想「顺手支持一下矢量图」时，先去读那段注释。
 */
const acceptTypes = computed(() => isVideo.value
  ? '.mp4,.webm,.mov,.m4v'
  : isModel.value
    // 只收 glb。gltf 是多文件格式（外链 .bin 和贴图），单文件传上去必裂，后端也拒
    ? '.glb'
    : '.jpg,.jpeg,.png,.gif,.webp,.bmp')

const keyOf = item => isVideo.value ? item.videoId : isModel.value ? item.modelId : item.imageId
const nameOf = item => isVideo.value ? item.videoName : isModel.value ? item.modelName : item.imageName


/**
 * 读整棵树。切页签（图片/视频）时也要重读——两棵树是分开的。
 *
 * ⚠ 读完要检查当前选中的文件夹还在不在：别人可能刚把它删了，
 *   留着一个不存在的 id 会让列表恒为空，而页面上看不出为什么。
 */
async function loadFolders() {
  try {
    const response = await listSiteFolder(activeType.value)
    folders.value = response.data || []
  } catch {
    folders.value = []
  }
  const id = currentFolderId.value
  if (id && id !== 'ROOT' && !findFolder(folders.value, id)) {
    currentFolderId.value = ''
  }
}

/** 在树里按 id 找一个节点。文件夹总共几十个，递归找足够快 */
function findFolder(nodes, folderId) {
  for (const node of nodes || []) {
    if (node.folderId === folderId) return node
    const hit = findFolder(node.children, folderId)
    if (hit) return hit
  }
  return null
}

function handleFolderClick(data) {
  // 换文件夹要清选择：留着上一个文件夹里勾的东西，一不小心就整批移错地方
  clearPicked()
  currentFolderId.value = data.folderId
  queryPage.value = 1
  loadList()
}

async function openFolderAdd(parent) {
  try {
    const { value } = await ElMessageBox.prompt(
      parent ? t('files.msg.addSubIn', { name: parent.folderName }) : t('files.msg.addTop'),
      t('files.msg.addTitle'),
      { inputPattern: /\S/, inputErrorMessage: t('files.msg.nameRequired'), inputPlaceholder: t('files.msg.namePlaceholder') }
    )
    await addSiteFolder({
      mediaType: activeType.value,
      parentId: parent ? parent.folderId : null,
      folderName: value.trim()
    })
    ElMessage.success(t('goods.msg.created'))
    await loadFolders()
  } catch (e) {
    // 点取消时 ElMessageBox 也会 reject，不能当成错误弹提示
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || t('files.msg.addFailed'))
  }
}

async function openFolderRename(node) {
  try {
    const { value } = await ElMessageBox.prompt(t('files.msg.newName'), t('files.msg.renameTitle'), {
      inputValue: node.folderName,
      inputPattern: /\S/,
      inputErrorMessage: t('files.msg.nameRequired')
    })
    await updateSiteFolder({ folderId: node.folderId, folderName: value.trim() })
    ElMessage.success(t('files.msg.renamed'))
    await loadFolders()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || t('files.msg.renameFailed'))
  }
}

/**
 * 删文件夹。
 *
 * ⚠ 提示语必须说清楚「文件不会被删」——不然没人敢点，或者点了之后以为图没了。
 */
async function handleFolderRemove(node) {
  const count = node.fileCount || 0
  const tail = count
    ? t('files.msg.delTailFiles', { n: count })
    : t('files.msg.delTailEmpty')
  try {
    await ElMessageBox.confirm(t('files.msg.delConfirm', { name: node.folderName, tail }), t('files.msg.delTitle'), {
      type: 'warning'
    })
    await delSiteFolder(node.folderId)
    ElMessage.success(t('common.deleted'))
    if (currentFolderId.value === node.folderId) currentFolderId.value = ''
    await loadFolders()
    loadList()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || t('files.msg.delFailed'))
  }
}
// ---------------- 多选与拖拽归类 ----------------
//
// 两条路都通向同一个接口 moveSiteFiles：
//   · 勾几个 → 工具条里选「移动到…」
//   · 直接把卡片拖到左边的文件夹上
//
// ⚠ 拖一个「已经勾上的」文件时，移动的是整批勾选；拖一个没勾的，只移动它自己。
//   这是文件管理器的通用行为，不这么做会让人以为勾选没生效。
const pickedKeys = ref([])
const draggingKeys = ref([])
const dropTargetId = ref(null)
const moveTarget = ref('')

const isPicked = item => pickedKeys.value.includes(keyOf(item))

function togglePick(item) {
  const key = keyOf(item)
  const at = pickedKeys.value.indexOf(key)
  if (at >= 0) pickedKeys.value.splice(at, 1)
  else pickedKeys.value.push(key)
}

function clearPicked() {
  pickedKeys.value = []
  moveTarget.value = ''
}

/** 「移动到…」下拉用的扁平选项，名字带层级缩进，一眼看得出父子关系 */
const flatFolderOptions = computed(() => {
  const out = []
  const walk = (nodes, depth) => {
    for (const node of nodes || []) {
      out.push({ folderId: node.folderId, label: '　'.repeat(depth) + node.folderName })
      walk(node.children, depth + 1)
    }
  }
  walk(folders.value, 0)
  return out
})

function onFileDragStart(event, item) {
  const key = keyOf(item)
  // 拖已勾选的 → 拖整批；拖没勾选的 → 只拖它
  draggingKeys.value = pickedKeys.value.includes(key) ? [...pickedKeys.value] : [key]
  // ⚠ 必须往 dataTransfer 里塞点东西，否则 Firefox 不认这是一次有效拖拽，
  //   drop 事件根本不会触发。内容本身用不上，真正的数据在 draggingKeys 里
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', key)
}

function onFileDragEnd() {
  draggingKeys.value = []
  dropTargetId.value = null
}

function onFolderDragOver(data) {
  // 「全部文件」不是一个能放东西的地方；「未分类」可以（等于取消归类）
  if (!draggingKeys.value.length) return
  if (data.virtual && data.folderId !== 'ROOT') return
  dropTargetId.value = data.folderId
}

function onFolderDragLeave(data) {
  if (dropTargetId.value === data.folderId) dropTargetId.value = null
}

async function onDropToFolder(data) {
  const ids = [...draggingKeys.value]
  dropTargetId.value = null
  draggingKeys.value = []
  if (!ids.length) return
  if (data.virtual && data.folderId !== 'ROOT') return
  // ROOT 这个虚拟节点对应后端的「挪回未分类」，传 null
  await moveFilesTo(ids, data.folderId === 'ROOT' ? null : data.folderId, data.folderName)
}

function handleMoveSelected(value) {
  if (!value || !pickedKeys.value.length) return
  const label = value === 'ROOT'
    ? t('files.unfiled')
    : (flatFolderOptions.value.find(o => o.folderId === value) || {}).label?.trim() || t('files.msg.targetFolder')
  moveFilesTo([...pickedKeys.value], value === 'ROOT' ? null : value, label)
  moveTarget.value = ''
}

/**
 * 真正调接口的那一步。
 *
 * ⚠ 移完要同时刷新列表和树：列表是因为当前视图可能正筛着某个文件夹（移走的文件要消失），
 *   树是因为每个节点上挂着文件数角标。只刷一个会留下对不上的数字。
 */
async function moveFilesTo(fileIds, folderId, folderName) {
  try {
    await moveSiteFiles(activeType.value, fileIds, folderId)
    ElMessage.success(t('files.msg.moved', { n: fileIds.length, name: folderName }))
    clearPicked()
    await loadFolders()
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || t('files.msg.moveFailed'))
  }
}

// ---------------- 文件夹之间的拖拽（调层级） ----------------

/** 虚拟节点（全部 / 未分类）不能拖 */
function allowFolderDrag(node) {
  return !node.data.virtual
}

/**
 * 能不能放。
 *
 * ⚠ 不允许 prev/next（同级排序）：sort 字段还没有前端交互，放开了会让人
 *   拖出一个「看起来变了、刷新又回去」的顺序。只允许拖进另一个文件夹里。
 * ⚠ 也不允许拖进虚拟节点——它们不是真的文件夹。
 */
function allowFolderDrop(draggingNode, dropNode, type) {
  if (type !== 'inner') return false
  return !dropNode.data.virtual
}

async function onFolderDrop(draggingNode, dropNode) {
  try {
    await updateSiteFolder({ folderId: draggingNode.data.folderId, parentId: dropNode.data.folderId })
    ElMessage.success(t('files.msg.movedShort'))
  } catch (e) {
    ElMessage.error(e?.message || t('files.msg.moveFailed'))
  }
  // 成功失败都重读：失败时要把树恢复成库里的真实结构，
  // 否则 el-tree 已经在界面上把节点挪过去了，看着像成功了
  await loadFolders()
}

function handleTypeChange() {
  // 两棵树分开，切页签要重读；选中项也得清掉，否则会拿另一棵树的 id 去查
  currentFolderId.value = ""
  clearPicked()
  loadFolders()
  queryPage.value = 1
  list.value = []
  loadList()
}

function loadList() {
  loading.value = true
  const query = {
    pageNum: queryPage.value,
    pageSize: queryLimit.value
  }
  // 空串 = 全部（不带这个参数）；ROOT = 只看未分类；具体 id = 只看那个文件夹
  if (currentFolderId.value) query.folderId = currentFolderId.value
  if (queryName.value) {
    // 后端按各自的名称列做模糊匹配，字段名不同所以这里要分开传
    if (isVideo.value) query.videoName = queryName.value
    else if (isModel.value) query.modelName = queryName.value
    else query.imageName = queryName.value
  }
  const req = isVideo.value ? listSiteVideo(query) : isModel.value ? listSiteModel(query) : listSiteImage(query)
  req.then(response => {
    list.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    loading.value = false
  })
}

/*
  去「站点概览」配存储。

  ⚠ 这里只跳到那一页，不带 hash 也不做滚动定位。存储那张卡在页面第二屏，
    锚点定位要么依赖 el-card 的内部 DOM，要么得在 Overview 里加一个 id 和
    onMounted 的 scrollIntoView——为了少滚一下鼠标，多两个页面之间的耦合，不值。
*/
function goStorage() {
  router.push('/overview')
}
function pickFile() {
  if (storageMissing.value) {
    // 两种原因两句话。本机磁盘没配地址时说「去改存储配置」是错的指路——
    // 那一栏在管理台里改不了，只能找部署方改环境变量
    ElMessage.warning(storageMissingReason.value === 'base-url'
      ? t('files.msg.needBaseUrl')
      : t('files.msg.needBucket'))
    return
  }
  fileInput.value.value = ''
  fileInput.value.click()
}

async function handleFilePicked(event) {
  const file = event.target.files && event.target.files[0]
  if (!file) return

  uploading.value = true
  uploadPercent.value = 0
  try {
    // 先在浏览器里量尺寸，量到什么传什么。量不到不算错——
    // 后端允许这几个字段为空，列表会退回按自然尺寸渲染。
    // 模型没有宽高时长可量，跳过（量它等于让浏览器把 glb 当图片解一次，必失败）
    const meta = isModel.value ? {} : await measure(file, isImage.value).catch(() => ({}))

    const form = new FormData()
    form.append('file', file)
    // ⚠ ROOT 是「未分类」这个视图，不是一个能往里传的文件夹
    if (currentFolderId.value && currentFolderId.value !== 'ROOT') {
      form.append('folderId', currentFolderId.value)
    }
    if (meta.width) form.append('width', meta.width)
    if (meta.height) form.append('height', meta.height)
    if (isVideo.value && meta.duration) form.append('duration', meta.duration)

    const onProgress = e => {
      if (e.total) uploadPercent.value = Math.round((e.loaded * 100) / e.total)
    }
    const req = isVideo.value ? uploadSiteVideo(form, onProgress)
      : isModel.value ? uploadSiteModel(form, onProgress)
        : uploadSiteImage(form, onProgress)
    await req
    ElMessage.success(t('files.msg.uploaded'))
    queryPage.value = 1
    loadList()
  } catch (e) {
    // request 拦截器已经弹过后端返回的 msg 了，这里不重复提示
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

/**
 * 在浏览器里量出宽高（视频再量时长）。
 *
 * 用 objectURL 而不是 FileReader：前者不用把整个文件读成 base64，
 * 一个 100MB 的视频用 FileReader 会先在内存里撑成一百多兆的字符串。
 * 量完必须 revokeObjectURL，否则这块内存要等到页面卸载才释放。
 */
function measure(file, asImage) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const done = result => { URL.revokeObjectURL(url); resolve(result) }
    const fail = () => { URL.revokeObjectURL(url); reject(new Error('measure failed')) }

    if (asImage) {
      const img = new Image()
      img.onload = () => done({ width: img.naturalWidth, height: img.naturalHeight })
      img.onerror = fail
      img.src = url
      return
    }
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => done({
      width: video.videoWidth,
      height: video.videoHeight,
      duration: Math.round(video.duration) || null
    })
    video.onerror = fail
    video.src = url
  })
}

function openRename(item) {
  renameForm.value = {
    id: keyOf(item),
    name: nameOf(item),
    remark: item.remark
  }
  renameOpen.value = true
}

function submitRename() {
  if (!renameForm.value.name || !renameForm.value.name.trim()) {
    ElMessage.warning(t('files.msg.nameRequired'))
    return
  }
  renameSaving.value = true
  const { id, name, remark } = renameForm.value
  const req = isVideo.value ? updateSiteVideo({ videoId: id, videoName: name, remark })
    : isModel.value ? updateSiteModel({ modelId: id, modelName: name, remark })
      : updateSiteImage({ imageId: id, imageName: name, remark })
  req.then(() => {
    ElMessage.success(t('common.saved'))
    renameOpen.value = false
    loadList()
  }).finally(() => {
    renameSaving.value = false
  })
}

function handleDelete(item) {
  ElMessageBox.confirm(
    t('files.msg.delFileConfirm', { name: nameOf(item) }),
    t('common.confirmTitle'), { type: 'warning' }
  ).then(() => {
    return isVideo.value ? delSiteVideo(keyOf(item))
      : isModel.value ? delSiteModel(keyOf(item))
        : delSiteImage(keyOf(item))
  }).then(() => {
    ElMessage.success(t('common.deleted'))
    loadList()
  }).catch(() => {})
}

function copyUrl(item) {
  const url = isVideo.value ? item.videoUrl : isModel.value ? item.modelUrl : item.imageUrl
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(url)
      .then(() => ElMessage.success(t('files.msg.copied')))
      .catch(() => ElMessage.warning(t('files.msg.copyFailed')))
    return
  }
  ElMessage.warning(t('files.msg.copyUnsupported'))
}

// 进页面就探一次：没配存储的话上传按钮点了也是白点，不如先把话说在前面。
//
// 先在前端判一次权限再发请求：普通员工能传文件（用商家已经配好的存储），
// 但没有看密钥的权限。不判的话，他一进页面就会吃一个 403，
// 而 request 拦截器会把它弹成「当前操作没有权限」——一个他既看不懂、也不需要处理的报错。
function checkStorage() {
  if (!hasPermi(['site:storage:query'])) {
    storageMissingReason.value = ''
    return
  }
  getShopStorage().then(response => {
    storageMissingReason.value = missingReasonOf(response.data)
  }).catch(() => {
    storageMissingReason.value = ''
  })
}

/**
 * 「现在能不能传文件」。
 *
 * ⚠ 原来这里写的是 `!data || !data.secretConfigured`，那一行把**本机磁盘模式误判成
 * 「没配存储」**，于是拦掉了上传按钮。而本机磁盘是一个完整可用的模式（运维手册 §8.1），
 * 库里一行配置都没有时后端回的就是它。
 *
 * 症状还特别怪：checkStorage 只对有 site:storage:query 权限的人跑，所以
 * **站长传不了文件，普通员工反而能传**——权限越大越用不了，谁也不会往这上面想。
 *
 * 本机磁盘唯一真的传不了的情况是没配 station.upload.base-url（后端会把它放在
 * publicUrl 里回显）。那时候地址拼不出来，SiteFileService.publicUrl() 直接抛异常。
 */
function missingReasonOf(data) {
  if (!data) return 'secret'
  if (data.provider === 'local') return data.publicUrl ? '' : 'base-url'
  return data.secretConfigured ? '' : 'secret'
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatDuration(seconds) {
  if (!seconds) return '-'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

onMounted(() => {
  loadFolders()
  loadList()
  checkStorage()
})
</script>

<style scoped lang="scss">
.site-file { padding: 4px 2px; }

.file-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.file-search { width: 240px; }
.head-actions { margin-left: auto; display: flex; gap: 8px; }

.hidden-input { display: none; }
.storage-alert { margin-bottom: 14px; }
.upload-progress { margin-bottom: 14px; }

/*
  图片和视频用同一个网格、同一个列宽，所以两种卡片天然等宽——
  这是「视频封面宽度和视频宽度保持统一」那条要求的落点：
  媒体区高度也固定，配合 object-fit: contain，
  不同宽高比的素材不会把某一张卡片撑高或压扁，整行始终对齐。
*/
.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
  min-height: 200px;
}

.file-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-bg-color);
  transition: border-color .2s;
}
.file-card:hover { border-color: var(--el-color-primary); }

.card-media {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
}
.media-inner {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.media-error {
  display: grid;
  place-items: center;
  height: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.media-model {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: var(--el-fill-color-light);
}
.media-model-icon { font-size: 40px; color: var(--el-color-primary); }
.preview-hint { margin: 10px 0 0; color: var(--el-text-color-secondary); font-size: 12px; }

.card-body { padding: 8px 10px 6px; }
.card-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  gap: 10px;
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.card-actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 4px;
}

.file-empty { grid-column: 1 / -1; }

/*
  ⚠ 这下面原来还有 .dot / .form-tip / .form-tip-warn / .footer-hint / .storage-tip，
    全是「对象存储设置」弹窗的样式，跟着弹窗一起搬去 Overview.vue 了。
*/

/* 提示条里那个环境变量名，要能一眼和正文分开 */
.storage-alert code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color);
  font-family: Consolas, Monaco, monospace;
}

/* ---------------- 目录树 ---------------- */
/*
  左右两栏。⚠ 右栏要 min-width: 0——flex 子项的默认 min-width 是 auto，
  里面那个图片网格撑开时会把左栏挤没，而不是自己出滚动条。
*/
.file-layout {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.file-main {
  flex: 1;
  min-width: 0;
}
.folder-side {
  flex: 0 0 220px;
  max-height: calc(100vh - 140px);
  overflow-y: auto;
  padding: 10px 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}
.folder-side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 6px;
}
.folder-side-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.folder-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding-right: 4px;
  overflow: hidden;
}
.folder-node-icon {
  flex: 0 0 auto;
  color: var(--el-color-warning);
}
/* 名字太长时截断而不是把计数和按钮挤出去 */
.folder-node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-node-count {
  flex: 0 0 auto;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 16px;
}
/* 操作图标平时不显示：一行三个图标常驻会让树看着很吵 */
.folder-node-ops {
  display: none;
  flex: 0 0 auto;
  gap: 6px;
}
.folder-node:hover .folder-node-ops {
  display: inline-flex;
}
.folder-node-ops .el-icon:hover {
  color: var(--el-color-primary);
}
.folder-node-ops .is-danger:hover {
  color: var(--el-color-danger);
}

@media (max-width: 900px) {
  /* 窄屏把树收到上面去，横着放两栏会让图片网格只剩一列 */
  .file-layout { flex-direction: column; }
  .folder-side { flex: none; width: 100%; max-height: 220px; }
}

/* ---------------- 多选与拖拽 ---------------- */
.pick-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
}
.pick-bar-text { font-size: 13px; font-weight: 600; }
.pick-bar-select { width: 200px; }
.pick-bar-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
/* 卡片要 position: relative，复选框才能定位在它左上角 */
.file-card { position: relative; }
.card-check {
  position: absolute;
  top: 6px;
  left: 8px;
  z-index: 2;
  padding: 2px;
  border-radius: 4px;
  background: rgba(255, 255, 255, .85);
}
.file-card.picked {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}
/* 拖起来的卡片半透明，让人看出「正在搬的是这个」 */
.file-card.dragging { opacity: .4; }
/* 放置目标高亮。⚠ 用 outline 不用 border：border 会改变盒子尺寸，
   悬停时整棵树会跟着抖一下 */
.folder-node.drop-over {
  outline: 2px dashed var(--el-color-primary);
  outline-offset: 1px;
  border-radius: 4px;
  background: var(--el-color-primary-light-9);
}
</style>
