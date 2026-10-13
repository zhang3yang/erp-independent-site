/**
 * 多币种公共配置。财务管理与订单管理的金额一律走这里格式化，
 * 页面里不要再写死 ¥ 或自己 toFixed，否则换币种时会有漏网的地方。
 *
 * 后端白名单在 CurrencySupport.java，两边取值范围必须一致，加币种要同时改两处。
 * 库里只存 ISO 4217 代码，符号只在显示时拼，不入库。
 */
export const currencyOptions = [
  { code: 'CNY', symbol: '¥', name: '人民币' },
  { code: 'USD', symbol: '$', name: '美元' },
  { code: 'EUR', symbol: '€', name: '欧元' },
  { code: 'GBP', symbol: '£', name: '英镑' },
  // 日元符号与人民币同形，靠代码区分，所以 currencyLabel 一律把代码带上
  { code: 'JPY', symbol: '¥', name: '日元' },
  { code: 'HKD', symbol: 'HK$', name: '港币' }
]

export const DEFAULT_CURRENCY = 'CNY'

const currencyMap = currencyOptions.reduce((map, item) => {
  map[item.code] = item
  return map
}, {})

/** 不认识的币种（老数据、脏数据）一律当 CNY，宁可显示成人民币也不要让整页报错 */
export function normalizeCurrency(currency) {
  const code = String(currency || DEFAULT_CURRENCY).trim().toUpperCase()
  return currencyMap[code] ? code : DEFAULT_CURRENCY
}

export function currencySymbol(currency) {
  return currencyMap[normalizeCurrency(currency)].symbol
}

/** 下拉与只读展示用：CNY（¥）。带上代码是因为 CNY 和 JPY 的符号一样 */
export function currencyLabel(currency) {
  const item = currencyMap[normalizeCurrency(currency)]
  return `${item.code}（${item.symbol}）`
}

/** 新建表单的下拉项文案：人民币 CNY（¥） */
export function currencyOptionLabel(item) {
  return `${item.name} ${item.code}（${item.symbol}）`
}

/**
 * 金额最多显示 6 位小数。再多的位数只会是 JS 浮点误差（0.1 * 3 = 0.30000000000000004），
 * 不是用户真填进来的，直接四舍五入掉。
 */
const MAX_FRACTION_DIGITS = 6

/**
 * 决定这笔金额显示几位小数：不足 minDigits 的补零，超过的按值本身来。
 * 填 12 显示 12.00，填 12.5 显示 12.50，填 12.3456 就原样显示 12.3456。
 */
function fractionDigits(value, minDigits) {
  const text = String(Math.abs(Number(value.toFixed(MAX_FRACTION_DIGITS))))
  const dot = text.indexOf('.')
  const actual = dot < 0 ? 0 : text.length - dot - 1
  return Math.min(Math.max(minDigits, actual), MAX_FRACTION_DIGITS)
}

/**
 * 金额格式化：符号 + 千分位 + 小数，例如 ¥1,000.00 / $1,000.50 / €1,000.1234。
 *
 * 负数把负号放在最前面（-¥1,000.00），与资金流水里支出记负数的口径一致。
 *
 * @param value 金额，null/undefined/'' 一律当 0
 * @param currency ISO 4217 代码，缺省 CNY
 * @param digits 最少几位小数，缺省 2；位数不够补零，超了按原值显示
 */
export function formatCurrency(value, currency, digits = 2) {
  const number = Number(value)
  const safe = Number.isFinite(number) ? number : 0
  const fraction = fractionDigits(safe, digits)
  const body = Math.abs(safe).toLocaleString('en-US', {
    minimumFractionDigits: fraction,
    maximumFractionDigits: fraction
  })
  return `${safe < 0 ? '-' : ''}${currencySymbol(currency)}${body}`
}

/** 不带币种符号的金额，用在上下文已经写明币种的地方。小数位规则同 formatCurrency */
export function formatAmount(value, digits = 2) {
  const number = Number(value)
  const safe = Number.isFinite(number) ? number : 0
  const fraction = fractionDigits(safe, digits)
  return safe.toLocaleString('en-US', {
    minimumFractionDigits: fraction,
    maximumFractionDigits: fraction
  })
}
