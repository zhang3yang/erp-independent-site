import request from '@/utils/request'

// 查询商品位置列表
export function listGoodsPosition(query) {
  return request({
    url: '/inventory/goodsPosition/list',
    method: 'get',
    params: query
  })
}

// 查询商品位置详细
export function getGoodsPosition(positionId) {
  return request({
    url: '/inventory/goodsPosition/' + positionId,
    method: 'get'
  })
}

// 新增商品位置
export function addGoodsPosition(data) {
  return request({
    url: '/inventory/goodsPosition',
    method: 'post',
    data: data
  })
}

// 修改商品位置
export function updateGoodsPosition(data) {
  return request({
    url: '/inventory/goodsPosition',
    method: 'put',
    data: data
  })
}

// 删除商品位置
export function delGoodsPosition(positionIds) {
  return request({
    url: '/inventory/goodsPosition/' + positionIds,
    method: 'delete'
  })
}
