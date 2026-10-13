import { fileURLToPath } from 'node:url'

// 独立站前台。SSR，一站一部署。
//
// ## 为什么是 SSR 而不是 SPA
//
// 外贸独立站的流量靠 Google 收录。纯客户端渲染的话，爬虫拿到的是一个空壳，
// 商品页基本不会被收录——那等于把这个站存在的理由去掉了一半。
//
// 也不能用构建时预生成（SSG）：装修页上明写着「保存后刷新站点立刻生效，没有『发布』
// 这一步」，预生成要求每次改商品或装修都重新构建，和那句承诺直接冲突。
//
// 所以是 SSR：服务端取数、返回完整 HTML，内容改完刷新即生效。
// 代价是服务器上要多跑一个 Node 进程，见 README 的部署一节。
//
// ## 站点身份
//
// ⚠ 这里原来靠 NUXT_PUBLIC_SITE_KEY 区分是哪个站——那是「一个 ERP 服务 N 个站」
// 时代的产物：前台带着 key 去 ERP 取数，ERP 按它限定数据范围。
//
// 那个参数删了（docs/独立站与ERP的边界.md §7.2）。现在前台打的是
// **同一台机器上的站点后端**（station-console，本机 8081），
// 那个后端只连本站的库，物理上就取不到别的站的数据——没有范围要限定。
//
// 站点身份由后端自己知道（它读本库的 u_s_siteinfo），前台一个字都不用配。

export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  devtools: { enabled: false },

  runtimeConfig: {
    // 服务端取数用的地址。SSR 时是 Node 进程自己发请求，所以直接打本机：
    // nginx → Node → 127.0.0.1:8081。
    //
    // ⚠ 8081 不是 8080。8080 是 ERP，而前台**不该认识 ERP**——
    //   要点 1：两边后端没有任何互通。填成 ERP 的地址，那些接口在那边已经删了，
    //   得到的是一片 404。
    apiBase: process.env.NUXT_API_BASE || 'http://127.0.0.1:8081',
    public: {
      // 浏览器侧取数用的地址（提交询盘、翻页）。跨根域，走 CORS，不带 Cookie。
      // 留空则用相对路径，由 nginx 转发——生产上推荐这样，省掉一次跨域
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '',
      /**
       * 站点自己的对外地址，也就是**商城主域**。
       *
       * 三处用它：canonical 和 hreflang 的绝对 URL、sitemap 里的地址、
       * 以及埋点的「只在自己域名上发」判据（app/plugins/track.client.ts）。
       *
       * ⚠ 设错的两个症状里只有一个看得见：canonical 会指向错的域（SEO 直接受伤，
       *   `curl -s <主域>/ | grep canonical` 能看出来），而埋点会静默地发到错的
       *   域上或者干脆不发——页面正常、控制台干净、库里就是不进数据。
       *   所以发版后拿 canonical 当哨兵，它对了埋点也就对了。
       */
      siteUrl: process.env.NUXT_PUBLIC_SITE_URL || '',
      /**
       * 买家中心的域名（我的订单、账号资料、地址簿管理）。
       *
       * 前台拆成两个域名：商城在 `siteUrl`，买家中心在这里。
       * 设计见 `docs/设计稿/2026-09-08-独立站域名拆分.md`。
       *
       * ⚠ **留空则整套域名拆分逻辑不生效**，站还是单域，和拆分之前一样。
       *   本机开发就靠这个：不配这个变量，`server/middleware/domain-split.ts`
       *   直接放行，robots/sitemap 也不按域区分。
       *
       * ⚠ 别和 `siteUrl` 设反了。设反的话商城会被 301 到 buyer. 上，
       *   而且 canonical 也跟着反——见上面那条。
       */
      buyerUrl: process.env.NUXT_PUBLIC_BUYER_URL || ''
    }
  },

  app: {
    head: {
      htmlAttrs: { lang: 'en' },
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' }
      ],
      /**
       * 标题字体 Archivo + 正文 Inter，主题里用的就是它们（app/assets/site.css）。
       *
       * **2026-09-21 起从 Fraunces 换成 Archivo。** Fraunces 是衬线体，属于上一版
       * spa 调子；Esva boots 的 logo 是重体压缩斜体（黑底电光蓝），要的是运动无衬线。
       * Archivo 700/800 是最接近的一档，而且它有真斜体——`.eyebrow` 那行标语
       * （"RECOVERY IN MOTION"，照 logo 的做法）用的就是它，不靠浏览器合成倾斜。
       *
       * ⚠ 只取 600/700/800 三档 + 800 斜体。全字重拉下来对首屏没好处，
       *   而这个站的标题只用得上重的那几档，斜体只有 .eyebrow 一处在用。
       *
       * 放在这里而不是 CSS 里 `@import`：`@import` 要等样式文件下完、解析到那一行
       * 才发起请求，白多一次串行往返。
       *
       * `preconnect` 两条都要：css2 那个域发的是样式表，字体文件本身在 gstatic 上，
       * 只连前者等于第二次握手还是要重来一遍。gstatic 那条必须带 crossorigin，
       * 字体是跨源请求，不带的话预连的连接用不上，等于白配。
       *
       * ⚠ 这是这个站唯一一个外部依赖。Google 连不上的网络里（比如国内开发机预览时），
       *   这个 <link> 会拖慢首屏，字体退到 site.css 里配的本地兜底栈——
       *   `display=swap` 保证文字先用兜底字体显示出来，不会白屏等字体。
       *   站本身是给海外买家看的，那边访问 fonts.googleapis.com 没有问题。
       */
      link: [
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        {
          rel: 'stylesheet',
          href: 'https://fonts.googleapis.com/css2?family=Archivo:ital,wght@0,600;0,700;0,800;1,800&family=Inter:wght@400;500;600;700&display=swap'
        }
      ]
    }
  },

  /* theme-classic.css 只在 <html data-theme="classic"> 时生效（康辰站），
     其余站点走 site.css 本身那套。见那个文件的文件头 */
  css: ['~/assets/site.css', '~/assets/theme-classic.css'],

  /**
   * 2026-09-25 加：前台第一次引用仓库根目录的 station-shared/——只引 model3d/marks.js
   * （商品 3D 标注的画法），管理台编辑器用的是同一份，保证「后台摆的位置 = 前台显示的位置」。
   * 设计：docs/superpowers/specs/2026-09-25-product-3d-marks-design.md §4。
   *
   * 只是构建期引用：产物里是打包进来的代码，部署上和管理台互不依赖。
   *
   * ⚠ 两条别名缺一不可，理由和 station-console-ui/vite.config.js 那几条一样：
   *   station-shared 在项目根目录之外，它里面的裸导入 `three` 会从它自己的位置往上找
   *   node_modules，找不到就构建失败；而且必须指到本项目那一份，否则会打进第二个 three，
   *   instanceof 判定全部失效。字符串别名对 `three/examples/jsm/…` 子路径同样生效。
   *   dev server 默认不许读项目外的文件，fs.allow 放开上一级。
   */
  alias: {
    '@station-shared': fileURLToPath(new URL('../station-shared', import.meta.url)),
    three: fileURLToPath(new URL('./node_modules/three', import.meta.url))
  },
  vite: {
    resolve: { dedupe: ['three'] },
    server: { fs: { allow: ['..'] } }
  },

  hooks: {
    /**
     * 一站多语走**路径前缀**：/ja/products、/pt/products/G001。
     *
     * 为什么不是 ?lang=ja：Google 会把查询参数当成同一个页面的变体，多半只收录一种语言，
     * 多语站的 SEO 价值就拿不到了。路径前缀 + hreflang 才能让日语页和中文页被当成两个页面
     * 分别收录、分别推给对应地区的人。
     *
     * 为什么不用 @nuxtjs/i18n：那个模块要求构建时就知道有哪些语言，而这里的语言是**每个站
     * 自己在 ERP 里配的**，运行时才从 /openapi/site/info 拿到。所以自己加一层前缀路由。
     *
     * 语言段用 [a-z-]{2,5} 粗匹配，真正的校验在运行时做（不在站点开的语言里就 404），
     * 见 app/composables/useSite.ts。静态路径（/products）在 vue-router 里的优先级高于
     * 动态段，所以不会被误当成语言。
     */
    'pages:extend'(pages) {
      const prefixed = pages.map(page => ({
        ...page,
        name: `lang-${page.name}`,
        path: `/:lang([a-z-]{2,5})${page.path === '/' ? '' : page.path}`
      }))
      pages.push(...prefixed)
    }
  },

  nitro: {
    // 反代在前面，取真实客户端 IP 用（询盘限流按 IP 算）
    routeRules: {
      '/**': { headers: { 'X-Content-Type-Options': 'nosniff' } }
    }
  }
})
