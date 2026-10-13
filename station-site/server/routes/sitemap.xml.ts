/**
 * sitemap.xml。把首页、列表页和**每一个商品**的每一种语言都列出来。
 *
 * 为什么值得做：商品详情页是这个站 SEO 价值最高的东西，但它们只被列表页的分页链接
 * 引用着——爬虫要翻很多页才能发现深处的商品。sitemap 直接把全部地址交出去。
 *
 * 每条 <url> 里还带 xhtml:link 的 hreflang，和页面上的 <link rel=alternate> 一致。
 * 两处都声明是 Google 推荐的做法，页面上的那份负责单页，这里的负责整站。
 *
 * ## 取数上限（2026-09 改成可配）
 *
 * 原来是写死的：`size=60` × 最多 9 页 = 540 条，注释里写着「真超过 500 个商品时
 * 要改成分片 sitemap」。现在上限来自站长工具（`u_s_seo.sitemapmax`，默认 5000），
 * 翻页圈数由它算出来。
 *
 * ⚠ **仍然不做分片。** sitemap 单文件规范上限是 5 万条 URL，而 5000 个商品 ×
 * 8 种语言 ≈ 4 万条，还在规范之内。真要更多才需要索引文件，为一个还不存在的
 * 问题引入那一层不值得。所以 `sitemapmax` 在后端和前端都被夹在 6000 以内。
 */
import { fetchServerSiteInfo, normalizeSeo } from '../utils/seoConfig'

/** 商品接口的每页条数上限，后端 `MAX_PAGE_SIZE` 定死的 */
const PAGE_SIZE = 60

export default defineEventHandler(async event => {
  const config = useRuntimeConfig(event)

  // 买家中心那个域没有 sitemap：它整站 Disallow，一条都不该被收录。
  //
  // 返回 404 而不是 301 到主域的那份：主域的 sitemap 里全是主域的绝对地址
  // （下面那个 origin 取的是 siteUrl），从买家域发一份指向别处的清单出去，
  // 只会让「这个域到底该收录什么」变得含糊。没有就是没有。
  if (onBuyerHost(event)) {
    setResponseStatus(event, 404)
    return ''
  }

  const origin = (config.public.siteUrl || getRequestURL(event).origin).replace(/\/+$/, '')

  /*
   * 一次取回语言列表和站长工具配置。
   *
   * ⚠ 走 fetchServerSiteInfo 而不是自己 $fetch：那个函数带 60 秒缓存，
   *   而 robots.txt 和 head 注入也在用同一份，省掉重复往返。
   *   它取不到数时回 null（不抛异常）——下面按 503 处理。
   *
   * 不再拼 siteKey：一个部署一个站，前台打的是本机的站点后端（§7.2）
   */
  const info = await fetchServerSiteInfo(event)

  if (!info) {
    // 取不到站点信息 = 站点档案没建、或者状态不是「运行中」、或者后端连不上。
    // 这时候不能输出一份空的 sitemap：空清单会让 Google 认为「这个站没有页面」，
    // 比拿不到 sitemap 糟得多。回 503 让它稍后再来。
    setResponseStatus(event, 503)
    return ''
  }

  const seo = normalizeSeo(info.seo)

  /*
   * 站长工具里关掉了 sitemap，或者整站禁收录。
   *
   * ⚠ 禁收录时一律不发，哪怕 sitemapOn=1（normalizeSeo 不管这个，
   *   所以这里两个条件都要判）：交一份清单出去、同时在 robots.txt 里说
   *   这些都别收，是自相矛盾的，而 Google 对这种矛盾的处理没有承诺。
   */
  if (!seo.sitemapOn || seo.noindexAll) {
    setResponseStatus(event, 404)
    return ''
  }

  const def = info.defaultLang || 'en'
  const langs = info.langs?.length ? info.langs : [def]

  /*
   * 翻页把商品编码拉全。
   *
   * ⚠ 圈数由 sitemapmax 算出来，不再是写死的 9。向上取整，至少 1 圈。
   *   5000 / 60 = 84 圈——听起来多，但只在爬虫来要 sitemap 时发生，
   *   而且商品不够时会提前 break（下面那两个条件）。
   */
  const maxPages = Math.max(1, Math.ceil(seo.sitemapMax / PAGE_SIZE))
  const codes: string[] = []

  for (let page = 1; page <= maxPages; page++) {
    const res = await $fetch<{ code: number; data: { rows: Array<{ code: string }> } }>(
      `${config.apiBase}/openapi/site/products`, { query: { size: PAGE_SIZE, page } })
      .catch(() => null)

    if (res?.code !== 200 || !res.data?.rows?.length) break
    codes.push(...res.data.rows.map(r => r.code))

    // 这一页没满 = 已经是最后一页
    if (res.data.rows.length < PAGE_SIZE) break
    // 够了就停。⚠ 判在 break 之后取数之前，免得多打一次后端
    if (codes.length >= seo.sitemapMax) break
  }

  // 上面那个循环可能多拿了一点（最后一页是整页），这里裁到配置的上限
  const picked = codes.slice(0, seo.sitemapMax)

  const urlFor = (lang: string, path: string) =>
    lang === def ? `${origin}${path}` : `${origin}/${lang}${path === '/' ? '' : path}`

  // changefreq 也来自站长工具。首页和列表页一个值，商品详情页另一个——
  // 商品改得比首页少，给它们同一个 daily 会让爬虫的预算花在没变的页面上
  const freqFor = (path: string) =>
    path === '/' || path === '/products' || path === '/blog' ? seo.homeFreq : seo.goodsFreq

  /**
   * 一条 <url>，含它那一组 hreflang。
   *
   * ⚠ `entryLangs` 是**这个地址真的存在的语言**，不一定等于站点开的全部语言：
   *   博客文章是一种语言一种语言攒出来的，没译文的那个语言前缀下它是 404。
   *   列一个不存在的语言 = 向 Google 承诺一个 404，而 hreflang 里有一个坏链接，
   *   Google 会忽略**整组**声明。
   */
  const urlEntry = (path: string, entryLangs: string[], lastmod?: string | null) => {
    const alt = entryLangs
      .map(l => `    <xhtml:link rel="alternate" hreflang="${l}" href="${urlFor(l, path)}"/>`)
      .join('\n')
    // x-default 指默认语言那一份，但前提是它真的存在；否则退到第一个有的
    const xdef = entryLangs.includes(def) ? def : (entryLangs[0] || def)
    return entryLangs.map(lang => `  <url>
    <loc>${urlFor(lang, path)}</loc>
${alt}
    <xhtml:link rel="alternate" hreflang="x-default" href="${urlFor(xdef, path)}"/>
${lastmod ? `    <lastmod>${lastmod}</lastmod>\n` : ''}    <changefreq>${freqFor(path)}</changefreq>
  </url>`).join('\n')
  }

  /*
   * 博客文章。
   *
   * ⚠ 走 /post-sitemap 而不是 /posts。那一条是**按语言过滤**的（它服务的是
   *   「/ja/blog 这一页显示什么」），用它生成 sitemap 会漏掉
   *   「只翻译了日语、没有默认语言版本」的文章——而那种漏的表现是
   *   「文章在站上打得开、Google 却一直发现不了」，最难注意到的一类问题。
   *
   * ⚠ 取不到就当没有文章（catch → 空数组），**不让整个 sitemap 挂掉**：
   *   商品页仍然是这个站 SEO 的主体，为了博客那一段把商品也丢掉是不划算的。
   *   老后端（没有这个接口）会走到这条路上，那时候 sitemap 就是博客上线之前的样子。
   */
  const postRes = await $fetch<{
    code: number
    data: { rows: Array<{ slug: string; langs: string[]; publishTime: string | null }> }
  }>(`${config.apiBase}/openapi/site/post-sitemap`, {
    query: { limit: seo.sitemapMax }
  }).catch(() => null)

  const posts = postRes?.code === 200 ? (postRes.data.rows || []) : []

  /** 文章的 lastmod 用发布时间。⚠ 后端给的是 `yyyy-MM-dd HH:mm:ss`，sitemap 要 ISO 日期 */
  const lastmodOf = (t: string | null) => {
    if (!t) return null
    const d = new Date(t.replace(' ', 'T'))
    return isNaN(d.getTime()) ? null : d.toISOString().slice(0, 10)
  }

  const entries: string[] = [
    // 首页、商品列表页：每种语言都存在
    urlEntry('/', langs),
    urlEntry('/products', langs),
    ...picked.map(c => urlEntry(`/products/${encodeURIComponent(c)}`, langs)),
    // 博客列表页只在有文章的时候列——一个空列表页进了 sitemap，
    // 等于主动请 Google 来收录一个「没有内容」的页面
    ...(posts.length ? [urlEntry('/blog', langs)] : []),
    ...posts.map(p =>
      urlEntry(`/blog/${encodeURIComponent(p.slug)}`, p.langs || [], lastmodOf(p.publishTime)))
  ]

  const body = entries.filter(Boolean).join('\n')

  setHeader(event, 'content-type', 'application/xml; charset=utf-8')
  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
        xmlns:xhtml="http://www.w3.org/1999/xhtml">
${body}
</urlset>
`
})
