<template>
  <div class="app-container">
    <el-alert title="库存数据由业务单据自动维护，当前页面仅供查询。" type="info" :closable="false" class="mb8" />
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="商品编码" prop="goodsCode">
        <el-input v-model="queryParams.goodsCode" placeholder="请输入商品编码" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品名称" prop="goodsName">
        <el-input v-model="queryParams.goodsName" placeholder="请输入商品名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库" prop="wareCode">
        <el-select v-model="queryParams.wareCode" placeholder="请选择仓库" clearable style="width: 240px">
          <el-option v-for="item in warehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="良品状态" prop="qualityFlag">
        <el-select v-model="queryParams.qualityFlag" placeholder="请选择" clearable style="width: 120px">
          <el-option v-for="item in QUALITY_FLAG_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="在库状态" prop="onhandFlag">
        <el-select v-model="queryParams.onhandFlag" placeholder="请选择" clearable style="width: 120px">
          <el-option v-for="item in ONHAND_FLAG_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="stockList">
      <el-table-column label="商品编码" align="center" prop="goodsCode" width="100" />
      <el-table-column label="商品名称" align="center" prop="goodsName" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="规格型号" align="center" prop="spec" min-width="110" :show-overflow-tooltip="true" />
      <el-table-column label="单位" align="center" prop="unit" width="60" />
      <el-table-column label="仓库" align="center" prop="wareName" width="80" :show-overflow-tooltip="true" />
      <el-table-column label="良品状态" align="center" prop="qualityName" width="90" />
      <el-table-column label="在库状态" align="center" prop="onhandName" width="90" />
      <el-table-column label="库存数量" align="center" prop="physicalQty" width="90">

      </el-table-column>
      <!-- 锁定 = 待审核出库单占的。可用库存 = 库存数量 − 锁定，
           少显示这一列会让可用库存看着对不上账。 -->
      <el-table-column label="锁定数量" align="center" prop="lockedQty" width="90">
        <template #default="scope">
          <span :class="{ 'text-orange': scope.row.lockedQty > 0 }">{{ scope.row.lockedQty || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="可用库存" align="center" prop="availableQty" width="90">
        <template #default="scope">
          <span :class="{ 'text-red': availableOf(scope.row) <= 0 }">{{ availableOf(scope.row) }}</span>
        </template>
      </el-table-column><template #default="scope">
      <span :class="{ 'text-red': scope.row.physicalQty <= 0 }">{{ scope.row.physicalQty }}</span>
    </template>
      <el-table-column v-if="canSeeCost" label="库存金额" align="center" prop="totalCostAmount" min-width="120">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.totalCostAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canSeeCost" label="平均成本价" align="center" prop="avgCostPrice" min-width="115">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.avgCostPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最后更新" align="center" prop="lastUpdated" min-width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastUpdated) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

  </div>
</template>

<script setup name="InventoryStock">
import { listStock } from '@/api/inventory/stock'
import { formatCurrency } from '@/utils/currency'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { QUALITY_FLAG_OPTIONS, ONHAND_FLAG_OPTIONS } from '@/utils/stockState'
import { canSeeCost } from '@/utils/price'

const { proxy } = getCurrentInstance()

const stockList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const warehouseOptions = ref([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    goodsCode: undefined,
    goodsName: undefined,
    wareCode: undefined,
    qualityFlag: undefined,
    onhandFlag: undefined
  }
})

const { queryParams } = toRefs(data)

/** 查询库存列表 */
async function getList() {
  loading.value = true
  try {
    const response = await listStock(queryParams.value)
    stockList.value = response.rows
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

/** 搜索按钮操作 */
/**
 * 可用库存 = 库存数量 − 待审核出库单锁定。
 *
 * 正常走后端算好的 availableQty；接口没返回时才本地兜底算一遍，
 * 口径必须和后端 InventoryStock.getAvailableQty() 一致。
 */
function availableOf(row) {
  if (row.availableQty !== null && row.availableQty !== undefined) return row.availableQty
  return Number(row.physicalQty || 0) - Number(row.lockedQty || 0)
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

getList()
loadWarehouseOptions()
</script>

<style scoped>
.text-red {
  color: #f56c6c;
  font-weight: bold;
}
.text-orange {
  color: #e6a23c;
  font-weight: bold;
}
</style>
