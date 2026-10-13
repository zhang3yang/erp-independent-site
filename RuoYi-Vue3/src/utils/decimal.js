/**
 * 只读展示用的小数格式化。
 *
 * 后端的数量和金额是 decimal(18,4) / decimal(18,6)，接口原样发下来就是
 * "34.0000"、"0.0000" 这种带一串零的字符串。直接往页面上打，一列全是
 * 1.0000、0.0000，看着像坏了。
 *
 * 口径与录入端的 DecimalInput 一致：不足 minDecimals 补零，超过的按值本身来。
 * 数量用默认的 0（34 就显示 34，34.5 就显示 34.5），金额传 2。
 */
export function formatDecimal(value, minDecimals = 0, maxDecimals = 6) {
  if (value === null || value === undefined || value === '') return ''
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return ''
  // 位数用字符串数：Number 的科学计数法（1e-7）在这里数不准
  const text = String(parsed)
  const dot = text.indexOf('.')
  const actual = dot < 0 ? 0 : text.length - dot - 1
  return parsed.toFixed(Math.min(Math.max(minDecimals, actual), maxDecimals))
}

/** 数量：34.0000 → 34，34.5000 → 34.5。空值显示成 0，不显示成空白 */
export function formatQty(value) {
  if (value === null || value === undefined || value === '') return '0'
  return formatDecimal(value, 0, 4)
}
