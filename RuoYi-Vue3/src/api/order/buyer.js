import request from '@/utils/request'

// 客户列表（分页）
export function listBuyers(query) {
  return request({ url: '/order/buyer/list', method: 'get', params: query })
}

// 客户详情，带历史订单数
export function getBuyer(buyerCode) {
  return request({ url: '/order/buyer/' + buyerCode, method: 'get' })
}

// 订单页客户选择器：只返回启用客户，最多 50 条
export function buyerSelector(keyword) {
  return request({ url: '/order/buyer/selector', method: 'get', params: { keyword } })
}

// 新增客户。客户编码由后端生成，不要在前端拼。
export function addBuyer(data) {
  return request({ url: '/order/buyer', method: 'post', data })
}

export function updateBuyer(data) {
  return request({ url: '/order/buyer', method: 'put', data })
}

// 有历史订单的客户后端会拒绝删除，提示改为停用。删除会级联清掉名下二级客户。
export function deleteBuyer(buyerCodes) {
  return request({ url: '/order/buyer/' + buyerCodes, method: 'delete' })
}

// ---- 二级客户 ----

// 某个主客户名下的二级客户，buyerCode 必传
export function listBuyer2(query) {
  return request({ url: '/order/buyer/subs', method: 'get', params: query })
}

// 订单页二级客户下拉：只返回该主客户名下启用中的
export function buyer2Options(buyerCode) {
  return request({ url: '/order/buyer/sub-options', method: 'get', params: { buyerCode } })
}

export function addBuyer2(data) {
  return request({ url: '/order/buyer/sub', method: 'post', data })
}

export function updateBuyer2(data) {
  return request({ url: '/order/buyer/sub', method: 'put', data })
}

export function deleteBuyer2(buyer2Codes) {
  return request({ url: '/order/buyer/sub/' + buyer2Codes, method: 'delete' })
}
