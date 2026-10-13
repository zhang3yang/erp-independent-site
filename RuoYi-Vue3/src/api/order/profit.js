import request from '@/utils/request'

// { begin, end, dimension, filterDim, filterKey }
export function getProfitReport(params) {
  return request({ url: '/order/profit/report', method: 'get', params })
}
