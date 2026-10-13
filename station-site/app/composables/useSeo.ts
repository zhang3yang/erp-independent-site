import type { SiteInfo } from './useSite'

/**
 * SEO 头。多语站的 SEO 全靠这里的 hreflang + canonical。
 *
 * ## hreflang 是多语言路径前缀唯一的意义所在
 *
 * 光把日语页放到 /ja/ 下还不够——对 Google 来说 /ja/products 和 /products 只是两个
 * 内容不同的页面，它不知道这两个是「同一个页面的不同语言版本」。结果是两页互相抢排名
 * （keyword cannibalization），甚至被判成内容重复。
 *
 * hreflang 就是在告诉它：这几个 URL 是一组，请按访问者的语言/地区分别推。
 *
 * 三条规矩，缺一条 Google 就会忽略整组：
 *   1. **每一组必须互相声明**——日语页上要列出中文页，中文页上也要列出日语页；
 *   2. **必须是绝对 URL**，相对路径无效；
 *   3. **要有 x-default**，指给「语言都不匹配时去哪」，这里指默认语言那一份。
 *
 * ## canonical
 *
 * 同一份内容可能被好几个 URL 拿到（带不带尾斜杠、带不带 ?utm_source=xxx 的推广参数）。
 * canonical 指明「以这个 URL 为准」，把权重收拢到一个地址上。
 */
export function useSiteSeo(site: SiteInfo, options: {
  title?: string
  description?: string
  image?: string
  /** 不带语言前缀的路径，如 /products/G001。首页传 / */
  path: string
  /**
   * `og:type`。默认 `website`，博客文章页传 `article`。
   *
   * 差别不只是一个字符串：Facebook / LinkedIn / WhatsApp 对 `article` 会额外读
   * `article:published_time`，把发布日期显示在分享卡片上。一篇 2024 年的文章
   * 顶着「刚刚」的样子被转发，点进去看到旧内容，对 B2B 读者是负面信号。
   */
  type?: 'website' | 'article'
  /** 文章发布时间（ISO 8601）。⚠ 只在 `type === 'article'` 时输出 */
  publishedTime?: string | null
  /**
   * hreflang 要列的语言。**默认用站点开的全部语言**。
   *
   * ⚠⚠ **博客文章页必须传它**，传这篇文章真的有译文的那几种。
   *
   * 站点开了 8 种语言不等于每篇文章都有 8 个版本——文章是一种语言一种语言攒出来的，
   * 而没有译文的那个语言前缀下这篇文章是 404（后端 inner join，见 SitePostMapper）。
   * 用站点语言列表去拼，就等于向 Google 承诺一批打不开的地址。
   *
   * 而这件事的代价比「少收录一个语言版本」大得多：hreflang 里有一个坏链接，
   * Google 会忽略**整组**声明（见上面那三条规矩），也就是这个站多语言 SEO 的全部价值。
   */
  langs?: string[]
}) {
  const config = useRuntimeConfig()
  const requestUrl = useRequestURL()
  // 配了对外地址就用配的，没配就从当前请求推——本地开发和忘了配的情况下也不会拼出坏链接
  const origin = (config.public.siteUrl || requestUrl.origin).replace(/\/+$/, '')

  const clean = options.path === '/' ? '' : options.path
  const urlFor = (lang: string) =>
    lang === site.defaultLang ? `${origin}${clean || '/'}` : `${origin}/${lang}${clean}`

  const title = options.title
    ? `${options.title} | ${site.companyName}`
    : (site.seoTitle || site.companyName)

  const description = options.description || site.seoDescription || site.slogan || ''

  /*
   * 整站禁收录（站长工具里的开关，u_s_seo.noindexall）。
   *
   * ⚠ 这一处和 robots.txt 里那条 `Disallow: /` 是**两件不同的事**，缺一个都不算数：
   *   Disallow 管「别来爬」，noindex 管「爬到了别收」。被外链指到的地址不会被
   *   Disallow 拦住（爬虫本来就没打算读那个目录的规则），那时候只有 noindex 有用。
   *   这条分工在 server/routes/robots.txt.ts 里已经为 cart/checkout 写过一遍，
   *   全站开关沿用同一条。
   *
   * ⚠ canonical 和 hreflang **照旧输出**，不因为 noindex 就撤掉：
   *   它们没有坏处，而撤掉之后一旦把开关关回来，那几天 Google 眼里的站点结构是断的。
   *
   * ⚠ 只对**调用了 useSiteSeo 的页面**生效——也就是本来要被收录的那些。
   *   cart / checkout / account / login 那几页自己写着 noindex、也不调这个函数，
   *   所以它们不受影响（本来就已经是 noindex 了）。
   */
  /*
   * ⚠ 装修预览页（`?__preview=1`）也一律 noindex。
   *
   * 那是一份**还没保存**的内容，被收录进去等于让搜索引擎记住一个从来没上线过的版本。
   * 而且预览页的地址就是首页地址加一个参数——不挡的话它会和首页争同一份内容。
   */
  const isPreview = useRoute().query.__preview === '1'
  const noindexAll = site.seo?.noindexAll === 1 || isPreview

  const ogType = options.type || 'website'

  /*
   * hreflang 列哪些语言。
   *
   * ⚠ 默认是站点开的全部语言（首页、商品页都成立——那些页面每种语言都存在，
   *   商品文案缺了会回退默认语言）。
   *
   * ⚠ 博客文章页**不成立**，必须由调用方传 options.langs。见那个参数的注释。
   *
   * 兜一层 filter：传进来的语言如果不在站点开的语言里，那个前缀在前台会 404
   * （useSite.ts 里那段语言校验），不能放进 hreflang。后端已经交过一次集了，
   * 这里再交一次——这条链上任何一环漏掉，代价都是整组 hreflang 被忽略。
   */
  const hreflangs = (options.langs?.length ? options.langs : site.langs)
    .filter(l => site.langs.includes(l))

  useHead({
    htmlAttrs: { lang: site.lang },
    title,
    meta: [
      { name: 'description', content: description },
      ...(noindexAll ? [{ name: 'robots', content: 'noindex,nofollow' }] : []),
      // 分享到 WhatsApp / Facebook / LinkedIn 时的卡片。外贸客户很依赖这些渠道传链接
      { property: 'og:type', content: ogType },
      // 文章才有发布日期。⚠ 不是 article 时**一个字都不输出**：
      // og:type=website 配一个 article:published_time 是自相矛盾的标记，
      // 抓取器的处理方式没有承诺
      ...(ogType === 'article' && options.publishedTime
        ? [{ property: 'article:published_time', content: options.publishedTime }]
        : []),
      { property: 'og:title', content: title },
      { property: 'og:description', content: description },
      { property: 'og:url', content: urlFor(site.lang) },
      { property: 'og:site_name', content: site.companyName },
      ...(options.image || site.logo
        ? [{ property: 'og:image', content: options.image || site.logo }]
        : []),
      { name: 'twitter:card', content: options.image ? 'summary_large_image' : 'summary' }
    ],
    link: [
      { rel: 'canonical', href: urlFor(site.lang) },
      // 每种语言一条，外加 x-default。⚠ 用 hreflangs 不是 site.langs——
      // 博客文章页只有部分语言有译文，见上面那段注释
      ...hreflangs.map(lang => ({
        rel: 'alternate',
        hreflang: lang,
        href: urlFor(lang)
      })),
      /*
       * x-default：语言都不匹配时去哪。
       *
       * ⚠ 指向站点默认语言那一份，但**前提是它在 hreflangs 里**。
       *   博客文章可能只有日语版而没有默认语言版本（先写日语版是正常的工作方式），
       *   那时候指向默认语言就是指向一个 404。退到第一个有译文的语言上。
       */
      {
        rel: 'alternate',
        hreflang: 'x-default',
        href: urlFor(hreflangs.includes(site.defaultLang)
          ? site.defaultLang
          : (hreflangs[0] || site.defaultLang))
      }
    ]
  })
}
