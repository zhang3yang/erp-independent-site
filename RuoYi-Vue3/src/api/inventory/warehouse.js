import request from '@/utils/request'

// 查询仓库列表
export function listWarehouse(query) {
  return request({
    url: '/inventory/warehouses/list',
    method: 'get',
    params: query
  })
}

// 查询仓库详细
export function getWarehouse(wareCode) {
  return request({
    url: '/inventory/warehouses/' + wareCode,
    method: 'get'
  })
}

// 新增仓库
export function addWarehouse(data) {
  return request({
    url: '/inventory/warehouses',
    method: 'post',
    data: data
  })
}

// 修改仓库
export function updateWarehouse(data) {
  return request({
    url: '/inventory/warehouses',
    method: 'put',
    data: data
  })
}

// 删除仓库
export function delWarehouse(wareCodes) {
  return request({
    url: '/inventory/warehouses/' + wareCodes,
    method: 'delete'
  })
}
