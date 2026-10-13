import request from '@/utils/request'

export function getInbound(docCode, tableSuffix) {
  return request({ url: '/inventory/document/' + docCode, method: 'get', params: { tableSuffix } })
}

export function updateInbound(data) {
  return request({ url: '/inventory/document', method: 'put', data })
}

// 直接新增采购入库单
export function addDirectInbound(data) {
  return request({
    url: '/inventory/purchase-inbound/direct',
    method: 'post',
    data
  })
}

// 从采购订单生成入库单
export function createInbound(purchaseCode, data) {
  return request({
    url: '/inventory/purchase-inbound/create',
    method: 'post',
    params: { purchaseCode },
    data: data
  })
}

// 由采购发货单创建采购入库单（入库员填实际到货数量与签收信息）
export function createInboundFromShipment(data) {
  return request({
    url: '/inventory/purchase-inbound/from-shipment',
    method: 'post',
    data
  })
}

// 审核采购入库单
export function confirmInbound(docCode, tableSuffix, approved) {
  return request({
    url: '/inventory/purchase-inbound/confirm',
    method: 'post',
    params: { docCode, tableSuffix, approved }
  })
}
