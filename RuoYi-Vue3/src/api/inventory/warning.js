import request from '@/utils/request'

export function listWarningRules(query) {
  return request({ url: '/inventory/warning/rule/list', method: 'get', params: query })
}

export function getWarningRule(ruleId) {
  return request({ url: '/inventory/warning/rule/' + ruleId, method: 'get' })
}

export function addWarningRule(data) {
  return request({ url: '/inventory/warning/rule', method: 'post', data })
}

export function updateWarningRule(data) {
  return request({ url: '/inventory/warning/rule', method: 'put', data })
}

export function delWarningRule(ruleIds) {
  return request({ url: '/inventory/warning/rule/' + ruleIds, method: 'delete' })
}

export function listWarningRecords(query) {
  return request({ url: '/inventory/warning/record/list', method: 'get', params: query })
}

export function acknowledgeWarning(recordId, data) {
  return request({ url: '/inventory/warning/record/' + recordId + '/acknowledge', method: 'post', data })
}

export function closeWarning(recordId, data) {
  return request({ url: '/inventory/warning/record/' + recordId + '/close', method: 'post', data })
}

export function countWarnings() {
  return request({ url: '/inventory/warning/count', method: 'get' })
}

export function checkWarnings() {
  return request({ url: '/inventory/warning/check', method: 'post' })
}
