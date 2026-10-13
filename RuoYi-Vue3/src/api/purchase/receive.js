import request from '@/utils/request'

// 采购发货单列表（按月表查）
export function listReceive(query) {
  return request({ url: '/purchase/receive/list', method: 'get', params: query })
}

// 发货单详情
export function getReceive(receiveCode, tableSuffix) {
  return request({ url: '/purchase/receive/' + receiveCode, method: 'get', params: { tableSuffix } })
}

// 登记发货
export function addReceive(data) {
  return request({ url: '/purchase/receive', method: 'post', data })
}

// 修改发货单（仅全部明细行未入库时可改）
export function updateReceive(data, tableSuffix) {
  return request({ url: '/purchase/receive', method: 'put', params: { tableSuffix }, data })
}

// 取消发货单（仅全部明细行未入库时可取消）
export function cancelReceive(receiveCode, tableSuffix) {
  return request({ url: '/purchase/receive/' + receiveCode + '/cancel', method: 'post', params: { tableSuffix } })
}

// 可供入库的发货单，跨全部月表查询。供库存模块的采购入库页选单。
export function listPendingShipments() {
  return request({ url: '/purchase/receive/pending', method: 'get' })
}

// 某采购订单的短装汇总，跨全部月表统计
export function listShortage(purchaseCode) {
  return request({ url: '/purchase/receive/shortage', method: 'get', params: { purchaseCode } })
}
