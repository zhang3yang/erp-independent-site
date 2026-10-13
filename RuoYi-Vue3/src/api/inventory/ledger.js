import request from '@/utils/request'

// 查询库存流水列表
export function listLedger(query) {
  return request({
    url: '/inventory/ledger/list',
    method: 'get',
    params: query
  })
}

// 查询库存流水详细
export function getLedger(ledgerCode, tableSuffix) {
  return request({
    url: '/inventory/ledger/' + ledgerCode,
    method: 'get',
    params: { tableSuffix }
  })
}
