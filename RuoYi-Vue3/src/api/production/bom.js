import request from '@/utils/request'

export function listProductionBom(query) {
  return request({ url: '/production/bom/list', method: 'get', params: query })
}

// 已经有配方的成品编码（不分页）。新建配方选成品时把这些锁掉，一个成品只能有一套配方
export function listBomProductCodes() {
  return request({ url: '/production/bom/products', method: 'get' })
}

export function getProductionBom(bomCode) {
  return request({ url: '/production/bom/' + bomCode, method: 'get' })
}

// 按产出商品取启用中的配方，建生产工单时自动带出；没有配方或已停用时 data 为 null
export function getBomByProduct(productCode) {
  return request({ url: '/production/bom/product/' + productCode, method: 'get' })
}

export function addProductionBom(data) {
  return request({ url: '/production/bom', method: 'post', data })
}

export function updateProductionBom(data) {
  return request({ url: '/production/bom', method: 'put', data })
}

export function delProductionBom(bomCode) {
  return request({ url: '/production/bom/' + bomCode, method: 'delete' })
}
