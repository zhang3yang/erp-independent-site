import request from '@/utils/request'

// 查询商品分类列表
export function listCategory(query) {
  return request({
    url: '/inventory/goodsCategory/list',
    method: 'get',
    params: query
  })
}

// 查询商品分类列表（排除节点）
export function listCategoryExcludeChild(categoryCode) {
  return request({
    url: '/inventory/goodsCategory/list/exclude/' + categoryCode,
    method: 'get'
  })
}

// 查询商品分类树，供商品页的分类下拉使用
export function listCategoryTree(query) {
  return request({
    url: '/inventory/goodsCategory/tree',
    method: 'get',
    params: query
  })
}

// 查询商品分类详细
export function getCategory(categoryCode) {
  return request({
    url: '/inventory/goodsCategory/' + categoryCode,
    method: 'get'
  })
}

// 新增商品分类
export function addCategory(data) {
  return request({
    url: '/inventory/goodsCategory',
    method: 'post',
    data: data
  })
}

// 修改商品分类
export function updateCategory(data) {
  return request({
    url: '/inventory/goodsCategory',
    method: 'put',
    data: data
  })
}

// 保存商品分类排序
export function updateCategorySort(data) {
  return request({
    url: '/inventory/goodsCategory/updateSort',
    method: 'put',
    data: data
  })
}

// 删除商品分类
export function delCategory(categoryCode) {
  return request({
    url: '/inventory/goodsCategory/' + categoryCode,
    method: 'delete'
  })
}
