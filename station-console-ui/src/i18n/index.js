import { reactive, computed } from 'vue'
import elZh from 'element-plus/es/locale/lang/zh-cn'
import elEn from 'element-plus/es/locale/lang/en'
import elFr from 'element-plus/es/locale/lang/fr'
import zh from './locales/zh'
import en from './locales/en'
import fr from './locales/fr'

/**
 * 管理台的界面语言。
 *
 * <h3>为什么管理台也要多语言</h3>
 *
 * 这个后台原来通篇中文，因为用它的是我们自己的业务员。法国站（esvafit）交付之后
 * 不成立了：站长是外国人，他点开看到的是一整屏中文——十一个菜单、每一栏的说明、
 * 每一个按钮。他不是「看得慢一点」，是**根本不知道哪个按钮是保存**。
 *
 * 而业务员同时还在用同一个管理台，他们要看中文。所以这件事的形状是
 * 「同一套界面、两个人各看各的语言」，不是「把管理台翻译成法语」。
 *
 * <h3>两层：站点默认 + 每个人自己的选择</h3>
 *
 *   · 站点默认 —— u_s_siteinfo.consolelang，站长在「站点设置」里选，
 *     决定**第一次**进来的人看到什么语言；
 *   · 个人选择 —— 顶栏那个下拉，存在这个人自己浏览器的 localStorage 里
 *     （键 station-console-lang），此后只认它，不回站点默认。
 *
 * 少了第二层，站长切成法语的那一刻，所有业务员的管理台一起变法语；
 * 少了第一层，法国站长第一次进来还是一屏中文，而他未必找得到那个下拉。
 * 两层都要，这是这个功能的全部要点。
 *
 * <h3>回退：缺键显示中文，不显示键名</h3>
 *
 * 文案表按 `中文垫底 + 该语言覆盖` 取（和前台 useShopCopy 同一个路子）。
 * 某个键在 en.js 里漏了，英文界面上那一处显示的是中文原文，不是 `nav.goods`
 * 这种键名——漏翻是小事，一个看不懂的键名是把人堵死。
 *
 * ⚠ 加一种语言要动三处：这个文件的 LOCALES、新增 locales/xx.js、
 *   后端 SiteInfo.CONSOLE_LANGS（少了它，站点设置里选上也存不住）。
 */

/**
 * 有文案表的语言。
 *
 * label 一律用**该语言自己的写法**（English 不写「英语」）：看得懂这个下拉的人，
 * 恰恰是看不懂当前这屏语言的那个人。
 *
 * el 是 Element Plus 自带的组件文案（日期选择器的月份、分页的「条/页」、
 * 上传组件的提示）。不换它的话，界面是英文而日历是中文。
 *
 * html 写进 <html lang>，给浏览器的翻译栏和读屏软件看。
 */
export const LOCALES = [
  { code: 'zh', label: '中文', pack: zh, el: elZh, html: 'zh-CN' },
  { code: 'en', label: 'English', pack: en, el: elEn, html: 'en' },
  { code: 'fr', label: 'Français', pack: fr, el: elFr, html: 'fr' }
]

/** 没有任何依据时说中文——这个管理台的日常使用者是我们自己的业务员 */
export const FALLBACK_LANG = 'zh'

/**
 * 个人选择存在哪儿。
 *
 * ⚠ 存浏览器，不存账号。本站没有登录页（从 ERP 免密进），一个人换台电脑就得重选——
 * 这是可接受的：换电脑是罕见事件，而为它加一列、一个接口、一次写库，
 * 代价是每次切语言都要一个往返。
 */
const STORE_KEY = 'station-console-lang'

const BY_CODE = Object.fromEntries(LOCALES.map(item => [item.code, item]))

const state = reactive({
  /** 当前语言码 */
  lang: FALLBACK_LANG,
  /** 是不是这个人自己选过的（区别于「跟着站点默认走」），顶栏的提示文案要用 */
  chosen: false
})

/** 当前语言码，响应式。模板里用 $t 就行，这个给需要判断语言的少数地方 */
export const locale = computed(() => state.lang)

/** 当前语言对应的 Element Plus 文案包，给 <el-config-provider :locale> */
export const elLocale = computed(() => (BY_CODE[state.lang] || BY_CODE[FALLBACK_LANG]).el)

/** 这个人自己选过语言吗 */
export const localeChosen = computed(() => state.chosen)

function readStored() {
  try {
    return window.localStorage.getItem(STORE_KEY) || ''
  } catch (e) {
    // 隐私模式下 localStorage 会抛。切语言这件事不值得为它中断
    return ''
  }
}

function apply(code) {
  state.lang = BY_CODE[code] ? code : FALLBACK_LANG
  document.documentElement.setAttribute('lang', BY_CODE[state.lang].html)
}

/**
 * 启动时定一次语言。在 main.js 挂载之前调用。
 *
 * ⚠ 必须在 mount 之前：第一帧就要是对的语言。先挂载再改的话，法国站长会看到
 * 一闪而过的中文界面——那一下足够让人以为自己点错了地方。
 *
 * @param siteDefault 站点设置里的默认语言（/api/me 的 consoleLang）。拉不到身份时传空。
 */
export function initLocale(siteDefault) {
  const saved = readStored()
  state.chosen = !!BY_CODE[saved]
  apply(state.chosen ? saved : siteDefault)
}

/**
 * 切语言。顶栏那个下拉调它。
 *
 * <p>会记进 localStorage：切了就是这个人的选择，下次进来不再跟站点默认走。
 * 不记的话，业务员每次进来都得重切一遍。</p>
 */
export function setLocale(code) {
  if (!BY_CODE[code] || code === state.lang) return
  apply(code)
  state.chosen = true
  try {
    window.localStorage.setItem(STORE_KEY, code)
  } catch (e) {
    // 存不住就只在这一次会话里生效，比整个切换动作失败好
  }
}

/** 清掉个人选择，回到站点默认。站点设置里改完默认语言时用得上 */
export function resetLocale(siteDefault) {
  try {
    window.localStorage.removeItem(STORE_KEY)
  } catch (e) { /* 同上 */ }
  state.chosen = false
  apply(siteDefault)
}

/** `fill('已保存 {n} 条', { n: 3 })` → '已保存 3 条' */
function fill(text, vars) {
  return Object.entries(vars).reduce(
    (out, [key, value]) => out.split(`{${key}}`).join(String(value)),
    text
  )
}

/**
 * 取一句文案。模板里写 `$t('nav.goods')`，脚本里 `import { t } from '@/i18n'`。
 *
 * 回退链：当前语言 → 中文 → 键名本身。最后那一环只在中文表也漏了键时发生，
 * 那是个 bug，开发模式下会在控制台喊一声。
 *
 * @param key  点分键，见 locales/zh.js
 * @param vars 可选，替换文案里的 {xxx}
 */
export function t(key, vars) {
  const pack = (BY_CODE[state.lang] || BY_CODE[FALLBACK_LANG]).pack
  let text = pack[key]
  if (text === undefined) text = zh[key]
  if (text === undefined) {
    if (import.meta.env.DEV) console.warn('[i18n] 中文表里没有这个键：' + key)
    return key
  }
  return vars ? fill(text, vars) : text
}

/**
 * 和 {@link t} 一样，但**查不到键时返回 undefined**，而不是把键名当文字吐出来。
 *
 * 只给 station-shared 下那些共用组件用（见 station-shared/useStationT.js）：
 * 它们自带中文原文兜底，需要的是一个能说「我没有这个键」的翻译器——
 * t() 返回键名的话，那个兜底就永远轮不到，页面上会出现 `picker.confirm` 这种东西。
 */
export function tryT(key, vars) {
  const pack = (BY_CODE[state.lang] || BY_CODE[FALLBACK_LANG]).pack
  const text = pack[key] === undefined ? zh[key] : pack[key]
  if (text === undefined) return undefined
  return vars ? fill(text, vars) : text
}

/**
 * 装到 app 上，模板里就能直接写 `$t('...')`。
 *
 * ⚠ `t` 在渲染期间读了 state.lang（响应式），所以切语言时用到它的组件会自己重渲染，
 * 不需要 key 换绑或整页刷新。反过来说：**把 t() 的结果存进普通常量就丢了这个能力**
 * （比如 AppShell 那个导航数组，必须是 computed）。
 */
export default {
  install(app) {
    app.config.globalProperties.$t = t
  }
}
