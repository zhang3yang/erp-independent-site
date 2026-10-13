import request from '@/utils/request'

// 查询供应商/客户列表
export function listPartner(query) {
  return request({
    url: '/inventory/partners/list',
    method: 'get',
    params: query
  })
}

/**
 * 按商品查历史供应商，给供应商选择做推荐。
 *
 * 数据来自已审核采购入库单累积出的供货关系表，第一次入库的商品查不到记录，
 * 属于正常情况。一次把单据里的全部商品编码带上，不要一行一个请求。
 *
 * @param {string[]} goodsCodes 商品编码数组
 */
export function recommendPartners(goodsCodes) {
  return request({
    url: '/inventory/partners/recommend',
    method: 'get',
    params: { goodsCodes: (goodsCodes || []).join(',') }
  })
}

// 查询供应商/客户详细
export function getPartner(partnerCode) {
  return request({
    url: '/inventory/partners/' + partnerCode,
    method: 'get'
  })
}

// 新增供应商/客户
export function addPartner(data) {
  return request({
    url: '/inventory/partners',
    method: 'post',
    data: data
  })
}

// 修改供应商/客户
export function updatePartner(data) {
  return request({
    url: '/inventory/partners',
    method: 'put',
    data: data
  })
}

// 删除供应商/客户
export function delPartner(partnerCodes) {
  return request({
    url: '/inventory/partners/' + partnerCodes,
    method: 'delete'
  })
}
