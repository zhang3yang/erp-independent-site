/**
 * 把请求送到该去的域名上。
 *
 * 商城（首页、商品、购物车、下单、登录注册）在主域，
 * 买家中心（我的订单、账号资料、地址簿管理）在 `buyer.` 子域。
 * 走错域名的 GET 一律 301 到另一边的同一个路径。
 *
 * 哪些路径归买家中心：`shared/utils/buyerPaths.ts`（全站唯一一份，浏览器侧也用它）。
 * 当前请求打在哪个 host 上：`server/utils/hosts.ts`。
 * 设计在 `docs/设计稿/2026-09-08-独立站域名拆分.md`。
 *
 * ## 为什么这套规则在 Nuxt 里，不在 nginx 里
 *
 * 带语言前缀的路由（`/ja/orders`）是 `nuxt.config.ts` 里 `pages:extend` 钩子在
 * 构建时生成的。把路径清单抄一份到 nginx，就有了两份会各自变旧的清单——
 * 以后加一个买家页面，忘了改 nginx，那个页面在两个域上都能开。
 * 放在这里，判断逻辑和生成路由的逻辑在同一个项目里，改一处就够。
 *
 * ## ⚠ 顺带一提：`buyer.` 上的老链接靠这个中间件 301 到主域
 *
 * 拆分之前整个站都在 `buyer.` 上，Google 收录的商品页地址全是
 * `buyer.<域名>/products/xxx`。那些地址会命中「不是买家路径、却打在买家域上」
 * 这一支，301 到主域同路径——SEO 权重平滑转过去，不用额外配 nginx。
 */
import { isBuyerPath } from '#shared/utils/buyerPaths'

export default defineEventHandler(event => {
  const hosts = siteHosts()
  // 没配 buyerUrl 就是单域部署（本机开发、拆分上线前），整套逻辑不生效
  if (!hosts.enabled) {
    return
  }

  const url = getRequestURL(event)
  const currentHost = url.hostname

  // 不是这两个域名之一（直接打 IP、内网探活、配错的 Host）一律不管。
  // 不认识的 Host 该由 nginx 的兜底块处理，不该由这里猜
  if (currentHost !== hosts.site && currentHost !== hosts.buyer) {
    return
  }

  const path = url.pathname

  // ⚠ 接口不能跳。handoff（跨域交接会话）也在这下面，跳转了就废了
  if (path.startsWith('/openapi/site/')) {
    return
  }
  // 构建产物不跳
  if (path.startsWith('/_nuxt/')) {
    return
  }
  // 文件不跳：robots.txt 要在**两个域上各自**返回不同的内容（买家域是 Disallow: /），
  // sitemap.xml 要在买家域上 404，静态资源两边都可能被引用。
  // 判据是「最后一段带点」——比逐个列举文件名可靠，以后加文件不用回来改
  if (lastSegmentLooksLikeFile(path)) {
    return
  }

  const targetHost = isBuyerPath(path) ? hosts.buyer : hosts.site
  if (currentHost === targetHost) {
    return
  }

  // ⚠ 只跳 GET 和 HEAD。
  //
  // 301 一个 POST，浏览器会把它改成 GET 重发，body 丢掉——表现是
  // 「点了提交，什么都没发生」，而且非常难查。宁可 404：至少它会报错。
  if (event.method !== 'GET' && event.method !== 'HEAD') {
    throw createError({ statusCode: 404, statusMessage: 'Not Found' })
  }

  // 用配置里那个地址做基底，而不是自己拼 `https://` + host：
  // 协议和端口跟着配置走，本机用 http、带端口的场景才不会被写死成 https
  const config = useRuntimeConfig()
  const base = (targetHost === hosts.buyer ? config.public.buyerUrl : config.public.siteUrl)
    .replace(/\/+$/, '')

  return sendRedirect(event, base + path + url.search, 301)
})

/**
 * 最后一段像不像文件名。`/products/G001` 不像，`/robots.txt`、`/logo.png` 像。
 *
 * ⚠ 商品编码里要是出现点号，这条会把那个商品页也放行——那不会出错，
 *   只是它在错误的域名上不会被 301 走。目前编码是数字（比如 `12010901`），
 *   真出现带点的编码时这里要改成白名单。
 */
function lastSegmentLooksLikeFile(pathname: string): boolean {
  const last = pathname.slice(pathname.lastIndexOf('/') + 1)
  return last.includes('.')
}
