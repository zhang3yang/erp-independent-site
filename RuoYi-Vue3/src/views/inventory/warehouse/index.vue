<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="仓库名称" prop="wareName">
        <el-input v-model="queryParams.wareName" placeholder="请输入仓库名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库类型" prop="wareType">
        <el-select v-model="queryParams.wareType" placeholder="请选择仓库类型" clearable style="width: 240px">
          <el-option label="实体仓" value="实体仓" />
          <el-option label="虚拟仓" value="虚拟仓" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="activeFlag">
        <el-select v-model="queryParams.activeFlag" placeholder="请选择状态" clearable style="width: 240px">
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
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="warehouseList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="仓库名称" align="center" prop="wareName" :show-overflow-tooltip="true">
        <template #default="scope">
          <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.wareName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="仓库类型" align="center" prop="wareType" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.wareType === '实体仓' ? 'primary' : 'warning'">{{ scope.row.wareType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="地址" align="center" prop="address" :show-overflow-tooltip="true" />
      <el-table-column label="联系人" align="center" prop="contactPerson" width="100" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" width="120" />
      <el-table-column label="状态" align="center" prop="activeFlag" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.activeFlag === 1 ? 'success' : 'danger'">{{ scope.row.activeFlag === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改仓库对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="warehouseRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="仓库名称" prop="wareName">
              <el-input v-model="form.wareName" placeholder="请输入仓库名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库类型" prop="wareType">
              <el-select v-model="form.wareType" placeholder="请选择仓库类型" style="width: 100%">
                <el-option label="实体仓" value="实体仓" />
                <el-option label="虚拟仓" value="虚拟仓" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上级仓库" prop="parentCode">
              <el-select v-model="form.parentCode" placeholder="请选择上级仓库" clearable style="width: 100%">
                <el-option v-for="item in parentWarehouseOptions" :key="item.wareCode" :label="warehouseLabel(item)" :value="item.wareCode" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="仓库地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入仓库地址" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" maxlength="11" placeholder="请输入 11 位手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="activeFlag">
              <el-radio-group v-model="form.activeFlag">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 仓库详情对话框 -->
    <el-dialog title="仓库详情" v-model="detailOpen" width="600px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="仓库名称">{{ detailForm.wareName }}</el-descriptions-item>
        <el-descriptions-item label="仓库类型">{{ detailForm.wareType }}</el-descriptions-item>
        <el-descriptions-item label="上级仓库">{{ detailForm.parentName || '无' }}</el-descriptions-item>
        <el-descriptions-item label="仓库地址" :span="2">{{ detailForm.address || '无' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailForm.contactPerson || '无' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailForm.contactPhone || '无' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailForm.activeFlag === 1 ? 'success' : 'danger'">{{ detailForm.activeFlag === 1 ? '启用' : '停用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailForm.remark || '无' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="InventoryWarehouse">
import { listWarehouse, getWarehouse, addWarehouse, updateWarehouse, delWarehouse } from '@/api/inventory/warehouse'
import { warehouseLabel } from '@/utils/warehouse'
import { mobileRule } from '@/utils/validate'

const { proxy } = getCurrentInstance()

const warehouseList = ref([])
const parentWarehouseOptions = ref([])
const open = ref(false)
const detailOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const data = reactive({
  form: {},
  detailForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    wareCode: undefined,
    wareName: undefined,
    wareType: undefined,
    activeFlag: undefined
  },
  rules: {
    wareName: [
      { required: true, message: '仓库名称不能为空', trigger: 'blur' }
    ],
    wareType: [
      { required: true, message: '请选择仓库类型', trigger: 'change' }
    ],
    // 联系电话按 11 位手机号校验，口径统一在 utils/validate.js 的 MOBILE_PATTERN
    contactPhone: [mobileRule('联系电话必须是 11 位手机号')]
  }
})

const { form, detailForm, queryParams, rules } = toRefs(data)

/** 查询仓库列表 */
function getList() {
  loading.value = true
  listWarehouse(queryParams.value).then(response => {
    warehouseList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 查询上级仓库列表 */
function getParentWarehouseList() {
  listWarehouse({ activeFlag: 1 }).then(response => {
    parentWarehouseOptions.value = response.rows
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.wareCode)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  getParentWarehouseList()
  open.value = true
  title.value = '添加仓库'
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  getParentWarehouseList()
  const wareCode = row.wareCode || ids.value[0]
  getWarehouse(wareCode).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改仓库'
  })
}

/** 详情按钮操作 */
function handleDetail(row) {
  getWarehouse(row.wareCode).then(response => {
    detailForm.value = response.data
    detailOpen.value = true
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const wareCodes = row.wareCode || ids.value
  const deleteTarget = row.wareName ? '仓库“' + row.wareName + '”' : '选中的' + ids.value.length + '个仓库'
  proxy.$modal.confirm('是否确认删除' + deleteTarget + '？').then(function () {
    return delWarehouse(wareCodes)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 表单重置 */
function reset() {
  form.value = {
    wareCode: undefined,
    wareName: undefined,
    parentCode: undefined,
    wareType: '实体仓',
    address: undefined,
    contactPerson: undefined,
    contactPhone: undefined,
    activeFlag: 1,
    remark: undefined
  }
  proxy.resetForm('warehouseRef')
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 确定按钮 */
function submitForm() {
  proxy.$refs['warehouseRef'].validate(valid => {
    if (valid) {
      if (form.value.shopCode) {
        updateWarehouse(form.value).then(response => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addWarehouse(form.value).then(response => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

getList()
</script>
