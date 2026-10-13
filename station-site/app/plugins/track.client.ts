/**
 * 浏览量埋点。每次路由变化往 `POST /openapi/site/pv` 发一条。
 *
 * 后端在 station-console 的 `OpenApiController.reportPageView`，表结构和全部设计决定在
 * `sql/station/migration/2026-09-08-pageview.sql` 的文件头里。
 *
 * ## ⚠ 为什么是 .client.ts，而且必须是
 *
 * 文件名的 `.client` 后缀让这个插件**只在浏览器里跑**。这不是优化，是整套统计的前提。
 *
 * 前台是 SSR：首屏渲染跑在 Node 里，那里发出去的请求源 IP 是服务器自己
 * （127.0.0.1）、没有浏览器 UA、Set-Cookie 落在 Node 进程上而不是访客的浏览器里。
 * 如果这个插件在服务端也跑，结果是：
 *
 *   · 每个访客的 IP 都是 127.0.0.1，地域统计全废；
 *   · UV 永远等于 PV（每次都是新 Cookie）；
 *   · 而 **PV 那个数字看起来完全正常** —— 最坏的一点，整份报表会安安静静地是错的。
 *
 * 同样的理由，后端也刻意**没有**在 `/openapi/site/products/{code}` 里顺手计数：
 * 那个接口首屏是服务端调的。见 PageViewService 的类注释。
 *
 * ## ⚠ 为什么用 sendBeacon 而不是 fetch
 *
 * `navigator.sendBeacon` 是「发出去就不管了」：不占用页面的网络优先级、
 * 页面正在卸载时也发得出去、失败了不会在控制台里留下红字。
 * 埋点绝不该让访客等，也绝不该看起来像个错误。
 *
 * 代价是**拿不到响应**——状态码、返回体都读不到。所以后端那边一律回 204，
 * 要看有没有在工作只能查库。
 *
 * ## ⚠ 页面类型按 route.name 判，不按路径
 *
 * 因为这个站是路径前缀多语言（`/ja/products/G001`），按路径判要先剥语言段，
 * 而语言列表是运行时才知道的（每个站在 ERP 里自己配）。route.name 天然不受影响：
 * nuxt.config.ts 里那个 `pages:extend` 给带前缀的路由起名 `lang-<原名>`，
 * 剥掉这个前缀就是同一个页面。
 */

import type { RouteLocationNormalized } from 'vue-router'

/**
 * 页面类型映射：route.name（已剥掉 lang- 前缀）→ 后端认的 pagetype。
 *
 * ⚠ 右边那些值必须在后端 `PageViewService.PAGE_TYPES` 白名单里。
 *   报一个不在白名单里的值不会报错，后端会记成 `other`——那个页面的浏览量
 *   会消失在 other 里，而没有任何迹象。加页面类型时两边一起改。
 */
const PAGE_TYPES: Record<string, string> = {
  index: 'home',
  products: 'list',
  'products-code': 'detail',
  cart: 'cart',
  checkout: 'checkout',
  // 买家自己那几个页面归成一类：分开看没有意义，而合起来能回答
  // 「有多少人真的在用账号功能」
  account: 'account',
  orders: 'account',
  'orders-id': 'account',
  login: 'account',
  register: 'account',
  'forgot-password': 'account',
  'reset-password': 'account',
  'verify-email': 'account'
}

/**
 * 要不要尊重浏览器的「别追踪我」信号。
 *
 * ⚠ 开着（默认）会让统计数字偏低一点——装了隐私插件、或者在浏览器里开了
 *   Global Privacy Control 的访客不会被统计到。
 *
 * 为什么默认开：这个站面向境外买家（尤其欧盟），而 GPC 在加州和几个欧盟成员国
 * 已经被当作有法律效力的「拒绝」表示。少几个数字换一个站得住的立场，值。
 *
 * 真要关就把这一行改成 false，改完前端要重新打包上传。
 */
const RESPECT_PRIVACY_SIGNALS = true

export default defineNuxtPlugin(nuxtApp => {
  const router = useRouter()
  const config = useRuntimeConfig()

  if (RESPECT_PRIVACY_SIGNALS && optedOut()) {
    return
  }

  /**
   * 只在「站点自己的域名」上发埋点。
   *
   * ⚠ 判据用 `NUXT_PUBLIC_SITE_URL`，**不是写死某个 host**。写死的话，哪天域名
   *   再变一次，那行代码就成了一个静默的错误来源——而这个文件里所有的错都是静默的。
   *
   * ## 为什么需要它
   *
   * `SITE_VISITOR` 是 host-only 的，只在设置它的那个域名上有效。前台拆成
   * 主域（商城）和 `buyer.` 子域（我的订单、账号资料）之后，同一个人在主域一个 id、
   * 在 `buyer.` 上另一个 id——**每个查订单的买家都会被永久算成两个访客**。
   * 不是搬迁那一次的断层，是从此每天都在多算。
   *
   * 设计见 `docs/设计稿/2026-09-08-独立站域名拆分.md`。
   *
   * 代价是 `buyer.` 上那几个页面（账号、订单）的浏览量不再进统计。可以接受：
   * 私密页面，`pagetype: 'account'` 这一档本来也没人拿来做决策。
   * `/login`、`/register` 留在主域，登录漏斗不受影响。
   *
   * ## ⚠ 取不到 siteUrl 就放行
   *
   * 统计少记几条可以接受，静默停止不行——和这个文件里其他几处失败处理是同一个取舍。
   * 本机开发不配 `NUXT_PUBLIC_SITE_URL`，走的就是这条放行分支，行为和现在完全一样。
   *
   * 判一次就够，不放进 `send()` 里：host 在整个页面生命周期里不会变。
   */
  const siteHost = (() => {
    try {
      return new URL(config.public.siteUrl).hostname
    } catch {
      return ''
    }
  })()
  if (siteHost && siteHost !== window.location.hostname) {
    return
  }

  /*
   * ⚠ 装修预览页不上报。
   *
   * 预览就开在主域上（`<主域>/?__preview=1`），上面那道 host 判断拦不住它。
   * 不挡的话，商家改一版装修点几次预览，浏览量统计里就多几条 home——
   * 而那是他自己，不是客户。这一档数据是拿来做决策的，掺自己的访问就废了。
   */
  if (new URLSearchParams(window.location.search).get('__preview') === '1') {
    return
  }

  /**
   * 已经上报过的那个地址。**去重的唯一依据。**
   *
   * ⚠ 这里原来写的是「上一个地址非空才去重」（`if (previousUrl && ...)`），
   *   那是错的，而且 2026-09-08 上线当天就在线上数据里显形了：
   *
   *     2  home  /  16:35:32.581
   *     3  home  /  16:35:32.589   ← 同一次加载，隔 8 毫秒又发了一条
   *
   *   原因是 `router.afterEach` 有时**先于** `app:mounted` 触发。那一刻
   *   `previousUrl` 还是空串，`previousUrl &&` 把整个守卫短路掉，于是 afterEach
   *   发一条、紧接着 app:mounted 再发一条。**每个访客的首屏都被算了两次。**
   *
   *   而 PV 翻倍这种错看起来完全正常——只是「量比想象的大」。
   *
   *   所以去重不能依赖任何「上一步已经跑过」的假设：只认「这个地址报过没有」，
   *   两个入口谁先谁后都对。
   */
  let lastSentPath: string | null = null

  /**
   * 上一个页面的完整地址，用来当站内跳转的 referrer。
   *
   * ⚠ 不能每次都用 document.referrer。它在整个 SPA 生命周期里**一直是最初那个外部来源**，
   *   每次跳转都报它的结果是：一个从 Google 进来、点了 10 个页面的访客，
   *   会被记成「10 次来自 Google 的访问」。来源报表里 Google 的量凭空翻十倍，
   *   而这个数字看起来只像「SEO 做得真好」。
   *
   *   所以：第一条用 document.referrer（真的外部来源），之后用上一个站内地址。
   *   这也是通行统计工具的做法。
   */
  let previousUrl = ''

  /**
   * ⚠ 两个入口都要留着。
   *
   * `router.afterEach` 在客户端水合时**不一定**会为当前路由触发（跟版本和是否
   * 有重定向有关）。只靠它的话，「直接打开一个商品详情页然后关掉」这种访问可能
   * 一条记录都没有——而那恰好是搜索引擎带来的流量最典型的形状。
   *
   * 只靠 `app:mounted` 也不行：它只跑一次，之后的路由变化就记不到了。
   *
   * 所以两个都挂上，靠上面 `lastSentPath` 去重。别为了「看起来干净」删掉一个。
   */
  nuxtApp.hook('app:mounted', () => {
    send(router.currentRoute.value)
  })

  router.afterEach(to => {
    send(to)
  })

  function send(route: RouteLocationNormalized) {
    // 同一个地址不重复上报。见 lastSentPath 上面那段——这一条挡的是首屏双发
    if (route.fullPath === lastSentPath) {
      return
    }
    // 第一条用真的外部来源，之后用上一个站内地址
    const referrer = lastSentPath === null ? (document.referrer || '') : previousUrl
    lastSentPath = route.fullPath

    // 记完再更新，不然自己就是自己的 referrer
    const here = window.location.href
    previousUrl = here

    // route.name 形如 'products-code' 或 'lang-products-code'
    const name = String(route.name || '').replace(/^lang-/, '')

    const payload = {
      // ⚠ 用 route.path 不用 fullPath：fullPath 带 query string，
      //   而 ?utm_source=x 会让同一个页面在报表里裂成无穷多个「不同页面」。
      //   后端也会再削一次（normalizePath），这里削是为了少传点东西
      path: route.path,
      type: PAGE_TYPES[name] || 'other',
      // 只有详情页有。⚠ 后端会验它在 u_s_sitegoods 里真的存在，不是这里说什么就存什么
      goodsCode: name === 'products-code' ? String(route.params.code || '') : '',
      lang: String(route.params.lang || ''),
      referrer
    }

    const url = `${config.public.apiBase || ''}/openapi/site/pv`
    const body = JSON.stringify(payload)

    try {
      if (navigator.sendBeacon) {
        // ⚠ Content-Type 必须是 application/json——后端是 @RequestBody。
        //   同源请求不走 CORS 预检，所以这个非安全列表的类型在这里没问题。
        //
        //   ⚠ 但如果哪天把 NUXT_PUBLIC_API_BASE 配成了另一个域名（比如直接指
        //     data.kangchen.vclinker.com），这条就会变成跨源：预检会被挡、
        //     Cookie 也不一定带得过去，统计会静默停止。埋点必须走同源相对路径。
        navigator.sendBeacon(url, new Blob([body], { type: 'application/json' }))
        return
      }
      // sendBeacon 用不了（很老的浏览器）就退回 fetch。
      // keepalive 让请求在页面卸载后仍能发完，是 sendBeacon 语义最接近的替代
      void fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
        keepalive: true,
        // 同源，但写明白：Cookie 必须带上，不带的话每次都是新访客
        credentials: 'same-origin'
      }).catch(() => {
        // 埋点失败什么都不做。⚠ 别在这里 console.error——
        // 统计出问题不该让访客的控制台里出现红字，也不该让排查页面 bug 的人被它干扰
      })
    } catch {
      // 同上：埋点的任何异常都不该冒出去
    }
  }

  /**
   * 访客明确表示了「别追踪我」吗。
   *
   * - `globalPrivacyControl` —— GPC，现在还活着的那个标准；
   * - `doNotTrack` —— 老 DNT。已经被大部分浏览器移除了，但装了隐私插件的还会有。
   */
  function optedOut() {
    const nav = navigator as Navigator & {
      globalPrivacyControl?: boolean
      msDoNotTrack?: string
    }
    if (nav.globalPrivacyControl === true) {
      return true
    }
    const dnt = nav.doNotTrack || nav.msDoNotTrack || (window as unknown as { doNotTrack?: string }).doNotTrack
    return dnt === '1' || dnt === 'yes'
  }
})
