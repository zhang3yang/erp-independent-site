/**
 * 前台**两个 host** 的分工，以及跨 host 的链接怎么拼。**浏览器侧只有这一份。**
 *
 * ```
 * kangchen.vclinker.com        商城：首页、商品、购物车、结算，以及登录/注册/找回密码
 * buyer.kangchen.vclinker.com  买家中心：/account（账号资料、地址簿）、/orders（我的订单）
 * ```
 *
 * 跑的是**同一个 Node 进程**，分的是 host 不是应用——所以这里不需要判断
 * 「这个页面存在吗」，只需要判断「它该挂在哪个 host 上」。
 *
 * 「哪些路径归买家中心」的清单在 `shared/utils/buyerPaths.ts`，服务端那几处
 * （`server/middleware/domain-split.ts` 的 301、`robots.txt`）用的是同一份。
 *
 * ## ⚠ 2026-09-10 之前这件事有两份实现
 *
 * 这个文件和 `useSiteDomains.ts` 功能重叠（都判「在哪个 host 上」、都拼跨 host
 * 链接），是合并 `fdae0b80` 两侧各写一版留下的，而 `SiteHeader.vue`、`useBuyer.ts`
 * **同时用了两边**。已经合成这一份，`useSiteDomains` 不再存在；
 * 那边的 `shopUrl()` 现在是 {@link useSiteHosts} 的一个成员，语义一字未改。
 *
 * ## 这一层解决的是什么
 *
 * 整页请求走错 host 时，`server/middleware/domain-split.ts` 会 301 纠正。
 * 但**站内点击不是整页请求**：`<NuxtLink to="/products">` 是 vue-router 在浏览器里
 * 接管的，一个字节都不发给服务器，那条 301 永远不会触发。
 *
 * 于是在 buyer. 上点「首页」，首页就渲染在 buyer. 域下了——地址栏不对、
 * canonical 不对、购物车读的是这个域的 localStorage（空的），
 * 而**页面看起来完全正常**。2026-09-09 线上就是这么坏的，2026-09-10 又在
 * `orders/[id].vue` 的商品链接上找到两处。
 *
 * 所以跨 host 的链接必须是**绝对地址**（NuxtLink 认出绝对地址会渲染成普通
 * `<a href>`、走整页跳转）。这个文件就是干这个的。
 *
 * ## ⚠ 分界线是「不登录就看不到」
 *
 * 购物车和结算**留在商城 host 上**：它们不要求登录（匿名下单是刻意的，
 * 挡一道登录会掉单），而且购物车数据在 localStorage 里（{@link useCart}），
 * 按源隔离——挪到 buyer. 上，买家在主域商品页加的货在那边就是空的，页面还不报错。
 * 登录注册也在商城 host 上，理由见 `shared/utils/buyerPaths.ts`。
 *
 * ## ⚠ 没配 buyerUrl 时整套逻辑自动退化
 *
 * 本地开发只有一个 origin（localhost:3000），`NUXT_PUBLIC_BUYER_URL` 不填，
 * 下面所有函数就都返回相对路径——和改造之前的行为一模一样。
 * 生产上两个变量都要填，见 `station-site/README.md` 的部署一节。
 */
import { isBuyerPath } from '#shared/utils/buyerPaths'

/** 去掉尾斜杠，拼出来就不会有双斜杠 */
function trimOrigin(value: unknown): string {
  return String(value || '').trim().replace(/\/+$/, '')
}

/**
 * 从一个绝对地址里取 host。取不到返回空串。
 *
 * ⚠ 用 `host` 不是 `hostname`——**带端口**。本机开发真有可能把两个 host 配成
 *   `localhost:3000` 和 `localhost:3001`，去掉端口它们就成了同一个 host，
 *   拆分逻辑会整套失效（而生产上两者没区别，域名不带端口）。
 */
function hostOf(url: string): string {
  try {
    return new URL(url).host
  } catch {
    // 配错了就当没配。这里不抛——抛了整个站起不来，而域名拆分只是个路由规则
    return ''
  }
}

/**
 * 两个 host 的地址、当前在哪个 host 上，以及跨 host 链接怎么拼。
 *
 * ⚠ 比的是 host 不是 origin：nginx 转发时协议靠 `X-Forwarded-Proto` 传，
 *   本地或者反代配歪时服务端看到的可能是 http，比 origin 会误判成「跨 host」——
 *   那时候站内每个链接都变成绝对地址，能用但很丑。
 */
export function useSiteHosts() {
  const config = useRuntimeConfig()
  const requestUrl = useRequestURL()

  const siteUrl = trimOrigin(config.public.siteUrl)
  const buyerUrl = trimOrigin(config.public.buyerUrl)
  const siteHost = hostOf(siteUrl)
  const buyerHost = hostOf(buyerUrl)

  /** 两个都配了、而且不一样，域名拆分才生效。本机开发不配 buyerUrl，恒为 false */
  const enabled = siteHost !== '' && buyerHost !== '' && siteHost !== buyerHost

  const onBuyerHost = enabled && requestUrl.host === buyerHost

  /**
   * 把一个商城路径变成「在当前页面上点了能到对地方」的地址。
   *
   * - 已经在商城主域上 → 原样返回，走客户端路由，快
   * - 在买家域上 → 返回**主域的绝对地址**，整页跳过去
   *
   * ## ⚠ 为什么必须绝对地址，不能靠中间件兜底
   *
   * `server/middleware/domain-split.ts` 只在**服务端**跑。而 `<NuxtLink to="/products">`
   * 在浏览器里是 vue-router 接管的客户端跳转——**根本不发请求到服务器**，
   * 中间件那条 301 永远不会触发。
   *
   * 结果是：在 buyer. 上点「继续购物」，商城页面就直接渲染在 buyer. 域下。
   * 地址栏是买家域、canonical 指向主域、购物车读的是买家域那份（空的）——
   * 页面看起来完全正常，全是错的。
   *
   * NuxtLink 拿到 `https://` 开头的地址会自动渲染成普通 `<a>`，所以调用方
   * 不用额外传 `external`。但**脚本里跳转**要用
   * `navigateTo(href, { external: href !== 相对路径 })`，
   * 不能用 `router.push` / `router.replace`——vue-router 只认当前 origin，
   * 它会把 `https://buyer.…/account` 当成站内路径，地址栏变成 `/https://buyer.…`。
   */
  function shopUrl(path: string): string {
    if (!onBuyerHost || siteUrl === '') {
      return path
    }
    return siteUrl + path
  }

  return {
    enabled,
    /** 当前页在买家中心那个域上 */
    onBuyerHost,
    /** 商城主域的地址，没配就是空串（调用方据此退回相对路径）。canonical 用的也是它 */
    siteUrl,
    /** 买家中心的地址，同上 */
    buyerUrl,
    /**
     * 两个 host 共用的 Cookie 作用域，`undefined` = 不设 Domain（host-only）。
     *
     * 取的是**商城主域的主机名**：`kangchen.vclinker.com` 这个 Domain 值
     * 按 RFC 6265 同时覆盖它自己和它的子域，于是 buyer. 上选的币种回到主域也还在。
     *
     * ⚠ 只在两个地址都配了（= 真的分了 host）时才给值。本地开发一个 origin，
     *   设成 `localhost` 虽然合法但没有意义，还会让「Cookie 到底种在哪」变复杂。
     *
     * ⚠ 不能带端口——Cookie 的 Domain 属性里放端口是无效值，浏览器整条丢掉。
     *
     * ⚠ **这只给前台自己那些非敏感 Cookie 用**（币种偏好那类）。买家会话
     *   `BUYER_SESSION` 是**另一回事**：它永远 host-only，两个域各持一份装同一个
     *   令牌，靠 handoff 交接（`useBuyer.afterLogin`）。放宽它的 Domain 会让会话
     *   跟着每一张图片请求发到 `file.` 那个 CNAME 到腾讯云 COS 的域上去，
     *   理由见后端 `BuyerSession` 的类注释。
     */
    cookieDomain: enabled ? (siteHost.split(':')[0] || undefined) : undefined,
    shopUrl
  }
}

/**
 * 站内链接：**同 host 给相对路径，跨 host 给绝对地址**，语言前缀照 {@link useLocalePath} 拼。
 *
 * 直接替掉页面里的 `useLocalePath()` 就行——同 host 时两者返回值完全一样，
 * 所以不需要在每个调用点判断「这个目标跨不跨 host」。
 *
 * ⚠ 跨 host 的那种返回值给 `<NuxtLink :to>` 是正确的：NuxtLink 认出绝对地址会渲染成
 *   普通 `<a href>`、走整页跳转。反过来**不能**拿它去 `router.push` / `router.replace`
 *   ——理由见 {@link useSiteHosts} 里 `shopUrl` 那段。要在脚本里跳转用
 *   `navigateTo(href, { external: true })`。
 */
export function useLocaleHref() {
  const localePath = useLocalePath()
  const siteHref = useSiteHref()
  return (path: string) => siteHref(localePath(path))
}

/**
 * 同 {@link useLocaleHref}，但**不碰语言前缀**——给已经是完整路径的值用。
 *
 * 典型场景是 `?redirect=/ja/cart` 这种从 URL 里读出来的路径：它自带前缀了，
 * 再过一次 `localePath` 会拼成 `/ja/ja/cart`。
 *
 * ⚠ 传进来的必须是**站内路径**。这里只判「该挂哪个 host」，<b>不校验路径本身</b>——
 *   看到一个绝对地址会原样返回。所以拿用户可控的值（`?redirect=` 那种）调它之前
 *   必须先过一道白名单，`//evil.com`、`/\evil.com`（浏览器把 `\` 规范化成 `/`）、
 *   带 tab / 换行的串都要挡掉，否则就是一个开放重定向。
 *   现成的实现见 `app/pages/login.vue` 的 `safeRedirect`。
 */
export function useSiteHref() {
  const { siteUrl, buyerUrl } = useSiteHosts()
  const here = useRequestURL()

  return (path: string) => {
    const want = isBuyerPath(path) ? buyerUrl : siteUrl
    if (!want || hostOf(want) === here.host) return path
    return want + path
  }
}
