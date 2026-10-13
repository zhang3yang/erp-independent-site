import request from '@/utils/request'

// 查询资金流水列表（跨月合并，服务端按说明书流程4 放宽 createtime 范围）
export function listTransaction(query) {
  return request({
    url: '/finance/transaction/list',
    method: 'get',
    params: query
  })
}

// 查询流水详情。tableSuffix 决定读哪张月表，必须带上
export function getTransaction(transactionCode, tableSuffix) {
  return request({
    url: '/finance/transaction/' + transactionCode,
    method: 'get',
    params: { tableSuffix }
  })
}

// 接口1：创建资金流水
export function addTransaction(data) {
  return request({
    url: '/finance/transaction',
    method: 'post',
    data
  })
}

// 修改草稿或已驳回的流水
export function updateTransaction(data) {
  return request({
    url: '/finance/transaction',
    method: 'put',
    data
  })
}

// 接口2：提交审核（回款流水叫「提交认领」）
export function submitTransaction(transactionCode, tableSuffix) {
  return request({
    url: '/finance/transaction/' + transactionCode + '/submit',
    method: 'post',
    params: { tableSuffix }
  })
}

// 接口3：审核（通过 / 驳回）
export function auditTransaction(transactionCode, data) {
  return request({
    url: '/finance/transaction/' + transactionCode + '/audit',
    method: 'post',
    data
  })
}

// 接口4：财务执行打款
export function executeTransaction(transactionCode, data) {
  return request({
    url: '/finance/transaction/' + transactionCode + '/execute',
    method: 'post',
    data
  })
}

// 接口5：业务员认领并绑定订单。
// data.allowOverpay 为 true 才允许认领金额超过订单待收——默认拦住，
// 因为超额认领终审后订单会被「已收满」规则永久剔出认领下拉
export function claimTransaction(transactionCode, data) {
  return request({
    url: '/finance/transaction/' + transactionCode + '/claim',
    method: 'post',
    data
  })
}

// 撤回认领：已审核（已认领、还没终审）退回待审核，清掉订单号。
// 认领人本人或财务审核人才能撤，后端判
export function unclaimTransaction(transactionCode, tableSuffix) {
  return request({
    url: '/finance/transaction/' + transactionCode + '/unclaim',
    method: 'post',
    params: { tableSuffix }
  })
}

// 待认领的客户回款：销售 + 收入 + 待审核，只列与订单同币种的。
// 供销售订单详情的「认领打款」弹窗使用，权限与认领同一个
export function listUnclaimedReceipts(currency) {
  return request({
    url: '/finance/transaction/unclaimed',
    method: 'get',
    params: { currency }
  })
}

// 接口6：完成流水（非业务类）
export function completeTransaction(transactionCode, tableSuffix) {
  return request({
    url: '/finance/transaction/' + transactionCode + '/complete',
    method: 'post',
    params: { tableSuffix }
  })
}

// 删除草稿或已驳回的流水
export function delTransaction(transactionCode, tableSuffix) {
  return request({
    url: '/finance/transaction/' + transactionCode,
    method: 'delete',
    params: { tableSuffix }
  })
}

// 说明书 6.4：按业务单据号查该单的全部流水（含草稿、待审核等在途的）。
// 供订单详情的「收款流水」与采购单详情的「付款流水」使用，只要登录到店铺即可调，
// 不需要 finance:transaction:list 权限。
export function listTransactionByBiz(bizType, bizCode) {
  return request({
    url: '/finance/transaction/biz',
    method: 'get',
    params: { bizType, bizCode }
  })
}

// 说明书 6.4：按业务单据号查已完成的收付款合计
export function transactionProgress(bizType, bizCode) {
  return request({
    url: '/finance/transaction/progress',
    method: 'get',
    params: { bizType, bizCode }
  })
}
