<!--
  销售利润：按客户、业务员、国家、商品、平台店铺、月份、订单看赚了多少。

  口径（后端 SalesProfitServiceImpl 类注释有完整说明）：
  - 按发货确认：统计区间内已审核的发货出库，日期是发货批次完成那天，没发货的不算
  - 收入 = 发货数量 × 订单单价；成本 = 出库时的实际成本；退货冲回；补发只有成本
  - 收客户的运费按发货进度分摊，付承运商的运费记在对应批次上
  - 外币收入按发货当天生效的汇率折人民币；没录汇率的单不算进来，顶部会列出来
  点某一行可以下钻到这一组里的订单明细。
-->
<template>
  <div class="app-container">
    <el-form :inline="true" class="filters">
      <el-form-item label="发货日期">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
          :shortcuts="shortcuts" :clearable="false" style="width: 260px" @change="load" />
      </el-form-item>
      <el-form-item label="按">
        <el-radio-group v-model="dimension" @change="onDimension">
          <el-radio-button v-for="d in DIMS" :key="d.value" :value="d.value">{{ d.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button icon="Refresh" @click="load">刷新</el-button>
        <el-button icon="Download" :disabled="!report.rows?.length" @click="exportCsv">导出</el-button>
      </el-form-item>
    </el-form>

    <div v-if="drill" class="drill">
      <el-tag closable type="primary" @close="clearDrill">{{ dimLabel(drill.dim) }}：{{ drill.label }}</el-tag>
      <span class="hint">正在看这一组里的{{ dimLabel(dimension) }}明细</span>
    </div>

    <el-alert v-for="m in report.missingRates || []" :key="m.currency" type="warning" show-icon :closable="false" class="mb8">
      <template #title>
        {{ m.currency }} 订单 {{ m.orderCount }} 单没有汇率（最早 {{ m.earliestDate }} 发货），这部分没算进利润。
        <el-link v-hasPermi="['finance:rate:list']" type="primary" @click="$router.push('/finance/rate')">去录汇率</el-link>
      </template>
    </el-alert>
    <el-alert v-if="report.zeroCostLines" type="info" show-icon :closable="false" class="mb8"
      :title="`有 ${report.zeroCostLines} 行发货的出库成本是 0（多半是零成本入库的货），这些商品的利润会偏高，建议核对入库价`" />
    <el-alert v-if="report.unmatchedLines" type="info" show-icon :closable="false" class="mb8"
      :title="`有 ${report.unmatchedLines} 行出库/退货找不到对应的订单，没算进来`" />

    <el-row :gutter="12" class="cards" v-loading="loading">
      <el-col :xs="12" :sm="8" :lg="4" v-for="c in cards" :key="c.label">
        <div class="card">
          <div class="card-label">{{ c.label }}</div>
          <div class="card-value" :class="c.negative ? 'neg' : ''">{{ c.value }}</div>
          <div class="card-sub">{{ c.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="report.rows || []" border stripe size="small" max-height="600"
      :default-sort="{ prop: dimension === 'month' ? 'key' : 'grossProfit', order: dimension === 'month' ? 'ascending' : 'descending' }"
      :row-class-name="({ row }) => (dimension !== 'order' ? 'clickable' : '')" @row-click="onRowClick">
      <el-table-column :label="dimLabel(dimension)" prop="key" min-width="180" sortable fixed>
        <template #default="{ row }">
          <div>{{ row.label }}</div>
          <div v-if="row.subLabel" class="hint">{{ row.subLabel }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="dimension === 'order'" label="原币收入" width="130" align="right">
        <template #default="{ row }">{{ formatCurrency(row.revenueOriginal, row.currency) }}</template>
      </el-table-column>
      <el-table-column v-if="dimension !== 'order'" label="订单数" prop="orderCount" width="80" align="right" sortable />
      <el-table-column label="净发货量" prop="qty" width="95" align="right" sortable>
        <template #default="{ row }">{{ num(row.qty, 0) }}</template>
      </el-table-column>
      <el-table-column label="销售收入" prop="revenue" width="120" align="right" sortable>
        <template #default="{ row }">{{ money(row.revenue) }}</template>
      </el-table-column>
      <el-table-column label="出库成本" prop="cost" width="120" align="right" sortable>
        <template #default="{ row }">
          {{ money(row.cost) }}
          <el-tooltip v-if="Number(row.reshipCost)" :content="'其中补发成本 ' + money(row.reshipCost)">
            <el-icon class="hint"><InfoFilled /></el-icon>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="商品毛利" prop="grossProfit" width="120" align="right" sortable>
        <template #default="{ row }"><span :class="neg(row.grossProfit)">{{ money(row.grossProfit) }}</span></template>
      </el-table-column>
      <el-table-column label="毛利率" prop="grossMargin" width="90" align="right" sortable>
        <template #default="{ row }"><span :class="neg(row.grossMargin)">{{ pct(row.grossMargin) }}</span></template>
      </el-table-column>
      <el-table-column label="收客户运费" prop="freightIncome" width="110" align="right" sortable>
        <template #default="{ row }">{{ money(row.freightIncome) }}</template>
      </el-table-column>
      <el-table-column label="付快递运费" prop="freightCost" width="110" align="right" sortable>
        <template #default="{ row }">{{ money(row.freightCost) }}</template>
      </el-table-column>
      <el-table-column label="含运费毛利" prop="netProfit" width="120" align="right" sortable>
        <template #default="{ row }"><span :class="neg(row.netProfit)">{{ money(row.netProfit) }}</span></template>
      </el-table-column>
      <el-table-column label="含运费毛利率" prop="netMargin" width="110" align="right" sortable>
        <template #default="{ row }"><span :class="neg(row.netMargin)">{{ pct(row.netMargin) }}</span></template>
      </el-table-column>
    </el-table>
    <div class="hint mt8">
      金额单位：人民币元。{{ dimension !== 'order' ? '点任意一行，看这一组里的订单明细。' : '' }}
      收入按发货数量 × 订单单价，成本按出库实际成本；退货冲回，补发只算成本。
    </div>
  </div>
</template>

<script setup name="SalesProfit">
import { getProfitReport } from '@/api/order/profit'
import { formatCurrency } from '@/utils/currency'

const { proxy } = getCurrentInstance()

const DIMS = [
  { value: 'customer', label: '客户' },
  { value: 'staff', label: '业务员' },
  { value: 'country', label: '国家' },
  { value: 'goods', label: '商品' },
  { value: 'store', label: '平台店铺' },
  { value: 'month', label: '月份' },
  { value: 'order', label: '订单' }
]

const fmt = d => proxy.parseTime(d, '{y}-{m}-{d}')
function monthRange(offset) {
  const now = new Date()
  const first = new Date(now.getFullYear(), now.getMonth() + offset, 1)
  const last = offset === 0 ? now : new Date(now.getFullYear(), now.getMonth() + offset + 1, 0)
  return [fmt(first), fmt(last)]
}
const shortcuts = [
  { text: '本月', value: () => monthRange(0) },
  { text: '上月', value: () => monthRange(-1) },
  { text: '近三个月', value: () => [fmt(new Date(new Date().getFullYear(), new Date().getMonth() - 2, 1)), fmt(new Date())] },
  { text: '今年', value: () => [fmt(new Date(new Date().getFullYear(), 0, 1)), fmt(new Date())] }
]

const range = ref(monthRange(0))
const dimension = ref('customer')
const drill = ref(null)
const report = ref({})
const loading = ref(false)

const cards = computed(() => {
  const s = report.value.summary || {}
  const freightNet = Number(s.freightIncome || 0) - Number(s.freightCost || 0)
  return [
    { label: '销售收入', value: money(s.revenue), sub: `${s.orderCount || 0} 单 · 净发货 ${num(s.qty, 0)}` },
    { label: '出库成本', value: money(s.cost), sub: Number(s.reshipCost) ? `含补发 ${money(s.reshipCost)}` : '' },
    { label: '商品毛利', value: money(s.grossProfit), sub: `毛利率 ${pct(s.grossMargin)}`, negative: Number(s.grossProfit) < 0 },
    { label: '运费净额', value: money(freightNet), sub: `收 ${money(s.freightIncome)} / 付 ${money(s.freightCost)}`, negative: freightNet < 0 },
    { label: '含运费毛利', value: money(s.netProfit), sub: `毛利率 ${pct(s.netMargin)}`, negative: Number(s.netProfit) < 0 }
  ]
})

function dimLabel(v) {
  return (DIMS.find(d => d.value === v) || {}).label || ''
}

function money(v) {
  if (v == null) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function num(v, digits = 2) {
  return v == null ? '-' : String(Math.round(Number(v) * 10 ** digits) / 10 ** digits)
}

function pct(v) {
  return v == null ? '-' : Number(v).toFixed(2) + '%'
}

function neg(v) {
  return Number(v) < 0 ? 'neg' : ''
}

function load() {
  if (!range.value) return
  loading.value = true
  getProfitReport({
    begin: range.value[0], end: range.value[1], dimension: dimension.value,
    filterDim: drill.value?.dim, filterKey: drill.value?.key
  }).then(res => {
    report.value = res.data || {}
  }).finally(() => { loading.value = false })
}

function onDimension() {
  if (drill.value && drill.value.dim === dimension.value) drill.value = null
  load()
}

function onRowClick(row) {
  if (dimension.value === 'order') return
  drill.value = { dim: dimension.value, key: row.key, label: row.label }
  dimension.value = 'order'
  load()
}

function clearDrill() {
  const back = drill.value?.dim
  drill.value = null
  if (back) dimension.value = back
  load()
}

/** 导出当前表格为 CSV（带 BOM，Excel 直接打开不乱码） */
function exportCsv() {
  const head = [dimLabel(dimension.value), '订单数', '净发货量', '销售收入', '出库成本', '其中补发成本', '商品毛利', '毛利率%',
    '收客户运费', '付快递运费', '含运费毛利', '含运费毛利率%']
  const cell = v => {
    const s = v == null ? '' : String(v)
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
  }
  const toLine = r => [r.label + (r.subLabel ? ' ' + r.subLabel : ''), r.orderCount, r.qty, r.revenue, r.cost, r.reshipCost,
    r.grossProfit, r.grossMargin, r.freightIncome, r.freightCost, r.netProfit, r.netMargin].map(cell).join(',')
  const lines = [head.join(','), ...report.value.rows.map(toLine), toLine(report.value.summary)]
  const blob = new Blob(['﻿' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `销售利润_按${dimLabel(dimension.value)}_${range.value[0]}_${range.value[1]}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

load()
</script>

<style lang="scss" scoped>
.filters :deep(.el-form-item) {
  margin-bottom: 10px;
}
.drill {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.mb8 {
  margin-bottom: 8px;
}
.mt8 {
  margin-top: 8px;
}
.cards {
  margin-bottom: 12px;
}
.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 8px;
  background: var(--el-bg-color);
}
.card-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.card-value {
  font-size: 22px;
  font-weight: 600;
  margin: 4px 0 2px;
  font-variant-numeric: tabular-nums;
}
.card-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-height: 18px;
}
.neg {
  color: var(--el-color-danger);
}
:deep(.clickable) {
  cursor: pointer;
}
</style>
