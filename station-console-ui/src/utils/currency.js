/**
 * 多币种公共配置。财务管理与订单管理的金额一律走这里格式化，
 * 页面里不要再写死 ¥ 或自己 toFixed，否则换币种时会有漏网的地方。
 *
 * ⚠ 这份清单不是「站点支持的币种」，是「管理台画得出符号的币种」——两件事。
 *   站点后端没有币种白名单（u_s_siteinfo.currency 就是一列 varchar），
 *   前台 station-site 的 formatMoney 走 Intl.NumberFormat，任何合法 ISO 4217 码都渲染得出来。
 *   所以站点设成列表外的码（SGD、AUD……）前台是对的，只有管理台这边会出错：
 *   normalizeCurrency 把不认识的一律当 CNY，商品价格就画成了 ¥。
 *   站点概览的币种下拉按这份列表出，越界值会带一句提示，见 Overview.vue。
 *
 * 这个文件是从 ERP 前端抄过来的。ERP 那边确有后端白名单（CurrencySupport.java，
 * 同样这六个），但那管的是 ERP 的订单和财务，跟站点这边没有关系，别照着它去找站点后端。
 * 库里只存 ISO 4217 代码，符号只在显示时拼，不入库。
 */
/*
  ⚠ 这里**不再写币种的中文名**：币种名有现成的、每种语言都齐的来源——
    浏览器的 Intl.DisplayNames。手写一份就要手写三遍（中/英/法），
    而且以后每加一种界面语言都要再抄一遍六个名字。

    name 现在由 currencyName() 按当前界面语言现取，取不到就退回代码本身
    （老浏览器没有 Intl.DisplayNames，那时显示 "USD（$）"，照样能用）。
*/
import { locale } from '@/i18n'

export const currencyOptions = [
  { code: 'CNY', symbol: '¥' },
  { code: 'USD', symbol: '$' },
  { code: 'EUR', symbol: '€' },
  { code: 'GBP', symbol: '£' },
  // 日元符号与人民币同形，靠代码区分，所以 currencyLabel 一律把代码带上
  { code: 'JPY', symbol: '¥' },
  { code: 'HKD', symbol: 'HK$' }
]

/** 币种的本地化名称（人民币 / Chinese Yuan / yuan renminbi）。取不到返回空串 */
const currencyNames = new Map()
export function currencyName(code) {
  const lang = locale.value
  if (!currencyNames.has(lang)) {
    try {
      currencyNames.set(lang, new Intl.DisplayNames([lang], { type: 'currency' }))
    } catch (e) {
      currencyNames.set(lang, null)
    }
  }
  const names = currencyNames.get(lang)
  if (!names) return ''
  try {
    const name = names.of(code)
    // 取不到时 Intl 会原样回代码，那种情况当成没有名字
    return name === code ? '' : name
  } catch (e) {
    return ''
  }
}

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

/** 新建表单的下拉项文案：人民币 CNY（¥）。没有本地化名字时只剩 CNY（¥） */
export function currencyOptionLabel(item) {
  const name = currencyName(item.code)
  return `${name ? name + ' ' : ''}${item.code}（${item.symbol}）`
}

/**
 * 金额格式化：符号 + 千分位 + 定点小数，例如 ¥1,000.00 / $1,000.00 / €1,000.00。
 *
 * 负数把负号放在最前面（-¥1,000.00），与资金流水里支出记负数的口径一致。
 *
 * @param value 金额，null/undefined/'' 一律当 0
 * @param currency ISO 4217 代码，缺省 CNY
 * @param digits 小数位，单价这类要 4 位的地方显式传
 */
export function formatCurrency(value, currency, digits = 2) {
  const number = Number(value)
  const safe = Number.isFinite(number) ? number : 0
  const body = Math.abs(safe).toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
  return `${safe < 0 ? '-' : ''}${currencySymbol(currency)}${body}`
}
