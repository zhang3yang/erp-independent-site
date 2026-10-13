<!--
  商品明细录入表格。

  采购订单与采购入库单共用同一套明细录入交互：从「添加商品」弹窗选商品生成行，
  编码、名称、规格、单位、批次策略取商品档案且只读；改数量或单价重算金额。

  两者的差异通过 mode 控制：
    purchase —— 采购下单，含税率税额；不含批次序列号（说明书 3.3.2 的采购明细
                无这两个字段，下单时货未发出，批次未产生，序列号无从得知）
    inbound  —— 库存入库，含批次策略与序列号；不含税率税额（批次号在审核时生成）
-->
<template>
  <div class="goods-line-table">
    <div class="goods-line-header">
      <div>
        <div class="goods-line-title">{{ title }}</div>
        <div class="goods-line-tip">{{ tip }}</div>
      </div>
      <el-button v-if="!readonly" type="primary" icon="Plus" size="small" @click="addLine">添加商品</el-button>
    </div>

    <el-table :data="lines" border stripe max-height="360" empty-text="请添加商品" class="goods-line-body">
      <el-table-column label="行号" width="60" align="center">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <!-- 编码、名称、规格、单位是商品档案上的固定信息，只能通过「添加商品」选出来，
           单据上一律只读，改档案请去商品管理 -->
      <el-table-column label="商品编码" width="150">
        <template #default="scope">
          <span class="goods-line-fixed">{{ scope.row.goodsCode }}</span>
        </template>
      </el-table-column>

      <el-table-column label="商品名称" min-width="150" show-overflow-tooltip>
        <template #default="scope">
          <span class="goods-line-fixed">{{ scope.row.goodsName }}</span>
        </template>
      </el-table-column>

      <el-table-column label="规格" width="110" show-overflow-tooltip>
        <template #default="scope">
          <span class="goods-line-fixed">{{ scope.row.spec }}</span>
        </template>
      </el-table-column>

      <el-table-column label="单位" width="80" align="center">
        <template #default="scope">
          <span class="goods-line-fixed">{{ scope.row.unit }}</span>
        </template>
      </el-table-column>

      <el-table-column label="数量" width="130">
        <template #default="scope">
          <!-- 不设 precision：默认按整数显示和步进，用户手输小数照样收下并原样提交 -->
          <el-input-number v-if="!readonly" v-model="scope.row.qty" :min="0" :step="1"
            controls-position="right" style="width: 100%" @change="recalculate(scope.row)" />
          <span v-else>{{ num(scope.row.qty) }}</span>
        </template>
      </el-table-column>

      <!--
        单价用 DecimalInput 不用 el-input-number：默认补足两位（4 显示成 4.00，价格读起来像钱），
        人手输超过两位就按他输的留（4.12345 原样保住）。el-input-number 的 precision 是个死数，
        两头只能占一头。位数上限按各自的列走：入库成本 decimal(18,6)，采购单价 decimal(18,4)。
      -->
      <el-table-column v-if="canSeeCost" :label="priceLabel" width="130">
        <template #default="scope">
          <decimal-input v-if="!readonly" v-model="scope.row.unitPrice" :max-decimals="priceDecimals"
            style="width: 100%" @change="recalculate(scope.row)" />
          <span v-else>{{ scope.row.unitPrice }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="showTax" label="税率(%)" width="110">
        <template #default="scope">
          <el-input-number v-if="!readonly" v-model="scope.row.taxRate" :min="0" :max="100" :precision="2"
            controls-position="right" style="width: 100%" @change="recalculate(scope.row)" />
          <span v-else>{{ scope.row.taxRate }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="showTax && canSeeCost" label="税额" width="110" align="right">
        <template #default="scope">{{ money(scope.row.taxAmount) }}</template>
      </el-table-column>

      <el-table-column v-if="canSeeCost" :label="showTax ? '不含税金额' : '金额'" width="120" align="right">
        <template #default="scope">{{ money(scope.row.amount) }}</template>
      </el-table-column>

      <!-- 批次号在审核时才生成，开单时这里没有值可填，显示商品的批次策略更有意义 -->
      <el-table-column v-if="showTracking" label="批次策略" width="150">
        <template #default="scope">
          <span class="goods-line-readonly">{{ batchStrategyLabel(scope.row.batchStrategy) }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="showTracking" label="序列号" width="110" align="center">
        <template #default="scope">
          <el-button v-if="scope.row.batchStrategy === 'serial'" link type="primary"
            @click="openSerialDialog(scope.$index)">
            {{ scope.row.serialNumbers?.length ? scope.row.serialNumbers.length + '个' : '必须输入' }}
          </el-button>
          <span v-else class="goods-line-readonly">无需填写</span>
        </template>
      </el-table-column>

      <el-table-column v-if="showWarehouse" label="入库仓库" width="150">
        <template #default="scope">
          <el-select v-if="!readonly" v-model="scope.row.warehouseCode" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
          </el-select>
          <span v-else>{{ scope.row.warehouseName }}</span>
        </template>
      </el-table-column>

      <!--
        历史供应商：这一行的商品上次是从哪家进的。一张单只能填一个供应商，
        所以这里不是让人逐行选，而是拿来对表头选的那家 —— 不一致就标出来，
        供应商多、名字又像的时候，选错当场就能看见。
      -->
      <el-table-column v-if="showPartnerHint" label="历史供应商" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="!partnerHints[scope.row.goodsCode]" class="goods-line-readonly">
            {{ mode === 'purchase' ? '首次采购' : '首次入库' }}
          </span>
          <el-tag v-else-if="partnerHints[scope.row.goodsCode].partnerCode === currentPartnerCode"
            type="success" size="small" effect="plain">一致</el-tag>
          <el-tag v-else type="warning" size="small" effect="plain">
            {{ partnerHints[scope.row.goodsCode].partnerName }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="行备注" min-width="140">
        <template #default="scope">
          <el-input v-if="!readonly" v-model="scope.row.remark" placeholder="选填" maxlength="200" />
          <span v-else>{{ scope.row.remark }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="!readonly" label="操作" width="70" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="removeLine(scope.$index)" />
        </template>
      </el-table-column>
    </el-table>

    <!-- 合计要跟着单价一起藏：一张单只有一行时，合计就是单价 -->
    <div v-if="canSeeCost" class="goods-line-summary">
      <template v-if="showTax">
        <span>不含税合计：{{ money(summary.amount) }}</span>
        <span>税额合计：{{ money(summary.taxAmount) }}</span>
        <span class="goods-line-total">价税合计：{{ money(summary.total) }}</span>
      </template>
      <span v-else class="goods-line-total">合计金额：{{ money(summary.total) }}</span>
    </div>

    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="selectedGoodsCodes" @confirm="handleGoodsSelected" />

    <el-dialog title="输入序列号" v-model="serialOpen" width="500px" append-to-body :close-on-click-modal="false">
      <el-input v-model="serialText" type="textarea" :rows="8" placeholder="每行一个序列号" />
      <template #footer>
        <el-button type="primary" @click="confirmSerial">确定</el-button>
        <el-button @click="serialOpen = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="GoodsLineTable">
import { computed, getCurrentInstance, ref } from 'vue'
import { formatCurrency } from '@/utils/currency'
import { warehouseLabel } from '@/utils/warehouse'
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import DecimalInput from '@/components/DecimalInput'
import { canSeeCost } from '@/utils/price'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  /** purchase 采购下单 | inbound 库存入库 */
  mode: { type: String, default: 'purchase' },
  title: { type: String, default: '商品明细' },
  tip: { type: String, default: '' },
  readonly: { type: Boolean, default: false },
  /** 显示入库仓库列，供到货单按行选仓库使用 */
  showWarehouse: { type: Boolean, default: false },
  warehouseOptions: { type: Array, default: () => [] },
  /** 显示「历史供应商」列，用于和表头选的供应商对比 */
  showPartnerHint: { type: Boolean, default: false },
  /** 每个商品的历史供应商：{ [goodsCode]: { partnerCode, partnerName } } */
  partnerHints: { type: Object, default: () => ({}) },
  /** 表头当前选的供应商编码，用来判断某一行是否不一致 */
  currentPartnerCode: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'summary-change', 'goods-added'])

const { proxy } = getCurrentInstance()

const lines = computed(() => props.modelValue)
const showTax = computed(() => props.mode === 'purchase')
const showTracking = computed(() => props.mode === 'inbound')
const priceLabel = computed(() => (props.mode === 'purchase' ? '采购单价' : '成本单价'))
/**
 * 单价最多录几位小数，按各自落库的列走：
 * 采购明细 u_p_purchasedetail.price 是 decimal(18,4)，入库明细 u_f_docdetail.costprice 是 decimal(18,6)。
 * 录入时就截掉多的位数，别等到落库时被 MySQL 悄悄截断——那样界面显示的和账上记的对不上。
 */
const priceDecimals = computed(() => (props.mode === 'purchase' ? 4 : 6))

const summary = computed(() => {
  const amount = lines.value.reduce((sum, row) => sum + num(row.amount), 0)
  const taxAmount = showTax.value ? lines.value.reduce((sum, row) => sum + num(row.taxAmount), 0) : 0
  return { amount, taxAmount, total: amount + taxAmount }
})

const serialOpen = ref(false)
const serialText = ref('')
const serialIndex = ref(-1)
const goodsSelectRef = ref(null)

const selectedGoodsCodes = computed(() => lines.value.map(row => row.goodsCode).filter(Boolean))

function num(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function money(value) {
  return formatCurrency(value)
}

/** 「添加商品」改成从分类树里挑，挑完一个商品生成一行 */
function addLine() {
  goodsSelectRef.value?.open()
}

function handleGoodsSelected(goodsItems) {
  goodsItems.forEach(goods => {
    lines.value.push({
      goodsCode: goods.goodsCode,
      goodsName: goods.goodsName || '',
      spec: goods.spec || '',
      unit: goods.unit || '',
      qty: 0, unitPrice: 0, amount: 0,
      taxRate: 0, taxAmount: 0,
      batchStrategy: normalizeStrategy(goods.batchStrategy), batchCode: '', serialNumbers: [],
      warehouseCode: '', warehouseName: '', remark: ''
    })
  })
  emit('update:modelValue', lines.value)
  // modelValue 传的是同一个数组引用，父组件 watch 不到 push，所以单独发一个事件。
  // 父组件靠它去查这批商品的历史供应商。
  emit('goods-added', goodsItems.map(goods => goods.goodsCode))
}

function removeLine(index) {
  lines.value.splice(index, 1)
  emit('update:modelValue', lines.value)
}

/** 金额 = 数量 × 单价；税额 = 金额 × 税率 / 100。与后端 applyLineAmounts 保持一致。 */
function recalculate(row) {
  const amount = num(row.qty) * num(row.unitPrice)
  row.amount = round2(amount)
  if (showTax.value) {
    row.taxAmount = round2((row.amount * num(row.taxRate)) / 100)
  }
  emit('summary-change', summary.value)
}

function round2(value) {
  return Math.round((num(value) + Number.EPSILON) * 100) / 100
}

function normalizeStrategy(strategy) {
  const value = String(strategy || 'none').trim().toLowerCase()
  return ['none', 'batch', 'serial'].includes(value) ? value : 'none'
}

const BATCH_STRATEGY_LABELS = {
  none: '无批次',
  batch: '批次管理',
  serial: '序列号管理'
}

function batchStrategyLabel(strategy) {
  return BATCH_STRATEGY_LABELS[normalizeStrategy(strategy)]
}

function openSerialDialog(index) {
  if (lines.value[index]?.batchStrategy !== 'serial') {
    proxy.$modal.msgWarning('该商品不采用序列号管理')
    return
  }
  serialIndex.value = index
  serialText.value = (lines.value[index].serialNumbers || []).join('\n')
  serialOpen.value = true
}

function confirmSerial() {
  const values = [...new Set(String(serialText.value || '')
    .split('\n')
    .map(value => value.trim())
    .filter(Boolean))]
  if (serialIndex.value >= 0) lines.value[serialIndex.value].serialNumbers = values
  serialOpen.value = false
}

defineExpose({ summary })
</script>

<style scoped>
.goods-line-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin: 12px 0 8px;
}
.goods-line-title {
  font-size: 15px;
  font-weight: 600;
}
.goods-line-tip {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.goods-line-fixed {
  color: var(--el-text-color-primary);
}
.goods-line-readonly {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.goods-line-summary {
  display: flex;
  justify-content: flex-end;
  gap: 24px;
  margin-top: 12px;
  font-size: 14px;
}
.goods-line-total {
  font-size: 16px;
  font-weight: 700;
}
</style>
