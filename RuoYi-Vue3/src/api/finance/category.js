import request from '@/utils/request'

// 查询收支分类列表
export function listCategory(query) {
  return request({
    url: '/finance/category/list',
    method: 'get',
    params: query
  })
}

// 启用中的分类，可按收支方向过滤
export function categoryOptions(type) {
  return request({
    url: '/finance/category/options',
    method: 'get',
    params: { type }
  })
}

// 新增收支分类
export function addCategory(data) {
  return request({
    url: '/finance/category',
    method: 'post',
    data
  })
}

// 修改收支分类
export function updateCategory(data) {
  return request({
    url: '/finance/category',
    method: 'put',
    data
  })
}

// 删除收支分类
export function delCategory(ids) {
  return request({
    url: '/finance/category/' + ids,
    method: 'delete'
  })
}
