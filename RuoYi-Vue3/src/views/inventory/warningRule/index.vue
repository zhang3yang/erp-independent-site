<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item label="商品编码" prop="goodsCode">
        <el-input v-model="queryParams.goodsCode" placeholder="请输入商品编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品名称" prop="goodsName">
        <el-input v-model="queryParams.goodsName" placeholder="请输入商品名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库" prop="wareCode">
        <el-select v-model="queryParams.wareCode" placeholder="请选择仓库" clearable filterable>
          <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-select v-model="queryParams.enabled" placeholder="请选择状态" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button v-hasPermi="['inventory:warning:rule:add']" type="primary" plain icon="Plus" @click="handleAdd">新增规则</el-button></el-col>
      <el-col :span="1.5"><el-button v-hasPermi="['inventory:warning:rule:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button></el-col>
      <el-col :span="1.5"><el-button v-hasPermi="['inventory:warning:rule:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="ruleList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="商品编码" prop="goodsCode" align="center" width="130" />
      <el-table-column label="商品名称" prop="goodsName" align="center" min-width="150" show-overflow-tooltip />
      <el-table-column label="仓库" prop="wareName" align="center" min-width="130" show-overflow-tooltip />
      <el-table-column label="预警阈值" prop="thresholdQty" align="center" width="110" />
      <el-table-column label="状态" prop="enabled" align="center" width="90">
        <template #default="{ row }"><el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" align="center" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['inventory:warning:rule:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
          <el-button v-hasPermi="['inventory:warning:rule:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="520px" append-to-body :close-on-click-modal="false">
      <el-form ref="ruleRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="商品" prop="goodsCode">
          <el-select v-model="form.goodsCode" filterable placeholder="请选择商品" style="width: 100%">
            <el-option v-for="item in goods" :key="item.goodsCode" :label="item.goodsName" :value="item.goodsCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="wareCode">
          <el-select v-model="form.wareCode" filterable placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouses" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警阈值" prop="thresholdQty">
          <decimal-input :min-decimals="0" :max-decimals="4" v-model="form.thresholdQty" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="submitForm">确定</el-button><el-button @click="cancel">取消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="InventoryWarningRule">
import { listGoods } from '@/api/inventory/goods'
import { listWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { addWarningRule, delWarningRule, listWarningRules, updateWarningRule } from '@/api/inventory/warning'
import DecimalInput from '@/components/DecimalInput'

const { proxy } = getCurrentInstance()
const ruleList = ref([])
const goods = ref([])
const warehouses = ref([])
const loading = ref(true)
const showSearch = ref(true)
const open = ref(false)
const title = ref('')
const total = ref(0)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const data = reactive({
  form: {},
  queryParams: { pageNum: 1, pageSize: 20, goodsCode: undefined, goodsName: undefined, wareCode: undefined, enabled: undefined },
  rules: {
    goodsCode: [{ required: true, message: '请选择商品', trigger: 'change' }],
    wareCode: [{ required: true, message: '请选择仓库', trigger: 'change' }],
    thresholdQty: [{ required: true, message: '请输入预警阈值', trigger: 'blur' }]
  }
})
const { form, queryParams, rules } = toRefs(data)

function getList() {
  loading.value = true
  listWarningRules(queryParams.value).then(res => { ruleList.value = res.rows; total.value = res.total }).finally(() => { loading.value = false })
}
function loadOptions() {
  Promise.all([listGoods({ pageSize: 1000 }), listWarehouse({ pageSize: 1000, activeFlag: 1 })]).then(([goodsRes, warehouseRes]) => {
    goods.value = goodsRes.rows || []
    warehouses.value = warehouseRes.rows || []
  })
}
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function handleSelectionChange(selection) { ids.value = selection.map(item => item.ruleId); single.value = selection.length !== 1; multiple.value = !selection.length }
function handleAdd() { reset(); title.value = '新增预警规则'; open.value = true }
function handleUpdate(row) {
  reset()
  const item = row || ruleList.value.find(item => item.ruleId === ids.value[0])
  form.value = { ...item }
  open.value = true
  title.value = '修改预警规则'
}
function handleDelete(row) {
  const ruleIds = row?.ruleId || ids.value
  proxy.$modal.confirm(`是否确认删除预警规则编号为“${ruleIds}”的数据项？`).then(() => delWarningRule(ruleIds)).then(() => { getList(); proxy.$modal.msgSuccess('删除成功') }).catch(() => {})
}
function reset() { form.value = { ruleId: undefined, goodsCode: undefined, wareCode: undefined, thresholdQty: 0, enabled: 1, remark: undefined }; proxy.resetForm('ruleRef') }
function cancel() { open.value = false; reset() }
function submitForm() {
  proxy.$refs.ruleRef.validate(valid => {
    if (!valid) return
    const action = form.value.ruleId ? updateWarningRule : addWarningRule
    action(form.value).then(() => { proxy.$modal.msgSuccess(form.value.ruleId ? '修改成功' : '新增成功'); open.value = false; getList() })
  })
}

loadOptions()
getList()
</script>
