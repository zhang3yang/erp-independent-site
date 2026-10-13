<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="单据代码" prop="docCode">
        <el-input v-model="queryParams.docCode" placeholder="请输入单据代码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单据类型" prop="docType">
        <el-select v-model="queryParams.docType" placeholder="请选择类型" clearable style="width: 240px">
          <el-option v-for="item in docTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务来源" prop="sourceDocType">
        <el-select v-model="queryParams.sourceDocType" placeholder="请选择来源" clearable style="width: 180px">
          <el-option label="普通销售发货" value="XSFH" />
          <el-option label="销售补发" value="XSBF" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源单号" prop="sourceDocCode">
        <el-input v-model="queryParams.sourceDocCode" placeholder="批次号或补发单号" clearable style="width: 210px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品编码" prop="goodsCode">
        <el-input v-model="queryParams.goodsCode" placeholder="请输入商品编码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库" prop="wareCode">
        <el-select v-model="queryParams.wareCode" placeholder="请选择仓库" clearable style="width: 240px">
          <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作时间">
        <el-date-picker v-model="operateTimeRange" type="daterange" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="ledgerList">
      <el-table-column label="单据代码" align="center" prop="docCode" width="150" />
      <el-table-column label="单据类型" align="center" prop="docTypeName" width="130">
        <template #default="scope">
          <el-tag v-if="scope.row.sourceDocType === 'XSBF'" type="warning">销售补发出库</el-tag>
          <span v-else>{{ scope.row.docTypeName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="业务来源" align="center" width="180" :show-overflow-tooltip="true">
        <template #default="scope">
          <span v-if="scope.row.sourceDocType === 'XSBF'" class="reship-source">补发单：{{ scope.row.sourceDocCode }}</span>
          <span v-else-if="scope.row.sourceDocType === 'XSFH'">发货批次：{{ scope.row.sourceDocCode }}</span>
          <span v-else>{{ scope.row.sourceDocCode || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="商品编码" align="center" prop="goodsCode" width="120" />
      <el-table-column label="商品名称" align="center" prop="goodsName" :show-overflow-tooltip="true" />
      <el-table-column label="仓库" align="center" prop="wareName" width="100" />
      <el-table-column label="库存状态" align="center" width="150" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ [scope.row.qualityName, scope.row.onhandName].filter(Boolean).join('·') }}
        </template>
      </el-table-column>
      <el-table-column label="变动前数量" align="center" prop="beforeQty" width="100">
        <template #default="scope">
          <span>{{ scope.row.beforeQty }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变动数量" align="center" prop="changeQty" width="100">
        <template #default="scope">
          <span :class="{ 'text-green': scope.row.changeQty > 0, 'text-red': scope.row.changeQty < 0 }">
            {{ scope.row.changeQty > 0 ? '+' : '' }}{{ scope.row.changeQty }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="变动后数量" align="center" prop="afterQty" width="100">
        <template #default="scope">
          <span>{{ scope.row.afterQty }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canSeeCost" label="成本单价" align="center" prop="costPrice" width="100">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.costPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canSeeCost" label="成本金额" align="center" prop="costAmount" width="120">
        <template #default="scope">
          <span :class="{ 'text-green': scope.row.costAmount > 0, 'text-red': scope.row.costAmount < 0 }">
            {{ scope.row.costAmount > 0 ? '+' : '' }}{{ formatCurrency(scope.row.costAmount) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作人" align="center" prop="operatorName" width="100" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.operatorName || scope.row.operator }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作时间" align="center" prop="operateTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.operateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup name="InventoryLedger">
import { listLedger } from '@/api/inventory/ledger'
import { formatCurrency } from '@/utils/currency'
import { listDocTypes } from '@/api/inventory/docType'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { canSeeCost } from '@/utils/price'

const { proxy } = getCurrentInstance()

const ledgerList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const warehouseOptions = ref([])

/**
 * 单据类型选项，从 u_m_doctypeconfig 拉。
 *
 * 这里原来是写死的一份清单，跟不上单据类型的变化：生产三段式改造加的
 * SCLL / SCXH / SCTL 三种类型进不来（流水查得到、筛选下拉里选不到），
 * 还留着已废弃的 SCTLRK 和系统里根本不存在的 RO。过账引擎认的就是这张配置表，
 * 直接读它才不会再次跑偏。
 */
const docTypeOptions = ref([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    docCode: undefined,
    docType: undefined,
    sourceDocType: undefined,
    sourceDocCode: undefined,
    goodsCode: undefined,
    wareCode: undefined,
    operateTimeBegin: undefined,
    operateTimeEnd: undefined
  }
})

const { queryParams } = toRefs(data)

// 不填就是查全部月份。流水按月分表，后端会挑出区间覆盖到的月表；
// 留空时扫全部现存月表，宁可慢一点也不能让上个月的流水查不出来。
const operateTimeRange = ref([])

/** 查询库存流水列表 */
async function getList() {
  loading.value = true
  const range = operateTimeRange.value
  queryParams.value.operateTimeBegin = range && range.length ? range[0] + ' 00:00:00' : undefined
  queryParams.value.operateTimeEnd = range && range.length ? range[1] + ' 23:59:59' : undefined
  try {
    const response = await listLedger(queryParams.value)
    ledgerList.value = response.rows
    total.value = response.total
  } finally {
    loading.value = false
  }
}

/** 加载仓库选项 */
async function loadWarehouseOptions() {
  const response = await listWarehouse({ pageSize: 100 })
  warehouseOptions.value = response.rows || []
}

/** 加载单据类型选项。类型总共十几种，一页拉完不分页 */
async function loadDocTypeOptions() {
  const response = await listDocTypes({ pageNum: 1, pageSize: 200 })
  docTypeOptions.value = (response.rows || []).map(item => ({
    value: item.docType,
    label: item.docTypeName || item.docType
  }))
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  // 日期区间没绑在 queryParams 上，resetForm 清不掉，要单独还原
  operateTimeRange.value = []
  handleQuery()
}

getList()
loadWarehouseOptions()
loadDocTypeOptions()
</script>

<style scoped>
.text-green {
  color: #67c23a;
  font-weight: bold;
}
.text-red {
  color: #f56c6c;
  font-weight: bold;
}
.reship-source {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
