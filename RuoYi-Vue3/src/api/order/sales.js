import request from '@/utils/request'

// 订单按 createtime 分月表，列表查询必须带月份；按单号查可以不带，后端会跨月找。
// 列表返回的每一行都带着 tableSuffix，后续操作原样回传能省掉一次跨月扫表。

// 销售订单列表
export function listOrder(query) {
  return request({ url: '/order/sales/list', method: 'get', params: query })
}

// 订单详情。不传 tableSuffix 则跨月查找
export function getOrder(orderCode, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode, method: 'get', params: { tableSuffix } })
}

// 可发货订单，供发货批次页选单
export function listShippableOrders(tableSuffix) {
  return request({ url: '/order/sales/shippable', method: 'get', params: { tableSuffix } })
}

// 可回款订单（还没收满的），供财务认领回款流水时选单。
// tableSuffix 留空表示全部月份——回款常常跨月，只给当月会漏掉更早的欠款单
export function listReceivableOrders(tableSuffix) {
  return request({ url: '/order/sales/receivable', method: 'get', params: { tableSuffix } })
}

// 可退款订单（已经收过钱的），供财务建销售退款流水时选单。
// 与 receivable 正好是相反的集合，已取消的订单也在内——取消后退款是常态
export function listRefundableOrders(tableSuffix) {
  return request({ url: '/order/sales/refundable', method: 'get', params: { tableSuffix } })
}

// 可排产的订单明细，供生产按订单建工单时选单。列的是「还没发完」的行，不要求有缺口——
// 定制品的规格库存备货满足不了，哪怕现货已经占上也只能专门生产。
// tableSuffix 留空表示全部月份——上月下的单这月才排产是常态。
// goodsCode 传了就只看这个商品。权限走 production:order:add，调用方是生产建单弹窗
export function listProductionCandidates(params) {
  return request({ url: '/order/sales/production-candidates', method: 'get', params })
}

// 这张订单关联的生产工单，不分状态。按工单的产出分配反查，所以已关闭、已完工的也列得出来；
// 订单明细上的 sourcecode 是单值的、关闭工单时还会被清空，拿它追溯会丢历史。
export function listOrderProductions(orderCode) {
  return request({ url: '/order/sales/' + orderCode + '/productions', method: 'get' })
}

// 新建订单（草稿）
export function addOrder(data) {
  return request({ url: '/order/sales', method: 'post', data })
}

// 修改订单，仅草稿可改
export function updateOrder(data, tableSuffix) {
  return request({ url: '/order/sales', method: 'put', params: { tableSuffix }, data })
}

// 新建并直接提交审核：一步落待审核，不经过草稿。校验与新建相同，任一步不过整单不落库
export function addAndSubmitOrder(data) {
  return request({ url: '/order/sales/submit-new', method: 'post', data })
}

// 修改并重新提交：草稿/已驳回 → 待审核，一个事务
export function updateAndSubmitOrder(data, tableSuffix) {
  return request({ url: '/order/sales/submit', method: 'put', params: { tableSuffix }, data })
}

// 审核通过：待审核 → 已备货/备货中。不占用任何库存，库存到发货那一刻才由出库单锁定扣减
export function confirmOrder(orderCode, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode + '/confirm', method: 'post', data: { tableSuffix } })
}

// 提交审核：草稿/已驳回 → 待审核。提交不预占库存
export function submitOrder(orderCode, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode + '/submit', method: 'post', params: { tableSuffix } })
}

// 审核驳回：待审核 → 已驳回，原因必填
export function rejectOrder(orderCode, reason, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode + '/reject', method: 'post', data: { reason, tableSuffix } })
}

// 撤回：待审核 → 草稿。提交人自己收回还没审的单，没占过库存，不用释放任何东西
export function withdrawOrder(orderCode, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode + '/withdraw', method: 'post', params: { tableSuffix } })
}

// 撤审：已备货/备货中 → 草稿。要求未发过货、无在途批次、无在制工单。不占库存，没有要释放的东西
export function unconfirmOrder(orderCode, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode + '/unconfirm', method: 'post', params: { tableSuffix } })
}

// 强制关闭：发了一部分不再发了，原因必填，释放剩余预占
export function closeOrder(orderCode, reason, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode + '/close', method: 'post', data: { reason, tableSuffix } })
}

// 取消订单，释放全部预占
export function cancelOrder(orderCode, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode + '/cancel', method: 'post', params: { tableSuffix } })
}

// 删除草稿
export function delOrder(orderCode, tableSuffix) {
  return request({ url: '/order/sales/' + orderCode, method: 'delete', params: { tableSuffix } })
}

// 登记明细缺口的备货来源，供工单完工或采购到货时反查该发哪张订单
export function updateItemSource(orderCode, data) {
  return request({ url: '/order/sales/' + orderCode + '/source', method: 'post', data })
}

// 订单没有回款登记接口：已收金额只由财务模块的回款流水终审通过时回写。
// 要看这张订单收了多少钱、钱走到哪一步，用 finance/transaction 的 listTransactionByBiz。

