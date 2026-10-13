<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item label="商品" prop="goodsCode"><el-input v-model="queryParams.goodsCode" placeholder="商品编码" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="仓库" prop="wareCode"><el-select v-model="queryParams.wareCode" placeholder="请选择仓库" clearable filterable><el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" /></el-select></el-form-item>
      <el-form-item label="状态" prop="status"><el-select v-model="queryParams.status" placeholder="请选择状态" clearable><el-option label="预警中" value="ACTIVE" /><el-option label="已确认" value="ACKNOWLEDGED" /><el-option label="已恢复" value="RECOVERED" /><el-option label="已关闭" value="CLOSED" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><el-button v-hasPermi="['inventory:warning:check']" type="warning" plain icon="Refresh" @click="handleCheck">立即检查</el-button><right-toolbar v-model:showSearch="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="recordList">
      <el-table-column label="商品" prop="goodsName" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.goodsName }}（{{ row.goodsCode }}）</template></el-table-column>
      <el-table-column label="仓库" prop="wareName" min-width="120" show-overflow-tooltip />
      <el-table-column label="可用库存" prop="availableQty" align="center" width="100" />
      <el-table-column label="预警阈值" prop="thresholdQty" align="center" width="100" />
      <el-table-column label="状态" prop="status" align="center" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="触发时间" prop="triggerTime" align="center" width="165"><template #default="{ row }">{{ parseTime(row.triggerTime) }}</template></el-table-column>
      <el-table-column label="最近检查" prop="lastCheckedTime" align="center" width="165"><template #default="{ row }">{{ parseTime(row.lastCheckedTime) }}</template></el-table-column>
      <el-table-column label="处理备注" prop="handleRemark" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="150" fixed="right"><template #default="{ row }"><el-button v-if="row.status === 'ACTIVE'" v-hasPermi="['inventory:warning:record:handle']" link type="primary" @click="handleAcknowledge(row)">确认</el-button><el-button v-if="row.status === 'ACTIVE' || row.status === 'ACKNOWLEDGED'" v-hasPermi="['inventory:warning:record:handle']" link type="danger" @click="handleClose(row)">关闭</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="InventoryWarningRecord">
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { acknowledgeWarning, checkWarnings, closeWarning, listWarningRecords } from '@/api/inventory/warning'

const { proxy } = getCurrentInstance()
const recordList = ref([])
const warehouses = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const data = reactive({ queryParams: { pageNum: 1, pageSize: 20, goodsCode: undefined, wareCode: undefined, status: undefined } })
const { queryParams } = toRefs(data)
function getList() { loading.value = true; listWarningRecords(queryParams.value).then(res => { recordList.value = res.rows; total.value = res.total }).finally(() => { loading.value = false }) }
function loadOptions() { listWarehouse({ pageSize: 1000, activeFlag: 1 }).then(res => { warehouses.value = res.rows || [] }) }
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function inputRemark(title) { return proxy.$prompt('请输入处理备注（可选）', title, { inputType: 'textarea', inputValidator: value => !value || value.length <= 500 || '备注不能超过500个字符' }).then(({ value }) => ({ remark: value })) }
function handleAcknowledge(row) { inputRemark('确认预警').then(body => acknowledgeWarning(row.recordId, body)).then(() => { proxy.$modal.msgSuccess('已确认'); getList() }).catch(() => {}) }
function handleClose(row) { inputRemark('关闭预警').then(body => closeWarning(row.recordId, body)).then(() => { proxy.$modal.msgSuccess('已关闭'); getList() }).catch(() => {}) }
function handleCheck() { checkWarnings().then(() => { proxy.$modal.msgSuccess('检查完成'); getList() }) }
function statusLabel(status) { return { ACTIVE: '预警中', ACKNOWLEDGED: '已确认', RECOVERED: '已恢复', CLOSED: '已关闭' }[status] || status }
function statusType(status) { return { ACTIVE: 'danger', ACKNOWLEDGED: 'warning', RECOVERED: 'success', CLOSED: 'info' }[status] || 'info' }
loadOptions()
getList()
</script>
