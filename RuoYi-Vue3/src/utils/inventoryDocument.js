/** 库存单据明细的统一默认结构。 */
export function createInventoryDocumentLine(overrides = {}) {
  const serialNumbers = Array.isArray(overrides.serialNumbers) ? [...overrides.serialNumbers] : []
  return {
    goodsCode: '',
    goodsName: '',
    spec: '',
    unit: '',
    currentQty: 0,
    qty: 0,
    batchCode: '',
    batchDisplay: '',
    serialNumbers,
    batchStrategy: 'none',
    costCalculation: 'moving_avg',
    fifo: false,
    availableBatches: [],
    // 销售出库定价：costPrice 为本行入库成本（默认售价的来源），salePrice 为本单快照（可改价）。
    // salePriceEdited 记录业务员是否手工改过价，改过的行不再跟随成本变化。
    costPrice: null,
    salePrice: null,
    salePriceEdited: false,
    ...overrides,
    serialNumbers
  }
}

/** 销售单价按后端 saleprice 列的 4 位精度取整，成本是 6 位，比较前必须先规约。 */
export function inventorySalePriceScale(value) {
  const price = Number(value)
  return Number.isFinite(price) ? Math.round(price * 10000) / 10000 : null
}

/** 销售单价是否被业务员改过：与按售价精度规约后的成本不等即视为改过价。 */
export function inventorySalePriceOverridden(salePrice, costPrice) {
  if (salePrice == null) return false
  const cost = inventorySalePriceScale(costPrice)
  return cost == null || Number(salePrice) !== cost
}

/** 销售金额 = 销售单价 × 数量，保留两位小数。 */
export function inventorySaleAmount(row) {
  const price = Number(row?.salePrice)
  const quantity = Number(row?.qty)
  if (!Number.isFinite(price) || !Number.isFinite(quantity)) return 0
  return Math.round(price * quantity * 100) / 100
}

/** 清空依赖商品、仓库或库存状态的追踪选择。 */
export function clearInventoryTracking(row) {
  row.serialNumbers = []
  row.batchCode = ''
  row.batchDisplay = ''
}

export function availableInventoryQuantity(row) {
  const quantity = Number(row?.currentQty)
  return Number.isFinite(quantity) && quantity > 0 ? quantity : 0
}

export function serialQuantityMatches(row, quantityField = 'qty') {
  const quantity = Number(row?.[quantityField])
  return Number.isInteger(quantity)
    && quantity > 0
    && Array.isArray(row?.serialNumbers)
    && row.serialNumbers.length === quantity
}

export function parseSerialNumbers(text) {
  return [...new Set(String(text || '')
    .split(/\r?\n/)
    .map(value => value.trim())
    .filter(Boolean))]
}

export function formatSerialNumbers(serialNumbers) {
  return Array.isArray(serialNumbers) ? serialNumbers.join('\n') : ''
}

/** 应用序列号选择，并根据所选序列号所属批次更新批次显示。 */
export function applySerialSelection(row, selectedValues, options = []) {
  const selected = [...new Set(selectedValues || [])]
  row.serialNumbers = selected
  const batchCodes = [...new Set(options
    .filter(option => selected.includes(option.serialNo))
    .map(option => option.batchCode)
    .filter(Boolean))]
  row.batchCode = batchCodes.length === 1 ? batchCodes[0] : ''
  row.batchDisplay = batchCodes.length > 1 ? '多个批次（系统自动分配）' : (batchCodes[0] || '')
  return selected
}
