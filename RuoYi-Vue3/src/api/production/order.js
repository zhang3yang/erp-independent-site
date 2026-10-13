import request from '@/utils/request'

export function listProductionOrder(query) {
  return request({ url: '/production/order/list', method: 'get', params: query })
}

export function getProductionOrder(productionCode) {
  return request({ url: '/production/order/' + productionCode, method: 'get' })
}

export function addProductionOrder(data) {
  return request({ url: '/production/order', method: 'post', data })
}

export function updateProductionOrder(data) {
  return request({ url: '/production/order', method: 'put', data })
}

// 删除工单：只有待生产的能删，已开工的走关闭
export function delProductionOrder(productionCode) {
  return request({ url: '/production/order/' + productionCode, method: 'delete' })
}

export function pickProductionOrder(productionCode, data) {
  return request({ url: '/production/order/' + productionCode + '/pick', method: 'post', data })
}

export function completeProductionOrder(productionCode, data) {
  return request({ url: '/production/order/' + productionCode + '/complete', method: 'post', data })
}

export function returnProductionMaterial(productionCode, data) {
  return request({ url: '/production/order/' + productionCode + '/return', method: 'post', data })
}

// 完工后补录/修改加工费、运费、其他费用，重算总成本与单位成本（不同步库存成本）
export function adjustProductionCost(productionCode, data) {
  return request({ url: '/production/order/' + productionCode + '/cost', method: 'post', data })
}

// 关闭工单。已完工是正常结案；未完工是终止「这批不做了」，必须给原因
export function closeProductionOrder(productionCode, reason) {
  return request({
    url: '/production/order/' + productionCode + '/close',
    method: 'post',
    params: reason ? { reason } : undefined
  })
}

export function listProductionLogs(productionCode, params) {
  return request({ url: '/production/order/' + productionCode + '/logs', method: 'get', params })
}
