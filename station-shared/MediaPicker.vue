<!--
  文件资源库选择器。商品、博客封面、首页装修的图片格子都用它。

  ## 宿主要提供两样东西

  1. `provide('stationMediaApi', { listImage, listVideo, listFolder })`——三个列表接口，
     签名和 station-console-ui 的 `api/console.js` 里同名那三个一致。见下面 script 里的注释。
  2. 全局注册的 `<pagination>` 组件（两个宿主都在 main.js 里注册了）。

  ## 为什么在 station-shared 而不是宿主自己的 components 下

  首页装修表单（DecorateForm.vue）在这个目录里，它要用挑图。共用目录里的组件不能
  反过来 import 某个宿主的 `@/`——那是 README 里「谁都不 import 谁」那条。
  所以它得跟着搬过来，宿主的 Goods.vue / Blog.vue 改成从 `@station-shared/` 引。
-->
<template>
  <el-dialog v-model="visible" :title="st('picker.title', `从文件资源库选择${typeLabel}`, { type: typeLabel })" width="760px" append-to-body
    @open="handleOpen">
    <div class="picker-body">
      <!--
        左侧目录树。只读——在这里只是「按文件夹筛」，建/改/删都在「文件资源」页做。
        选择器里塞增删改，会让人在挑图的半路上误删文件夹。
      -->
      <aside class="picker-side">
        <el-tree
          class="picker-tree"
          :data="folderTreeData"
          node-key="folderId"
          :props="{ label: 'folderName', children: 'children' }"
          :expand-on-click-node="false"
          :current-node-key="currentFolderId"
          highlight-current
          default-expand-all
          :empty-text="st('files.noFolder', '还没有文件夹')"
          @node-click="handleFolderClick">
          <template #default="{ data }">
            <span class="picker-node">
              <span class="picker-node-name" :title="data.folderName">{{ data.folderName }}</span>
              <span v-if="data.fileCount" class="picker-node-count">{{ data.fileCount }}</span>
            </span>
          </template>
        </el-tree>
      </aside>

      <div class="picker-main">
    <div class="picker-head">
      <el-input v-model="queryName" class="picker-search" clearable :placeholder="st('files.searchName', '按名称搜索')"
        @keyup.enter="reload" @clear="reload">
        <template #append><el-button icon="Search" @click="reload" /></template>
      </el-input>
      <span class="picker-count">
        {{ st('picker.picked', '已选', {}) }} {{ picked.length }}<template v-if="limit"> / {{ limit }}</template>
      </span>
    </div>

    <div v-loading="loading" class="picker-grid">
      <div v-for="item in list" :key="urlOf(item)" class="picker-card"
        :class="{ on: isPicked(item) }" @click="toggle(item)">
        <el-image v-if="isImage" :src="item.thumbUrl || item.imageUrl" fit="contain" class="picker-media">
          <template #error><div class="picker-err">{{ st('files.loadFailed', '加载失败') }}</div></template>
        </el-image>
        <!--
          视频用 preload="metadata" 显示首帧当封面，不额外生成封面文件。
          不给 controls：这里是选择器，点卡片是「选中」而不是「播放」，
          放上播放条会让人点了半天不知道为什么没选上。
        -->
        <video v-else-if="isVideo" class="picker-media" preload="metadata" muted :src="item.videoUrl" />
        <!--
          模型没有封面：一个立方体图标 + 「预览」。预览按钮 .stop，否则点它等于点卡片（选中）。
          three.js 只在点预览时才下（ModelPreview 是异步组件）。
        -->
        <div v-else class="picker-media picker-model">
          <!-- 内联 SVG 而不是 icons-vue：共用目录里的裸导入要靠宿主 vite 别名，少一个依赖少一个坑 -->
          <svg class="picker-model-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 2.5 3.5 7v10L12 21.5l8.5-4.5V7L12 2.5Zm0 2.3 6 3.2-6 3.2-6-3.2 6-3.2ZM5.5 9.3l5.5 2.9v6.6l-5.5-2.9V9.3Zm7.5 9.5v-6.6l5.5-2.9v6.6L13 18.8Z"
                  fill="currentColor"/>
          </svg>
          <el-button link type="primary" size="small" @click.stop="openPreview(item)">{{ st('common.preview', '预览') }}</el-button>
        </div>

        <!-- 序号而不是对勾：多选时顺序有意义，第一个是主图/主视频 -->
        <span v-if="isPicked(item)" class="picker-badge">{{ indexOf(item) + 1 }}</span>
        <div class="picker-name" :title="nameOf(item)">{{ nameOf(item) }}</div>
      </div>
      <el-empty v-if="!loading && !list.length" class="picker-empty"
        :description="st('picker.empty', `文件资源库里还没有${typeLabel}，先去「文件资源」上传`, { type: typeLabel })" />
    </div>

    <pagination v-show="total > 0" v-model:page="queryPage" v-model:limit="queryLimit"
      :total="total" :page-sizes="[12, 24, 48]" @pagination="loadList" />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ st('common.cancel', '取消') }}</el-button>
      <el-button type="primary" @click="confirm">{{ st('common.confirm', '确定') }}</el-button>
    </template>

    <!-- 模型预览。嵌在选择器对话框里再开一层，destroy-on-close 让 WebGL 上下文随关闭释放 -->
    <el-dialog v-model="previewOpen" :title="st('goods.media.modelPreview', '3D 模型预览')" width="720px" append-to-body destroy-on-close>
      <model-preview v-if="previewOpen && previewUrl" :src="previewUrl" :height="420" />
    </el-dialog>
  </el-dialog>
</template>

<script setup name="SiteMediaPicker">
import { ref, computed, inject, defineAsyncComponent } from 'vue'
import { ElMessage } from 'element-plus'
import { useStationT } from './useStationT'

// 同目录相对引用，不走宿主别名（共用目录不许依赖某个宿主）。异步：three.js 只在点预览时才下
const ModelPreview = defineAsyncComponent(() => import('./ModelPreview.vue'))
const previewOpen = ref(false)
const previewUrl = ref('')
function openPreview(item) {
  previewUrl.value = item.modelUrl
  previewOpen.value = true
}

/**
 * 三个列表接口由宿主注入，不在这里 import。
 *
 * 这个文件搬到 station-shared 之前写的是 `from '@/api/console'`——那个别名指向
 * station-console-ui 自己的 src，搬过来就解析不到了。而且 README 里那条
 * 「谁都不 import 谁」的约束，本来也不许共用目录反过来依赖某一个宿主。
 *
 * 宿主在 main.js 里 provide 一次（见 station-console-ui/src/main.js），
 * 三个函数的签名和 api/console.js 里同名的那三个一致。
 */
const mediaApi = inject('stationMediaApi', null)
if (!mediaApi) {
  // 早点炸在控制台，比弹窗打开后空着一片让人以为「文件库里没东西」强
  console.error('[MediaPicker] 宿主没有 provide("stationMediaApi")，选择器读不到文件列表')
}
const listSiteImage = query => mediaApi.listImage(query)
const listSiteVideo = query => mediaApi.listVideo(query)
// 2026-09-20 加。老宿主没 provide listModel 的话，type="model" 会在这里炸出一个清楚的错，而不是空列表
const listSiteModel = query => mediaApi.listModel(query)
const listSiteFolder = mediaType => mediaApi.listFolder(mediaType)

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** image | video | model —— 三种在后端是三张表、三组接口 */
  type: { type: String, default: 'image' },
  /** 已选中的 URL 列表，用来回显勾选状态 */
  selected: { type: Array, default: () => [] },
  /** 最多能选几个，0 表示不限 */
  limit: { type: Number, default: 0 }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const isVideo = computed(() => props.type === 'video')
const isModel = computed(() => props.type === 'model')
// 认不出来的一律当图片，和后端 SiteFolderService.normalizeType 一致
const isImage = computed(() => !isVideo.value && !isModel.value)
const st = useStationT()

const typeLabel = computed(() => (isVideo.value
  ? st('files.type.video', '视频')
  : (isModel.value ? st('files.type.model', '3D 模型') : st('files.type.image', '图片'))))
const mediaType = computed(() => isVideo.value ? 'video' : isModel.value ? 'model' : 'image')

const list = ref([])
const total = ref(0)
const loading = ref(false)
const queryName = ref('')
const queryPage = ref(1)
const queryLimit = ref(12)

// 存 URL 而不是整条记录：外部只认 URL，
// 而且翻页之后 list 里没有上一页的记录了，靠 URL 才能保住跨页的选择
const picked = ref([])

const urlOf = item => isVideo.value ? item.videoUrl : isModel.value ? item.modelUrl : item.imageUrl
const nameOf = item => isVideo.value ? item.videoName : isModel.value ? item.modelName : item.imageName

// ---------------- 文件夹筛选 ----------------
//
// 只读。建/改/删都在「文件资源」页做——在挑图的半路上误删文件夹的代价太大。
//
// currentFolderId 三个状态和后端约定一致：'' 全部 / 'ROOT' 未分类 / 具体 id。
const folders = ref([])
const currentFolderId = ref('')

// 虚拟节点拼在真实树前面，库里没有对应的行
const folderTreeData = computed(() => [
  /* ⚠ 这两个是虚拟节点，folderName 在这里是**界面文字**，显示时走 folderLabel() 翻译 */
  { folderId: '', folderName: '全部', virtual: true, children: [] },
  { folderId: 'ROOT', folderName: '未分类', virtual: true, children: [] },
  ...folders.value
])

async function loadFolders() {
  try {
    const response = await listSiteFolder(mediaType.value)
    folders.value = response.data || []
  } catch {
    // 树读不出来不该挡住挑图：退化成没有文件夹，右边照常显示全部
    folders.value = []
  }
}

function handleFolderClick(data) {
  currentFolderId.value = data.folderId
  queryPage.value = 1
  loadList()
}

/** 打开弹窗时一起读：树和列表都要按当前的 type 来，而 type 是 prop，可能每次不同 */
function handleOpen() {
  currentFolderId.value = ''
  loadFolders()
  loadList()
}

function loadList() {
  // 每次打开都用外部传进来的当前值初始化，避免上次的选择残留
  if (!picked.value.length || !visible.value) {
    picked.value = [...props.selected]
  }
  loading.value = true
  const query = { pageNum: queryPage.value, pageSize: queryLimit.value }
  // 空串 = 全部（不带这个参数）；ROOT = 只看未分类；具体 id = 只看那个文件夹
  if (currentFolderId.value) query.folderId = currentFolderId.value
  if (queryName.value) {
    // 三张表的名称列不同名，查询参数也得分开传
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

function reload() {
  queryPage.value = 1
  loadList()
}

const isPicked = item => picked.value.includes(urlOf(item))
const indexOf = item => picked.value.indexOf(urlOf(item))

function toggle(item) {
  const url = urlOf(item)
  const index = picked.value.indexOf(url)
  if (index >= 0) {
    picked.value.splice(index, 1)
    return
  }
  /*
   * limit=1 是「单选」，点另一张就是**换一张**，不是「超额」。
   *
   * 原来这里一视同仁地弹「最多选 1 个」，于是换图要点两下：先把当前那张点掉，
   * 再点新的——而第一下看起来什么也没发生（选中框消失而已），
   * 多数人会以为选择器坏了。用装修的图片格子换背景图时最明显。
   *
   * 两张以上仍然提示：那时候「超额」是真的，用户得自己决定去掉哪一张。
   */
  if (props.limit === 1) {
    picked.value = [url]
    return
  }
  if (props.limit && picked.value.length >= props.limit) {
    ElMessage.warning(st('picker.max', `最多选 ${props.limit} 个`, { n: props.limit }))
    return
  }
  picked.value.push(url)
}

function confirm() {
  emit('confirm', [...picked.value])
  visible.value = false
}
</script>

<style scoped lang="scss">
.picker-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.picker-search { width: 240px; }
.picker-count { color: var(--el-text-color-secondary); font-size: 13px; }

.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  min-height: 240px;
  max-height: 420px;
  overflow-y: auto;
}
.picker-card {
  position: relative;
  border: 2px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color .2s;
}
.picker-card:hover { border-color: var(--el-color-primary-light-5); }
.picker-card.on { border-color: var(--el-color-primary); }

/* 图片和视频用同一个尺寸，两种卡片在网格里等宽等高 */
.picker-media {
  display: block;
  width: 100%;
  height: 110px;
  object-fit: contain;
  background: var(--el-fill-color-light);
}
.picker-model {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.picker-model-icon { width: 40px; height: 40px; color: var(--el-color-primary); }
.picker-err {
  display: grid;
  place-items: center;
  height: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.picker-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
}
.picker-name {
  padding: 4px 6px;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.picker-empty { grid-column: 1 / -1; }

/* ---------------- 目录树 ---------------- */
/* 右栏 min-width: 0——否则图片网格会把左侧树挤没 */
.picker-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.picker-main {
  flex: 1;
  min-width: 0;
}
.picker-side {
  flex: 0 0 170px;
  max-height: 420px;
  overflow-y: auto;
  padding: 6px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.picker-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  overflow: hidden;
}
.picker-node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.picker-node-count {
  flex: 0 0 auto;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 16px;
}
</style>
