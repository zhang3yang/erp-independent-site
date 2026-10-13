import request from '@/utils/request'

export function listPlatforms(query) {
  return request({ url: '/order/platform/list', method: 'get', params: query })
}
export function platformOptions() {
  return request({ url: '/order/platform/options', method: 'get' })
}
export function addPlatform(data) {
  return request({ url: '/order/platform', method: 'post', data })
}
export function updatePlatform(data) {
  return request({ url: '/order/platform', method: 'put', data })
}
export function deletePlatform(id) {
  return request({ url: '/order/platform/' + id, method: 'delete' })
}
export function listStores(query) {
  return request({ url: '/order/platform/stores', method: 'get', params: query })
}
export function storeOptions(platformCode) {
  return request({ url: '/order/platform/store-options', method: 'get', params: { platformCode } })
}
export function addStore(data) {
  return request({ url: '/order/platform/store', method: 'post', data })
}
export function updateStore(data) {
  return request({ url: '/order/platform/store', method: 'put', data })
}
export function deleteStore(id) {
  return request({ url: '/order/platform/store/' + id, method: 'delete' })
}
