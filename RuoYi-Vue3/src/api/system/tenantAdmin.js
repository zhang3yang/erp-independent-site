import request from '@/utils/request'

export function listTenantAdmins() {
  return request({
    url: '/system/tenant-admin/list',
    method: 'get'
  })
}

export function listPlatformDepartments(params) {
  return request({
    url: '/system/tenant-admin/departments',
    method: 'get',
    params
  })
}

export function listPlatformEmployees(params) {
  return request({
    url: '/system/tenant-admin/employees',
    method: 'get',
    params
  })
}

export function getTenantShop(shopCode) {
  return request({
    url: `/system/tenant-admin/shop/${shopCode}`,
    method: 'get'
  })
}

export function createTenantShop(data) {
  return request({
    url: '/system/tenant-admin/shop',
    method: 'post',
    data
  })
}

export function updateTenantShop(shopCode, data) {
  return request({
    url: `/system/tenant-admin/shop/${shopCode}`,
    method: 'put',
    data
  })
}

export function deleteTenantShop(shopCode) {
  return request({
    url: `/system/tenant-admin/shop/${shopCode}`,
    method: 'delete'
  })
}

export function getTenantAdminPermissionTree(roleCode) {
  return request({
    url: '/system/tenant-admin/permission-tree/' + roleCode,
    method: 'get'
  })
}

export function updateTenantAdminPermissions(roleCode, funCodes) {
  return request({
    url: `/system/tenant-admin/${roleCode}/permissions`,
    method: 'put',
    data: { funCodes }
  })
}

export function saveTenantAdminAccount(roleCode, data) {
  return request({
    url: `/system/tenant-admin/${roleCode}/account`,
    method: 'put',
    data
  })
}

export function sendTenantAdminAccountSmsCode(roleCode, data) {
  return request({
    url: `/system/tenant-admin/${roleCode}/account/sms/code`,
    method: 'post',
    data
  })
}
