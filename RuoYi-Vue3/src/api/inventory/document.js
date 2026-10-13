import request from '@/utils/request'

// 查询单据列表
export function listDocument(query) {
  return request({
    url: '/inventory/document/list',
    method: 'get',
    params: query
  })
}

// 查询单据详细
export function getDocument(docCode, tableSuffix) {
  return request({
    url: '/inventory/document/' + docCode,
    method: 'get',
    params: { tableSuffix }
  })
}

// 新增单据
export function addDocument(data) {
  return request({
    url: '/inventory/document',
    method: 'post',
    data: data
  })
}

// 修改单据
export function updateDocument(data) {
  return request({
    url: '/inventory/document',
    method: 'put',
    data: data
  })
}

// 删除单据
export function delDocument(docCode, tableSuffix) {
  return request({
    url: '/inventory/document/' + docCode,
    method: 'delete',
    params: { tableSuffix }
  })
}

// 审核单据
export function confirmDocument(docCode, tableSuffix, approved) {
  return request({
    url: '/inventory/document/' + docCode + '/confirm',
    method: 'post',
    params: { tableSuffix, approved }
  })
}
