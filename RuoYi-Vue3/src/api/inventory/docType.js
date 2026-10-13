import request from '@/utils/request'

// 查询单据类型配置列表（u_m_doctypeconfig，驱动库存过账引擎的那张表）
export function listDocTypes(query) {
  return request({
    url: '/inventory/doctypes/list',
    method: 'get',
    params: query
  })
}
