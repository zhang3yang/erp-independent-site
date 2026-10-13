<!--
  成本调整：数量不动，只改库存成本。

  典型场景：采购运费后到要补进成本、入库单价录错要更正。审核通过即改成本，按商品的计价方式
  落到出库时取成本的那一层——移动平均改库存均价，批次计价改批次单价，序列号个别计价把差额
  摊到指定的序列号上。

  只能调还在库里的货：已经卖出去的那部分差额属于销售成本，不归这张单管。
  每行可以填「调整后单价」或「调整总额」，填一个另一个自动换算，提交的是调整总额。
-->
<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item label="单号" prop="docCode">
        <el-input v-model="queryParams.docCode" placeholder="请输入单号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="confirmState">
        <el-select v-model="queryParams.confirmState" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="s in ['待审核', '已审核', '已驳回']" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['inventory:costadjust:add']" type="primary" plain icon="Plus" @click="handleAdd">新建成本调整</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="list" @row-dblclick="openDetail">
      <el-table-column label="单号" align="center" width="170">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row)">{{ row.docCode }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="仓库" prop="fromWareName" align="center" min-width="120" show-overflow-tooltip />
      <el-table-column label="商品" prop="goodsSummary" align="center" min-width="150" show-overflow-tooltip />
      <el-table-column v-if="canSeeCost" label="调整总额" align="right" width="130">
        <template #default="{ row }">
          <span :class="Number(row.totalAmount) > 0 ? 'up' : 'down'">{{ signed(row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="stateTag(row.confirmState)">{{ row.confirmState }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="调整原因" prop="remark" align="left" min-width="200" show-overflow-tooltip />
      <el-table-column label="制单人" align="center" width="100">
        <template #default="{ row }">{{ row.createUserName || row.createUser }}</template>
      </el-table-column>
      <el-table-column label="单据日期" align="center" width="110">
        <template #default="{ row }">{{ parseTime(row.docDate, '{y}-{m}-{d}') }}</template>
      </el-table-column>
      <el-table-column label="审核人" align="center" width="100">
        <template #default="{ row }">{{ row.confirmUserName || row.confirmUser || '-' }}</template>
      </el-table-column>
      <!-- 审核、驳回、删除都在详情弹窗里：看过明细再动手 -->
      <el-table-column label="操作" align="center" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" icon="View" @click="openDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <!-- 新建 -->
    <el-dialog v-model="createOpen" title="新建成本调整" width="1100px" top="5vh" append-to-body :close-on-click-modal="false">
      <el-form ref="createRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="10">
            <el-form-item label="仓库" prop="wareCode">
              <el-select v-model="form.wareCode" placeholder="请选择仓库" filterable style="width: 100%" @change="refreshAll">
                <el-option v-for="w in warehouses" :key="w.wareCode" :label="warehouseLabel(w)" :value="w.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="调整原因" prop="remark">
              <el-input v-model="form.remark" maxlength="500" placeholder="例如：9 月第 2 批采购运费 320 元后到，补进成本" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="toolbar">
        <el-button icon="Plus" :disabled="!form.wareCode" @click="goodsSelectRef?.open()">添加商品</el-button>
        <span class="hint">只调良品·在库的货；先选仓库再加商品</span>
      </div>

      <el-table :data="form.items" border size="small" max-height="460">
        <el-table-column label="商品" min-width="150">
          <template #default="{ row }">
            <div>{{ row.goodsName }}</div>
            <div class="hint">{{ row.goodsCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="计价" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.preview" size="small" :type="LAYER[row.preview.layer].type">{{ LAYER[row.preview.layer].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="批次 / 序列号" min-width="190">
          <template #default="{ row }">
            <el-select v-if="row.preview?.layer === 'BATCH'" v-model="row.batchCode" placeholder="选择批次" size="small"
              filterable style="width: 100%" @visible-change="v => v && loadBatches(row)" @change="refreshRow(row)">
              <el-option v-for="b in row.batches || []" :key="b.batchCode" :label="b.batchCode + '（剩 ' + qty(b.remainQty) + '）'" :value="b.batchCode" />
            </el-select>
            <el-input v-else-if="row.preview?.layer === 'SERIAL'" v-model="row.serialNos" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }"
              size="small" placeholder="要调的序列号，逗号或换行分隔" @blur="refreshRow(row)" />
            <span v-else class="hint">按全仓均价调</span>
          </template>
        </el-table-column>
        <el-table-column label="当前数量" width="90" align="right">
          <template #default="{ row }">{{ row.preview?.qty != null ? qty(row.preview.qty) : '-' }}</template>
        </el-table-column>
        <el-table-column label="当前单价" width="100" align="right">
          <template #default="{ row }">{{ row.preview?.unitCost != null ? money(row.preview.unitCost, 4) : '-' }}</template>
        </el-table-column>
        <el-table-column label="当前成本" width="110" align="right">
          <template #default="{ row }">{{ row.preview?.totalCost != null ? money(row.preview.totalCost) : '-' }}</template>
        </el-table-column>
        <el-table-column label="调整后单价" width="130" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.targetPrice" :min="0" :precision="4" :controls="false" size="small" style="width: 110px"
              :disabled="!ready(row)" @change="fromTarget(row)" />
          </template>
        </el-table-column>
        <el-table-column label="调整总额" width="130" align="center">
          <template #header>
            调整总额
            <el-tooltip content="这一行库存总成本增加或减少多少钱，不是每件。例如运费一共 300 元，就填 300" placement="top">
              <el-icon class="hint" style="vertical-align: -2px"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <el-input-number v-model="row.adjustAmount" :precision="2" :controls="false" size="small" style="width: 110px"
              :disabled="!ready(row)" placeholder="+调增 / -调减" @change="fromAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="" width="50" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" icon="Delete" @click="form.items.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>
      <div class="summary">
        合计调整 <b :class="totalAdjust >= 0 ? 'up' : 'down'">{{ signed(totalAdjust) }}</b>
        <span class="hint">（正数调增、负数调减；审核通过后才改库存成本）</span>
      </div>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="submitCreate">提交审核</el-button>
        <el-button @click="createOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailOpen" :title="'成本调整 ' + (detail.docCode || '')" width="900px" append-to-body>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="仓库">{{ detail.fromWareName }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="stateTag(detail.confirmState)">{{ detail.confirmState }}</el-tag></el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="调整合计">{{ signed(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.confirmUserName || detail.confirmUser || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单据日期">{{ parseTime(detail.docDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="调整原因" :span="3">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.details || []" border size="small" class="mt8">
        <el-table-column label="商品编码" prop="goodsCode" width="140" />
        <el-table-column label="商品名称" prop="goodsName" min-width="140" show-overflow-tooltip />
        <el-table-column label="批次 / 序列号" min-width="160">
          <template #default="{ row }">{{ row.batchCode || (row.serialNumbers || []).join('，') || '全仓均价' }}</template>
        </el-table-column>
        <el-table-column label="摊到数量" width="90" align="right">
          <template #default="{ row }">{{ qty(row.qty) }}</template>
        </el-table-column>
        <el-table-column v-if="canSeeCost" label="每件调整" width="110" align="right">
          <template #default="{ row }">{{ signed(row.costPrice, 4) }}</template>
        </el-table-column>
        <el-table-column v-if="canSeeCost" label="调整总额" width="120" align="right">
          <template #default="{ row }">
            <span :class="Number(row.costAmount) > 0 ? 'up' : 'down'">{{ signed(row.costAmount) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="hint mt8">摊到数量是建单时的数量；审核时按当时的库存重新校验，流水里记的是审核那一刻的实际数。</div>
      <template #footer>
        <template v-if="detail.confirmState === '待审核'">
          <el-button v-hasPermi="['inventory:costadjust:add']" type="danger" plain icon="Delete" @click="handleDelete">删除</el-button>
          <el-button v-hasPermi="['inventory:costadjust:approve']" type="warning" icon="Back" @click="handleReject">驳回</el-button>
          <el-button v-hasPermi="['inventory:costadjust:approve']" type="success" icon="CircleCheck" :loading="acting" @click="handleApprove">审核通过</el-button>
        </template>
        <el-button @click="detailOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="stockLayerCodes" @confirm="handleGoodsPicked" />
  </div>
</template>

<script setup name="InventoryCostAdjust">
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import { listWarehouse } from '@/api/inventory/warehouse'
import { listBatch } from '@/api/inventory/batch'
import { warehouseLabel } from '@/utils/warehouse'
import { canSeeCost } from '@/utils/price'
import {
  listCostAdjust, getCostAdjust, previewCostAdjust, addCostAdjust,
  approveCostAdjust, rejectCostAdjust, delCostAdjust
} from '@/api/inventory/costAdjust'

const { proxy } = getCurrentInstance()

const LAYER = {
  STOCK: { label: '移动平均', type: 'info' },
  BATCH: { label: '按批次', type: 'warning' },
  SERIAL: { label: '按序列号', type: 'success' }
}

const list = ref([])
const warehouses = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const queryParams = ref({ pageNum: 1, pageSize: 20, docCode: undefined, confirmState: undefined })

const createOpen = ref(false)
const saving = ref(false)
const goodsSelectRef = ref(null)
const form = ref(emptyForm())
const rules = {
  wareCode: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  remark: [{ required: true, message: '请填写调整原因', trigger: 'blur' }]
}

const detailOpen = ref(false)
const acting = ref(false)
const detail = ref({})

// 移动平均的商品一个仓库只该有一行；批次、序列号商品可以按不同批次各开一行，所以不排除
const stockLayerCodes = computed(() => form.value.items.filter(i => i.preview?.layer === 'STOCK').map(i => i.goodsCode))
const totalAdjust = computed(() => form.value.items.reduce((s, i) => s + (Number(i.adjustAmount) || 0), 0))

function emptyForm() {
  return { wareCode: undefined, remark: '', items: [] }
}

function qty(v) {
  return v == null ? '-' : String(Math.round(Number(v) * 10000) / 10000)
}

function money(v, digits = 2) {
  return v == null ? '-' : Number(v).toFixed(digits)
}

function signed(v, digits = 2) {
  if (v == null) return '-'
  const n = Number(v)
  return (n > 0 ? '+' : '') + n.toFixed(digits)
}

function stateTag(s) {
  return { 待审核: 'warning', 已审核: 'success', 已驳回: 'info' }[s] || 'info'
}

// ---- 列表 ----
function getList() {
  loading.value = true
  listCostAdjust(queryParams.value).then(res => {
    list.value = res.rows
    total.value = res.total
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function openDetail(row) {
  getCostAdjust(row.docCode, row.tableSuffix).then(res => {
    detail.value = res.data
    detailOpen.value = true
  })
}

// 以下三个动作都在详情弹窗里点，作用于当前打开的那张单；成功后关弹窗刷新列表，
// 失败（比如撞上盘点冻结）弹窗留着，报错由请求拦截器弹出
function handleApprove() {
  const d = detail.value
  proxy.$modal.confirm(`审核通过后立即改动库存成本（合计 ${signed(d.totalAmount)}），确认？`)
    .then(() => {
      acting.value = true
      return approveCostAdjust(d.docCode, d.tableSuffix)
    })
    .then(() => { proxy.$modal.msgSuccess('已审核，库存成本已调整'); detailOpen.value = false; getList() })
    .catch(() => {})
    .finally(() => { acting.value = false })
}

function handleReject() {
  const d = detail.value
  proxy.$modal.confirm(`驳回成本调整单 ${d.docCode}？库存成本不会有任何变化。`)
    .then(() => rejectCostAdjust(d.docCode, d.tableSuffix))
    .then(() => { proxy.$modal.msgSuccess('已驳回'); detailOpen.value = false; getList() })
    .catch(() => {})
}

function handleDelete() {
  const d = detail.value
  proxy.$modal.confirm(`删除成本调整单 ${d.docCode}？`)
    .then(() => delCostAdjust(d.docCode, d.tableSuffix))
    .then(() => { proxy.$modal.msgSuccess('已删除'); detailOpen.value = false; getList() })
    .catch(() => {})
}

// ---- 新建 ----
function handleAdd() {
  form.value = emptyForm()
  createOpen.value = true
  nextTick(() => proxy.resetForm('createRef'))
}

function handleGoodsPicked(goodsList) {
  for (const g of goodsList || []) {
    const row = reactive({
      goodsCode: g.goodsCode, goodsName: g.goodsName, batchCode: undefined, serialNos: '',
      preview: null, batches: null, targetPrice: undefined, adjustAmount: undefined
    })
    form.value.items.push(row)
    refreshRow(row)
  }
}

function serialsOf(row) {
  return String(row.serialNos || '').split(/[,，;；\s]+/).map(s => s.trim()).filter(Boolean)
}

// 预览接口按当前的仓库、批次、序列号取「现在这部分货多少件、成本多少」
function refreshRow(row) {
  if (!form.value.wareCode) return
  const params = { wareCode: form.value.wareCode, goodsCode: row.goodsCode }
  if (row.batchCode) params.batchCode = row.batchCode
  const serials = serialsOf(row)
  if (serials.length) params.serialNos = serials.join(',')
  previewCostAdjust(params).then(res => {
    row.preview = res.data
    if (row.adjustAmount != null) fromAmount(row)
  }).catch(() => { row.preview = row.preview ? { layer: row.preview.layer } : null })
}

function refreshAll() {
  for (const row of form.value.items) {
    row.batchCode = undefined
    row.batches = null
    refreshRow(row)
  }
}

function ready(row) {
  return row.preview && Number(row.preview.qty) > 0
}

function fromTarget(row) {
  if (!ready(row) || row.targetPrice == null) return
  const p = row.preview
  row.adjustAmount = Math.round((Number(row.targetPrice) * Number(p.qty) - Number(p.totalCost)) * 100) / 100
}

function fromAmount(row) {
  if (!ready(row) || row.adjustAmount == null) return
  const p = row.preview
  row.targetPrice = Math.round((Number(p.totalCost) + Number(row.adjustAmount)) / Number(p.qty) * 10000) / 10000
}

function loadBatches(row) {
  if (row.batches) return
  listBatch({ pageNum: 1, pageSize: 200, goodsCode: row.goodsCode, wareCode: form.value.wareCode, qualityFlag: 1 })
    .then(res => { row.batches = (res.rows || []).filter(b => Number(b.remainQty) > 0 && b.onhandFlag !== 0) })
}

function submitCreate() {
  proxy.$refs.createRef.validate(valid => {
    if (!valid) return
    if (!form.value.items.length) {
      proxy.$modal.msgWarning('请至少添加一个商品')
      return
    }
    const bad = form.value.items.find(i => !Number(i.adjustAmount))
    if (bad) {
      proxy.$modal.msgWarning(`「${bad.goodsName}」还没填调整后单价或调整总额`)
      return
    }
    saving.value = true
    addCostAdjust({
      wareCode: form.value.wareCode,
      remark: form.value.remark,
      items: form.value.items.map(i => ({
        goodsCode: i.goodsCode, batchCode: i.batchCode || null,
        serialNos: i.serialNos || null, adjustAmount: i.adjustAmount
      }))
    }).then(res => {
      proxy.$modal.msgSuccess(res.msg + '，等待审核')
      createOpen.value = false
      getList()
    }).finally(() => { saving.value = false })
  })
}

listWarehouse({ pageSize: 1000, activeFlag: 1 }).then(res => { warehouses.value = res.rows || [] })
getList()
</script>

<style lang="scss" scoped>
.hint {
  font-size: 12px;
  color: #909399;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 4px 0 8px;
}
.summary {
  margin-top: 8px;
  font-size: 13px;
}
.mt8 {
  margin-top: 8px;
}
.up {
  color: #c93c37;
}
.down {
  color: #1f7a4d;
}
</style>
