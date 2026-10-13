import { locale, t } from '@/i18n'
import zh from './locales/zh'

/**
 * 把后端回来的中文提示换成当前界面语言。
 *
 * <h3>为什么在前端翻，而不是让后端按语言回话</h3>
 *
 * 管理台后端（station-console）的提示语全是中文，散在四十来个文件的
 * `new ServiceException("…")` 里。前台买家那一侧走的是 BuyerText（每句一个键、
 * 按请求语言渲染），管理台当初没做，因为「管理台只有中文界面」——2026-09-22
 * 管理台上了多语言，这个前提不在了。
 *
 * 在后端补要改几十个抛异常的地方、再发一次 jar；在这里补，后端一行不动，
 * 文案和其它界面文字待在同一套 locales 里，由同一个人维护。代价是两边靠**原文**对上：
 * 后端改了一句话的措辞，这里就匹配不上——匹配不上的表现是**照旧显示中文原话**，
 * 和没有这一层时一模一样，不会更坏。
 *
 * <h3>句式是封闭的，变量不是</h3>
 *
 * 「商品 EB-BOOT-01 不存在」每次都不一样，但句式只有一个。文案表里的 `server.*` 键，
 * 中文那一份就是后端的原话，其中运行期拼进去的部分写成 `{0}`、`{1}`：
 *
 *     'server.goods.notFound': '商品 {0} 不存在'
 *
 * 匹配时把 `{n}` 当通配，抠出来的片段原样填进译文的同一个位置。片段本身不翻
 * （商品编码、文件名、语言代码——翻了反而错）。
 *
 * <h3>⚠ 维护</h3>
 *
 * 后端新增或改写一句会弹到界面上的话，要在三份 locales 里同步一条 `server.*`。
 * 中文那一份**必须和后端逐字一致**（全角半角、空格都算），差一个字就匹配不上。
 */

const PLACEHOLDER = /\{(\d+)\}/g

/** 精确匹配（没有占位符的句子）：原文 → 键 */
let exact = null
/** 带占位符的句式：[{ key, re }]，长的在前，免得短句式先把长句吞掉 */
let patterns = null

function build() {
  exact = new Map()
  patterns = []
  for (const [key, text] of Object.entries(zh)) {
    if (!key.startsWith('server.')) continue
    if (!PLACEHOLDER.test(text)) {
      exact.set(text, key)
      continue
    }
    PLACEHOLDER.lastIndex = 0
    const source = text
      .split(PLACEHOLDER)
      // split 带捕获组：偶数位是字面量，奇数位是占位符的序号
      .map((part, i) => (i % 2 === 0 ? part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') : `(?<p${part}>[\\s\\S]+?)`))
      .join('')
    patterns.push({ key, re: new RegExp(`^${source}$`), weight: text.replace(PLACEHOLDER, '').length })
  }
  patterns.sort((a, b) => b.weight - a.weight)
}

/**
 * 库里存的中文状态值 → 界面上的译名键。
 *
 * 后端有几句话会把状态原值拼进去（「订单已经是「待确认」，不能退回……」），
 * 这些值本身是库里的枚举（见 Orders.vue 的 statuses），界面上本来就有译名。
 * 只收这几组：别的片段是商品编码、文件名这种，翻了反而错。
 */
const VALUE_KEYS = {
  待确认: 'orders.status.pending',
  已确认: 'orders.status.confirmed',
  已发货: 'orders.status.shipped',
  已完成: 'orders.status.done',
  已取消: 'orders.status.cancelled',
  草稿: 'goods.status.draft',
  已上架: 'goods.status.online',
  已下架: 'goods.status.offline',
  已发布: 'blog.status.published'
}

/**
 * 抠出来的片段。原样保留，只动三种确定是「界面腔」的东西：
 * 整段就是一个状态值；中文顿号连起来的列表（「EB-01、EB-02」）；列表尾巴上的「等 12 个」。
 */
function translateValue(value) {
  if (!/[一-鿿、]/.test(value)) return value
  if (VALUE_KEYS[value]) return t(VALUE_KEYS[value])
  let out = value
  const more = out.match(/\s*等\s*(\d+)\s*个$/)
  if (more) out = out.slice(0, more.index) + ' ' + t('server.seo.moreCount', { 0: more[1] })
  return out.replace(/、/g, ', ')
}

/**
 * 翻一句后端的话。中文界面、非字符串、没有中文的一律原样返回。
 *
 * @param text 后端回的原话（msg，或者数据里的说明文字）
 */
export function translateServerText(text) {
  if (typeof text !== 'string' || !text || locale.value === 'zh') return text
  if (!/[一-鿿]/.test(text)) return text
  if (!exact) build()

  const trimmed = text.trim()
  const key = exact.get(trimmed)
  if (key) return t(key)

  for (const { key: k, re } of patterns) {
    const m = re.exec(trimmed)
    if (!m) continue
    const vars = {}
    for (const [name, value] of Object.entries(m.groups || {})) vars[name.slice(1)] = translateValue(value)
    return t(k, vars)
  }
  if (import.meta.env.DEV) console.info('[i18n] 后端这句没有译文：' + trimmed)
  return text
}
