/**
 * 库存二维状态：良品状态 + 在库状态。
 *
 * 值域和后端的 QualityFlag / OnhandFlag 两个枚举一一对应，是业务流转规则硬依赖的
 * 东西，不做成可配置字典，所以前端也写死在这里，不再调接口拉取。
 *
 * 四种组合的业务含义：
 *   良品·在库    可用库存，能销售、调拨、借出、领料
 *   不良品·在库  待处理的坏品，也包括厂内维修中的
 *   良品·不在库  借出在外的好货，以及领到车间的在制品
 *   不良品·不在库 送外维修中的，以及借出在外的坏货
 *
 * 后两格里「为什么不在库」库存表不回答——借出、生产领用、送外维修混在一起，
 * 要区分只能翻单据。
 */

/** 良品状态 */
export const QUALITY_FLAG = { GOOD: 1, DEFECTIVE: 0 }

/** 在库状态 */
export const ONHAND_FLAG = { IN: 1, OUT: 0 }

export const QUALITY_FLAG_OPTIONS = [
  { value: QUALITY_FLAG.GOOD, label: '良品' },
  { value: QUALITY_FLAG.DEFECTIVE, label: '不良品' }
]

export const ONHAND_FLAG_OPTIONS = [
  { value: ONHAND_FLAG.IN, label: '在库' },
  { value: ONHAND_FLAG.OUT, label: '不在库' }
]

/** 可用库存：良品 + 在库，能销售、调拨、借出、领料的就是这一格 */
export const AVAILABLE_STATE = {
  qualityFlag: QUALITY_FLAG.GOOD,
  onhandFlag: ONHAND_FLAG.IN
}

function labelOf(options, value) {
  const hit = options.find(item => item.value === value)
  return hit ? hit.label : ''
}

export function qualityLabel(value) {
  return labelOf(QUALITY_FLAG_OPTIONS, value)
}

export function onhandLabel(value) {
  return labelOf(ONHAND_FLAG_OPTIONS, value)
}

/**
 * 二维状态的完整名称，形如「良品·在库」。
 * 后端返回的行上也带 qualityName/onhandName，这里是给纯前端构造的对象用的。
 *
 * @param {object} row 带 qualityFlag / onhandFlag 的对象
 * @return {string} 状态名称，缺字段时返回空串
 */
export function stockStateLabel(row) {
  if (!row) return ''
  const parts = [qualityLabel(row.qualityFlag), onhandLabel(row.onhandFlag)].filter(Boolean)
  return parts.length === 2 ? parts.join('·') : ''
}

/** 是不是可用库存那一格 */
export function isAvailableState(row) {
  return !!row
    && row.qualityFlag === AVAILABLE_STATE.qualityFlag
    && row.onhandFlag === AVAILABLE_STATE.onhandFlag
}
