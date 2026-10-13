<template>
  <div class="app-container tenant-role-page">
    <aside class="role-panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">角色列表</div>
          <div class="panel-subtitle">管理本商家的员工角色</div>
        </div>
        <el-button type="primary" circle icon="Plus" @click="handleAddRole" v-hasPermi="['tenant:employee:role:add']" />
      </div>

      <el-input v-model="roleKeyword" placeholder="搜索角色名称" clearable class="role-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <div v-loading="roleLoading" class="role-list">
        <el-empty v-if="!filteredRoles.length && !roleLoading" description="暂无角色" :image-size="72" />
        <button
          v-for="role in filteredRoles"
          :key="role.roleCode"
          type="button"
          class="role-item"
          :class="{ active: selectedRoleCode === role.roleCode }"
          @click="selectRole(role)"
        >
          <span class="role-icon" :class="{ builtin: isBuiltIn(role) }">
            <el-icon><UserFilled /></el-icon>
          </span>
          <span class="role-main">
            <span class="role-name-row">
              <span class="role-name">{{ role.roleName }}</span>
              <el-tag v-if="isBuiltIn(role)" size="small" type="warning">内置</el-tag>
            </span>
            <span class="role-description">{{ role.remark || role.roleKey || '暂无备注' }}</span>
          </span>
          <el-tag size="small" :type="String(role.status) === '0' ? 'success' : 'info'">
            {{ String(role.status) === '0' ? '启用' : '停用' }}
          </el-tag>
        </button>
      </div>
    </aside>

    <section class="permission-panel">
      <template v-if="selectedRole">
        <div class="permission-header">
          <div>
            <div class="permission-title-row">
              <h3>{{ selectedRole.roleName }}</h3>
              <el-tag v-if="isBuiltIn(selectedRole)" type="warning">内置管理员角色</el-tag>
            </div>
            <p>
              {{ builtInDescription }}
            </p>
          </div>

          <div class="permission-actions">
            <template v-if="permissionEditing">
              <el-button type="primary" icon="Check" :loading="savingPermissions" @click="savePermissions">保存</el-button>
              <el-button icon="Close" :disabled="savingPermissions" @click="cancelPermissionEdit">取消</el-button>
            </template>
            <template v-else>
              <el-button
                v-if="isBuiltIn(selectedRole) || canEditPermissions"
                type="primary"
                plain
                icon="Edit"
                :disabled="isBuiltIn(selectedRole) && !isSuperAdmin"
                @click="startPermissionEdit"
              >编辑权限</el-button>
              <el-button
                v-if="isBuiltIn(selectedRole) || canEditRole"
                icon="EditPen"
                :disabled="isBuiltIn(selectedRole)"
                @click="handleEditRole"
              >编辑角色</el-button>
              <el-button
                v-if="isBuiltIn(selectedRole) || canDeleteRole"
                type="danger"
                plain
                icon="Delete"
                :disabled="isBuiltIn(selectedRole)"
                @click="handleDeleteRole"
              >删除角色</el-button>
              <el-button icon="Check" disabled>保存</el-button>
              <el-button icon="Close" disabled>取消</el-button>
            </template>
          </div>
        </div>

        <el-alert
          v-if="permissionEditing"
          title="只能分配当前商家管理员自身拥有的权限，保存时服务端会再次校验。"
          type="info"
          :closable="false"
          show-icon
          class="permission-alert"
        />

        <div class="tree-toolbar">
          <div class="tree-legend">
            <span><i class="legend-dot module"></i>模块</span>
            <span><i class="legend-dot menu"></i>菜单</span>
            <span><i class="legend-dot button"></i>按钮</span>
          </div>
          <div v-if="permissionEditing" class="tree-tools">
            <el-checkbox v-model="permissionExpand" @change="togglePermissionExpand">展开全部</el-checkbox>
            <el-button link type="primary" @click="checkAllPermissions">全选</el-button>
            <el-button link type="primary" @click="clearPermissions">清空</el-button>
          </div>
          <el-tag v-else type="info">只读模式</el-tag>
        </div>

        <div v-loading="treeLoading" class="permission-tree-wrap">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            node-key="id"
            show-checkbox
            default-expand-all
            :check-strictly="false"
            :expand-on-click-node="false"
            :props="treeProps"
            class="permission-tree"
          >
            <template #default="{ data }">
              <div class="permission-node">
                <span class="permission-node-icon" :class="permissionTypeClass(data)">
                  <el-icon>
                    <Menu v-if="permissionType(data) === 'M'" />
                    <Document v-else-if="permissionType(data) === 'C'" />
                    <Pointer v-else />
                  </el-icon>
                </span>
                <span class="permission-node-label">{{ data.label }}</span>
                <el-tag size="small" :type="permissionTagType(data)" effect="plain">{{ permissionTypeText(data) }}</el-tag>
              </div>
            </template>
          </el-tree>
        </div>
      </template>

      <el-empty v-else description="请选择一个角色查看权限" />
    </section>

    <el-dialog :title="roleDialogTitle" v-model="roleDialogOpen" width="520px" append-to-body @closed="resetRoleForm" :close-on-click-modal="false">
      <el-form ref="roleRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="权限字符" prop="roleKey">
          <el-input v-model="roleForm.roleKey" placeholder="请输入权限字符" maxlength="50" />
        </el-form-item>
        <el-form-item label="显示排序" prop="roleSort">
          <el-input-number v-model="roleForm.roleSort" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="角色状态" prop="status">
          <el-radio-group v-model="roleForm.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="roleForm.remark" type="textarea" :rows="3" placeholder="请输入备注" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitRole">确定</el-button>
          <el-button @click="roleDialogOpen = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TenantEmployeePermission">
import {
  addRole,
  delRole,
  getBuiltInRolePermissionTree,
  getRole,
  getRolePermissionTree,
  listRole,
  updateRole,
  updateBuiltInRolePermissions,
  updateRolePermissions
} from '@/api/tenant/employeeManagement'
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()

const hasPermission = permission => userStore.permissions.includes('*:*:*') || userStore.permissions.includes(permission)
const isSuperAdmin = computed(() => Number(userStore.id) === 1)
const canEditPermissions = computed(() => isSuperAdmin.value || hasPermission('tenant:employee:role:editPermission'))
const canEditRole = computed(() => [
  'tenant:employee:role:edit',
  'tenant:employee:role:query'
].every(hasPermission))
const canDeleteRole = computed(() => hasPermission('tenant:employee:role:remove'))

const roles = ref([])
const roleKeyword = ref('')
const roleLoading = ref(false)
const selectedRoleCode = ref(undefined)
const permissionTree = ref([])
const checkedKeys = ref([])
const originalCheckedKeys = ref([])
const treeLoading = ref(false)
const permissionEditing = ref(false)
const permissionExpand = ref(true)
const savingPermissions = ref(false)
const permissionTreeRef = ref(null)

const roleDialogOpen = ref(false)
const roleDialogTitle = ref('')
const roleForm = ref({})
const roleRules = {
  roleName: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  roleKey: [{ required: true, message: '权限字符不能为空', trigger: 'blur' }],
  roleSort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
}

const filteredRoles = computed(() => {
  const keyword = roleKeyword.value.trim().toLowerCase()
  if (!keyword) return roles.value
  return roles.value.filter(role => [role.roleName, role.roleKey, role.remark]
    .filter(Boolean)
    .some(value => String(value).toLowerCase().includes(keyword)))
})

const selectedRole = computed(() => roles.value.find(role => String(role.roleCode) === String(selectedRoleCode.value)))
const builtInDescription = computed(() => {
  if (!isBuiltIn(selectedRole.value)) {
    return selectedRole.value?.remark || '查看并维护该角色可访问的模块、菜单和按钮。'
  }
  return isSuperAdmin.value
    ? '这是内置商家管理员角色。超级管理员可以分配权限，但不能修改或删除角色。'
    : '该角色由平台分配，只能查看权限，不能修改或删除。'
})

const treeProps = computed(() => ({
  children: 'children',
  label: 'label',
  disabled: () => !permissionEditing.value
}))

function responseArray(response) {
  if (Array.isArray(response.rows)) return response.rows
  if (Array.isArray(response.data)) return response.data
  return []
}

function isBuiltIn(role) {
  return Number(role?.systemFlag) === 1 || String(role?.roleType || '').toUpperCase() === 'BUILT_IN'
}

function getRoles(preferredRoleCode) {
  roleLoading.value = true
  return listRole({ pageNum: 1, pageSize: 1000 }).then(response => {
    roles.value = responseArray(response)
    const nextRole = roles.value.find(role => String(role.roleCode) === String(preferredRoleCode ?? selectedRoleCode.value)) || roles.value[0]
    if (nextRole) {
      return selectRole(nextRole, true)
    }
    selectedRoleCode.value = undefined
    permissionTree.value = []
  }).finally(() => {
    roleLoading.value = false
  })
}

function normalizePermissionNode(node) {
  return {
    ...node,
    id: node.id ?? node.funCode,
    label: node.label ?? node.menuName ?? node.funText ?? node.funName,
    type: node.type ?? node.menuType ?? node.funType,
    children: (node.children || []).map(normalizePermissionNode)
  }
}

function selectRole(role, force = false) {
  if (!force && permissionEditing.value && String(selectedRoleCode.value) !== String(role.roleCode)) {
    proxy.$modal.msgWarning('请先保存或取消当前权限修改')
    return Promise.resolve()
  }
  selectedRoleCode.value = role.roleCode
  permissionEditing.value = false
  return loadPermissionTree(role.roleCode)
}

function loadPermissionTree(roleCode) {
  treeLoading.value = true
  const request = isBuiltIn(selectedRole.value)
    ? getBuiltInRolePermissionTree(roleCode)
    : getRolePermissionTree(roleCode)
  return request.then(response => {
    permissionTree.value = (response.menus || response.data || []).map(normalizePermissionNode)
    checkedKeys.value = [...(response.checkedKeys || [])]
    originalCheckedKeys.value = [...checkedKeys.value]
    nextTick(() => {
      restoreCheckedKeys(checkedKeys.value)
    })
  }).finally(() => {
    treeLoading.value = false
  })
}

function startPermissionEdit() {
  if (!selectedRole.value || (isBuiltIn(selectedRole.value) && !isSuperAdmin.value)) return
  originalCheckedKeys.value = getAllCheckedKeys()
  permissionEditing.value = true
}

function cancelPermissionEdit() {
  permissionEditing.value = false
  nextTick(() => {
    restoreCheckedKeys(originalCheckedKeys.value)
  })
}

function restoreCheckedKeys(keys) {
  const tree = permissionTreeRef.value
  if (!tree) return
  tree.setCheckedKeys([])
  keys.forEach(key => tree.setChecked(key, true, false))
}

function getAllCheckedKeys() {
  const tree = permissionTreeRef.value
  if (!tree) return []
  return [...new Set([...tree.getCheckedKeys(), ...tree.getHalfCheckedKeys()])]
}

function savePermissions() {
  if (!selectedRole.value || (isBuiltIn(selectedRole.value) && !isSuperAdmin.value)) return
  const funCodes = getAllCheckedKeys()
  savingPermissions.value = true
  const request = isBuiltIn(selectedRole.value)
    ? updateBuiltInRolePermissions(selectedRole.value.roleCode, funCodes)
    : updateRolePermissions(selectedRole.value.roleCode, funCodes)
  request.then(() => {
    proxy.$modal.msgSuccess('权限保存成功')
    originalCheckedKeys.value = [...funCodes]
    permissionEditing.value = false
  }).finally(() => {
    savingPermissions.value = false
  })
}

function togglePermissionExpand(expanded) {
  const nodesMap = permissionTreeRef.value?.store?.nodesMap || {}
  Object.values(nodesMap).forEach(node => {
    node.expanded = expanded
  })
}

function checkAllPermissions() {
  permissionTreeRef.value?.setCheckedNodes(permissionTree.value)
}

function clearPermissions() {
  permissionTreeRef.value?.setCheckedKeys([])
}

function resetRoleForm() {
  roleForm.value = {
    roleCode: undefined,
    roleName: undefined,
    roleKey: undefined,
    roleSort: 0,
    status: '0',
    remark: undefined,
    systemFlag: 0
  }
  proxy.resetForm('roleRef')
}

function handleAddRole() {
  if (permissionEditing.value) {
    proxy.$modal.msgWarning('请先保存或取消当前权限修改')
    return
  }
  resetRoleForm()
  roleDialogTitle.value = '新增角色'
  roleDialogOpen.value = true
}

function handleEditRole() {
  if (!selectedRole.value || isBuiltIn(selectedRole.value)) return
  getRole(selectedRole.value.roleCode).then(response => {
    roleForm.value = { ...response.data, status: String(response.data.status ?? '0') }
    roleDialogTitle.value = '编辑角色'
    roleDialogOpen.value = true
  })
}

function submitRole() {
  proxy.$refs.roleRef.validate(valid => {
    if (!valid) return
    if (isBuiltIn(roleForm.value)) {
      proxy.$modal.msgError('内置管理员角色不能修改')
      return
    }
    const isAdd = roleForm.value.roleCode === undefined
    const request = isAdd ? addRole : updateRole
    request(roleForm.value).then(response => {
      proxy.$modal.msgSuccess(isAdd ? '新增成功' : '修改成功')
      roleDialogOpen.value = false
      const savedRoleCode = response.data?.roleCode ?? roleForm.value.roleCode
      getRoles(savedRoleCode)
    })
  })
}

function handleDeleteRole() {
  if (!selectedRole.value || isBuiltIn(selectedRole.value)) return
  const role = selectedRole.value
  proxy.$modal.confirm(`是否确认删除角色“${role.roleName}”？`).then(() => delRole(role.roleCode)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    selectedRoleCode.value = undefined
    getRoles()
  }).catch(() => {})
}

function permissionType(data) {
  const value = data.type
  if (value === 1 || value === '1') return 'M'
  if (value === 2 || value === '2') return 'C'
  if (value === 3 || value === '3') return 'F'
  return String(value || 'C').toUpperCase()
}

function permissionTypeText(data) {
  return { M: '模块', C: '菜单', F: '按钮' }[permissionType(data)] || '菜单'
}

function permissionTypeClass(data) {
  return { M: 'module', C: 'menu', F: 'button' }[permissionType(data)] || 'menu'
}

function permissionTagType(data) {
  return { M: 'primary', C: 'success', F: 'warning' }[permissionType(data)] || 'success'
}

getRoles()
</script>

<style scoped lang="scss">
.tenant-role-page {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 16px;
  min-height: calc(100vh - 130px);
  background: var(--el-bg-color-page);
}

.role-panel,
.permission-panel {
  min-width: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.role-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header,
.permission-header,
.permission-title-row,
.role-name-row,
.tree-toolbar,
.tree-legend,
.tree-tools,
.permission-node {
  display: flex;
  align-items: center;
}

.panel-header {
  justify-content: space-between;
  padding: 18px 18px 12px;
}

.panel-title {
  color: var(--el-text-color-primary);
  font-size: 17px;
  font-weight: 600;
}

.panel-subtitle {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.role-search {
  width: calc(100% - 32px);
  margin: 0 16px 12px;
}

.role-list {
  flex: 1;
  min-height: 180px;
  overflow-y: auto;
  padding: 0 10px 14px;
}

.role-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  margin: 4px 0;
  padding: 12px 10px;
  cursor: pointer;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: inherit;
  transition: 0.2s ease;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &.active {
    border-color: var(--el-color-primary);
    background: color-mix(in srgb, var(--el-color-primary) 12%, var(--el-bg-color));
    box-shadow: inset 3px 0 0 var(--el-color-primary);
  }

  &.active:hover {
    background: color-mix(in srgb, var(--el-color-primary) 16%, var(--el-bg-color));
  }

  &.active .role-name {
    color: var(--el-text-color-primary);
  }

  &.active .role-description {
    color: var(--el-text-color-regular);
  }
}

.role-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  place-items: center;
  border-radius: 9px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);

  &.builtin {
    background: var(--el-color-warning-light-8);
    color: var(--el-color-warning);
  }
}

.role-main {
  min-width: 0;
  flex: 1;
}

.role-name-row {
  gap: 6px;
}

.role-name,
.role-description {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-name {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.role-description {
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.permission-panel {
  padding: 20px;
}

.permission-header {
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  h3 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 20px;
  }

  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.permission-title-row,
.permission-actions {
  gap: 10px;
}

.permission-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.permission-alert {
  margin-top: 16px;
}

.tree-toolbar {
  justify-content: space-between;
  min-height: 52px;
}

.tree-legend,
.tree-tools {
  gap: 16px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;

  &.module { background: var(--el-color-primary); }
  &.menu { background: var(--el-color-success); }
  &.button { background: var(--el-color-warning); }
}

.permission-tree-wrap {
  min-height: 360px;
  max-height: calc(100vh - 300px);
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.permission-tree {
  background: transparent;
}

.permission-node {
  min-width: 0;
  flex: 1;
  gap: 8px;
}

.permission-node-icon {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 5px;

  &.module { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
  &.menu { color: var(--el-color-success); background: var(--el-color-success-light-9); }
  &.button { color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
}

.permission-node-label {
  min-width: 80px;
  color: var(--el-text-color-primary);
}

@media (max-width: 900px) {
  .tenant-role-page {
    grid-template-columns: 1fr;
  }

  .role-panel {
    max-height: 420px;
  }

  .permission-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .permission-actions {
    justify-content: flex-start;
  }
}
</style>
