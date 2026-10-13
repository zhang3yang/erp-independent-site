import type { SiteInfo } from '~/composables/useSite'
// lib/ 不在 Nuxt 的自动导入范围里（只有 composables/ 和 utils/ 是），必须显式导入
import { formatMoney } from '~/lib/shop'

/**
 * 买家选的显示币种，以及按它换算金额。
 *
 * ## 这一层解决什么
 *
 * 商品价格在库里只有一个数字，计价单位是站点本币（`site.currency`）。买家想看欧元，
 * 就得乘一个汇率。汇率由后端随 `/openapi/site/info` 一起下发，**已经以本币为基准**
 * （`rates[本币] === 1`），所以这里只做一次乘法。
 *
 * ## ⚠ 为什么是 Cookie 不是 localStorage
 *
 * 前台是 SSR。用 localStorage 的话服务端读不到，首屏必然渲染成本币，
 * 等 hydrate 之后才跳成欧元——买家看到价格闪一下，而且 Vue 会报 hydration mismatch。
 * Cookie 服务端拿得到，首屏直接就是对的。
 *
 * ## ⚠ 为什么不进 URL
 *
 * 语言进 URL 是必须的（Google 要能索引到每个语言版本，hreflang 靠它）。
 * 币种反过来：进了 URL，每个页面就有「8 种语言 × N 种币种」个地址，内容却一样，
 * 是标准的重复内容问题。所以币种只存 Cookie，不参与路由。
 *
 * ## 换算只到结算页为止
 *
 * 商品列表、详情、购物车按选中的币种显示；**结算页和订单页一律本币**。
 * 站上不收款，订单是「下单请求」，金额必须有个稳定口径——否则汇率一变，
 * 老订单到底是 €184 还是 $200 就成了糊涂账。这条约束不在本文件里执行，
 * 在各个页面的调用点上，见 spec §5.1 那张表。
 */

/** Cookie 名。改它等于让所有老访客的选择失效，不要轻易改 */
const COOKIE_NAME = 'SITE_CURRENCY'

/** 记住一年。买家的币种偏好不会常变，每次来都要重选很烦 */
const COOKIE_MAX_AGE = 365 * 24 * 60 * 60

/**
 * 币种偏好那条 Cookie 本身。{@link useDisplayCurrency} 和 {@link resetDisplayCurrency}
 * 共用这一份，**两处的选项必须一模一样**——尤其是 domain：删的时候少给 Domain，
 * 浏览器会当成另一条 Cookie，手上这条纹丝不动，表现成「登录了价格还是旧币种」。
 *
 * ⚠ 带 domain：前台劈成了两个 host（主域放公开页、buyer. 放账号页），
 *   而顶栏那个币种切换器在两个 host 上都渲染。不放宽作用域的话，在 buyer. 上
 *   改了币种回到主域就没了——两个 host 各存一份同名 Cookie，
 *   浏览器还会把两份都发上来，谁生效不确定。
 *   没分 host（本地开发）时 cookieDomain 是 undefined，行为和以前一样。
 *   取值规则见 composables/useSiteHosts.ts。
 */
function currencyCookie() {
  const { cookieDomain } = useSiteHosts()
  return useCookie<string | null>(COOKIE_NAME, {
    maxAge: COOKIE_MAX_AGE,
    sameSite: 'lax',
    path: '/',
    domain: cookieDomain
  })
}

/**
 * 把显示币种清回站点本位币——就是把那条 Cookie 删掉，`current` 自动回落 `base`。
 *
 * 给 {@link useBuyer} 用。登录之后顶栏不再渲染币种切换器（SiteHeader.vue），
 * 而这个偏好记一年：不清的话，登录前挑过 HKD 的人登录后价格还是 HKD，
 * 而换回去的按钮已经没了。
 *
 * ⚠ 清的是**显示偏好**，不是订单。订单金额一律本位币，而且下单那一刻就把币种
 *   写死在订单行里，和这条 Cookie 无关（见文件头「换算只到结算页为止」）。
 */
export function resetDisplayCurrency() {
  const cookie = currencyCookie()
  // 本来就没有就不写——省掉一次没有意义的 Set-Cookie
  if (cookie.value !== null && cookie.value !== undefined) cookie.value = null
}

export function useDisplayCurrency(site: MaybeRefOrGetter<SiteInfo>) {
  const cookie = currencyCookie()

  const base = computed(() => toValue(site)?.currency || 'USD')
  const options = computed(() => {
    const list = toValue(site)?.currencies
    return Array.isArray(list) && list.length ? list : [base.value]
  })
  const rates = computed(() => toValue(site)?.rates || {})

  /**
   * 当前显示币种。
   *
   * Cookie 里的值不在可选列表里就静默回落本币——运营可能刚把某个币种从
   * 「买家可选」里删掉，而访客的 Cookie 还留着它。这时候报错或者留在旧币种上
   * 都不对：旧币种已经没有汇率了，会显示成一个没换算的数字配着另一个币种的符号。
   */
  const current = computed(() => {
    const picked = String(cookie.value || '').trim().toUpperCase()
    return picked && options.value.includes(picked) ? picked : base.value
  })

  /** 要不要渲染切换器。只有本币一种时不渲染，和语言切换器的行为一致 */
  const enabled = computed(() => options.value.length > 1)

  function setCurrency(code: string) {
    const next = String(code || '').trim().toUpperCase()
    if (!options.value.includes(next)) return
    // 回落到本币时把 Cookie 清掉而不是写 'USD'：少存一条没有信息量的记录，
    // 而且以后本币改了，老 Cookie 不会把买家钉在旧本币上
    cookie.value = next === base.value ? null : next
  }

  /**
   * 把本币金额换算成当前显示币种。
   *
   * 汇率缺失时**原样返回本币金额和本币代码**，不是返回 0、也不是抛错：
   * 这条路径正常走不到（后端只下发算得出汇率的币种），真走到了，
   * 显示一个本币价格远好过显示 0 或者整页崩掉。
   */
  function convert(amount: number): { amount: number; currency: string } {
    const value = Number(amount)
    const safe = Number.isFinite(value) ? value : 0
    const code = current.value
    if (code === base.value) return { amount: safe, currency: base.value }
    const rate = Number(rates.value[code])
    if (!Number.isFinite(rate) || rate <= 0) {
      return { amount: safe, currency: base.value }
    }
    return { amount: safe * rate, currency: code }
  }

  /**
   * 换算 + 格式化，一步到位。页面上绝大多数地方用这个。
   *
   * lang 传站点当前语言，交给 formatMoney 走 Intl（日元没有小数位、
   * 欧元在有些地区符号在后面，硬拼出来的金额在那些语言下是错的）。
   */
  function displayMoney(amount: number, lang?: string) {
    const converted = convert(amount)
    return formatMoney(converted.amount, converted.currency, lang)
  }

  return { current, base, options, enabled, setCurrency, convert, displayMoney }
}
