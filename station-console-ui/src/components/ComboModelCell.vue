<template>
  <!--
    规格（组合）各自的 3D 模型，一格（2026-10-09）。平铺组合表、规格矩阵的格子共用。
    设计：docs/superpowers/specs/2026-10-09-combo-model3d-design.md

    target 是那一行 / 那一格的对象，这里**就地改**它的 model3d、model3dMarks（同 ComboMatrix 的做法）。
    - model3dMarks 空 = logo 跟随主模型（前台自动投影）；有值 = 单独标注（logo / 屏幕 / 开机键都是它自己的）。
    - 清掉模型时标注一起清（后端也会清：标注离开模型没有意义）。
  -->
  <div class="cmc">
    <template v-if="target.model3d">
      <span class="cmc-file" :title="target.model3d">{{ fileName(target.model3d) }}</span>
      <el-tag size="small" :type="target.model3dMarks ? 'success' : 'info'">
        {{ target.model3dMarks ? t('goods.comboModel.own') : t('goods.comboModel.follow') }}
      </el-tag>
      <div class="cmc-actions">
        <el-button link type="primary" size="small" :disabled="disabled" @click="previewOpen = true">{{ t('common.preview') }}</el-button>
        <el-button link type="primary" size="small" :disabled="disabled" @click="marksOpen = true">{{ t('goods.comboModel.mark') }}</el-button>
        <el-button link type="primary" size="small" :disabled="disabled" @click="statesOpen = true">{{ t('goods.states.title') }} ({{ target.model3dStateRows?.length || 0 }}/3)</el-button>
        <el-button v-if="target.model3dMarks" link size="small" :disabled="disabled" @click="unmark">{{ t('goods.comboModel.unmark') }}</el-button>
        <el-button link type="primary" size="small" :disabled="disabled" @click="pickerOpen = true">{{ t('goods.media.replace') }}</el-button>
        <el-button link type="danger" size="small" :disabled="disabled" @click="clear">{{ t('goods.combo.clear') }}</el-button>
      </div>
    </template>
    <el-button v-else link type="primary" size="small" :disabled="disabled" @click="pickerOpen = true">
      {{ t('goods.comboModel.pick') }}
    </el-button>

    <media-picker v-model="pickerOpen" type="model" :limit="1"
      :selected="target.model3d ? [target.model3d] : []" @confirm="onPicked" />
    <el-dialog v-model="marksOpen" :title="t('goods.comboModel.markTitle', { name: label || '' })" width="90vw" top="4vh"
      append-to-body destroy-on-close>
      <model-marks-editor v-if="marksOpen && target.model3d" :src="target.model3d" :marks="target.model3dMarks || ''"
        @confirm="v => { target.model3dMarks = v || ''; marksOpen = false }" @cancel="marksOpen = false" />
    </el-dialog>
    <el-dialog v-model="statesOpen" :title="t('goods.states.title') + ' · ' + (label || '')" width="760px"
      append-to-body destroy-on-close>
      <model-states-field v-if="statesOpen" :rows="target.model3dStateRows" :langs="langs" :default-lang="defaultLang"
        :main-model="target.model3d" :marks="target.model3dMarks || ''" :fixed-count="3" />
      <div class="hint mt8">{{ t('goods.states.hint') }}</div>
    </el-dialog>
    <el-dialog v-model="previewOpen" :title="t('goods.comboModel.previewTitle', { name: label || '' })" width="760px"
      append-to-body destroy-on-close>
      <model-preview v-if="previewOpen" v-bind="previewProps" :height="440" />
      <div class="hint mt8">{{ target.model3dMarks ? t('goods.comboModel.previewOwnHint') : t('goods.comboModel.previewFollowHint') }}</div>
    </el-dialog>
  </div>
</template>

<script setup name="ComboModelCell">
import { ref, computed, defineAsyncComponent } from 'vue'
import { ElMessageBox } from 'element-plus'
import MediaPicker from '@station-shared/MediaPicker.vue'
import { t } from '@/i18n'
import ModelStatesField from './ModelStatesField.vue'

// 异步：three.js 只在点「预览」「单独标注」时才下
const ModelPreview = defineAsyncComponent(() => import('@station-shared/ModelPreview.vue'))
const ModelMarksEditor = defineAsyncComponent(() => import('@station-shared/ModelMarksEditor.vue'))

const props = defineProps({
  /** 那一行 / 那一格：读写它的 model3d、model3dMarks */
  target: { type: Object, required: true },
  /** 商品主模型地址；跟随主模型时预览要拿它算投影 */
  mainModel: { type: String, default: '' },
  /** 主模型标注 JSON（预览时投影它的 logo） */
  mainMarks: { type: String, default: '' },
  langs: { type: Array, default: () => ['en'] },
  defaultLang: { type: String, default: 'en' },
  /** 弹窗标题里的规格名 */
  label: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const pickerOpen = ref(false)
const marksOpen = ref(false)
const statesOpen = ref(false)
const previewOpen = ref(false)

const fileName = url => {
  try { return decodeURIComponent(String(url).split('?')[0].split('/').pop() || url) } catch { return url }
}

function onPicked(urls) {
  if (urls?.[0]) props.target.model3d = urls[0]
}

async function clear() {
  if (props.target.model3dMarks) {
    try {
      await ElMessageBox.confirm(t('goods.comboModel.clearConfirm'), { type: 'warning' })
    } catch {
      return
    }
  }
  props.target.model3d = ''
  props.target.model3dMarks = ''
  props.target.model3dStateRows = []
}

async function unmark() {
  try {
    await ElMessageBox.confirm(t('goods.comboModel.unmarkConfirm'), { type: 'warning' })
  } catch {
    return
  }
  props.target.model3dMarks = ''
}

/**
 * 预览：单独标注 → 规格模型 + 它自己的标注；
 * 跟随主模型 → 主模型 + 主模型 logo，再把规格模型当成第二个「状态」，切过去看投影（前台同一套）。
 */
const previewProps = computed(() => {
  const own = props.target.model3dMarks
  const rows = props.target.model3dStateRows || []
  if (rows.length === 3) {
    return {
      src: props.target.model3d,
      marks: own || null,
      states: rows.map((r, i) => ({
        id: r.id,
        name: r.name?.[props.defaultLang] || r.id,
        ...(i > 0 ? { model: r.model } : {})
      }))
    }
  }
  if (own || !props.mainModel) return { src: props.target.model3d, marks: own || null }
  return {
    src: props.mainModel,
    marks: props.mainMarks || null,
    states: [
      { id: 'main', name: t('goods.states.main') },
      { id: 'combo', name: props.label || t('goods.comboModel.thisSpec'), model: props.target.model3d }
    ]
  }
})
</script>

<style scoped>
.cmc { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; }
.cmc-file { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: #606266; }
.cmc-actions { display: flex; flex-wrap: wrap; gap: 0 2px; }
.cmc-actions .el-button + .el-button { margin-left: 4px; }
.mt8 { margin-top: 8px; }
</style>
