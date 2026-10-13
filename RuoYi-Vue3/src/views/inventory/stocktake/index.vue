<!--
  库存盘点：账面快照 → 录实盘数 → 提交 → 审核生成盘盈入库 / 盘亏出库。

  盘点单从新建到审核完成，单上的商品在这个仓库里不能过账任何单据（发货完成、入库审核、
  调拨审核都会被拦下提示盘点单号），所以建完单要尽快盘、尽快审。不在单上的商品照常进出。

  差异以审核那一刻的库存为账面。冻结期间两者本该一致，页面上「当前库存」和「账面」对不上时
  会标出来，审核时以当前库存为准。
-->
<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item label="盘点单号" prop="stocktakeCode">
        <el-input v-model="queryParams.stocktakeCode" placeholder="请输入盘点单号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库" prop="wareCode">
        <el-select v-model="queryParams.wareCode" placeholder="请选择仓库" clearable filterable style="width: 240px">
          <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['inventory:stocktake:add']" type="primary" plain icon="Plus" @click="handleAdd">新建盘点</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="stocktakeList" @row-dblclick="row => openDetail(row.stocktakeCode)">
      <el-table-column label="盘点单号" prop="stocktakeCode" align="center" width="150">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.stocktakeCode)">{{ row.stocktakeCode }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="仓库" prop="wareName" align="center" min-width="130" show-overflow-tooltip />
      <el-table-column label="范围" align="center" width="90">
        <template #default="{ row }">{{ row.scope === 'goods' ? '指定商品' : '全仓' }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品行" prop="lineCount" align="center" width="80" />
      <el-table-column label="差异行" align="center" width="80">
        <template #default="{ row }">{{ row.status === '已完成' ? row.diffCount : '-' }}</template>
      </el-table-column>
      <el-table-column label="盘盈" align="center" width="130">
        <template #default="{ row }">
          <template v-if="row.status === '已完成' && Number(row.gainQty) > 0">
            <span class="gain">+{{ qty(row.gainQty) }}</span>
            <div v-if="canSeeCost" class="sub">{{ formatCurrency(row.gainAmount) }}</div>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="盘亏" align="center" width="130">
        <template #default="{ row }">
          <template v-if="row.status === '已完成' && Number(row.lossQty) > 0">
            <span class="loss">-{{ qty(row.lossQty) }}</span>
            <div v-if="canSeeCost" class="sub">{{ formatCurrency(row.lossAmount) }}</div>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="调整单" prop="adjustDocCodes" align="center" min-width="160" show-overflow-tooltip />
      <el-table-column label="创建人" align="center" width="100">
        <template #default="{ row }">{{ row.createUserName || row.createUser }}</template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" width="160">
        <template #default="{ row }">{{ parseTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="审核人" align="center" width="100">
        <template #default="{ row }">{{ row.confirmUserName || row.confirmUser || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="row.status === '盘点中' ? 'Edit' : 'View'" @click="openDetail(row.stocktakeCode)">
            {{ row.status === '盘点中' ? '盘点' : '查看' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <!-- 新建盘点 -->
    <el-dialog v-model="createOpen" title="新建盘点" width="620px" append-to-body :close-on-click-modal="false">
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="盘点仓库" prop="wareCode">
          <el-select v-model="createForm.wareCode" placeholder="请选择仓库" filterable style="width: 100%">
            <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点范围" prop="scope">
          <el-radio-group v-model="createForm.scope">
            <el-radio value="all">全仓（账上有货的全部商品）</el-radio>
            <el-radio value="goods">指定商品</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.scope === 'goods'" label="商品" prop="goodsList">
          <div style="width: 100%">
            <el-button icon="Search" @click="goodsSelectRef?.open()">选择商品</el-button>
            <span class="hint" style="margin-left: 8px">已选 {{ createForm.goodsList.length }} 个；账上没有的货也可以选，盘出来记盘盈</span>
            <div class="picked">
              <el-tag v-for="g in createForm.goodsList" :key="g.goodsCode" closable @close="removePicked(g.goodsCode)">
                {{ g.goodsName }}
              </el-tag>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="不良品">
          <el-checkbox v-model="createForm.includeDefective">把在库的不良品也一起盘（单独成行）</el-checkbox>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" maxlength="500" placeholder="例如：9 月月末盘点" />
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon
          title="建单即冻结：盘点结束（审核或作废）前，单上的商品在这个仓库里不能过账任何出入库单据" />
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="creating" @click="submitCreate">开始盘点</el-button>
        <el-button @click="createOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 盘点详情 / 录入 -->
    <el-dialog v-model="detailOpen" :title="'盘点单 ' + (detail.stocktakeCode || '')" width="92%" top="4vh"
      append-to-body :close-on-click-modal="false" @closed="getList">
      <div v-loading="detailLoading">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="仓库">{{ detail.wareName }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusTag(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createUserName || detail.createUser }}</el-descriptions-item>
          <el-descriptions-item label="账面快照时间">{{ parseTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="4">{{ detail.remark }}</el-descriptions-item>
          <template v-if="detail.status === '已完成'">
            <el-descriptions-item label="审核人">{{ detail.confirmUserName || detail.confirmUser }}</el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ parseTime(detail.confirmTime) }}</el-descriptions-item>
            <el-descriptions-item label="调整单" :span="2">{{ detail.adjustDocCodes || '无差异，未生成调整单' }}</el-descriptions-item>
          </template>
        </el-descriptions>

        <el-alert v-if="detail.status === '盘点中' && detail.rejectReason" type="error" :closable="false" show-icon
          class="mt8" :title="'被驳回：' + detail.rejectReason" />
        <el-alert v-if="bookDrift.length" type="warning" :closable="false" show-icon class="mt8"
          :title="`有 ${bookDrift.length} 行当前库存和建单时的账面不一致（如 ${bookDrift[0].goodsName}），差异按当前库存算`" />

        <div class="toolbar">
          <div class="left">
            <template v-if="editable">
              <el-button v-hasPermi="['inventory:stocktake:add']" type="primary" icon="Check" :loading="saving" @click="saveCounts()">保存</el-button>
              <el-button v-hasPermi="['inventory:stocktake:add']" icon="DocumentCopy" @click="fillBook">未填的按账面填</el-button>
              <el-button v-hasPermi="['inventory:stocktake:add']" icon="Plus" @click="addGoodsRef?.open()">添加商品</el-button>
              <el-button v-hasPermi="['inventory:stocktake:add']" type="success" icon="Upload" @click="handleSubmit">提交审核</el-button>
            </template>
            <template v-if="detail.status === '待审核'">
              <el-button v-hasPermi="['inventory:stocktake:approve']" type="success" icon="CircleCheck" :loading="approving" @click="handleApprove">审核通过</el-button>
              <el-button v-hasPermi="['inventory:stocktake:approve']" type="warning" icon="Back" @click="handleReject">驳回</el-button>
            </template>
            <el-button v-if="detail.status === '盘点中' || detail.status === '待审核'" v-hasPermi="['inventory:stocktake:add']"
              type="danger" plain icon="Close" @click="handleCancel">作废</el-button>
            <el-button icon="Printer" @click="printSheet">打印盘点表</el-button>
          </div>
          <div class="right">
            <el-input v-model="keyword" placeholder="按编码/名称筛选" clearable prefix-icon="Search" style="width: 200px" />
            <el-checkbox v-model="onlyDiff" style="margin-left: 12px">只看有差异 / 未盘</el-checkbox>
          </div>
        </div>

        <div class="summary">
          共 {{ items.length }} 行，已盘 {{ countedCount }} 行；
          <span class="gain">盘盈 {{ summary.gainLines }} 行 +{{ qty(summary.gainQty) }}</span>，
          <span class="loss">盘亏 {{ summary.lossLines }} 行 -{{ qty(summary.lossQty) }}</span>
          <template v-if="canSeeCost && detail.status === '已完成'">
            ；盘盈金额 {{ formatCurrency(detail.gainAmount) }}，盘亏金额 {{ formatCurrency(detail.lossAmount) }}
          </template>
        </div>

        <el-table :data="visibleItems" border size="small" max-height="560" :row-class-name="rowClass">
          <el-table-column label="商品编码" prop="goodsCode" width="130" show-overflow-tooltip />
          <el-table-column label="商品名称" prop="goodsName" min-width="150" show-overflow-tooltip />
          <el-table-column label="规格" prop="spec" width="100" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="60" align="center" />
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.qualityFlag === 0" type="danger" size="small">不良品</el-tag>
              <span v-else>良品</span>
            </template>
          </el-table-column>
          <el-table-column label="账面" width="90" align="right">
            <template #default="{ row }">
              {{ qty(bookOf(row)) }}
              <el-tooltip v-if="drifted(row)" :content="'建单时账面 ' + qty(row.bookQty) + '，现在 ' + qty(row.currentQty)">
                <el-icon class="warn"><Warning /></el-icon>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="实盘" width="150" align="center">
            <template #default="{ row }">
              <el-input-number v-if="editable" v-model="row.countedQty" :min="0" :precision="row.batchStrategy === 'serial' ? 0 : undefined"
                controls-position="right" size="small" style="width: 130px" placeholder="未盘" />
              <span v-else>{{ row.countedQty == null ? '-' : qty(row.countedQty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异" width="90" align="right">
            <template #default="{ row }">
              <span v-if="diffOf(row) == null" class="muted">-</span>
              <span v-else :class="diffOf(row) > 0 ? 'gain' : diffOf(row) < 0 ? 'loss' : 'muted'">
                {{ diffOf(row) > 0 ? '+' : '' }}{{ qty(diffOf(row)) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeCost" label="盘盈成本单价" width="140" align="right">
            <template #default="{ row }">
              <el-input-number v-if="editable && diffOf(row) > 0" v-model="row.costPrice" :min="0" :precision="4"
                :controls="false" size="small" style="width: 120px" />
              <span v-else-if="diffOf(row) > 0">{{ row.costPrice }}</span>
              <span v-else class="muted" title="盘亏按出库实际成本结算">-</span>
            </template>
          </el-table-column>
          <el-table-column v-if="canSeeCost && detail.status === '已完成'" label="差异金额" width="110" align="right">
            <template #default="{ row }">
              <span :class="Number(row.diffAmount) > 0 ? 'gain' : Number(row.diffAmount) < 0 ? 'loss' : 'muted'">
                {{ formatCurrency(row.diffAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="批次 / 序列号" min-width="200">
            <template #default="{ row }">
              <template v-if="needBatch(row)">
                <el-select v-if="editable" v-model="row.batchCode" placeholder="盘亏出自哪个批次" size="small" filterable
                  style="width: 100%" @visible-change="v => v && loadBatches(row)">
                  <el-option v-for="b in batchOptions[batchKey(row)] || []" :key="b.batchCode"
                    :label="b.batchCode + '（剩 ' + qty(b.remainQty) + '）'" :value="b.batchCode" />
                </el-select>
                <span v-else>{{ row.batchCode }}</span>
              </template>
              <template v-else-if="needSerial(row)">
                <el-input v-if="editable" v-model="row.serialNos" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" size="small"
                  :placeholder="(diffOf(row) < 0 ? '找不到的' : '多出来的') + ' ' + Math.abs(diffOf(row)) + ' 个序列号，逗号或换行分隔'" />
                <span v-else class="serials">{{ row.serialNos }}</span>
              </template>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="差异原因" min-width="150">
            <template #default="{ row }">
              <el-input v-if="editable" v-model="row.remark" size="small" maxlength="500" placeholder="选填" />
              <span v-else>{{ row.remark }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="editable" label="" width="60" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-hasPermi="['inventory:stocktake:add']" link type="danger" icon="Delete" @click="removeRow(row)" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <goods-select-dialog ref="goodsSelectRef" :exclude-codes="createForm.goodsList.map(g => g.goodsCode)" @confirm="handlePicked" />
    <goods-select-dialog ref="addGoodsRef" :exclude-codes="goodItemCodes" @confirm="handleAddGoods" />
  </div>
</template>

<script setup name="InventoryStocktake">
import GoodsSelectDialog from '@/components/GoodsSelectDialog'
import { listWarehouse } from '@/api/inventory/warehouse'
import { listBatch } from '@/api/inventory/batch'
import { warehouseLabel } from '@/utils/warehouse'
import { formatCurrency } from '@/utils/currency'
import { canSeeCost } from '@/utils/price'
import {
  listStocktake, getStocktake, addStocktake, saveStocktakeCounts, addStocktakeItems, delStocktakeItem,
  submitStocktake, cancelStocktake, rejectStocktake, approveStocktake
} from '@/api/inventory/stocktake'

const { proxy } = getCurrentInstance()

const STATUSES = ['盘点中', '待审核', '已完成', '已作废']

const stocktakeList = ref([])
const warehouses = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)

const queryParams = ref({ pageNum: 1, pageSize: 20, stocktakeCode: undefined, wareCode: undefined, status: undefined })

// ---- 新建 ----
const createOpen = ref(false)
const creating = ref(false)
const goodsSelectRef = ref(null)
const createForm = ref(emptyCreateForm())
const createRules = {
  wareCode: [{ required: true, message: '请选择盘点仓库', trigger: 'change' }],
  scope: [{ required: true, message: '请选择盘点范围', trigger: 'change' }]
}

// ---- 详情 ----
const detailOpen = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const approving = ref(false)
const detail = ref({})
const items = ref([])
const keyword = ref('')
const onlyDiff = ref(false)
const addGoodsRef = ref(null)
const batchOptions = ref({})

const editable = computed(() => detail.value.status === '盘点中')
const goodItemCodes = computed(() => items.value.filter(i => i.qualityFlag !== 0).map(i => i.goodsCode))
const countedCount = computed(() => items.value.filter(i => i.countedQty != null).length)
const bookDrift = computed(() => detail.value.status === '已完成' ? [] : items.value.filter(drifted))

const summary = computed(() => {
  const s = { gainLines: 0, gainQty: 0, lossLines: 0, lossQty: 0 }
  for (const row of items.value) {
    const d = diffOf(row)
    if (d > 0) { s.gainLines++; s.gainQty += d }
    if (d < 0) { s.lossLines++; s.lossQty -= d }
  }
  return s
})

const visibleItems = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return items.value.filter(row => {
    if (kw && !(String(row.goodsCode).toLowerCase().includes(kw) || String(row.goodsName || '').toLowerCase().includes(kw))) {
      return false
    }
    if (onlyDiff.value) {
      const d = diffOf(row)
      return d == null || d !== 0
    }
    return true
  })
})

function emptyCreateForm() {
  return { wareCode: undefined, scope: 'all', goodsList: [], includeDefective: false, remark: '' }
}

// 已完成的单以审核时回写的账面为准，其余用实时库存
function bookOf(row) {
  return detail.value.status === '已完成' ? Number(row.bookQty) : Number(row.currentQty ?? row.bookQty)
}

function diffOf(row) {
  if (detail.value.status === '已完成') return row.diffQty == null ? null : Number(row.diffQty)
  if (row.countedQty == null) return null
  return round4(Number(row.countedQty) - bookOf(row))
}

function drifted(row) {
  return detail.value.status !== '已完成' && Number(row.currentQty) !== Number(row.bookQty)
}

function needBatch(row) {
  const d = diffOf(row)
  return d != null && d < 0 && row.batchStrategy === 'batch' && row.costCalculation === 'individual'
}

function needSerial(row) {
  const d = diffOf(row)
  return d != null && d !== 0 && row.batchStrategy === 'serial'
}

function round4(n) {
  return Math.round(n * 10000) / 10000
}

function qty(v) {
  if (v == null || v === '') return '-'
  return String(round4(Number(v)))
}

function statusTag(status) {
  return { 盘点中: 'primary', 待审核: 'warning', 已完成: 'success', 已作废: 'info' }[status] || 'info'
}

function rowClass({ row }) {
  const d = diffOf(row)
  if (d == null) return 'row-uncounted'
  return d !== 0 ? 'row-diff' : ''
}

// ---- 列表 ----
function getList() {
  loading.value = true
  listStocktake(queryParams.value).then(res => {
    stocktakeList.value = res.rows
    total.value = res.total
  }).finally(() => { loading.value = false })
}

function loadWarehouses() {
  listWarehouse({ pageSize: 1000, activeFlag: 1 }).then(res => { warehouses.value = res.rows || [] })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

// ---- 新建 ----
function handleAdd() {
  createForm.value = emptyCreateForm()
  createOpen.value = true
  nextTick(() => proxy.resetForm('createRef'))
}

function handlePicked(list) {
  const have = new Set(createForm.value.goodsList.map(g => g.goodsCode))
  for (const g of list || []) {
    if (!have.has(g.goodsCode)) createForm.value.goodsList.push({ goodsCode: g.goodsCode, goodsName: g.goodsName })
  }
}

function removePicked(code) {
  createForm.value.goodsList = createForm.value.goodsList.filter(g => g.goodsCode !== code)
}

function submitCreate() {
  proxy.$refs.createRef.validate(valid => {
    if (!valid) return
    const f = createForm.value
    if (f.scope === 'goods' && !f.goodsList.length) {
      proxy.$modal.msgWarning('指定商品盘点至少要选一个商品')
      return
    }
    creating.value = true
    addStocktake({
      wareCode: f.wareCode,
      scope: f.scope,
      goodsCodes: f.goodsList.map(g => g.goodsCode),
      includeDefective: f.includeDefective,
      remark: f.remark
    }).then(res => {
      proxy.$modal.msgSuccess(res.msg)
      createOpen.value = false
      getList()
      openDetail(res.stocktakeCode)
    }).finally(() => { creating.value = false })
  })
}

// ---- 详情 ----
function openDetail(code) {
  detailOpen.value = true
  keyword.value = ''
  onlyDiff.value = false
  loadDetail(code)
}

function loadDetail(code) {
  detailLoading.value = true
  return getStocktake(code || detail.value.stocktakeCode).then(res => {
    detail.value = res.data
    items.value = (res.data.items || []).map(row => ({
      ...row,
      countedQty: row.countedQty == null ? undefined : Number(row.countedQty),
      costPrice: row.costPrice == null ? undefined : Number(row.costPrice)
    }))
  }).finally(() => { detailLoading.value = false })
}

function countLines() {
  return items.value.map(row => ({
    id: row.id,
    countedQty: row.countedQty == null ? null : row.countedQty,
    costPrice: row.costPrice == null ? null : row.costPrice,
    batchCode: row.batchCode || null,
    serialNos: row.serialNos || null,
    remark: row.remark || null
  }))
}

function saveCounts(silent) {
  saving.value = true
  return saveStocktakeCounts(detail.value.stocktakeCode, countLines()).then(() => {
    if (!silent) proxy.$modal.msgSuccess('已保存')
  }).finally(() => { saving.value = false })
}

function fillBook() {
  let n = 0
  for (const row of items.value) {
    if (row.countedQty == null) {
      row.countedQty = bookOf(row)
      n++
    }
  }
  proxy.$modal.msgSuccess(n ? `已把 ${n} 行未盘的实盘数填成账面数，记得保存` : '没有未填的行')
}

function handleAddGoods(list) {
  const codes = (list || []).map(g => g.goodsCode)
  if (!codes.length) return
  // 先存一下，否则重新加载明细会丢掉还没保存的实盘数
  saveCounts(true)
    .then(() => addStocktakeItems(detail.value.stocktakeCode, codes, 1))
    .then(() => {
      proxy.$modal.msgSuccess(`已添加 ${codes.length} 个商品`)
      return loadDetail()
    })
}

function removeRow(row) {
  proxy.$modal.confirm(`从盘点单上删掉「${row.goodsName}」这一行？删掉后这个商品不参与本次盘点，也就解除冻结。`)
    .then(() => saveCounts(true))
    .then(() => delStocktakeItem(detail.value.stocktakeCode, row.id))
    .then(() => loadDetail())
    .catch(() => {})
}

function handleSubmit() {
  const uncounted = items.value.filter(r => r.countedQty == null).length
  if (uncounted) {
    proxy.$modal.msgWarning(`还有 ${uncounted} 行没填实盘数。没清点的删掉这一行，数量和账面一致的点「未填的按账面填」`)
    return
  }
  const s = summary.value
  proxy.$modal.confirm(`提交后等待审核。盘盈 ${s.gainLines} 行，盘亏 ${s.lossLines} 行，确认提交？`)
    .then(() => saveCounts(true))
    .then(() => submitStocktake(detail.value.stocktakeCode))
    .then(() => {
      proxy.$modal.msgSuccess('已提交，等待审核')
      return loadDetail()
    })
    .catch(() => {})
}

function handleApprove() {
  const s = summary.value
  const text = s.gainLines || s.lossLines
    ? `审核通过将生成调整单并立即改动库存：盘盈 ${s.gainLines} 行 +${qty(s.gainQty)}，盘亏 ${s.lossLines} 行 -${qty(s.lossQty)}。确认？`
    : '本次盘点没有差异，审核后直接完成、解除冻结。确认？'
  proxy.$modal.confirm(text).then(() => {
    approving.value = true
    return approveStocktake(detail.value.stocktakeCode)
  }).then(res => {
    proxy.$modal.msgSuccess(res.data?.adjustDocCodes ? '审核完成，已生成 ' + res.data.adjustDocCodes : '审核完成，无差异')
    return loadDetail()
  }).catch(() => {}).finally(() => { approving.value = false })
}

function handleReject() {
  proxy.$prompt('驳回原因（盘点人要照着去复盘）', '驳回盘点', {
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
    inputValidator: v => !!(v && v.trim()) || '请填写驳回原因'
  }).then(({ value }) => rejectStocktake(detail.value.stocktakeCode, value))
    .then(() => {
      proxy.$modal.msgSuccess('已驳回，回到盘点中')
      return loadDetail()
    }).catch(() => {})
}

function handleCancel() {
  proxy.$modal.confirm('作废后这张盘点单不再生效，单上商品解除冻结，库存不做任何调整。确认作废？')
    .then(() => cancelStocktake(detail.value.stocktakeCode))
    .then(() => {
      proxy.$modal.msgSuccess('已作废')
      return loadDetail()
    }).catch(() => {})
}

function batchKey(row) {
  return row.goodsCode + '|' + row.qualityFlag
}

function loadBatches(row) {
  const key = batchKey(row)
  if (batchOptions.value[key]) return
  listBatch({ pageNum: 1, pageSize: 200, goodsCode: row.goodsCode, wareCode: detail.value.wareCode, qualityFlag: row.qualityFlag })
    .then(res => {
      batchOptions.value[key] = (res.rows || []).filter(b => Number(b.remainQty) > 0 && b.onhandFlag !== 0)
    })
}

/**
 * 盘点表是盲盘用的：不打印账面数，免得清点的人照着账面「对数」。
 */
function printSheet() {
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
  const rows = items.value.map((r, i) => `<tr><td>${i + 1}</td><td>${esc(r.goodsCode)}</td><td>${esc(r.goodsName)}</td>
    <td>${esc(r.spec)}</td><td>${esc(r.unit)}</td><td>${r.qualityFlag === 0 ? '不良品' : '良品'}</td><td></td><td></td></tr>`).join('')
  const d = detail.value
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>盘点表 ${esc(d.stocktakeCode)}</title>
    <style>body{font:13px "Microsoft YaHei",sans-serif;margin:24px}h2{margin:0 0 6px}p{margin:0 0 12px;color:#555}
    table{width:100%;border-collapse:collapse}th,td{border:1px solid #333;padding:6px 8px;text-align:left}
    th{background:#f0f0f0}td:nth-child(7){width:90px}td:nth-child(8){width:160px}
    .sign{margin-top:28px;display:flex;gap:80px}</style></head><body>
    <h2>库存盘点表</h2>
    <p>盘点单号：${esc(d.stocktakeCode)}　仓库：${esc(d.wareName)}　账面快照：${esc(proxy.parseTime(d.createTime))}　共 ${items.value.length} 行</p>
    <table><thead><tr><th>#</th><th>商品编码</th><th>商品名称</th><th>规格</th><th>单位</th><th>状态</th><th>实盘数量</th><th>备注</th></tr></thead>
    <tbody>${rows}</tbody></table>
    <div class="sign"><span>盘点人：__________</span><span>复核人：__________</span><span>日期：__________</span></div>
    <script>window.onload=function(){window.print()}<\/script></body></html>`
  const w = window.open('', '_blank')
  if (!w) {
    proxy.$modal.msgWarning('浏览器拦截了弹出窗口，请允许本站弹窗后重试')
    return
  }
  w.document.write(html)
  w.document.close()
}

loadWarehouses()
getList()
</script>

<style lang="scss" scoped>
.hint, .sub, .muted {
  font-size: 12px;
  color: #909399;
}
.picked {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.gain {
  color: #1f7a4d;
  font-weight: 600;
}
.loss {
  color: #c93c37;
  font-weight: 600;
}
.mt8 {
  margin-top: 8px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 8px;
}
.summary {
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}
.warn {
  color: #e6a23c;
  vertical-align: -2px;
  margin-left: 2px;
}
.serials {
  word-break: break-all;
  font-size: 12px;
}
:deep(.row-diff) {
  background: #fdf6ec;
}
:deep(.row-uncounted) td:first-child {
  border-left: 3px solid #409eff;
}
</style>
