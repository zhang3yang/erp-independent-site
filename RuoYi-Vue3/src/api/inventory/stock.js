import request from '@/utils/request'

// 查询库存列表
export function listStock(query) {
  return request({
    url: '/inventory/stock/list',
    method: 'get',
    params: query
  })
}

// 查询当前商家全部借出中的库存
export function listBorrowedStock() {
  return request({
    url: '/inventory/stock/borrowed',
    method: 'get'
  })
}

// 查询当前商家全部维修中的库存
export function listRepairingStock() {
  return request({
    url: '/inventory/stock/repairing',
    method: 'get'
  })
}

// 查询在库的不良品（损坏待送修）
export function listDamagedStock() {
  return request({
    url: '/inventory/stock/damaged',
    method: 'get'
  })
}

// 查询库存详细
export function getStock(goodsCode, wareCode, state) {
  return request({
    url: '/inventory/stock/detail',
    method: 'get',
    params: { goodsCode, wareCode, ...state }
  })
}

// 新增库存记录
export function addStock(data) {
  return request({
    url: '/inventory/stock',
    method: 'post',
    data: data
  })
}

// 修改库存记录
export function updateStock(data) {
  return request({
    url: '/inventory/stock',
    method: 'put',
    data: data
  })
}

// 调整库存数量
export function adjustStock(goodsCode, wareCode, state, adjustQty, costAmount) {
  return request({
    url: '/inventory/stock/adjust',
    method: 'post',
    params: { goodsCode, wareCode, ...state, adjustQty, costAmount }
  })
}

// 删除库存记录
export function delStock(goodsCodes) {
  return request({
    url: '/inventory/stock/' + goodsCodes,
    method: 'delete'
  })
}
