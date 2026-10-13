import request from '@/utils/request'

// 查询序列号列表
export function listSerial(query) {
  return request({
    url: '/inventory/serial/list',
    method: 'get',
    params: query
  })
}

// 查询在库序列号列表
export function listInStockSerial(goodsCode, wareCode, state) {
  return request({
    url: '/inventory/serial/instock',
    method: 'get',
    params: { goodsCode, wareCode, ...state }
  })
}

// 查询序列号详细
export function getSerial(serialNo) {
  return request({
    url: '/inventory/serial/' + serialNo,
    method: 'get'
  })
}

// 新增序列号
export function addSerial(data) {
  return request({
    url: '/inventory/serial',
    method: 'post',
    data: data
  })
}

// 批量新增序列号
export function batchAddSerial(data) {
  return request({
    url: '/inventory/serial/batch',
    method: 'post',
    data: data
  })
}

// 修改序列号
export function updateSerial(data) {
  return request({
    url: '/inventory/serial',
    method: 'put',
    data: data
  })
}
