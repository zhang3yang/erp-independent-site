import request from '@/utils/request'

// 发货批次按「计划发货日期」分月表，与订单的月份无关——
// 一张订单的多个批次可以落在不同月份，按订单查要走 listBatchesByOrder（跨月汇总）。
//
// 状态机（括号里是责任岗位）：
//   待确认 →(确认／业务员) 已提交仓库 →(打印备货单／仓库) 已打印备货单
//          →(上传照片+备货完成／仓库) 备货完成 →(录入快递单号／业务员) 已录入快递单号
//          →(打印快递单号／仓库) 已打印快递单号 →(完成／仓库) 已完成
//
// 库存只在最后一步「完成」时扣，那一刻对应现实中承运商把货取走——在那之前货一直还在
// 仓库里，「已打印快递单号」也只表示面单贴上了、在等人上门。所以中间那五格都能被仓库
// 「驳回」，整个退回「待确认」重来。

// 批次列表（按月表查）。仓库发货管理传 warehouseView: true，
// 后端只返回「已提交仓库」及之后的状态——没提交的批次仓库不该看见
export function listBatch(query) {
  return request({ url: '/order/shipbatch/list', method: 'get', params: query })
}

// 某订单的全部批次，跨月汇总
export function listBatchesByOrder(orderCode) {
  return request({ url: '/order/shipbatch/byOrder/' + orderCode, method: 'get' })
}

// 批次详情。不传 tableSuffix 则跨月查找
export function getBatch(shipCode, tableSuffix) {
  return request({ url: '/order/shipbatch/' + shipCode, method: 'get', params: { tableSuffix } })
}

// 建发货批次
export function addBatch(data) {
  return request({ url: '/order/shipbatch', method: 'post', data })
}

// 修改批次，仅待确认可改
export function updateBatch(data, tableSuffix) {
  return request({ url: '/order/shipbatch', method: 'put', params: { tableSuffix }, data })
}

// 确认并提交仓库：待确认 → 已提交仓库。之后订单侧只剩查看
export function confirmBatch(shipCode, tableSuffix) {
  return request({ url: '/order/shipbatch/' + shipCode + '/confirm', method: 'post', params: { tableSuffix } })
}

// 打印备货单：已提交仓库 → 已打印备货单。仓库拿到批次后的第一步，照着单子去拣货
export function printBatch(shipCode, tableSuffix) {
  return request({ url: '/order/shipbatch/' + shipCode + '/print', method: 'post', params: { tableSuffix } })
}

// 备货完成：已打印备货单 → 备货完成。不扣库存，只落备货照片。
// data.photos 是逗号分隔的图片路径（ImageUpload 的 v-model 格式），至少一张——
// 业务员在录快递单号之前，只能靠这组照片确认货备成什么样
export function completePicking(shipCode, data) {
  return request({ url: '/order/shipbatch/' + shipCode + '/picking', method: 'post', data })
}

// 补传 / 替换备货照片，不改状态。只有「已打印备货单」和「备货完成」两格能改，
// 再往后业务员已经按这组照片录过快递单号了
export function savePickPhotos(shipCode, data) {
  return request({ url: '/order/shipbatch/' + shipCode + '/photos', method: 'post', data })
}

// 「录入快递单号」弹窗要的数据：各行计划量与单价，加上这张订单的付款额度
// （已收金额、已占额度、本批次还能发多少货值）
export function getLogisticsInfo(shipCode, tableSuffix) {
  return request({ url: '/order/shipbatch/' + shipCode + '/logisticsInfo', method: 'get', params: { tableSuffix } })
}

// 录入快递单号：备货完成 → 已录入快递单号。不扣库存，货还在仓库里。
// data.carrier / data.logisticsNo 都必填（来源订单是「上门提货」的除外——客户自己来拉，
// 没有承运商也没有面单）；批次上原本已经有值的，后端认已有值，不要求重传。
// data.qtys 是「批次明细代码 → 本次发货数量」，只能往下调，填 0 的行整行不发。
// 这一步卡客户付款额度：整单累计的发货货值不能超过已收金额，超了后端直接拒
export function enterLogistics(shipCode, data) {
  return request({ url: '/order/shipbatch/' + shipCode + '/logistics', method: 'post', data })
}

// 打印快递单号：已录入快递单号 → 已打印快递单号。仓库把面单打出来贴箱子上，仍然不扣库存
export function printLabel(shipCode, tableSuffix) {
  return request({ url: '/order/shipbatch/' + shipCode + '/printLabel', method: 'post', params: { tableSuffix } })
}

// 驳回：中间五格 → 待确认。仓库的后悔药，货没被取走都能驳。
// data.reason 必填——批次退回业务员手里，得让人知道为什么退的
export function rejectBatch(shipCode, data) {
  return request({ url: '/order/shipbatch/' + shipCode + '/reject', method: 'post', data })
}

// 完成：已打印快递单号 → 已完成，对应承运商把货取走那一刻。
// 开销售出库单 → 同事务过账扣库存 → 回写订单已发量与进度
export function completeBatch(shipCode, data) {
  return request({ url: '/order/shipbatch/' + shipCode + '/complete', method: 'post', data })
}

// 取消批次，到「备货完成」为止。录完快递单号的先让仓库驳回退成待确认再取消；
// 已完成的走退货流程
export function cancelBatch(shipCode, tableSuffix) {
  return request({ url: '/order/shipbatch/' + shipCode + '/cancel', method: 'post', params: { tableSuffix } })
}
