<template>
  <!--
    组合的「规格矩阵」编辑器：上半部分定义维度和取值（尺码：S/M、L…；款式：黑色、灰色…），
    下半部分是自动展开的「尺码 × 款式」表，每一格就是一条组合（SKU）：提不提供、多少钱、配哪张图。

    数据模型和几个转换函数在 utils/comboMatrix.js；这个组件只管画和改，
    保存时由 Goods.vue 调 toPayload 组提交数据。

    名称类的输入框（维度名、取值名）跟着 Goods.vue 那个语言开关（editLang）走，
    价格、图片、提不提供所有语言通用——和平铺模式同一条规矩。
  -->
  <div class="cm">
    <!--
      一键翻译：以**正在填的这种语言**为准，铺到其余所有语言（包括默认语言）。
      商家的做法是在中文里把维度填好，点这一下。只补空的、以及本次机器翻出来还没手改过的，
      手填过的不动——规则在 utils/comboMatrix.js 的 runNameTranslation。
      翻译请求由 Goods.vue 发（它有翻译接口和 loading 状态），这里只发一个事件。
    -->
    <div v-if="langs.length > 1" class="cm-translate">
      <el-button size="small" type="success" plain icon="MagicStick" :loading="translating"
        @click="$emit('translate')">
        {{ $t('goods.matrix.translateBtn', { lang: editLang }) }}
      </el-button>
      <span class="hint">{{ $t('goods.matrix.translateHint') }}</span>
    </div>
    <div v-for="(dim, di) in model.dims" :key="dim.uid" class="cm-dim">
      <div class="cm-dim-head">
        <span class="cm-dim-no">{{ $t('goods.matrix.dimNo', { n: di + 1 }) }}</span>
        <el-input v-model="dim.names[editLang]" size="small" style="width: 200px" maxlength="60"
          :placeholder="dim.names[defaultLang] || $t(di === 0 ? 'goods.matrix.dim1Placeholder' : 'goods.matrix.dim2Placeholder')" />
        <el-button v-if="di === 1" link type="danger" size="small" @click="dropSecondDim">
          {{ $t('goods.matrix.removeDim') }}
        </el-button>
      </div>
      <div class="cm-values">
        <div v-for="(v, vi) in dim.values" :key="v.uid" class="cm-value">
          <!-- 取值图（款式图）。前台选中这个取值时主图切到它；组合格子自己配了图的以格子为准 -->
          <el-image v-if="v.image" :src="v.image" fit="cover" class="cm-thumb"
            :title="$t('goods.matrix.valueImage')" @click="pickImage(v)" />
          <el-button v-else class="cm-thumb cm-thumb--empty" :title="$t('goods.matrix.valueImage')" @click="pickImage(v)">
            <el-icon><Picture /></el-icon>
          </el-button>
          <el-input v-model="v.names[editLang]" size="small" style="width: 130px" maxlength="60"
            :placeholder="v.names[defaultLang] || $t('goods.matrix.valuePlaceholder')" />
          <el-button v-if="v.image" link size="small" @click="v.image = ''">{{ $t('goods.combo.clear') }}</el-button>
          <el-button link type="danger" size="small" :disabled="dim.values.length === 1"
            @click="dim.values.splice(vi, 1)">×</el-button>
        </div>
        <el-button link type="primary" size="small" icon="Plus" @click="dim.values.push(newValue(langs))">
          {{ $t('goods.matrix.addValue') }}
        </el-button>
      </div>
    </div>
    <el-button v-if="model.dims.length < MAX_DIMS" link type="primary" icon="Plus" @click="addSecondDim">
      {{ $t('goods.matrix.addDim') }}
    </el-button>

    <div class="cm-grid-head">
      <span class="hint">{{ $t('goods.matrix.gridHint', { n: rows.length, on: onCount }) }}</span>
      <span class="cm-bulk">
        <el-input-number v-model="bulkPrice" :min="0" :precision="4" size="small" controls-position="right" />
        <el-button size="small" @click="applyBulk">{{ $t('goods.matrix.bulkPrice') }}</el-button>
      </span>
    </div>
    <el-table :data="rows" size="small" max-height="420" row-key="key">
      <el-table-column :label="dimLabel(0)" min-width="110">
        <template #default="{ row }">{{ nameOf(row.v1) }}</template>
      </el-table-column>
      <el-table-column v-if="model.dims.length > 1" :label="dimLabel(1)" min-width="110">
        <template #default="{ row }">{{ nameOf(row.v2) }}</template>
      </el-table-column>
      <el-table-column :label="$t('goods.matrix.offered')" width="80" align="center">
        <template #default="{ row }">
          <el-checkbox v-model="row.cell.enabled" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('goods.col.price')" width="170">
        <template #default="{ row }">
          <el-input-number v-model="row.cell.price" :min="0" :precision="4" size="small"
            controls-position="right" style="width: 100%" :disabled="!row.cell.enabled" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('goods.combo.thumb')" width="110" align="center">
        <template #default="{ row }">
          <el-image v-if="row.cell.image" :src="row.cell.image" fit="contain" class="combo-thumb"
            @click="pickImage(row.cell)" />
          <el-button v-else link type="primary" size="small" @click="pickImage(row.cell)">{{ $t('goods.combo.pick') }}</el-button>
          <el-button v-if="row.cell.image" link type="danger" size="small" @click="row.cell.image = ''">{{ $t('goods.combo.clear') }}</el-button>
        </template>
      </el-table-column>
      <!-- 规格自己的 3D 模型（2026-10-09）：空 = 用主模型 -->
      <el-table-column :label="$t('goods.comboModel.col')" min-width="200">
        <template #default="{ row }">
          <combo-model-cell :target="row.cell" :main-model="mainModel" :main-marks="mainMarks"
            :langs="langs" :default-lang="defaultLang"
            :label="[nameOf(row.v1), row.v2 ? nameOf(row.v2) : ''].filter(Boolean).join(' · ')" :disabled="!row.cell.enabled" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('goods.combo.code')" width="130">
        <template #default="{ row }">
          <span v-if="row.cell.comboCode" class="cm-code">{{ row.cell.comboCode }}</span>
          <span v-else class="hint">{{ $t('goods.matrix.codeLater') }}</span>
        </template>
      </el-table-column>
    </el-table>

    <media-picker v-model="pickerOpen" type="image" :limit="1"
      :selected="pickTarget?.image ? [pickTarget.image] : []" @confirm="onPicked" />
  </div>
</template>

<script setup name="ComboMatrix">
import { ref, computed, watch } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import MediaPicker from '@station-shared/MediaPicker.vue'
import ComboModelCell from '@/components/ComboModelCell.vue'
import { MAX_DIMS, newDim, newValue, pairs, syncCells, removeSecondDim } from '@/utils/comboMatrix'
import { t } from '@/i18n'

const props = defineProps({
  /** utils/comboMatrix.js 里的那个模型。**就地修改**，Goods.vue 保存时直接读它 */
  model: { type: Object, required: true },
  langs: { type: Array, required: true },
  defaultLang: { type: String, required: true },
  /** 名称类输入框正在填哪种语言 */
  editLang: { type: String, required: true },
  /** 新出现的搭配默认用的价格（商品售价） */
  basePrice: { type: Number, default: 0 },
  /** 翻译请求在飞：按钮转圈，防止连点 */
  translating: { type: Boolean, default: false },
  /** 商品主模型和它的标注：格子里「跟随主模型」预览投影用 */
  mainModel: { type: String, default: '' },
  mainMarks: { type: String, default: '' }
})
defineEmits(['translate'])

/*
  维度、取值一变就补齐格子（新搭配默认提供、价格取商品售价；一维变二维时把现有组合挪过去）。
  deep 会连取值名的每次输入都触发一次——syncCells 只做几十次对象查找，不值得为它另写依赖。
*/
watch(() => props.model.dims, () => syncCells(props.model, props.basePrice), { deep: true, immediate: true })

const rows = computed(() => pairs(props.model)
  .map(p => ({ ...p, cell: props.model.cells[p.key] }))
  .filter(r => r.cell))

const onCount = computed(() => rows.value.filter(r => r.cell.enabled).length)

function nameOf(value) {
  if (!value) return ''
  return (value.names[props.editLang] || '').trim() || (value.names[props.defaultLang] || '').trim() || '—'
}

function dimLabel(di) {
  const dim = props.model.dims[di]
  return (dim?.names[props.editLang] || '').trim() || (dim?.names[props.defaultLang] || '').trim()
    || t('goods.matrix.dimNo', { n: di + 1 })
}

function addSecondDim() {
  if (props.model.dims.length >= MAX_DIMS) return
  props.model.dims.push(newDim(props.langs))
}

function dropSecondDim() {
  removeSecondDim(props.model)
}

/* 批量设价：只改勾着「提供」的那些。不提供的格子价格留着，重新勾上时还是原来的价 */
const bulkPrice = ref(props.basePrice || 0)
function applyBulk() {
  rows.value.forEach(r => { if (r.cell.enabled) r.cell.price = bulkPrice.value ?? 0 })
}

/* 挑图：取值（款式图）和格子（SKU 图）共用一个选择器，pickTarget 是要写 image 的那个对象 */
const pickerOpen = ref(false)
const pickTarget = ref(null)
function pickImage(target) {
  pickTarget.value = target
  pickerOpen.value = true
}
function onPicked(urls) {
  if (pickTarget.value) pickTarget.value.image = (urls || [])[0] || ''
  pickTarget.value = null
}
</script>

<style scoped>
.cm-translate { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 10px; margin-bottom: 10px; }
.cm-dim {
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.cm-dim-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.cm-dim-no { font-size: 12px; font-weight: 600; color: var(--el-text-color-secondary); }
.cm-values { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 14px; }
.cm-value { display: inline-flex; align-items: center; gap: 4px; }
.cm-thumb {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
  flex: none;
}
.cm-thumb--empty { padding: 0; color: var(--el-text-color-placeholder); }
.cm-grid-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 0 8px;
}
.cm-bulk { display: inline-flex; gap: 6px; }
.cm-code { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.combo-thumb { width: 40px; height: 40px; cursor: pointer; }
</style>
