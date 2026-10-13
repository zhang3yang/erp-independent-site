<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="单位名称" prop="partnerName">
        <el-input v-model="queryParams.partnerName" placeholder="请输入单位名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="类型" prop="partnerType">
        <el-select v-model="queryParams.partnerType" placeholder="请选择类型" clearable style="width: 240px">
          <el-option label="供应商" value="supplier" />
          <el-option label="客户" value="customer" />
          <el-option label="部门" value="department" />
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

    <el-table v-loading="loading" :data="partnerList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="单位名称" align="center" prop="partnerName" :show-overflow-tooltip="true">
        <template #default="scope">
          <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.partnerName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="类型" align="center" prop="partnerType" width="100">
        <template #default="scope">
          <el-tag :type="getPartnerTypeTag(scope.row.partnerType)">{{ getPartnerTypeLabel(scope.row.partnerType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="联系人" align="center" prop="contactPerson" width="100" />
      <el-table-column label="手机号" align="center" prop="mobile" width="120" />
      <el-table-column label="电话" align="center" prop="telephone" width="120" />
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

    <!-- 添加或修改供应商/客户对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="partnerRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="单位名称" prop="partnerName">
              <el-input v-model="form.partnerName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="类型" prop="partnerType">
              <el-select v-model="form.partnerType" placeholder="请选择类型" style="width: 100%">
                <el-option label="供应商" value="supplier" />
                <el-option label="客户" value="customer" />
                <el-option label="部门" value="department" />
              </el-select>
            </el-form-item>
          </el-col>
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
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入地址" />
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
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="form.mobile" maxlength="11" placeholder="请输入 11 位手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电话" prop="telephone">
              <el-input v-model="form.telephone" maxlength="11" placeholder="请输入 11 位手机号" />
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

    <!-- 供应商/客户详情对话框 -->
    <el-dialog title="供应商/客户详情" v-model="detailOpen" width="600px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="单位名称">{{ detailForm.partnerName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ getPartnerTypeLabel(detailForm.partnerType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailForm.activeFlag === 1 ? 'success' : 'danger'">{{ detailForm.activeFlag === 1 ? '启用' : '停用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ detailForm.address || '无' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailForm.contactPerson || '无' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailForm.mobile || '无' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ detailForm.telephone || '无' }}</el-descriptions-item>
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

<script setup name="InventoryPartner">
import { listPartner, getPartner, addPartner, updatePartner, delPartner } from '@/api/inventory/partner'
import { mobileRule } from '@/utils/validate'

const { proxy } = getCurrentInstance()

const partnerList = ref([])
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
    partnerCode: undefined,
    partnerName: undefined,
    partnerType: undefined,
    activeFlag: undefined
  },
  rules: {
    partnerName: [
      { required: true, message: '单位名称不能为空', trigger: 'blur' }
    ],
    partnerType: [
      { required: true, message: '请选择类型', trigger: 'change' }
    ],
    // 手机号和电话都按 11 位手机号校验，口径统一在 utils/validate.js 的 MOBILE_PATTERN
    mobile: [mobileRule('手机号必须是 11 位手机号')],
    telephone: [mobileRule('电话必须是 11 位手机号')]
  }
})

const { form, detailForm, queryParams, rules } = toRefs(data)

/** 查询供应商/客户列表 */
function getList() {
  loading.value = true
  listPartner(queryParams.value).then(response => {
    partnerList.value = response.rows
    total.value = response.total
    loading.value = false
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
  ids.value = selection.map(item => item.partnerCode)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '添加供应商/客户'
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const partnerCode = row.partnerCode || ids.value[0]
  getPartner(partnerCode).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改供应商/客户'
  })
}

/** 详情按钮操作 */
function handleDetail(row) {
  getPartner(row.partnerCode).then(response => {
    detailForm.value = response.data
    detailOpen.value = true
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const partnerCodes = row.partnerCode || ids.value
  const deleteTarget = row.partnerName ? '供应商“' + row.partnerName + '”' : '选中的' + ids.value.length + '个供应商'
  proxy.$modal.confirm('是否确认删除' + deleteTarget + '？').then(function () {
    return delPartner(partnerCodes)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 表单重置 */
function reset() {
  form.value = {
    partnerCode: undefined,
    partnerName: undefined,
    partnerType: 'supplier',
    address: undefined,
    contactPerson: undefined,
    mobile: undefined,
    telephone: undefined,
    activeFlag: 1,
    remark: undefined
  }
  proxy.resetForm('partnerRef')
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 确定按钮 */
function submitForm() {
  proxy.$refs['partnerRef'].validate(valid => {
    if (valid) {
      if (form.value.shopCode) {
        updatePartner(form.value).then(response => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addPartner(form.value).then(response => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 获取类型标签 */
function getPartnerTypeTag(type) {
  const typeMap = {
    'supplier': 'primary',
    'customer': 'success',
    'department': 'warning'
  }
  return typeMap[type] || 'info'
}

/** 获取类型标签 */
function getPartnerTypeLabel(type) {
  const typeMap = {
    'supplier': '供应商',
    'customer': '客户',
    'department': '部门'
  }
  return typeMap[type] || '未知'
}

getList()
</script>
