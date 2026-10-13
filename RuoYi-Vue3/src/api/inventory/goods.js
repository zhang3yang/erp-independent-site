import request from '@/utils/request'

// 查询商品列表
export function listGoods(query) {
  return request({
    url: '/inventory/goods/list',
    method: 'get',
    params: query
  })
}

// 按分类树节点查询商品列表
export function listGoodsByCategory(query) {
  return request({
    url: '/inventory/goods/categoryList',
    method: 'get',
    params: query
  })
}

// 查询商品详细
export function getGoods(goodsCode) {
  return request({
    url: '/inventory/goods/' + goodsCode,
    method: 'get'
  })
}

// 新增商品
export function addGoods(data) {
  return request({
    url: '/inventory/goods',
    method: 'post',
    data: data
  })
}

// 修改商品
export function updateGoods(data) {
  return request({
    url: '/inventory/goods',
    method: 'put',
    data: data
  })
}

// 删除商品
export function delGoods(goodsCodes) {
  return request({
    url: '/inventory/goods/' + goodsCodes,
    method: 'delete'
  })
}
