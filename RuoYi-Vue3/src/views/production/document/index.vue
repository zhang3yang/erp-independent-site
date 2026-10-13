<template>
  <div class="app-container">
    <el-alert type="info" show-icon :closable="false" class="mb8"
      title="生产工单产生的库存单据都在这里，只读"
      description="领料 / 消耗 / 退料 / 成品入库四类单据由生产工单自动开出并即时审核，不能在这里新建、修改或撤销。单据按月分表存放，查上月的单据请切换「单据月份」。" />

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="单据月份" prop="tableSuffix">
        <!-- 单据按月分表。选了月份就只查那一张表，清空则跨全部月份查 -->
        <el-date-picker v-model="queryParams.tableSuffix" type="month" value-format="YYYYMM" format="YYYY-MM"
          clearable placeholder="全部月份" style="width: 200px" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="单据类型" prop="docType">
        <el-select v-model="queryParams.docType" placeholder="全部类型" clearable style="width: 200px" @change="handleQuery">
          <el-option v-for="item in PRODUCTION_DOC_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="生产工单" prop="sourceDocCode">
        <el-input v-model="queryParams.sourceDocCode" placeholder="请输入工单号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单据号" prop="docCode">
        <el-input v-model="queryParams.docCode" placeholder="支持号段前缀" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="documentList" empty-text="该月份没有生产单据">
      <el-table-column label="单据号" align="center" prop="docCode" width="170" />
      <el-table-column label="单据类型" align="center" prop="docTypeName" width="130">
        <template #default="scope">
          <el-tag :type="docTagType(scope.row.docType)">{{ scope.row.docTypeName || docTypeLabel(scope.row.docType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生产工单" align="center" prop="sourceDocCode" width="160" />
      <el-table-column label="单据日期" align="center" width="115">
        <template #default="scope">{{ parseTime(scope.row.docDate, '{y}-{m}-{d}') }}</template>
      </el-table-column>
      <el-table-column label="仓库" align="center" :show-overflow-tooltip="true" min-width="110">
        <template #default="scope">{{ warehouseOf(scope.row) || '-' }}</template>
      </el-table-column>
      <el-table-column label="库存流转" align="center" min-width="180" :show-overflow-tooltip="true">
        <template #default="scope">{{ stateFlowOf(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="总数量" align="center" prop="totalQty" width="100" />
      <el-table-column v-if="canSeeCost" label="成本金额" align="center" width="120">
        <template #default="scope">{{ formatCurrency(scope.row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="confirmState" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.confirmState === '已审核' ? 'success' : scope.row.confirmState === '已驳回' ? 'danger' : 'warning'">
            {{ scope.row.confirmState }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="经办人" align="center" prop="handler" width="100" />
      <el-table-column label="操作" align="center" width="90" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="生产单据详情" v-model="detailOpen" width="960px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border v-loading="detailLoading">
        <el-descriptions-item label="单据号">{{ detail.docCode }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">{{ detail.docTypeName || docTypeLabel(detail.docType) }}</el-descriptions-item>
        <el-descriptions-item label="生产工单">{{ detail.sourceDocCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单据日期">{{ parseTime(detail.docDate, '{y}-{m}-{d}') }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ warehouseOf(detail) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存流转">{{ stateFlowOf(detail) }}</el-descriptions-item>
        <el-descriptions-item label="总数量">{{ detail.totalQty }}</el-descriptions-item>
        <el-descriptions-item v-if="canSeeCost" label="成本金额">{{ formatCurrency(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="经办人">{{ detail.handler || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.confirmUser || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">单据明细</el-divider>

      <el-table :data="detail.details" border v-loading="detailLoading">
        <el-table-column label="商品编码" prop="goodsCode" width="120" />
        <el-table-column label="商品名称" prop="goodsName" :show-overflow-tooltip="true" min-width="140" />
        <el-table-column label="规格" prop="spec" width="90" />
        <el-table-column label="单位" prop="unit" width="60" align="center" />
        <el-table-column label="仓库" prop="wareName" width="100" />
        <el-table-column label="数量" prop="qty" width="90" align="right" />
        <el-table-column v-if="canSeeCost" label="成本单价" width="110" align="right">
          <template #default="scope">{{ scope.row.costPrice == null ? '-' : formatCurrency(scope.row.costPrice) }}</template>
        </el-table-column>
        <el-table-column v-if="canSeeCost" label="成本金额" width="110" align="right">
          <template #default="scope">{{ scope.row.costAmount == null ? '-' : formatCurrency(scope.row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="批次" prop="batchCode" width="140" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.batchCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="序列号" min-width="140" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.serialNumbers?.length ? scope.row.serialNumbers.join('、') : '-' }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ProductionDocument">
import { listDocument, getDocument } from '@/api/inventory/document'
import { formatCurrency } from '@/utils/currency'
import { stockStateLabel } from '@/utils/stockState'
import { parseTime } from '@/utils/ruoyi'
import { canSeeCost } from '@/utils/price'

const { proxy } = getCurrentInstance()

/**
 * 生产工单会开出的库存单据类型。
 *
 * direction 只用于决定「仓库」和「库存流转」两列怎么展示：入库看去向、出库看来源、
 * 状态流转（N）是同一个仓库里换格子，要把两头都显示出来。
 * LLCK / SCTLRK 是三段式改造前的老类型，新单据不会再有，列在这里只为历史单据能查到。
 */
const PRODUCTION_DOC_TYPES = [
  { value: 'SCLL', label: '生产领料', direction: 'N' },
  { value: 'SCXH', label: '生产消耗', direction: 'O' },
  { value: 'SCCPRK', label: '生产成品入库', direction: 'I' },
  { value: 'SCTL', label: '生产退料', direction: 'N' },
  { value: 'LLCK', label: '领料出库（历史）', direction: 'O' },
  { value: 'SCTLRK', label: '生产退料入库（历史）', direction: 'I' }
]

const ALL_PRODUCTION_DOC_TYPES = PRODUCTION_DOC_TYPES.map(item => item.value).join(',')

const documentList = ref([])
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref({})

/** 默认查当月：单据按月分表，后端不给月份时也是落到当月 */
const currentSuffix = parseTime(new Date(), '{y}{m}')

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    tableSuffix: currentSuffix,
    docType: undefined,
    sourceDocCode: undefined,
    docCode: undefined,
    // 不选具体类型时，也只查生产这六种，别把采购、销售的单据混进来
    docTypes: ALL_PRODUCTION_DOC_TYPES
  }
})

const { queryParams } = toRefs(data)

function docTypeOf(docType) {
  return PRODUCTION_DOC_TYPES.find(item => item.value === docType)
}

function docTypeLabel(docType) {
  return docTypeOf(docType)?.label || docType || ''
}

function docTagType(docType) {
  const direction = docTypeOf(docType)?.direction
  if (direction === 'I') return 'success'
  if (direction === 'O') return 'warning'
  return 'info'
}

/** 出库看来源仓库，入库看目标仓库，状态流转两头是同一个仓库 */
function warehouseOf(row) {
  if (!row) return ''
  return docTypeOf(row.docType)?.direction === 'I'
    ? row.toWareName || row.toWareCode
    : row.fromWareName || row.fromWareCode
}

/**
 * 库存流转：领料是「良品·在库 → 良品·不在库」，退料反过来，
 * 消耗是从车间的在制品永久出库，成品入库是凭空入到可用库存。
 */
function stateFlowOf(row) {
  if (!row) return ''
  const from = stockStateLabel({ qualityFlag: row.fromQualityFlag, onhandFlag: row.fromOnhandFlag })
  const to = stockStateLabel({ qualityFlag: row.toQualityFlag, onhandFlag: row.toOnhandFlag })
  const direction = docTypeOf(row.docType)?.direction
  if (direction === 'I') return to ? `入库 → ${to}` : '-'
  if (direction === 'O') return from ? `${from} → 出库` : '-'
  return from && to ? `${from} → ${to}` : from || to || '-'
}

/** 查询生产单据列表 */
async function getList() {
  loading.value = true
  try {
    const response = await listDocument(queryParams.value)
    documentList.value = response.rows || []
    total.value = response.total || 0
  } finally {
    loading.value = false
  }
}

async function handleView(row) {
  detailOpen.value = true
  detail.value = {}
  detailLoading.value = true
  try {
    // 单据在哪张月表由列表行带回来，不能按当月查，否则跨月单据查不到
    const response = await getDocument(row.docCode, row.tableSuffix || queryParams.value.tableSuffix)
    detail.value = response.data || {}
  } finally {
    detailLoading.value = false
  }
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  // 重置回当月而不是「全部月份」：生产单据量大，默认跨月查会明显变慢，
  // 要翻历史的人自己清空月份即可
  queryParams.value.tableSuffix = currentSuffix
  handleQuery()
}

getList()
</script>
