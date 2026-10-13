import request from '@/utils/request'

/**
 * 首页看板卡片数据。
 *
 * 没有 dashboard:overview:query 权限时返回 403，管理员没选商家时返回一份 month 为空的
 * 空数据——两种都是正常状态，调用方自己降级，不要弹错误。
 */
export function getDashboardSummary() {
  return request({ url: '/dashboard/summary', method: 'get' })
}

/** 近 months 个月的出入库与收支趋势，按月份正序 */
export function getDashboardTrend(months) {
  return request({ url: '/dashboard/trend', method: 'get', params: { months } })
}
