import request from '@/utils/request'

// 创建出库单
export function createOutbound(params, data) {
  return request({
    url: '/inventory/outbound/create',
    method: 'post',
    params: params,
    data: data
  })
}

// 审核出库单
export function confirmOutbound(docCode, tableSuffix, approved) {
  return request({
    url: '/inventory/outbound/confirm',
    method: 'post',
    params: { docCode, tableSuffix, approved }
  })
}
