/**
 * robots.txt。动态生成而不是放静态文件，因为 sitemap 那一行要写绝对地址，
 * 而域名是每个部署自己的（以后每个客户带自己的品牌域名）。
 *
 * ## 三层，从硬到软
 *
 *   1. **买家域只挡买家中心那两支** —— 域名判据，代码里写死，不可配。
 *      （曾经是整站 Disallow，那会把老地址的 301 一起挡掉，见下面那段注释）；
 *   2. **基线 Disallow** —— 结构性事实，代码里写死，不可配（下面那一长串）；
 *   3. **站长工具里配的** —— `u_s_seo` 的 disallowpaths / robotsextra / noindexall。
 *
 * ⚠ 第 2 层**故意不搬进库**。购物车、结账、我的询价、登录注册、账号中心不该被收录，
 *   是这个站的结构性事实，不是运营偏好。搬进库的唯一效果，是某天有人把它们删掉，
 *   然后结账页开始出现在搜索结果里。库里那两列是**加**，不是**替换**。
 *
 * ⚠ 取不到配置时按第 1、2 层输出，也就是这个模块存在之前的原样。
 *   后端抖一下不该让 robots.txt 变成 500——那会让爬虫拿不到任何指引。
 */
import { getSeoConfig, disallowList, robotsExtraLines } from '../utils/seoConfig'
import { BUYER_PATHS } from '#shared/utils/buyerPaths'

export default defineEventHandler(async event => {
  const config = useRuntimeConfig(event)
  const origin = (config.public.siteUrl || getRequestURL(event).origin).replace(/\/+$/, '')
  setHeader(event, 'content-type', 'text/plain; charset=utf-8')

  /*
   * 买家域上只挡买家中心那两支，**其余放开**。
   *
   * ⚠ 这里曾经是 `Disallow: /`（整站挡），2026-09-10 改掉。原因是它和域名拆分
   *   设计里「`buyer.` 上的老链接 301 到主域，已收录的商品页权重平滑转过去」
   *   那一条**互相抵消**：
   *
   *     2026-09-05 到 09-09 整个前台在 buyer. 上，Google 收录的商品页地址全是
   *     buyer.<域名>/products/xxx。爬虫**在发请求之前**先读 robots.txt，
   *     整站 Disallow 之后它根本不会再去抓那些地址，于是中间件那条 301
   *     永远不会被看到——老地址不是「转走了」，是「被挡住、然后掉出索引」。
   *
   *   站点搬家的通行做法就是**别在 robots 里挡旧地址**，让 301 被吃到。
   *
   * ⚠ 那两支挡住即可，不会漏数据：买家域上除了 /account 和 /orders，
   *   其余路径全被 domain-split 中间件 301 回主域（这里放开的就是那些 301），
   *   而这两页自己还带 `noindex`（页面里写的），两处都有。
   *
   * ⚠ 前缀写法 `/account` 同时盖住 `/account/xxx`；带语言前缀的那一份靠下面
   *   flatMap 里那条通配版盖住（`Disallow: ` 后面接星号再接路径），
   *   和主域那份 `Disallow: /*` 系列的写法一致。
   *
   * ⚠ **不要在这里加 `Allow: /`。** 有的爬虫是「按顺序、首个匹配生效」而不是
   *   Google 的「最长匹配生效」，`Allow: /` 写在前面会让它把 /account 也当成允许。
   *
   * ⚠ 这一条要在**买家域自己**上返回，所以 domain-split 中间件特意放行了
   *   「最后一段带点」的路径——否则这个请求会先被 301 到主域，
   *   买家域上就永远拿不到自己的 robots.txt 了。
   *
   * ⚠ 这一层在读配置**之前**：它不可配，而且这样买家域上连一次后端请求都不用发。
   *   也不发 Sitemap 那一行——sitemap 只在主域服务（买家域上是 404）。
   */
  if (onBuyerHost(event)) {
    return [
      'User-agent: *',
      ...BUYER_PATHS.flatMap(p => [`Disallow: ${p}`, `Disallow: /*${p}`]),
      ''
    ].join('\n')
  }

  const seo = await getSeoConfig(event)

  /*
   * 整站禁收录。
   *
   * ⚠ 这和「站点状态 = 建设中/已停用」是两件事：那个会让前台整个不对外，
   *   压根走不到这里。这个开关管的是「站开着、对访客正常，但先别收录」——
   *   内容还没铺完就要给客户看的软上线阶段。
   *
   * ⚠ 只有这一行不够，页面上还要有 noindex（见 app/composables/useSeo.ts）：
   *   Disallow 管「别来爬」，noindex 管「爬到了别收」。被外链指到的地址
   *   不会被 Disallow 拦住，那时候靠的是 noindex。两处都要。
   *
   * 也不发 Sitemap 那一行：交一份清单出去、同时说这些都别收，是自相矛盾的。
   */
  if (seo.noindexAll) {
    return ['User-agent: *', 'Disallow: /', ''].join('\n')
  }

  const lines = [
    'User-agent: *',
    'Allow: /',
    // ---- 基线：第 2 层，不可配 ----
    //
    // 询价清单 / 结账 / 我的询价：每个访客各自的页面，收录没有意义，
    // 还会把爬虫的预算花在这上面，而这个站真正要被收录的是商品页。
    //
    // 这几页自己也带 `robots: noindex`（爬到了不收），两处都做：
    // Disallow 管的是「别来爬」，noindex 管的是「爬到了别收」——
    // 被外链指到的地址不会被 Disallow 拦住，那时候靠的是 noindex。
    //
    // ⚠ 前缀写法能同时盖住带语言前缀的那一份（/ja/cart 也匹配 /*/cart）。
    'Disallow: /cart',
    'Disallow: /checkout',
    'Disallow: /orders',
    // 游客的下单回执（pages/order-received/[code].vue）。同上：一人一张单，
    // 收录没有意义，而且地址里带着单号
    'Disallow: /order-received',
    'Disallow: /*/cart',
    'Disallow: /*/checkout',
    'Disallow: /*/orders',
    'Disallow: /*/order-received',
    // 登录那五页 + 账号页同理：不登录看不到内容，收录没有意义。
    //
    // ⚠ 这五页（login / register / forgot-password / reset-password / verify-email）
    //   **就在主域上**，不在 buyer. 上（判据是「不登录就看不到」，登录页反而是
    //   给没登录的人看的；用户 2026-09-08 明确定的，见 shared/utils/buyerPaths.ts）。
    //   所以这几条是在管本域自己，不是替另一个 host 代管。
    //
    // ⚠ /account 和 /orders 在主域上会被 301 到 buyer.。Disallow 一个 301 地址
    //   是有效的：爬虫在发请求之前就先看 robots.txt，根本不会走到那一跳。
    //   买家域自己那份 robots 也挡着同样两支（见上面 onBuyerHost 那一段）。
    'Disallow: /login',
    'Disallow: /register',
    'Disallow: /forgot-password',
    'Disallow: /reset-password',
    'Disallow: /verify-email',
    'Disallow: /account',
    'Disallow: /*/login',
    'Disallow: /*/register',
    'Disallow: /*/forgot-password',
    'Disallow: /*/reset-password',
    'Disallow: /*/verify-email',
    'Disallow: /*/account'
  ]

  // ---- 第 3 层：站长工具里配的额外路径 ----
  //
  // ⚠ 去掉和基线重复的，免得输出里出现两行一模一样的 Disallow。
  //   重复不会让 robots.txt 失效（解析器不在乎），但会让人以为配错了、
  //   然后去动上面那批基线。
  for (const path of disallowList(seo)) {
    const line = `Disallow: ${path}`
    if (!lines.includes(line)) lines.push(line)
  }

  // sitemap 关掉时不发这一行：指向一个返回 404 的地址，比不指更糟
  if (seo.sitemapOn) {
    lines.push(`Sitemap: ${origin}/sitemap.xml`)
  }

  // ---- 自定义追加，放最后 ----
  //
  // 放最后是因为 robots.txt 里 User-agent 段落是有顺序的：要针对某个爬虫单独
  // 写一段，必须在通用段之后。放前面会让那一段被通用段「接管」，
  // 而这种错不报任何错。
  const extra = robotsExtraLines(seo)
  if (extra.length) {
    lines.push('', ...extra)
  }

  return [...lines, ''].join('\n')
})
