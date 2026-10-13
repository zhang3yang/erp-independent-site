import request from '@/utils/request'

// 查询某销售订单的可退商品（已发 - 已退），按商品与发货仓库聚合
export function listReturnable(orderCode) {
  return request({ url: '/order/salesreturn/returnable', method: 'get', params: { orderCode } })
}

// 新建销售退货单。只建单，货还没回库，要再调 submitReturn 才入库。
export function addReturn(data) {
  return request({ url: '/order/salesreturn', method: 'post', data })
}

// 提交退货：审核该单，货入库并写库存流水
export function submitReturn(docCode, tableSuffix) {
  return request({ url: '/order/salesreturn/' + docCode + '/submit', method: 'post', params: { tableSuffix } })
}

// 删除还没提交的退货单，把占住的可退数量放回去
export function delReturn(docCode, tableSuffix) {
  return request({ url: '/order/salesreturn/' + docCode, method: 'delete', params: { tableSuffix } })
}

// 退货记录。退货落在库存 XSTHRK 入库单上，按月表查询。
export function listReturn(query) {
  return request({ url: '/order/salesreturn/list', method: 'get', params: query })
}

// 全部月份里还没提交的退货单。列表按月表查，跨月后上个月的待提交单会从视野里消失，
// 而它一直占着可退数量，所以单开一个不分月的查询做提醒。
export function listPendingReturns() {
  return request({ url: '/order/salesreturn/pending', method: 'get' })
}

// 退货单详情（含明细行）
export function getReturn(docCode, tableSuffix) {
  return request({ url: '/order/salesreturn/' + docCode, method: 'get', params: { tableSuffix } })
}
