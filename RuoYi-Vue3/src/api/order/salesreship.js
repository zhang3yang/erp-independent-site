import request from '@/utils/request'

export function listSalesReship(query) {
  return request({ url: '/order/salesreship/list', method: 'get', params: query })
}

export function getSalesReship(reshipCode) {
  return request({ url: '/order/salesreship/' + reshipCode, method: 'get' })
}

// 指定月份内已发过货、且还没有补发单的原订单
export function listEligibleOrders(tableSuffix) {
  return request({ url: '/order/salesreship/eligible', method: 'get', params: { tableSuffix } })
}

export function addSalesReship(data) {
  return request({ url: '/order/salesreship', method: 'post', data })
}

// 整张补发单一次性全部发出，不传行级数量
export function shipSalesReship(reshipCode, data) {
  return request({ url: '/order/salesreship/' + reshipCode + '/ship', method: 'post', data })
}

// 删除还没发货的补发单。补发单不能改，建错了靠删掉重建；
// 一张原订单只能有一张补发单，不删干净那张原订单就再也补发不了。
export function delSalesReship(reshipCode) {
  return request({ url: '/order/salesreship/' + reshipCode, method: 'delete' })
}
