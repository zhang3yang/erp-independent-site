import request from '@/utils/request'

const baseUrl = '/tenant/employee-management'

// 部门管理
export function getDeptTree(query) {
  return request({
    url: baseUrl + '/dept/tree',
    method: 'get',
    params: query
  })
}

export function getDept(deptId) {
  return request({
    url: baseUrl + '/dept/' + deptId,
    method: 'get'
  })
}

export function addDept(data) {
  return request({
    url: baseUrl + '/dept',
    method: 'post',
    data
  })
}

export function updateDept(data) {
  return request({
    url: baseUrl + '/dept',
    method: 'put',
    data
  })
}

export function delDept(deptId) {
  return request({
    url: baseUrl + '/dept/' + deptId,
    method: 'delete'
  })
}

// 员工管理
export function listEmployee(query) {
  return request({
    url: baseUrl + '/employee/list',
    method: 'get',
    params: query
  })
}

export function getEmployee(userCode) {
  return request({
    url: baseUrl + '/employee/' + userCode,
    method: 'get'
  })
}

export function addEmployee(data) {
  return request({
    url: baseUrl + '/employee',
    method: 'post',
    data
  })
}

export function sendEmployeeSmsCode(phonenumber, platcode) {
  return request({
    url: baseUrl + '/employee/sms/code',
    headers: { repeatSubmit: false },
    method: 'post',
    data: { phonenumber, platcode }
  })
}

export function updateEmployee(data) {
  return request({
    url: baseUrl + '/employee',
    method: 'put',
    data
  })
}

export function updateMyProfile(data) {
  return request({
    url: baseUrl + '/employee/me',
    method: 'put',
    data
  })
}

export function delEmployee(userCode) {
  return request({
    url: baseUrl + '/employee/' + userCode,
    method: 'delete'
  })
}

// 商家角色与权限
export function listRole(query) {
  return request({
    url: baseUrl + '/role/list',
    method: 'get',
    params: query
  })
}

export function getRole(roleCode) {
  return request({
    url: baseUrl + '/role/' + roleCode,
    method: 'get'
  })
}

export function addRole(data) {
  return request({
    url: baseUrl + '/role',
    method: 'post',
    data
  })
}

export function updateRole(data) {
  return request({
    url: baseUrl + '/role',
    method: 'put',
    data
  })
}

export function delRole(roleCode) {
  return request({
    url: baseUrl + '/role/' + roleCode,
    method: 'delete'
  })
}

export function getRolePermissionTree(roleCode) {
  const suffix = roleCode === undefined || roleCode === null || roleCode === '' ? '' : '/' + roleCode
  return request({
    url: baseUrl + '/role/permission-tree' + suffix,
    method: 'get'
  })
}

export function getBuiltInRolePermissionTree(roleCode) {
  return request({
    url: baseUrl + '/role/' + roleCode + '/built-in-permission-tree',
    method: 'get'
  })
}

export function updateRolePermissions(roleCode, funCodes) {
  return request({
    url: baseUrl + '/role/' + roleCode + '/permissions',
    method: 'put',
    data: { funCodes }
  })
}

export function updateBuiltInRolePermissions(roleCode, funCodes) {
  return request({
    url: baseUrl + '/role/' + roleCode + '/built-in-permissions',
    method: 'put',
    data: { funCodes }
  })
}
