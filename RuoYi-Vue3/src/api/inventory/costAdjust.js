import request from '@/utils/request'

// 成本调整单列表（CBTZ 类型的库存单据，跨月表）
export function listCostAdjust(query) {
  return request({
    url: '/inventory/costadjust/list',
    method: 'get',
    params: query
  })
}

export function getCostAdjust(docCode, tableSuffix) {
  return request({
    url: '/inventory/costadjust/' + docCode,
    method: 'get',
    params: { tableSuffix }
  })
}

// 某商品（批次 / 序列号）现在的数量和成本：{ layer, qty, unitCost, totalCost }
export function previewCostAdjust(params) {
  return request({
    url: '/inventory/costadjust/preview',
    method: 'get',
    params
  })
}

// { wareCode, remark, items: [{ goodsCode, batchCode, serialNos, adjustAmount }] }
export function addCostAdjust(data) {
  return request({
    url: '/inventory/costadjust',
    method: 'post',
    data
  })
}

export function approveCostAdjust(docCode, tableSuffix) {
  return request({
    url: '/inventory/costadjust/' + docCode + '/approve',
    method: 'post',
    params: { tableSuffix }
  })
}

export function rejectCostAdjust(docCode, tableSuffix) {
  return request({
    url: '/inventory/costadjust/' + docCode + '/reject',
    method: 'post',
    params: { tableSuffix }
  })
}

export function delCostAdjust(docCode, tableSuffix) {
  return request({
    url: '/inventory/costadjust/' + docCode,
    method: 'delete',
    params: { tableSuffix }
  })
}
