import request from '@/utils/request'

export function listRate(query) {
  return request({ url: '/finance/rate/list', method: 'get', params: query })
}

export function addRate(data) {
  return request({ url: '/finance/rate', method: 'post', data })
}

export function updateRate(data) {
  return request({ url: '/finance/rate', method: 'put', data })
}

export function delRate(id) {
  return request({ url: '/finance/rate/' + id, method: 'delete' })
}
