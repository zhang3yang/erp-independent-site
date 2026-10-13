<!--
  富文本字段：带工具条和图片上传的 HTML 编辑框。

  <h3>为什么不是所见即所得</h3>

  详情正文存的是 HTML，写入时会过 `RichTextCleaner` 的白名单——只留结构标签
  （h2~h6 / p / ul / ol / li / strong / em / table / img / a / blockquote……），
  **class 和 style 一律剥掉**。

  contenteditable 那类编辑器生成的东西正好全在白名单外：它靠 `<span style="...">`
  表达格式。用它的结果是「在管理台里排得好好的，保存完回来全没了」，而且不报错，
  商家只会觉得系统坏了。所以这里是**源码 + 工具条 + 预览**：
  工具条只吐白名单里的标签，预览用的是和前台同一套排版规则，
  所见即所得的那部分由预览兑现，不由编辑器假装。

  <h3>依赖</h3>

  和 MediaPicker 一样不 import 任何宿主的东西（station-shared/README.md 那条），
  上传接口由宿主 provide 进来：

      app.provide('stationMediaApi', { …, uploadImage })

  `uploadImage(formData, onUploadProgress)` 的签名和 `api/console.js` 的
  `uploadSiteImage` 一致。宿主没递的话「上传图片」按钮会禁用并给出提示，
  「从文件库选图」仍然可用——少一个功能，不是整个组件废掉。
-->
<template>
  <div class="rtf">
    <div class="rtf-bar">
      <el-button-group size="small">
        <el-button @click="wrapBlock('h3')">{{ st('rtf.heading', '小标题') }}</el-button>
        <el-button @click="wrapBlock('p')">{{ st('rtf.para', '段落') }}</el-button>
        <el-button @click="wrapInline('strong')">{{ st('rtf.bold', '加粗') }}</el-button>
        <el-button @click="makeList">{{ st('rtf.list', '列表') }}</el-button>
      </el-button-group>

      <el-button-group size="small">
        <el-button icon="Picture" @click="pickerOpen = true">
          {{ st('rtf.pickImage', '从文件库选图') }}
        </el-button>
        <el-button
          icon="Upload"
          :loading="uploading"
          :disabled="!canUpload"
          :title="canUpload ? '' : st('rtf.noUpload', '宿主没有接上传接口')"
          @click="pickFile"
        >{{ st('rtf.uploadImage', '上传图片') }}</el-button>
        <!-- 删图：业务员不该为了删一张图去源码里找 <img> 标签并精确选中它 -->
        <el-button
          icon="Delete"
          :disabled="!hasImageAtCaret"
          :title="hasImageAtCaret ? '' : st('rtf.noImageHere', '光标旁边没有图片')"
          @click="removeImageAtCaret"
        >{{ st('rtf.delImage', '删除图片') }}</el-button>
      </el-button-group>

      <el-button size="small" :type="previewing ? 'primary' : ''" @click="previewing = !previewing">
        {{ st('rtf.preview', '预览') }}
      </el-button>

      <span class="rtf-hint">{{ st('rtf.hint', '只保留结构标签，class 和 style 保存时会被过滤掉') }}</span>
    </div>

    <!-- 原生 input：只要「选一个文件」这一件事，el-upload 那一圈钩子在这里是负担 -->
    <input ref="fileEl" type="file" accept="image/*" hidden @change="onFile">

    <el-input
      ref="taEl"
      v-model="inner"
      type="textarea"
      :rows="rows"
      :placeholder="placeholder"
    />

    <el-progress v-if="uploading" :percentage="percent" :stroke-width="3" class="rtf-progress" />

    <div v-if="previewing" class="rtf-preview">
      <div class="rtf-preview-label">{{ st('rtf.previewLabel', '预览 —— 前台大致长这样') }}</div>
      <!-- eslint-disable-next-line vue/no-v-html -- 预览的是本人正在编辑的内容，且保存时会过白名单 -->
      <div class="rtf-body" v-html="inner"></div>
    </div>

    <MediaPicker v-model="pickerOpen" type="image" :limit="0" @confirm="insertImages" />
  </div>
</template>

<script setup>
import { computed, inject, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MediaPicker from './MediaPicker.vue'
import { useStationT } from './useStationT.js'
import { imgSrcs, imgBlock, srcOf } from './imgTags.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  rows: { type: Number, default: 14 },
  placeholder: { type: String, default: '' }
})
// `insert` / `remove` 只上报「动了哪张图」，**同步不同步由宿主决定**。
// 组件本身不认识"语言"这个概念（博客只有一个正文，商品详情有六个）——
// 把这个决定放进组件，它会开始猜宿主的数据模型。
const emit = defineEmits(['update:modelValue', 'insert', 'remove'])

const st = useStationT()
const mediaApi = inject('stationMediaApi', null)
const canUpload = computed(() => typeof mediaApi?.uploadImage === 'function')

const inner = computed({
  get: () => props.modelValue || '',
  set: v => emit('update:modelValue', v)
})

const taEl = ref(null)
const fileEl = ref(null)
const pickerOpen = ref(false)
const previewing = ref(false)
const uploading = ref(false)
const percent = ref(0)

/**
 * 拿原生 textarea。
 *
 * ⚠ 不能只认 `taEl.value.textarea`：Element 的内部 ref 名字跨版本变过，
 * 认不到就回落到 DOM 里找。找不到时所有插入操作退化成「追加到末尾」，
 * 比抛异常强——商家至少还能用。
 */
function nativeTa() {
  return taEl.value?.textarea || taEl.value?.$el?.querySelector('textarea') || null
}

/**
 * 在光标处替换选区。`select` 给出插入后要选中的范围（相对于插入文本），
 * 不给就把光标停在插入内容之后。
 */
function replaceSelection(build) {
  const ta = nativeTa()
  const text = inner.value
  const start = ta ? ta.selectionStart : text.length
  const end = ta ? ta.selectionEnd : text.length
  const picked = text.slice(start, end)

  const { out, caret } = build(picked)
  inner.value = text.slice(0, start) + out + text.slice(end)

  nextTick(() => {
    const t = nativeTa()
    if (!t) return
    const pos = start + (caret ?? out.length)
    t.focus()
    t.setSelectionRange(pos, pos)
  })
}

/** 前后各留一个空行：源码里块级标签各占一行，回头人肉看得懂 */
function block(html) {
  return `\n${html}\n`
}

function wrapBlock(tag) {
  replaceSelection(sel => {
    const body = sel || st('rtf.sampleText', '在这里写内容')
    return { out: block(`<${tag}>${body}</${tag}>`), caret: null }
  })
}

function wrapInline(tag) {
  replaceSelection(sel => {
    const body = sel || st('rtf.sampleText', '在这里写内容')
    return { out: `<${tag}>${body}</${tag}>`, caret: null }
  })
}

/** 选中几行就生成几个 li；没选就给一个空壳 */
function makeList() {
  replaceSelection(sel => {
    const lines = String(sel).split(/\r?\n/).map(s => s.trim()).filter(Boolean)
    const items = lines.length ? lines : [st('rtf.sampleItem', '第一条')]
    const li = items.map(s => `  <li>${s}</li>`).join('\n')
    return { out: block(`<ul>\n${li}\n</ul>`), caret: null }
  })
}

/**
 * 插图。
 *
 * ⚠ **已经在正文里的图不再插**（2026-09-25）。esvafit 的 EB-BOOT-01 就是这么坏的：
 *   同一组三张图在默认语言里被插了三遍——插进去之后编辑框里只是多了一行 `<img>` 源码，
 *   商家看不出插上了，就又插一次。每插一次宿主还会把这组图同步到其它五种语言，
 *   于是六种语言的详情里全是三份一样的图。
 *   真想让同一张图出现两次的情况极少，而重复插入是实实在在发生过的，所以直接跳过并说一声。
 */
function insertImages(urls) {
  const picked = [...new Set((urls || []).filter(Boolean))]
  if (!picked.length) return
  const present = new Set(imgSrcs(inner.value))
  const list = picked.filter(url => !present.has(url))
  const skipped = picked.length - list.length
  if (skipped) {
    ElMessage.warning(st('rtf.dupSkipped', '有 {n} 张图已经在正文里了，没有重复插入', { n: skipped }))
  }
  if (!list.length) return
  const html = imgBlock(list)
  // 插入点在「插入之前」的文本里的偏移。宿主靠它把同一张图同步到别的语言
  // （商品详情是按语言各存一份，商家在默认语言插了图，其它语言不会自己出现）。
  const ta = nativeTa()
  const at = ta ? ta.selectionStart : inner.value.length
  replaceSelection(() => ({ out: html, caret: null }))
  // urls 给宿主做同步时查重用：目标语言里已经有的那几张不该再追加一遍
  emit('insert', { html, at, urls: list })
}

/* ---------------- 删除图片 ---------------- */

/** 光标 / 选区。⚠ `selectionStart` 不是响应式的，读它不会建立依赖——见下面 hasImageAtCaret */
function caret() {
  const ta = nativeTa()
  const text = inner.value
  return {
    start: ta ? ta.selectionStart : text.length,
    end: ta ? ta.selectionEnd : text.length
  }
}

/**
 * 在给定文本 + 光标位置里，找那一张 `<img>`。
 *
 * <p>纯函数（不碰 DOM），这样 computed 里的依赖是 `text` 和 `start/end` 两个数字，
 * 光标一动就重算——按钮的可用态才会跟着变。</p>
 *
 * @returns {{from:number,to:number,tag:string}|null}
 */
function findImageAt(text, start, end) {
  const tagRe = /<img\b[^>]*>/gi

  // ① 有选区：只认完全落在选区里的，避免误删选区外的
  if (end > start) {
    const picked = text.slice(start, end)
    const m = picked.match(tagRe)
    if (!m) return null
    const from = start + picked.search(tagRe)
    return { from, to: from + m[0].length, tag: m[0] }
  }

  // ② 没选区：找包含光标的那一个
  tagRe.lastIndex = 0
  let m
  while ((m = tagRe.exec(text))) {
    const from = m.index
    const to = from + m[0].length
    if (start >= from && start <= to) return { from, to, tag: m[0] }
  }
  // ③ 光标紧贴在标签前（`>|<img` 这种）也算
  tagRe.lastIndex = 0
  while ((m = tagRe.exec(text))) {
    if (m.index === start) return { from: m.index, to: m.index + m[0].length, tag: m[0] }
  }
  return null
}

/**
 * 按钮的可用态：光标旁边有没有图。
 *
 * ⚠ 这里必须**显式读一次光标位置**。`selectionStart` 不是响应式数据，只调
 * `findImageAt(inner.value, ...)` 的话依赖里只有 `inner.value`——光标在正文里
 * 移来移去按钮一直不变灰，商家点了才知道没图。多写这两行是刻意的。
 */
const hasImageAtCaret = computed(() => {
  const { start, end } = caret()
  return !!findImageAt(inner.value, start, end)
})

/**
 * 删掉光标处那张图。
 *
 * <p>⚠ 只删 `<img>` 本身（以及它独占那一行留下的空行），
 * <b>不删同一行的其它内容</b>：商家可能把图和文字写在一行里，整行删掉等于
 * 把文字一起吃了。</p>
 *
 * <p>删完把光标停在原处，方便连着删下一张。</p>
 */
function removeImageAtCaret() {
  const { start, end } = caret()
  const hit = findImageAt(inner.value, start, end)
  if (!hit) {
    ElMessage.warning(st('rtf.noImageHere', '光标旁边没有图片'))
    return
  }
  const text = inner.value
  let from = hit.from
  let to = hit.to
  // 这一行除了这张图只剩空白 → 整行连换行一起删，不留空行
  const lineStart = text.lastIndexOf('\n', from - 1) + 1
  const nl = text.indexOf('\n', to)
  const lineEnd = nl < 0 ? text.length : nl
  if (text.slice(lineStart, from).trim() === '' && text.slice(to, lineEnd).trim() === '') {
    from = lineStart
    to = nl < 0 ? text.length : nl + 1
  }
  inner.value = text.slice(0, from) + text.slice(to)
  nextTick(() => {
    const t = nativeTa()
    if (!t) return
    const pos = Math.min(from, inner.value.length)
    t.focus()
    t.setSelectionRange(pos, pos)
  })
  emit('remove', { tag: hit.tag, src: srcOf(hit.tag) })
  ElMessage.success(st('rtf.imgRemoved', '图片已删除，别忘了保存'))
}

function pickFile() {
  if (!canUpload.value) return
  if (fileEl.value) fileEl.value.value = ''
  fileEl.value?.click()
}

/**
 * 宽高在浏览器里先量好再传，和文件资源页同一个做法：
 * 选文件时本来就要解码一次，服务端再解一遍是白花的 CPU。量不到就不传。
 */
function measure(file) {
  return new Promise(resolve => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => { URL.revokeObjectURL(url); resolve({ width: img.naturalWidth, height: img.naturalHeight }) }
    img.onerror = () => { URL.revokeObjectURL(url); resolve({}) }
    img.src = url
  })
}

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return

  uploading.value = true
  percent.value = 0
  try {
    const meta = await measure(file)
    const form = new FormData()
    form.append('file', file)
    if (meta.width) form.append('width', meta.width)
    if (meta.height) form.append('height', meta.height)

    const { data } = await mediaApi.uploadImage(form, ev => {
      if (ev.total) percent.value = Math.round((ev.loaded * 100) / ev.total)
    })
    // 后端返回整条记录；只有 URL 是详情里要的
    const url = data?.imageUrl || data?.url
    if (!url) throw new Error('no url')
    insertImages([url])
    ElMessage.success(st('rtf.uploaded', '已插入，图片同时进了文件资源库'))
  } catch (err) {
    ElMessage.error(st('rtf.uploadFailed', '上传失败') + (err?.message ? `：${err.message}` : ''))
  } finally {
    uploading.value = false
    percent.value = 0
  }
}
</script>

<style scoped>
.rtf-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.rtf-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.rtf-progress { margin-top: 6px; }

.rtf-preview {
  margin-top: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  padding: 14px 16px;
  background: var(--el-fill-color-lighter);
}
.rtf-preview-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 10px;
}

/*
  ⚠ 这几条要和前台 pages/products/[code].vue 的 .desc-body 保持一致。
  预览的意义就是「和线上一样」，两边飘了的话预览反而误导人。
  两边都改的时候记得一起改。
*/
.rtf-body { max-width: 640px; line-height: 1.9; color: var(--el-text-color-primary); }
.rtf-body :deep(h2),
.rtf-body :deep(h3),
.rtf-body :deep(h4) {
  font-weight: 700;
  line-height: 1.5;
  margin: 28px 0 12px;
  padding-left: 12px;
  border-left: 3px solid var(--el-color-primary);
}
.rtf-body :deep(h2) { font-size: 18px; }
.rtf-body :deep(h3) { font-size: 16px; }
.rtf-body :deep(h4) { font-size: 14px; }
.rtf-body :deep(> :first-child) { margin-top: 0; }
.rtf-body :deep(p) { margin: 0 0 14px; }
.rtf-body :deep(ul),
.rtf-body :deep(ol) { margin: 0 0 16px; padding: 0; list-style: none; }
.rtf-body :deep(li) { position: relative; padding-left: 22px; margin-bottom: 8px; }
.rtf-body :deep(ul) > li::before {
  content: '';
  position: absolute;
  left: 5px;
  top: .82em;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--el-color-primary);
}
.rtf-body :deep(ol) { counter-reset: rtfitem; }
.rtf-body :deep(ol) > li { counter-increment: rtfitem; }
.rtf-body :deep(ol) > li::before {
  content: counter(rtfitem) '.';
  position: absolute;
  left: 0;
  top: 0;
  color: var(--el-color-primary);
  font-weight: 700;
}
.rtf-body :deep(img) {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 16px 0;
}
.rtf-body :deep(table) { border-collapse: collapse; width: 100%; margin: 14px 0; }
.rtf-body :deep(th),
.rtf-body :deep(td) { border: 1px solid var(--el-border-color); padding: 6px 10px; text-align: left; }
.rtf-body :deep(th) { background: var(--el-fill-color-light); }
</style>
