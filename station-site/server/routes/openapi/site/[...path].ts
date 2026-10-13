/**
 * 把浏览器发来的 /openapi/site/** 转给 ERP 后端。
 *
 * ## 为什么需要它
 *
 * 前台的取数分两侧：**服务端**（SSR 渲染时）直接打 `NUXT_API_BASE`，走内网；
 * **浏览器侧**（翻页、切分类、提交询盘）用的是相对路径 `/openapi/site/...`。
 *
 * 相对路径在生产上没问题——nginx 里有一条 `location /openapi/site/` 转给 8080，
 * 浏览器发的是同源请求，不用 CORS 也少一次预检。但本地开发没有 nginx，
 * 那个相对路径就打在 Nuxt 自己身上，返回 404：
 *
 *     POST :3000/openapi/site/inquiry  →  404 Page not found
 *
 * 这个路由把这条路补上。生产上 nginx 的 location 优先级更高，请求根本到不了这里，
 * 所以它不会给生产增加一跳；万一哪天 nginx 那条 location 漏配了，它还是个兜底。
 *
 * ## 必须转发真实 IP
 *
 * 后端的 `IpUtils.getIpAddr()` 优先读 `x-forwarded-for`，取不到才用 socket 地址。
 * 如果这里不带上，所有请求在后端看来都来自 127.0.0.1，而询盘接口是
 * `@RateLimiter(count = 5, limitType = IP)`——**每分钟 5 条/IP 会退化成全站每分钟 5 条**，
 * 第 6 个客户就提交不了询盘了。这是一个自己给自己造的拒绝服务。
 *
 * ## 只转 site，绝不转 console
 *
 * 这个文件的路径把范围限死在 `/openapi/site/` 下。另外显式挡掉 `..`：
 * `/openapi/site/../console/redeem` 这种如果被转出去，等于把管理台的 S2S 接口
 * （能读全部站点的数据）从公网捅开一个口子。
 */
export default defineEventHandler(async event => {
  const config = useRuntimeConfig()

  const path = getRouterParam(event, 'path') || ''

  // 目录穿越。h3 一般已经规范化过路径了，但这条线通向「能读全部站点数据」的接口，
  // 值得自己再挡一次——防御深度，不是不信任框架
  if (path.includes('..') || path.includes('\\')) {
    throw createError({ statusCode: 400, statusMessage: 'Bad path' })
  }

  const query = getRequestURL(event).search
  const target = `${config.apiBase}/openapi/site/${path}${query}`

  // 真实客户端 IP。xForwardedFor: true 表示如果前面已经有代理链就沿用它给的，
  // 否则用连接的 socket 地址
  const clientIp = getRequestIP(event, { xForwardedFor: true }) || ''

  return proxyRequest(event, target, {
    headers: {
      'x-forwarded-for': clientIp,
      'x-real-ip': clientIp,
      'x-forwarded-proto': getRequestProtocol(event)
    }
  })
})
