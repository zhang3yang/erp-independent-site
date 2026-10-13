import request from '@/utils/request'

// 查询账户列表
export function listAccount(query) {
  return request({
    url: '/finance/account/list',
    method: 'get',
    params: query
  })
}

// 启用中的账户，供资金流水选择
export function accountOptions() {
  return request({
    url: '/finance/account/options',
    method: 'get'
  })
}

// 查询账户详情
export function getAccount(accountCode) {
  return request({
    url: '/finance/account/' + accountCode,
    method: 'get'
  })
}

// 新增账户
export function addAccount(data) {
  return request({
    url: '/finance/account',
    method: 'post',
    data
  })
}

// 修改账户（余额不接收，只由资金流水驱动）
export function updateAccount(data) {
  return request({
    url: '/finance/account',
    method: 'put',
    data
  })
}

// 删除账户
export function delAccount(ids) {
  return request({
    url: '/finance/account/' + ids,
    method: 'delete'
  })
}
