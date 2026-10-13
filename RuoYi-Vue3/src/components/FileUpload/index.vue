<template>
  <div class="upload-file">
    <el-upload
      multiple
      :action="uploadFileUrl"
      :before-upload="handleBeforeUpload"
      :file-list="fileList"
      :data="data"
      :limit="limit"
      :on-error="handleUploadError"
      :on-exceed="handleExceed"
      :on-success="handleUploadSuccess"
      :show-file-list="false"
      :headers="headers"
      class="upload-file-uploader"
      ref="fileUpload"
      v-if="!disabled"
    >
      <!-- 上传按钮 -->
      <el-button type="primary">选取文件</el-button>
    </el-upload>
    <!-- 上传提示 -->
    <div class="el-upload__tip" v-if="showTip && !disabled">
      请上传
      <template v-if="fileSize"> 大小不超过 <b style="color: #f56c6c">{{ fileSize }}MB</b> </template>
      <template v-if="fileType"> 格式为 <b style="color: #f56c6c">{{ fileType.join("/") }}</b> </template>
      的文件
    </div>
    <!--
      缩略图模式：图片直接出图，点开是大图；别的文件出一个扩展名徽标，点开是原链接。
      一眼能看出这是张图纸还是一份 PDF，比一行文件名管用。
    -->
    <div v-if="thumbnail" ref="uploadFileCards" class="upload-file-cards">
      <div v-for="(file, index) in fileList" :key="file.uid" class="upload-file-card">
        <el-image v-if="isImage(file)" :src="fileHref(file)" fit="cover" class="card-media"
          :preview-src-list="imagePreviewList" :initial-index="imageIndexOf(file)"
          preview-teleported hide-on-click-modal>
          <!-- 图挂了（文件被删、路径变过）也要占住位置，不然这一行卡片的高度会塌下去 -->
          <template #error>
            <div class="ext-fallback"><span class="ext-text">{{ fileExt(file) || '图片' }}</span></div>
          </template>
        </el-image>
        <a v-else class="card-media card-ext" :href="fileHref(file)" target="_blank">
          <span class="ext-text">{{ fileExt(file) || '文件' }}</span>
        </a>
        <div class="card-name" :title="getFileName(file.name)">{{ getFileName(file.name) }}</div>
        <el-link v-if="!disabled" class="card-remove" type="danger" underline="never"
          @click="handleDelete(index)">删除</el-link>
      </div>
    </div>

    <!-- 文件列表 -->
    <transition-group v-else ref="uploadFileList" class="upload-file-list el-upload-list el-upload-list--text" name="el-fade-in-linear" tag="ul">
      <li :key="file.uid" class="el-upload-list__item ele-upload-list__item-content" v-for="(file, index) in fileList">
        <el-link :href="fileHref(file)" underline="never" target="_blank">
          <span class="el-icon-document"> {{ getFileName(file.name) }} </span>
        </el-link>
        <div class="ele-upload-list__item-content-action">
          <el-link underline="never" @click="handleDelete(index)" type="danger" v-if="!disabled">&nbsp;删除</el-link>
        </div>
      </li>
    </transition-group>
  </div>
</template>

<script setup>
import { getToken } from "@/utils/auth"
import { isExternal } from "@/utils/validate"
import Sortable from 'sortablejs'

const props = defineProps({
  modelValue: [String, Object, Array],
  // 上传接口地址
  action: {
    type: String,
    default: "/common/upload"
  },
  // 上传携带的参数
  data: {
    type: Object
  },
  // 数量限制
  limit: {
    type: Number,
    default: 5
  },
  // 大小限制(MB)
  fileSize: {
    type: Number,
    default: 5
  },
  // 文件类型, 例如['png', 'jpg', 'jpeg']
  fileType: {
    type: Array,
    default: () => ["doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "pdf"]
  },
  // 是否显示提示
  isShowTip: {
    type: Boolean,
    default: true
  },
  // 禁用组件（仅查看文件）
  disabled: {
    type: Boolean,
    default: false
  },
  // 拖动排序
  drag: {
    type: Boolean,
    default: true
  },
  /**
   * 保留源文件名。
   *
   * 默认（false）只存 url，逗号分隔，页面上显示的是从 url 末段截出来的存储名——
   * 服务端会在原名后面缀一串序列号（FileUploadUtils.extractFilename），走 OSS 时
   * 更是整段换掉，用户看到的不是自己上传的那个文件名。
   *
   * 开启后 modelValue 变成一段 JSON：[{"name":"源文件名.png","url":"/profile/..."}]，
   * 名字按上传时的原名显示，存储名怎么定跟显示无关。字段要能装得下这段 JSON。
   */
  preserveName: {
    type: Boolean,
    default: false
  },
  /**
   * 缩略图模式。
   *
   * 默认（false）是 RuoYi 原来那种一行一个文件名的列表。开启后排成卡片：
   * 图片出缩略图、点开看大图，其它文件出一个扩展名徽标（PDF、XLSX……），
   * 名字挂在下面。附件多的时候，看图比看一串文件名快得多。
   */
  thumbnail: {
    type: Boolean,
    default: false
  }
})

/** 图片扩展名。判据只用扩展名，不去嗅探内容——列表是纯前端渲染，拿不到文件体 */
const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'ico']

const { proxy } = getCurrentInstance()
const emit = defineEmits()
const number = ref(0)
const uploadList = ref([])
const baseUrl = import.meta.env.VITE_APP_BASE_API
const uploadFileUrl = ref(import.meta.env.VITE_APP_BASE_API + props.action) // 上传文件服务器地址
const headers = ref({ Authorization: "Bearer " + getToken() })
const fileList = ref([])
const showTip = computed(
  () => props.isShowTip && (props.fileType || props.fileSize)
)

/**
 * 把 modelValue 还原成数组。
 *
 * preserveName 开着时存的是带源文件名的 JSON；解析不出来就按老格式（逗号分隔的 url）
 * 再走一遍——存量数据、别处塞进来的值都是那个格式，不能因为换了存法就显示不出来。
 */
function parseValue(value) {
  const text = String(value)
  if (props.preserveName && text.trim().startsWith('[')) {
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) return parsed
    } catch {
      // 不是合法 JSON（手改过、老数据），落回逗号分隔
    }
  }
  return text.split(',')
}

watch(() => props.modelValue, val => {
  if (val) {
    let temp = 1
    // 首先将值转为数组
    const list = Array.isArray(val) ? val : parseValue(props.modelValue)
    // 然后将数组转为对象数组
    fileList.value = list.map(item => {
      if (typeof item === "string") {
        item = { name: item, url: item }
      }
      item.uid = item.uid || new Date().getTime() + temp++
      return item
    })
  } else {
    fileList.value = []
    return []
  }
},{ deep: true, immediate: true })

// 上传前校检格式和大小
function handleBeforeUpload(file) {
  // 校检文件类型
  if (props.fileType.length) {
    const fileName = file.name.split('.')
    const fileExt = fileName[fileName.length - 1]
    const isTypeOk = props.fileType.indexOf(fileExt) >= 0
    if (!isTypeOk) {
      proxy.$modal.msgError(`文件格式不正确，请上传${props.fileType.join("/")}格式文件!`)
      return false
    }
  }
  // 校检文件名是否包含特殊字符
  if (file.name.includes(',')) {
    proxy.$modal.msgError('文件名不正确，不能包含英文逗号!')
    return false
  }
  // 校检文件大小
  if (props.fileSize) {
    const isLt = file.size / 1024 / 1024 < props.fileSize
    if (!isLt) {
      proxy.$modal.msgError(`上传文件大小不能超过 ${props.fileSize} MB!`)
      return false
    }
  }
  proxy.$modal.loading("正在上传文件，请稍候...")
  number.value++
  return true
}

// 文件个数超出
function handleExceed() {
  proxy.$modal.msgError(`上传文件数量不能超过 ${props.limit} 个!`)
}

// 上传失败
function handleUploadError(err) {
  proxy.$modal.msgError("上传文件失败")
  proxy.$modal.closeLoading()
}

// 上传成功回调
function handleUploadSuccess(res, file) {
  if (res.code === 200) {
    // originalFilename 是用户选文件时的那个名字，接口两个分支（本地盘、OSS）都会返回
    uploadList.value.push({
      name: props.preserveName ? (res.originalFilename || res.newFileName || res.fileName) : res.fileName,
      url: res.fileName
    })
    uploadedSuccessfully()
  } else {
    number.value--
    proxy.$modal.closeLoading()
    proxy.$modal.msgError(res.msg)
    proxy.$refs.fileUpload.handleRemove(file)
    uploadedSuccessfully()
  }
}

// 删除文件
function handleDelete(index) {
  fileList.value.splice(index, 1)
  emitValue()
}

// 上传结束处理
function uploadedSuccessfully() {
  if (number.value > 0 && uploadList.value.length === number.value) {
    fileList.value = fileList.value.filter(f => f.url !== undefined).concat(uploadList.value)
    uploadList.value = []
    number.value = 0
    emitValue()
    proxy.$modal.closeLoading()
  }
}

// 获取文件名称
function getFileName(name) {
  // 如果是url那么取最后的名字 如果不是直接返回
  if (name.lastIndexOf("/") > -1) {
    return name.slice(name.lastIndexOf("/") + 1)
  } else {
    return name
  }
}

/**
 * 文件的可访问地址。
 *
 * 走 OSS 时接口返回的就是一整条外链，再拼一次 baseUrl 只会拼出个 404；
 * 存本地盘时返回的是 /profile/... 这种相对路径，得补上 baseUrl 才请求得到。
 */
function fileHref(file) {
  const url = file.url || ''
  return isExternal(url) ? url : `${baseUrl}${url}`
}

/**
 * 扩展名，大写，用来做徽标文字。
 *
 * 先看文件名（preserveName 开着时它就是用户那个源文件名），名字里取不到才回头看 url——
 * 存储名一定带后缀，但它未必反映得出原来的类型。
 */
function fileExt(file) {
  const from = name => {
    const text = String(name || '').split('?')[0]
    const dot = text.lastIndexOf('.')
    return dot > -1 && dot < text.length - 1 ? text.slice(dot + 1).toLowerCase() : ''
  }
  return (from(file.name) || from(file.url)).toUpperCase()
}

function isImage(file) {
  return IMAGE_EXTENSIONS.includes(fileExt(file).toLowerCase())
}

/** 大图预览的图源，只收图片。点哪张就从哪张开始翻，靠下面的 imageIndexOf 定位 */
const imagePreviewList = computed(() => fileList.value.filter(isImage).map(fileHref))
function imageIndexOf(file) {
  return fileList.value.filter(isImage).indexOf(file)
}

/** 按 preserveName 决定落库格式：带源文件名的 JSON，或者老的逗号分隔 url */
function emitValue() {
  emit('update:modelValue', props.preserveName ? listToJson(fileList.value) : listToString(fileList.value))
}

/**
 * preserveName 开着时的落库格式。
 *
 * 只留 name 和 url 两个字段——el-upload 会往文件对象上塞 uid、status、raw 之类的东西，
 * 整个存进去既占地方，回显时也用不上。
 */
function listToJson(list) {
  const rows = list
    .filter(item => item.url)
    .map(item => ({ name: item.name || item.url, url: item.url }))
  return rows.length ? JSON.stringify(rows) : ''
}

// 对象转成指定字符串分隔
function listToString(list, separator) {
  let strs = ""
  separator = separator || ","
  for (let i in list) {
    if (list[i].url) {
      strs += list[i].url + separator
    }
  }
  return strs != '' ? strs.substr(0, strs.length - 1) : ''
}

// 初始化拖拽排序
onMounted(() => {
  if (props.drag && !props.disabled) {
    nextTick(() => {
      // 两种列表只会渲染其中一个，拿到哪个就给哪个装拖拽
      const list = proxy.$refs.uploadFileCards || proxy.$refs.uploadFileList
      const element = list?.$el || list
      if (!element) return
      Sortable.create(element, {
        ghostClass: 'file-upload-darg',
        onEnd: (evt) => {
          const movedItem = fileList.value.splice(evt.oldIndex, 1)[0]
          fileList.value.splice(evt.newIndex, 0, movedItem)
          emitValue()
        }
      })
    })
  }
})
</script>
<style scoped lang="scss">
.file-upload-darg {
  opacity: 0.5;
  background: #c8ebfb;
}
.upload-file-uploader {
  margin-bottom: 5px;
}
.upload-file-list .el-upload-list__item {
  border: 1px solid #e4e7ed;
  line-height: 2;
  margin-bottom: 10px;
  position: relative;
  transition: none !important;
}
.upload-file-list .ele-upload-list__item-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: inherit;
}
.ele-upload-list__item-content-action .el-link {
  margin-right: 10px;
}

/* ---------- 缩略图模式 ---------- */
.upload-file-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.upload-file-card {
  width: 96px;
  /* 颜色一律走 el 的 CSS 变量，暗色主题下跟着一起翻 */
  color: var(--el-text-color-regular);
  text-align: center;
}
.upload-file-card .card-media {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
  background-color: var(--el-fill-color-light);
}
/* 非图片：用扩展名当图标，认不出类型时上面兜的是「文件」两个字 */
.upload-file-card .card-ext {
  text-decoration: none;
  color: var(--el-color-primary);
  font-weight: 600;
}
/* 图片加载失败时的占位，边框由外面的 .card-media 画，这里只负责居中 */
.upload-file-card .ext-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}
.upload-file-card .ext-text {
  font-size: 15px;
  letter-spacing: 0.5px;
  /* 扩展名可能长到 XLSX、PPTX，容不下就缩，不要把边框撑破 */
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 文件名两行封顶：一长串中文文件名会把卡片拉得老高，行高参差 */
.upload-file-card .card-name {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.35;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.upload-file-card .card-remove {
  margin-top: 2px;
  font-size: 12px;
}
</style>
