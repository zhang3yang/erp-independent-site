<template>
  <!--
    商品 3D「模型状态」编辑区（2026-10-08，首个用例：腿套没气 / 半充 / 充满）。
    设计：docs/superpowers/specs/2026-10-08-product-3d-states-design.md

    - 第一行永远是主模型（就是上面挂的那个 .glb），只能改名字，不能删、不能挪。
    - 其余每行一个 .glb；可以先建好名字、模型以后补——没模型的行前台不显示。
    - 名字分语言，跟着这里自己的语言开关走；「全部语言翻译」由 Goods.vue 顺带补空。
    - logo 只在主模型上标；切到别的状态时前台自动投影过去，「预览全部状态」能看到效果。

    rows 是 Goods.vue 传进来的响应式数组，这里直接改（同 ComboMatrix 的做法），
    保存时由 Goods.vue 的 statesPayload 组 JSON。
  -->
  <div class="ms">
    <div v-if="!mainModel" class="hint">{{ t('goods.states.needMain') }}</div>
    <template v-else>
      <el-button v-if="!rows.length" size="small" icon="Plus" @click="enable">{{ t('goods.states.enable') }}</el-button>
      <template v-else>
        <div v-if="langs.length > 1" class="ms-langbar">
          <span class="hint">{{ t('goods.spec.editing') }}</span>
          <el-radio-group v-model="lang" size="small">
            <el-radio-button v-for="l in langs" :key="l" :value="l">
              {{ l }}<span v-if="l === defaultLang" class="lang-flag">{{ t('goods.defaultLangFlag') }}</span>
            </el-radio-button>
          </el-radio-group>
        </div>

        <div v-for="(row, index) in rows" :key="row.id" class="ms-row">
          <span class="ms-no">{{ index + 1 }}</span>
          <el-input v-model="row.name[lang]" class="ms-name" maxlength="30"
            :placeholder="lang === defaultLang ? t('goods.states.namePlaceholder') : (row.name[defaultLang] || t('goods.states.namePlaceholder'))" />
          <template v-if="index === 0">
            <el-tag size="small">{{ t('goods.states.main') }}</el-tag>
            <span class="ms-file" :title="mainModel">{{ fileName(mainModel) }}</span>
          </template>
          <template v-else>
            <span v-if="row.model" class="ms-file" :title="row.model">{{ fileName(row.model) }}</span>
            <span v-else class="ms-file ms-missing">{{ t('goods.states.noModel') }}</span>
            <el-button link type="primary" size="small" @click="pickFor(index)">
              {{ row.model ? t('goods.media.replace') : t('goods.states.pickModel') }}
            </el-button>
            <el-button link size="small" :disabled="index <= 1" @click="move(index, -1)">{{ t('goods.states.up') }}</el-button>
            <el-button link size="small" :disabled="index >= rows.length - 1" @click="move(index, 1)">{{ t('goods.states.down') }}</el-button>
            <el-button v-if="!fixedCount" link type="danger" size="small" @click="rows.splice(index, 1)">{{ t('goods.media.remove') }}</el-button>
          </template>
        </div>

        <div class="ms-actions">
          <el-button v-if="rows.length < stateLimit" link type="primary" icon="Plus" @click="addRow">
            {{ t('goods.states.add', { n: stateLimit }) }}
          </el-button>
          <el-button link type="primary" :disabled="previewStates.length < 2" @click="previewOpen = true">
            {{ t('goods.states.previewAll') }}
          </el-button>
          <el-button v-if="!fixedCount" link type="danger" @click="rows.splice(0, rows.length)">{{ t('goods.states.disable') }}</el-button>
        </div>
        <div class="hint">{{ t('goods.states.hint') }}</div>
      </template>
    </template>

    <media-picker v-model="pickerOpen" type="model" :selected="pickIndex > 0 && rows[pickIndex]?.model ? [rows[pickIndex].model] : []"
      :limit="1" @confirm="onPicked" />
    <el-dialog v-model="previewOpen" :title="t('goods.states.previewAll')" width="760px" append-to-body destroy-on-close>
      <model-preview v-if="previewOpen" :src="mainModel" :height="440" :states="previewStates" :marks="marks || null" />
      <div class="hint mt8">{{ t('goods.states.previewHint') }}</div>
    </el-dialog>
  </div>
</template>

<script setup name="ModelStatesField">
import { ref, computed, watch, defineAsyncComponent } from 'vue'
import MediaPicker from '@station-shared/MediaPicker.vue'
import { t } from '@/i18n'

// 异步：three.js 只在点「预览全部状态」时才下
const ModelPreview = defineAsyncComponent(() => import('@station-shared/ModelPreview.vue'))

/** 和后端 Model3dStates.MAX_STATES 一致 */
const MAX_STATES = 5

const props = defineProps({
  /** [{ id, name: { 语言: 名字 }, model }]，第一行是主模型（model 不用） */
  rows: { type: Array, required: true },
  langs: { type: Array, required: true },
  defaultLang: { type: String, required: true },
  /** 商品的主模型地址；没有时整区禁用 */
  mainModel: { type: String, default: '' },
  /** 主模型上的标注 JSON（预览时画 logo 并投影） */
  marks: { type: String, default: '' },
  /** Require an exact count for per-variant inflation states. */
  fixedCount: { type: Number, default: 0 }
})

const stateLimit = computed(() => props.fixedCount > 0 ? props.fixedCount : MAX_STATES)

const lang = ref(props.defaultLang)
watch(() => props.defaultLang, v => { lang.value = v })

const pickerOpen = ref(false)
const pickIndex = ref(-1)
const previewOpen = ref(false)

const fileName = url => {
  try { return decodeURIComponent(String(url).split('?')[0].split('/').pop() || url) } catch { return url }
}

function nextId() {
  let n = 1
  const used = new Set(props.rows.map(r => r.id))
  while (used.has('s' + n)) n++
  return 's' + n
}

function newRow() {
  return { id: nextId(), name: Object.fromEntries(props.langs.map(l => [l, ''])), model: '' }
}

function enable() {
  const initial = props.fixedCount > 0 ? props.fixedCount : 2
  for (let i = 0; i < initial; i++) props.rows.push(newRow())
}

function addRow() {
  if (props.rows.length < stateLimit.value) props.rows.push(newRow())
}

function move(index, delta) {
  const to = index + delta
  if (index < 1 || to < 1 || to >= props.rows.length) return
  const [row] = props.rows.splice(index, 1)
  props.rows.splice(to, 0, row)
}

function pickFor(index) {
  pickIndex.value = index
  pickerOpen.value = true
}

function onPicked(urls) {
  const row = props.rows[pickIndex.value]
  if (row && urls[0]) row.model = urls[0]
}

/** 预览用：同前台下发的形状——第一个 + 有模型的，名字取当前语言、缺了回退默认语言 */
const previewStates = computed(() => props.rows
  .filter((r, i) => i === 0 || r.model)
  .map((r, i) => ({
    id: r.id,
    name: (r.name[lang.value] || r.name[props.defaultLang] || r.id).trim(),
    ...(i === 0 ? {} : { model: r.model })
  })))
</script>

<style scoped>
.ms-langbar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.ms-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.ms-no { width: 18px; color: #909399; text-align: right; }
.ms-name { width: 180px; }
.ms-file { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #606266; font-size: 13px; }
.ms-missing { color: #c0c4cc; }
.ms-actions { display: flex; gap: 14px; margin: 4px 0 6px 28px; }
.lang-flag { margin-left: 4px; font-size: 11px; opacity: .7; }
.mt8 { margin-top: 8px; }
</style>
