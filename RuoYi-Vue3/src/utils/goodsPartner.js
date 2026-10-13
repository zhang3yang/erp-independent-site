/**
 * 商品历史供应商（推荐供应商）的归并逻辑。
 *
 * 后端 /inventory/partners/recommend 返回的是「商品 × 供应商」一行一条，
 * 已按 goodscode、最近一次入库日期倒序排好。采购下单页、手工入库页和
 * 供应商选择弹窗三处都要把它揉成自己要的形状，逻辑放这里共用。
 *
 * 数据源是已审核采购入库单累积出来的供货关系表，第一次进的货查不到记录，
 * 返回空数组是正常情况，不是出错。
 */

/**
 * 每个商品最近一次是从哪家进的。
 *
 * 用于明细表格里逐行和表头选的供应商比对：不一致就标出来。
 * 后端已按「商品、最近入库日期倒序」排好，所以每个商品取第一条即可。
 *
 * @param {Array} rows 接口返回的供货关系
 * @returns {Object} { [goodsCode]: { partnerCode, partnerName } }
 */
export function buildPartnerHints(rows) {
  const hints = {}
  ;(rows || []).forEach(row => {
    if (!row?.goodsCode || hints[row.goodsCode]) return
    hints[row.goodsCode] = { partnerCode: row.partnerCode, partnerName: row.partnerName }
  })
  return hints
}

/**
 * 按供应商归并：这家供过本单哪几个商品。
 *
 * 排序规则是「覆盖的商品数多的在前，同样多的按最近一次入库日期倒序」。
 * 覆盖数优先，是因为一张单只能填一个供应商 —— 能把本单的货一家全供了的
 * 那家最可能是对的；只按时间排会让刚进过一次货的边角商品把主供应商顶掉。
 *
 * @param {Array} rows 接口返回的供货关系
 * @param {Object} goodsNameMap { [goodsCode]: 商品名称 }，用来显示「供过哪些商品」
 * @returns {Array} 已排序的推荐列表
 */
export function groupPartnerRecommend(rows, goodsNameMap = {}) {
  const byPartner = new Map()
  ;(rows || []).forEach(row => {
    if (!row?.partnerCode) return
    let item = byPartner.get(row.partnerCode)
    if (!item) {
      item = {
        partnerCode: row.partnerCode,
        partnerName: row.partnerName,
        // activeFlag 从 u_m_partner 关联出来，供应商被删掉时为 null，
        // 一并当停用处理 —— 不存在的供应商不能选。
        disabled: Number(row.activeFlag) !== 1,
        goodsNames: [],
        inboundTimes: 0,
        lastDocDate: null,
        lastCostPrice: null
      }
      byPartner.set(row.partnerCode, item)
    }
    item.goodsNames.push(goodsNameMap[row.goodsCode] || row.goodsCode)
    item.inboundTimes += Number(row.inboundTimes || 0)
    // 同一家供过多个商品时，「最近入库」和「最近单价」取最近那一条。
    // 日期是 yyyy-MM-dd HH:mm:ss 字符串，字典序就是时间序。
    if (!item.lastDocDate || (row.lastDocDate && row.lastDocDate > item.lastDocDate)) {
      item.lastDocDate = row.lastDocDate
      item.lastCostPrice = row.lastCostPrice === undefined ? null : row.lastCostPrice
    }
  })

  return [...byPartner.values()].map(item => ({
    ...item,
    goodsCount: item.goodsNames.length,
    goodsLabel: item.goodsNames.join('、')
  })).sort((a, b) => (b.goodsCount - a.goodsCount)
    || String(b.lastDocDate || '').localeCompare(String(a.lastDocDate || '')))
}

/**
 * 自动填充用的首选供应商：排最前面且没停用的那家。
 *
 * 停用的供应商会出现在推荐里（历史事实不因停用而消失），但不能自动填进新单据。
 *
 * @param {Array} rows 接口返回的供货关系
 * @returns {Object|null} { partnerCode, partnerName, goodsCount } 或 null
 */
export function pickRecommendedPartner(rows) {
  return groupPartnerRecommend(rows).find(item => !item.disabled) || null
}
