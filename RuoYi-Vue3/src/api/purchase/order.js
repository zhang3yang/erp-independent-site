import request from '@/utils/request'

// 查询采购订单列表
export function listOrder(query) {
  return request({
    url: '/purchase/order/list',
    method: 'get',
    params: query
  })
}

// 查询采购订单详细
export function getOrder(id) {
  return request({
    url: '/purchase/order/' + id,
    method: 'get'
  })
}

// 查询采购订单明细列表
export function getOrderItems(purchaseCode) {
  return request({
    url: '/purchase/order/items',
    method: 'get',
    params: { purchaseCode }
  })
}

// 兼容别名
export const listPurchaseOrder = listOrder
export const getPurchaseOrderItems = getOrderItems

export function listAvailableOrders() {
  return request({ url: '/purchase/order/available', method: 'get' })
}

// 还要付款的采购单（已付货款没够订单总额的），供财务建采购付款流水时选单。
// 不分页——这是下拉的数据源，被截断就等于有些单永远选不到。
// includeCode 是「这一张无论如何都要带上」，改一笔已存草稿时传它绑着的单号，
// 免得那张单在草稿存下之后被付满、编辑框一打开单号就空了。
export function listPayableOrders(includeCode) {
  return request({ url: '/purchase/order/payable', method: 'get', params: { includeCode } })
}

// 可退款的采购单（已经付过钱的），与 payable 是相反的集合，含已取消的单——
// 采购单取消后把预付款要回来，正是采购退款最常见的场景。
export function listPurchaseRefundableOrders(includeCode) {
  return request({ url: '/purchase/order/refundable', method: 'get', params: { includeCode } })
}

// 新增采购订单
export function addOrder(data) {
  return request({
    url: '/purchase/order',
    method: 'post',
    data: data
  })
}

// 修改采购订单
export function updateOrder(data) {
  return request({
    url: '/purchase/order',
    method: 'put',
    data: data
  })
}

// 删除采购订单
export function delOrder(ids) {
  return request({
    url: '/purchase/order/' + ids,
    method: 'delete'
  })
}

// 提交采购订单审核
export function submitOrder(purchaseCode) {
  return request({
    url: '/purchase/order/' + purchaseCode + '/submit',
    method: 'post'
  })
}

// 审核采购订单。驳回时必须带驳回原因，后端会校验。
export function approveOrder(purchaseCode, approved, rejectReason) {
  return request({
    url: '/purchase/order/' + purchaseCode + '/approve',
    method: 'post',
    data: { approved: approved, rejectReason: rejectReason }
  })
}

// 录入物流信息（供应商发货后由采购员填写）
export function updateLogistics(purchaseCode, data) {
  return request({
    url: '/purchase/order/' + purchaseCode + '/logistics',
    method: 'post',
    data: data
  })
}

// 取消采购订单
export function cancelOrder(purchaseCode) {
  return request({
    url: '/purchase/order/' + purchaseCode + '/cancel',
    method: 'post'
  })
}
