import request from '@/utils/request'

// 查询批次列表
export function listBatch(query) {
  return request({
    url: '/inventory/batch/list',
    method: 'get',
    params: query
  })
}

// 查询可用批次列表
export function listAvailableBatch(query) {
  return request({
    url: '/inventory/batch/available',
    method: 'get',
    params: query
  })
}

// 查询批次详细
export function getBatch(batchCode, goodsCode, wareCode, qualityFlag, onhandFlag) {
  return request({
    url: '/inventory/batch/' + batchCode + '/' + goodsCode + '/' + wareCode
      + '/' + qualityFlag + '/' + onhandFlag,
    method: 'get'
  })
}

// 新增批次
export function addBatch(data) {
  return request({
    url: '/inventory/batch',
    method: 'post',
    data: data
  })
}

// 修改批次
export function updateBatch(data) {
  return request({
    url: '/inventory/batch',
    method: 'put',
    data: data
  })
}

// 改批次的存放货位。批次上只有这一个字段允许手工改——数量、成本、状态都是过账算出来的账，
// 货位是纯管理信息，不参与任何计算。传空字符串表示清掉货位。
export function updateBatchPosition(batchCode, goodsCode, wareCode, qualityFlag, onhandFlag, position) {
  return request({
    url: '/inventory/batch/' + batchCode + '/' + goodsCode + '/' + wareCode
      + '/' + qualityFlag + '/' + onhandFlag + '/position',
    method: 'put',
    data: { position }
  })
}

// 删除批次
export function delBatch(batchCode, goodsCode, wareCode, qualityFlag, onhandFlag) {
  return request({
    url: '/inventory/batch/' + batchCode + '/' + goodsCode + '/' + wareCode
      + '/' + qualityFlag + '/' + onhandFlag,
    method: 'delete'
  })
}
