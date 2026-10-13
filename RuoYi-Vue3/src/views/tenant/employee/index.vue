<template>
  <div class="app-container tree-sidebar-manage-wrap">
    <tree-panel
      ref="deptTreeRef"
      title="部门信息"
      :tree-data="deptOptions"
      search-placeholder="请输入部门名称"
      storage-key="tenant-employee-dept-sidebar-width"
      :default-expand-all="true"
      @node-click="handleDeptClick"
      @refresh="loadDeptTree"
    />

    <div class="tree-sidebar-content">
      <div class="content-inner">
        <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item label="员工姓名" prop="nickName">
            <el-input v-model="queryParams.nickName" placeholder="请输入员工姓名" clearable style="width: 200px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="工号" prop="jobNo">
            <el-input v-model="queryParams.jobNo" placeholder="请输入工号" clearable style="width: 180px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="手机号码" prop="phonenumber">
            <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号码" clearable style="width: 200px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-if="canAddEmployee" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          </el-col>
          <right-toolbar
            v-model:showSearch="showSearch"
            @queryTable="getList"
            :columns="columns"
            storage-key="tenant-employee-columns"
          />
        </el-row>

        <el-table v-loading="loading" :data="employeeList">
          <el-table-column label="员工姓名" prop="nickName" min-width="120" v-if="columns.nickName.visible" :show-overflow-tooltip="true" />
          <el-table-column label="工号" prop="jobNo" width="120" v-if="columns.jobNo.visible" :show-overflow-tooltip="true" />
          <el-table-column label="部门" min-width="140" v-if="columns.deptName.visible" :show-overflow-tooltip="true">
            <template #default="scope">{{ getEmployeeDeptName(scope.row) || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="170" v-if="columns.roleNames.visible" :show-overflow-tooltip="true">
            <template #default="scope">{{ formatRoleNames(scope.row.roleNames) || '-' }}</template>
          </el-table-column>
          <el-table-column label="手机号码" width="130" v-if="columns.phonenumber.visible">
            <template #default="scope">{{ maskPhoneNumber(scope.row.phonenumber) }}</template>
          </el-table-column>
          <el-table-column label="加入时间" align="center" prop="joinTime" width="170" v-if="columns.joinTime.visible">
            <template #default="scope">{{ parseTime(scope.row.joinTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
            <template #default="scope">
              <template v-if="!isShopAdministrator(scope.row)">
                <el-button v-if="canEditEmployee || isSelf(scope.row)" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
                <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['tenant:employee:user:remove']">删除</el-button>
              </template>
              <el-tag v-else size="small" type="warning">商家管理员</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </div>
    </div>

    <el-dialog :title="title" v-model="open" width="660px" append-to-body @closed="reset" :close-on-click-modal="false">
      <el-form ref="employeeRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="员工姓名" prop="nickName">
              <el-input v-model="form.nickName" placeholder="请输入员工姓名" maxlength="30" :disabled="isEditing" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号码" prop="phonenumber">
              <el-input v-model="form.phonenumber" placeholder="请输入手机号码" maxlength="11" :disabled="isEditing" @input="handleEmployeePhoneInput">
                <template v-if="!isEditing" #append>
                  <el-button :disabled="smsCountdown > 0 || smsSending" @click="handleSendSmsCode">
                    {{ smsCountdown > 0 ? `${smsCountdown}s后重试` : '获取验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" prop="jobNo">
              <el-input v-model="form.jobNo" placeholder="请输入工号" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col v-if="!isEditing" :span="12">
            <el-form-item label="短信验证码" prop="smsCode">
              <el-input
                v-model="form.smsCode"
                placeholder="请输入6位验证码"
                maxlength="6"
                inputmode="numeric"
                autocomplete="one-time-code"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="!isEditingSelf" :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptFormOptions"
                :props="{ value: 'deptId', label: 'deptName', children: 'children' }"
                value-key="deptId"
                placeholder="请选择所属部门"
                check-strictly
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="!isEditingSelf" label="分配角色" prop="roleCodes">
          <el-select v-model="form.roleCodes" multiple clearable collapse-tags collapse-tags-tooltip placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.roleCode"
              :label="role.roleName"
              :value="role.roleCode"
              :disabled="String(role.status) === '1' || Number(role.systemFlag) === 1"
            >
              <span>{{ role.roleName }}</span>
              <span v-if="Number(role.systemFlag) === 1" class="role-option-tip">内置管理员角色不可分配</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="!isEditing && !/^\d{6}$/.test(form.smsCode || '')" @click="submitForm">确定</el-button>
          <el-button @click="cancel">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TenantEmployee">
import TreePanel from '@/components/TreePanel'
import useUserStore from '@/store/modules/user'
import { mobileRule } from '@/utils/validate'
import {
  addEmployee,
  delEmployee,
  getDeptTree,
  getEmployee,
  listEmployee,
  listRole,
  sendEmployeeSmsCode,
  updateEmployee,
  updateMyProfile
} from '@/api/tenant/employeeManagement'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()

const hasPermission = permission => userStore.permissions.includes('*:*:*') || userStore.permissions.includes(permission)
const canUseDeptTree = computed(() => hasPermission('tenant:employee:dept:list'))
const canAddEmployee = computed(() => hasPermission('tenant:employee:user:add') && canUseDeptTree.value)
const canEditEmployee = computed(() => [
  'tenant:employee:user:edit',
  'tenant:employee:user:query'
].every(hasPermission) && canUseDeptTree.value)

function isSelf(row) {
  return String(row.userCode) === String(userStore.id)
}

const isEditing = computed(() => form.value.userCode !== undefined)
const isEditingSelf = computed(() => isEditing.value && String(form.value.userCode) === String(userStore.id))

const employeeList = ref([])
const deptOptions = ref([])
const deptFormOptions = ref([])
const deptNameMap = ref(new Map())
const roleOptions = ref([])
const loading = ref(false)
const open = ref(false)
const showSearch = ref(true)
const total = ref(0)
const title = ref('')
const smsCountdown = ref(0)
const smsSending = ref(false)
let smsTimer

const columns = ref({
  nickName: { label: '员工姓名', visible: true },
  jobNo: { label: '工号', visible: true },
  deptName: { label: '部门', visible: true },
  roleNames: { label: '角色', visible: true },
  phonenumber: { label: '手机号码', visible: true },
  joinTime: { label: '加入时间', visible: true }
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 20,
    nickName: undefined,
    jobNo: undefined,
    phonenumber: undefined,
    deptId: undefined
  },
  rules: {
    nickName: [{ required: true, message: '员工姓名不能为空', trigger: 'blur' }],
    phonenumber: [
      { required: true, message: '手机号码不能为空', trigger: 'blur' },
      mobileRule('请输入正确的11位手机号')
    ],
    smsCode: [
      {
        required: true,
        validator: (_rule, value, callback) => {
          if (form.value.userCode !== undefined) return callback()
          if (!value) return callback(new Error('短信验证码不能为空'))
          if (!/^\d{6}$/.test(value)) return callback(new Error('验证码必须是6位数字'))
          callback()
        },
        trigger: 'blur'
      }
    ],
    deptId: [
      {
        validator: (_rule, value, callback) => {
          if (form.value.userCode !== undefined && String(form.value.userCode) === String(userStore.id)) return callback()
          if (!value) return callback(new Error('所属部门不能为空'))
          callback()
        },
        trigger: 'change'
      }
    ]
  }
})

const { form, queryParams, rules } = toRefs(data)

function responseArray(response) {
  if (Array.isArray(response.data)) return response.data
  if (Array.isArray(response.rows)) return response.rows
  return []
}

function ensureDeptTree(list) {
  if (!Array.isArray(list)) return []
  if (list.some(item => Array.isArray(item.children) && item.children.length)) return list
  return proxy.handleTree(list, 'deptId')
}

function buildDeptTreeOptions(list) {
  return list.map(item => ({
    id: item.deptId,
    label: item.deptName,
    disabled: String(item.status) === '1',
    children: buildDeptTreeOptions(item.children || [])
  }))
}

function indexDeptNames(list, target = new Map()) {
  list.forEach(item => {
    target.set(String(item.deptId), item.deptName)
    if (item.children?.length) indexDeptNames(item.children, target)
  })
  return target
}

function loadDeptTree() {
  if (!canUseDeptTree.value) return Promise.resolve()
  return getDeptTree().then(response => {
    const tree = ensureDeptTree(responseArray(response))
    deptFormOptions.value = tree
    deptOptions.value = buildDeptTreeOptions(tree)
    deptNameMap.value = indexDeptNames(tree)
  })
}

function loadRoleOptions() {
  return listRole({ pageNum: 1, pageSize: 1000 }).then(response => {
    roleOptions.value = responseArray(response)
  })
}

function getList() {
  loading.value = true
  listEmployee(queryParams.value).then(response => {
    employeeList.value = response.rows || response.data || []
    total.value = Number(response.total ?? employeeList.value.length)
  }).finally(() => {
    loading.value = false
  })
}

function handleDeptClick(dept) {
  queryParams.value.deptId = dept.id
  handleQuery()
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.value.deptId = undefined
  proxy.$refs.deptTreeRef?.setCurrentKey(null)
  handleQuery()
}

function reset() {
  clearSmsTimer()
  form.value = {
    userCode: undefined,
    nickName: undefined,
    phonenumber: undefined,
    smsCode: undefined,
    jobNo: undefined,
    deptId: undefined,
    roleCodes: []
  }
  proxy.resetForm('employeeRef')
}

function clearSmsTimer() {
  if (smsTimer) window.clearInterval(smsTimer)
  smsTimer = undefined
  smsCountdown.value = 0
  smsSending.value = false
}

function handleEmployeePhoneInput() {
  if (form.value.userCode === undefined) {
    form.value.smsCode = undefined
    clearSmsTimer()
  }
}

function handleSendSmsCode() {
  proxy.$refs.employeeRef.validateField('phonenumber', valid => {
    if (valid !== true) return
    smsSending.value = true
    sendEmployeeSmsCode(form.value.phonenumber.trim()).then(response => {
      proxy.$modal.msgSuccess(response.msg || '验证码已发送')
      smsCountdown.value = Number(response.retryAfterSeconds || 60)
      if (smsTimer) window.clearInterval(smsTimer)
      smsTimer = window.setInterval(() => {
        smsCountdown.value -= 1
        if (smsCountdown.value <= 0) clearSmsTimer()
      }, 1000)
    }).finally(() => {
      smsSending.value = false
    })
  })
}

function handleAdd() {
  reset()
  form.value.deptId = queryParams.value.deptId
  Promise.all([loadDeptTree(), loadRoleOptions()]).then(() => {
    open.value = true
    title.value = '新增员工'
  })
}

function handleUpdate(row) {
  reset()
  const self = isSelf(row)
  const promises = [getEmployee(row.userCode)]
  if (!self) {
    promises.push(loadDeptTree(), loadRoleOptions())
  }
  Promise.all(promises).then(([response]) => {
    const detail = response.data || {}
    form.value = {
      userCode: detail.userCode,
      nickName: detail.nickName,
      phonenumber: detail.phonenumber,
      jobNo: detail.jobNo,
      deptId: detail.tenantDeptId ?? detail.deptId,
      roleCodes: response.roleCodes || detail.roleCodes || []
    }
    open.value = true
    title.value = self ? '修改个人信息' : '修改员工'
  })
}

function submitForm() {
  proxy.$refs.employeeRef.validate(valid => {
    if (!valid) return
    const payload = {
      userCode: form.value.userCode,
      nickName: form.value.nickName,
      phonenumber: form.value.phonenumber,
      smsCode: isEditing.value ? undefined : form.value.smsCode,
      deptId: form.value.deptId,
      jobNo: form.value.jobNo,
      roleCodes: form.value.roleCodes
    }
    const request = !isEditing.value ? addEmployee : isEditingSelf.value ? updateMyProfile : updateEmployee
    request(payload).then(() => {
      if (!isEditing.value) {
        proxy.$modal.alertSuccess('员工新增成功')
      } else {
        proxy.$modal.msgSuccess('修改成功')
      }
      open.value = false
      getList()
    })
  })
}

function cancel() {
  open.value = false
}

function handleDelete(row) {
  proxy.$modal.confirm(`是否确认从数据库删除员工“${row.nickName || '该员工'}”？若该员工仍属于其他商家，将仅删除当前商家的员工关系。`).then(() => delEmployee(row.userCode)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

function isShopAdministrator(row) {
  return Boolean(row.administrator)
}

function getEmployeeDeptName(row) {
  return row.dept?.deptName || row.deptName || deptNameMap.value.get(String(row.tenantDeptId ?? row.deptId ?? ''))
}

function formatRoleNames(value) {
  if (Array.isArray(value)) return value.join('、')
  return value || ''
}

function maskPhoneNumber(value) {
  const phone = String(value || '')
  if (!phone) return '-'
  if (/^1\d{10}$/.test(phone)) return `${phone.slice(0, 3)}****${phone.slice(-4)}`
  return '****'
}

getList()
loadDeptTree().catch(() => {})
loadRoleOptions().catch(() => {})
onBeforeUnmount(clearSmsTimer)
</script>

<style scoped lang="scss">
.role-option-tip {
  float: right;
  margin-left: 24px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
