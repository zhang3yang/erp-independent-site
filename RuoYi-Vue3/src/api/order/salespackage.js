import request from '@/utils/request'

// 套餐列表，不带明细
export function listPackage(query) {
  return request({ url: '/order/package/list', method: 'get', params: query })
}

// 套餐详情，含商品明细
export function getPackage(packageCode) {
  return request({ url: '/order/package/' + packageCode, method: 'get' })
}

// 下单时可选的套餐，含明细，只有启用且有商品的才在列表里
export function listActivePackages() {
  return request({ url: '/order/package/active', method: 'get' })
}

export function addPackage(data) {
  return request({ url: '/order/package', method: 'post', data })
}

export function updatePackage(data) {
  return request({ url: '/order/package', method: 'put', data })
}

export function delPackage(packageCode) {
  return request({ url: '/order/package/' + packageCode, method: 'delete' })
}
