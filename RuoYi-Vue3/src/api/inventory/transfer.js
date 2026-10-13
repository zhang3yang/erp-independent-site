import request from '@/utils/request'

// 创建调拨单
export function createTransfer(params, data) {
  return request({
    url: '/inventory/transfer/create',
    method: 'post',
    params: params,
    data: data
  })
}

// 审核调拨单
export function confirmTransfer(docCode, tableSuffix, approved) {
  return request({
    url: '/inventory/transfer/confirm',
    method: 'post',
    params: { docCode, tableSuffix, approved }
  })
}
