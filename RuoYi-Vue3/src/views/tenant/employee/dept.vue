<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['tenant:employee:dept:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Check" :disabled="!sortChanged || savingSort" :loading="savingSort" @click="handleSaveSort" v-hasPermi="['tenant:employee:dept:edit']">保存排序</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Sort" @click="toggleExpandAll">展开/折叠</el-button>
      </el-col>
      <right-toolbar :search="false" @queryTable="getList" />
    </el-row>

    <el-table
      v-if="refreshTable"
      v-loading="loading"
      :data="deptList"
      row-key="deptId"
      :default-expand-all="isExpandAll"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
    >
      <el-table-column prop="deptName" label="部门名称" min-width="240" />
      <el-table-column prop="orderNum" label="排序" width="150">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.orderNum"
            controls-position="right"
            :min="0"
            style="width: 96px"
            @change="markSortChanged"
          />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="String(scope.row.status) === '0' ? 'success' : 'danger'">
            {{ String(scope.row.status) === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="210" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['tenant:employee:dept:edit']">修改</el-button>
          <el-button link type="primary" icon="Plus" @click="handleAdd(scope.row)" v-hasPermi="['tenant:employee:dept:add']">新增</el-button>
          <el-button
            v-if="!isRootDept(scope.row)"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['tenant:employee:dept:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="title" v-model="open" width="560px" append-to-body @closed="reset" :close-on-click-modal="false">
      <el-form ref="deptRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="!isEditingRoot" label="上级部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="deptOptions"
            :props="{ value: 'deptId', label: 'deptName', children: 'children' }"
            value-key="deptId"
            placeholder="请选择上级部门"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="请输入部门名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="显示排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="部门状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确定</el-button>
          <el-button @click="cancel">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TenantEmployeeDept">
import { addDept, delDept, getDept, getDeptTree, updateDept } from '@/api/tenant/employeeManagement'

const { proxy } = getCurrentInstance()

const deptList = ref([])
const deptOptions = ref([])
const loading = ref(false)
const open = ref(false)
const title = ref('')
const isExpandAll = ref(true)
const refreshTable = ref(true)
const sortChanged = ref(false)
const savingSort = ref(false)
const originalOrders = ref(new Map())

const data = reactive({
  form: {},
  rules: {
    deptName: [{ required: true, message: '部门名称不能为空', trigger: 'blur' }],
    orderNum: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
  }
})

const { form, rules } = toRefs(data)
const isEditingRoot = computed(() => form.value.deptId !== undefined && isRootDept(form.value))

function responseData(response) {
  return Array.isArray(response.data) ? response.data : []
}

function toTree(list) {
  if (!Array.isArray(list)) return []
  if (list.some(item => Array.isArray(item.children) && item.children.length)) return list
  return proxy.handleTree(list, 'deptId')
}

function getList() {
  loading.value = true
  getDeptTree().then(response => {
    deptList.value = toTree(responseData(response))
    originalOrders.value = new Map(flattenDept(deptList.value).map(item => [String(item.deptId), item.orderNum]))
    sortChanged.value = false
  }).finally(() => {
    loading.value = false
  })
}

function loadDeptOptions(excludeId) {
  return getDeptTree().then(response => {
    const source = JSON.parse(JSON.stringify(responseData(response)))
    deptOptions.value = removeDept(toTree(source), excludeId)
  })
}

function removeDept(list, excludeId) {
  if (excludeId === undefined || excludeId === null) return list
  return list
    .filter(item => String(item.deptId) !== String(excludeId))
    .map(item => ({ ...item, children: removeDept(item.children || [], excludeId) }))
}

function reset() {
  form.value = {
    deptId: undefined,
    parentId: undefined,
    deptName: undefined,
    orderNum: 0,
    status: '0'
  }
  proxy.resetForm('deptRef')
}

function handleAdd(row) {
  reset()
  loadDeptOptions()
  if (row) form.value.parentId = row.deptId
  open.value = true
  title.value = '新增部门'
}

function handleUpdate(row) {
  reset()
  Promise.all([loadDeptOptions(row.deptId), getDept(row.deptId)]).then(([, response]) => {
    form.value = { ...response.data, status: String(response.data.status ?? '0') }
    open.value = true
    title.value = '修改部门'
  })
}

function submitForm() {
  proxy.$refs.deptRef.validate(valid => {
    if (!valid) return
    const request = form.value.deptId === undefined ? addDept : updateDept
    request(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.deptId === undefined ? '新增成功' : '修改成功')
      open.value = false
      getList()
    })
  })
}

function cancel() {
  open.value = false
}

function handleDelete(row) {
  proxy.$modal.confirm(`是否确认删除部门“${row.deptName}”？`).then(() => delDept(row.deptId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

function isRootDept(row) {
  return row.parentId === undefined || row.parentId === null || row.parentId === '' || Number(row.parentId) === 0
}

function toggleExpandAll() {
  refreshTable.value = false
  isExpandAll.value = !isExpandAll.value
  nextTick(() => {
    refreshTable.value = true
  })
}

function markSortChanged() {
  sortChanged.value = true
}

function flattenDept(list, result = []) {
  list.forEach(item => {
    result.push(item)
    if (item.children?.length) flattenDept(item.children, result)
  })
  return result
}

async function handleSaveSort() {
  const rows = flattenDept(deptList.value).filter(item =>
    String(originalOrders.value.get(String(item.deptId))) !== String(item.orderNum)
  )
  if (!rows.length) {
    sortChanged.value = false
    proxy.$modal.msgWarning('未检测到排序修改')
    return
  }
  savingSort.value = true
  try {
    await Promise.all(rows.map(item => updateDept({
      deptId: item.deptId,
      parentId: item.parentId,
      deptName: item.deptName,
      orderNum: item.orderNum,
      status: item.status
    })))
    proxy.$modal.msgSuccess('排序保存成功')
    getList()
  } catch (error) {
    getList()
  } finally {
    savingSort.value = false
  }
}

getList()
</script>
