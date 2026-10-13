import request from '@/utils/request'

// 查询盘点单列表
export function listStocktake(query) {
  return request({
    url: '/inventory/stocktake/list',
    method: 'get',
    params: query
  })
}

// 盘点单详情（含明细、当前库存）
export function getStocktake(stocktakeCode) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode,
    method: 'get'
  })
}

// 新建盘点单：{ wareCode, scope: 'all'|'goods', goodsCodes, includeDefective, remark }
export function addStocktake(data) {
  return request({
    url: '/inventory/stocktake',
    method: 'post',
    data: data
  })
}

// 保存实盘数：[{ id, countedQty, costPrice, batchCode, serialNos, remark }]
export function saveStocktakeCounts(stocktakeCode, lines) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode + '/counts',
    method: 'put',
    data: lines
  })
}

// 往盘点单上加商品
export function addStocktakeItems(stocktakeCode, goodsCodes, qualityFlag) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode + '/items',
    method: 'post',
    data: { goodsCodes, qualityFlag }
  })
}

// 删除一行盘点明细
export function delStocktakeItem(stocktakeCode, itemId) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode + '/items/' + itemId,
    method: 'delete'
  })
}

export function submitStocktake(stocktakeCode) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode + '/submit',
    method: 'post'
  })
}

export function cancelStocktake(stocktakeCode) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode + '/cancel',
    method: 'post'
  })
}

export function rejectStocktake(stocktakeCode, reason) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode + '/reject',
    method: 'post',
    data: { reason }
  })
}

// 审核通过：生成盘盈入库 / 盘亏出库并过账
export function approveStocktake(stocktakeCode) {
  return request({
    url: '/inventory/stocktake/' + stocktakeCode + '/approve',
    method: 'post'
  })
}
