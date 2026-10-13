/**
 * 站点数据与语言解析。前台所有取数都从这里走。
 *
 * ## 后端契约
 *
 * 四个接口，全部在 /openapi/site/** 下，匿名访问（一个部署一个站，没有 siteKey 了）：
 *
 *   GET  /info                  站点信息（装修内容、导航、语言列表、商品分类）
 *   GET  /products              商品列表（分页/搜索/分类）
 *   GET  /products/{code}       商品详情
 *   POST /inquiry               提交询盘（唯一的写接口）
 *
 * 返回体统一是 RuoYi 的 { code, msg, data }，code=200 才算成功。
 *
 * ## 语言
 *
 * URL 上的 /ja/ 前缀是**唯一**的语言来源，不看浏览器语言、不存 cookie——
 * 一个 URL 永远对应同一份内容，这是 SEO 的前提（爬虫和用户必须看到一样的东西）。
 *
 * 后端对语言是「不认识就退回默认语言」，所以前台这边要自己判断：URL 里的语言不在
 * 站点开的语言列表里，就是一个不存在的页面，返回 404 而不是悄悄显示默认语言的内容——
 * 否则 /xx/ 这种乱输的地址会变成无限多个内容重复的页面，被 Google 判成垃圾站。
 */

export interface SiteInfo {
  companyName: string
  slogan: string
  logo: string
  seoTitle: string
  seoDescription: string
  /** 站点本币。⚠ 这是**基准**：商品价格就是用它计价的，订单也一律落它 */
  currency: string
  /**
   * 买家能切到哪些显示币种，第一个恒为本币。
   *
   * ⚠ 后端只把「算得出汇率的」放进来——没有汇率的币种在 RateService.ratesFor
   * 那一层就被剔掉了。所以这个列表和 `rates` 的键**永远一致**，前台不用自己再判一次。
   *
   * 只有一个元素（就是本币）时不渲染切换器，和语言切换器「只开一种就不显示」一致。
   */
  currencies?: string[]
  /**
   * 汇率，**已经以本币为基准**，本币自己恒为 1。
   *
   * 前台直接乘：`目标金额 = 本币金额 × rates[目标币种]`。
   * 换算成本币基准这一步在后端做（见 SiteOpenService 里那段注释），
   * 因为算反了的结果仍然是个「看起来像价格」的数字，在前端极难被发现。
   */
  rates?: Record<string, number>
  /** 汇率最后一次成功更新的时间。没拉到过是 null */
  rateTime?: string | null
  lang: string
  defaultLang: string
  langs: string[]
  /**
   * 前台视觉主题（`u_s_siteinfo.theme`）。`classic` = 紫色 + Fraunces（康辰）；
   * 空或其他 = `esva`，黑 + 电光蓝（法国站，也是 site.css 本身的样子）。
   */
  theme?: string | null
  /**
   * 引导式下单（2026-09-28）。开着时顶栏多一个「在线定制」，挂到的商品详情页多一个入口。
   * 整份向导不在这里，另走 `/openapi/site/wizard`（见 `fetchWizard`）——每页都拉一份太重。
   * 老后端没有这个字段，前台当没开。
   */
  wizard?: { enabled: boolean; title?: string; goods?: string[] }
  /**
   * 顶栏导航。
   *
   * ⚠ 装修 JSON 里它是**两半**存的：有几条、指向哪、什么顺序在共享层
   * （`nav: [{id, url}]`，所有语言共用），每条的名字在语言层按 id 存（`navText`）。
   * 送到前台之前后端已经拼好（SiteOpenService#compose），所以这里还是老形状，
   * 只是多一个 `id` 可以当渲染的 key 用。`highlights`、`capabilities`、
   * `about.stats`、`reach.stats` 和 `hero.slides` 都是同一个套路。
   */
  nav: Array<{ id?: string; title: string; url: string }>
  hero: {
    /** 整组共用的大标题。某一张幻灯片没填自己的标题时用它 */
    title: string
    subtitle: string
    /** 大标题上面那个小圆标。留空时前台拿 slogan 兜底 */
    eyebrow?: string
    /** 两个按钮上的字。留空用前台内置的那套（按访客语言切） */
    btn1?: string
    btn2?: string
    /**
     * 两个按钮点了去哪儿（2026-09-13 起可配）。留空 = 原来那两个目的地：
     * 主按钮去商品列表、次按钮跳本页 `#inquiry`。
     *
     * ⚠ 在**共享层**（所有语言共用一个地址），和上面 btn1/btn2 那两个文案分开存，
     *   这也是共享层和语言层「同一个父键底下必须各用各的键名」那条规矩的一个例子。
     *
     * ⚠ 值会原样进 href，读它必须过 index.vue 的 `heroLink()`——那里挡掉
     *   `javascript:` 这类写法。别在别处直接 `:href="site.hero.btn1Url"`。
     */
    btn1Url?: string
    btn2Url?: string
    /** 最老的存法：单张背景图。2026-09-10 之前的数据只有这个 */
    image?: string
  /** 这个规格自己的 3D 模型（2026-10-09）；没有 = 用商品主模型 */
  model3d?: string
  /** 这个规格自己的标注；null = logo 跟随主模型。交给 marks.js 的 parseMarks */
  model3dMarks?: unknown
    /** 2026-09-10 那版：一组背景图，顺序就是轮播顺序 */
    images?: string[]
    /**
     * 现在的存法（2026-09-11 起）：幻灯片。三种都要认，见 index.vue 的 heroSlides。
     *
     * ⚠ 2026-09-21 起每张多一个可选的 `video`：填了就用视频当这一屏的背景，
     * `image` 退成它的封面（视频下载出来之前、以及访客勾了「减少动态效果」时显示的
     * 都是封面）。老数据里没有这个键 = 还是一张静态图，行为不变。
     *
     * ⚠ 库里它是**两半**存的：`id` 和 `image` 在共享层（有几张、什么顺序、哪张图，
     * 所有语言共用），`title` / `subtitle` 在语言层按 id 存。
     * **拼接是后端做的**（SiteOpenService#compose），所以前台这边只会看到拼好的一份，
     * 不需要、也不该自己按语言去拼——那等于把「缺译文退回默认语言」那套规则实现两遍。
     */
    slides?: Array<{ id: string; image?: string; video?: string; alt?: string; title?: string; subtitle?: string }>
  }
  /**
   * 四块的「区块标题 / 一句话说明」。**可选**：没配时前台用自己内置的那套文案
   * （按访客语言切），所以读它一律用 `site.bento?.title`。
   */
  bento?: {
    title?: string
    lead?: string
    /**
     * 三张卡里那张粉色数字卡。**留空时退回 `about.stats[0]`**——
     * 2026-09-11 之前只有那一条路，老站点都是那么配的。
     */
    stat?: { value?: string; label?: string }
  }
  products?: { title?: string; lead?: string }
  caps?: { title?: string; lead?: string }
  inquiry?: { title?: string; lead?: string }
  /** 三栏特色。图标不分语言、文字分语言，后端拼好的（见上面 nav 那条） */
  highlights: Array<{ id?: string; icon: string; title: string; desc: string }>
  capabilities: Array<{ id?: string; icon: string; title: string; desc: string }>
  /** `imageAlt` 是配图的图片说明，分语言；留空时前台退回 `about.title` */
  about: { title?: string; content?: string; image?: string; imageAlt?: string; stats?: Array<{ id?: string; value: string; label: string }> }
  contact: { address?: string; phone?: string; email?: string; whatsapp?: string }
  /**
   * 已上架商品用到的分类，平铺的原串（`加压疗法/腿部系统` 这种）。
   *
   * ⚠ 这里永远是**原串**，不分语言——它是筛选键（`?cat=`）和层次的载体。
   * 要显示给人看的名字在 {@link categoryNames} 里。
   */
  categories: string[]
  /**
   * 分类的当前语言显示名，键是 catkey（见 `app/lib/categories.ts` 的 `CategoryNames`）。
   *
   * **可选**：老后端没有这一块。没有、或者某个分类没译名时，前台显示原串，
   * 不会出空标签。当前语言就是站点默认语言时后端返回空对象。
   */
  categoryNames?: Record<string, string>
  /**
   * 站长工具配置（`u_s_seo`）。
   *
   * **可选**：老库没跑 `2026-09-08-seo-tools.sql` 时后端不会有这一块，
   * 那时候一切按这个模块存在之前的样子走。所以读它一律用 `site.seo?.xxx`。
   *
   * 页面这一侧只用得上 `noindexAll` 一个（见 `useSeo.ts`）。其余几项是给**服务端**
   * 用的，而且服务端不走这个对象——`robots.txt` / `sitemap.xml` / 注入 `<head>` 的
   * Nitro 插件走 `server/utils/seoConfig.ts`，那边有 60 秒缓存和一层输出前的
   * 格式校验。⚠ 别在页面里拼统计脚本：那样就绕过了那一层校验。
   */
  seo?: {
    /** 1 = 整站禁止收录。⚠ 和站点状态是两件事：那个管前台开不开，这个管收不收录 */
    noindexAll?: number
    sitemapOn?: number
    sitemapMax?: number
    homeFreq?: string
    goodsFreq?: string
    disallowPaths?: string | null
    robotsExtra?: string | null
    googleVerify?: string | null
    bingVerify?: string | null
    baiduVerify?: string | null
    ga4Id?: string | null
    gtmId?: string | null
    baiduHmId?: string | null
  }
  /**
   * 首页那张世界地图的数据（总部 + 目的地 + 几个数字）。
   *
   * 2026-09-10 起管理台「首页装修 → 全球布局」能配了，字段名就是当初在这里
   * 约定好的那套。没配的时候 GlobalReach 组件用自带的默认地点，首页不会空一块。
   */
  reach?: {
    hq?: { name: string; coords: [number, number] }
    destinations?: Array<{ id?: string; name: string; coords: [number, number] }>
    stats?: Array<{ id?: string; value: string; label: string }>
    title?: string
    lead?: string
  }
  /**
   * 承诺横条（2026-09-21 起）。Banner 底下那一条「保修 / 发货 / 退货 / 支持」。
   *
   * 和 {@link highlights} 是两件事，别合并：那三栏讲的是「我们凭什么做得好」，
   * 要读；这一条讲的是「买了之后你有什么保障」，是扫一眼就过的。消费品站把它
   * 紧贴 Banner 放，是因为访客的第一个疑问是「出了问题怎么办」，不是「你厂多大」。
   *
   * ⚠ 这里写的每一条都是**对外承诺**，会被买家拿来主张权利。没有对应售后流程的
   *   条目（分期付款、当日达）不要填——写了做不到比不写糟得多。
   */
  trust?: { items?: Array<{ id?: string; icon: string; title: string; desc?: string }> }
  /**
   * 顾客评价（2026-09-21 起）。
   *
   * ⚠ `score` / `count` 是**共享层**的（数字不分语言），评价正文分语言。
   *   两边在同一个 `reviews` 键底下各用各的子键，这是 overlay 只合一层的要求
   *   （见 SiteOpenService#siteContent 里「同名键怎么合」那一段）。
   *
   * ⚠ **不出 schema.org 的 AggregateRating**。那个富摘要要求评分来自站点上真实、
   *   可核查的评价，而这里的数字是商家在装修里手填的。Google 对「自报评分」的处理
   *   是整站结构化数据降权甚至人工处罚——多一颗星换整站信誉，不划算。
   *   页面上照常显示，只是不向搜索引擎声明。
   */
  reviews?: {
    title?: string
    lead?: string
    /** 平均分，手填的字符串（`4.8`）。留空整个评分头不渲染，只显示评价卡 */
    score?: string
    /** 评价条数，手填（`9363`）。和 score 一起显示 */
    count?: string
    items?: Array<{ id?: string; stars?: number; name?: string; role?: string; body?: string }>
  }
  /**
   * 合作方与代言人（2026-09-21 起）。运动队、俱乐部、运动员。
   *
   * `logo` 在共享层（图片只存一份），`name` / `role` 分语言——
   * 球队名在不同语言里确实不一样（「法国国家队」/「Équipe de France」）。
   */
  partners?: {
    title?: string
    lead?: string
    items?: Array<{ id?: string; logo?: string; name?: string; role?: string }>
    /**
     * 合作方下面那排竖屏短视频（2026-09-30）。视频和封面在共享层，说明分语言
     * （语言层 `partnerVideoText[id].caption`，后端 compose 拼好了才下发）。
     */
    videos?: Array<{ id?: string; video?: string; poster?: string; caption?: string }>
  }
  /**
   * 常见问题（2026-09-21 起）。
   *
   * 这一块是**有结构化数据的**（schema.org FAQPage，见 index.vue）：问答对是
   * Google 明确支持富摘要的少数几种内容之一，而且和上面 reviews 那条相反——
   * 问答是商家自己的陈述，不存在「自报他人评价」的问题，声明它是正当的。
   *
   * ⚠ 共享层只存 `{id}`（有几条、什么顺序），问和答全在语言层的 `faqText` 袋子里。
   */
  faq?: {
    title?: string
    lead?: string
    items?: Array<{ id?: string; q?: string; a?: string }>
  }
  /**
   * 首页区块从上到下的顺序（2026-09-10 起）。
   *
   * 值是区块的键：`hero` / `trust` / `bento` / `products` / `caps` / `reviews` /
   * `partners` / `about` / `faq` / `reach` / `inquiry`。
   * 没配、或者配成空数组，就按 index.vue 里那份 DEFAULT_ORDER 排。
   *
   * ⚠ 不认识的键会被丢掉，**漏掉的键会补在最后**——以后新增一种区块时，
   * 老站点存的 order 里没有它，靠这条它才会自动出现，而不是从此消失。
   */
  order?: string[]
  /** 关掉了哪几块。键同 order。整块不渲染，连标题都不出现 */
  off?: string[]
  /**
   * 每块自己的小设置。分语言的东西不在这儿（那些在语言层）——
   * 这里只放「摆法」这类不分语言的参数。
   */
  blocks?: {
    /**
     * Banner 的两个版式维度：文案靠左还是居中、整屏还是半屏。
     * 它不像别的块是一个 variant——这两件事互相独立，合成四个值只会更难读。
     */
    hero?: { align?: string; height?: string }
    /** 能力介绍里哪一张卡填成实心紫。-1 = 都不强调。默认第 2 张（下标 1） */
    caps?: { accent?: number; variant?: string }
    /**
     * 首页显示几个商品。默认 6：三列布局下正好两行满。
     * variant：`grid` 三列（默认）/ `wide` 两列大图 / `carousel` 横向轮播（2026-09-30，照 getjolt.fr）
     */
    products?: { limit?: number; variant?: string }
    /**
     * 其余几块的版式。取值见 index.vue 的 variantOf 那几行，
     * **必须和 station-shared/DecorateForm.vue 的 BLOCK_VARIANTS 一致**。
     */
    bento?: { variant?: string }
    about?: { variant?: string }
    reach?: { variant?: string }
    inquiry?: { variant?: string }
    /** 承诺横条：`bar` 深色细横条（默认）/ `cards` 四张浅色卡 */
    trust?: { variant?: string }
    /** 评价：`cards` 三栏卡片（默认）/ `strip` 一行横向滚动 */
    reviews?: { variant?: string }
    /** 合作方：`named` logo 下带名字和项目（默认）/ `logos` 只排 logo */
    partners?: { variant?: string }
    /** 常见问题：`accordion` 手风琴（默认，同时只开一条）/ `list` 全部展开 */
    faq?: { variant?: string }
  }
}

export interface ProductCard {
  code: string
  title: string
  summary: string
  category: string
  price: number
  currency: string
  moq: number
  unit: string
  featured: boolean
  images: string[]
  videos: string[]
}

/** 详情页上让客户选的组合（参考站叫 Combo）。后端 /products/{code} 返回 */
export interface ProductCombo {
  /** 型号编码。**这是稳定标识**：名称分语言、还会被商家改，只有它不变 */
  code: string
  /** 当前语言下的名称。后端已经做过语言回退，没名字的型号不会返回 */
  title: string
  /** 缩略图完整 URL，可能为空 */
  image?: string
  /** 规格独立模型及其可选三态配置 */
  model3d?: string | null
  model3dMarks?: unknown
  model3dStates?: unknown
  /** 该型号的价格。⚠ 0 = 面议，不是免费 */
  price: number
  currency: string
  /**
   * 二维模式（商品配了规格维度）下，这一条是第一维 / 第二维的哪个取值编码。
   * 平铺模式下不下发；只有一个维度时没有 opt2。
   */
  opt1?: string | null
  opt2?: string | null
}

/** 规格维度的一个取值（如「S/M」「黑色」） */
export interface ProductOptionValue {
  code: string
  /** 当前语言下的名称，后端已做语言回退 */
  name: string
  /** 款式图之类，可能为空。选中时主图切到它（组合自己有图时以组合图为准） */
  image?: string | null
}

/**
 * 规格维度（如「尺码」「款式」），最多两个。
 * 后端只下发至少被一个可选组合用到的取值。
 */
export interface ProductOption {
  code: string
  name: string
  values: ProductOptionValue[]
}

export interface ProductDetail extends ProductCard {
  spec: string
  keywords: string
  /**
   * 组合。**可能是空数组**——大多数商品没有组合可选，那时详情页不渲染这一块。
   *
   * 选中的型号会跟着「加入询价清单」和询盘一起提交；页面上的价格也以型号为准。
   */
  combos?: ProductCombo[]
  /**
   * 规格维度（2026-09-25）。**只有配了维度的商品才有**——那时组合按「尺码 × 款式」
   * 两排选择器显示，每条组合带 opt1/opt2；没有这个字段就是老的平铺一排。
   */
  options?: ProductOption[]
  description: string
  specs: Record<string, unknown>
  /**
   * 三维模型地址（.glb）。2026-09-20 起后端详情接口下发（`u_s_sitegoods.model3d`，
   * 管理台「商品」页从文件库的「模型」标签里挑）；没配就是 null。
   *
   * 商品有模型时详情页才渲染 3D 展示台。没有就不渲染——
   * 给一个阀门配上替身模型（一台按摩仪）比不显示 3D 糟得多。
   * 早期商家把地址填在规格参数里（键名 `model3d` 或 `3d`）的老商品仍然认，
   * 见 app/pages/products/[code].vue 的 modelUrl。
   */
  model3d?: string | null
  /**
   * 3D 标注（logo 贴花 / 屏幕 / 开机键），2026-09-25 起详情接口下发，是对象不是字符串。
   * 不在这里定型：交给 station-shared/model3d/marks.js 的 parseMarks 校验，坏的丢掉。
   */
  model3dMarks?: unknown
  /** 3D 模型状态（多个 glb 切换）：[{ id, name, model? }]，第一个是主模型；不足 2 个时为 null */
  model3dStates?: unknown
  /**
   * 主图视频（图库轮播的第一格）。2026-09-22 起后端详情接口下发
   * （`u_s_sitegoods.mainvideo`，管理台「商品」页从文件库的「视频」标签里挑一个）；
   * 没配就是 null，图库第一格仍是第一张图。
   *
   * ⚠ 和 {@link ProductCard.videos} 不是一回事：videos 是详情**底部**那一排视频，
   * 这一个在**顶部图库里**。一个商品两边可以都配。
   *
   * 封面后端不下发——前台直接拿 `images[0]` 当 poster。
   */
  mainVideo?: string | null
}

/** 当前 URL 段里的语言，没有前缀时返回空串（= 用站点默认语言） */
export function useUrlLang() {
  const route = useRoute()
  return computed(() => String(route.params.lang || ''))
}

/**
 * 拼一个带语言前缀的站内地址。
 *
 * 默认语言**不带前缀**（kangchen.com/ 而不是 kangchen.com/zh/）：根地址是权重最高的
 * 那一个，让它承载默认语言，比让它 302 到 /zh/ 更好。
 */
export function useLocalePath() {
  const lang = useUrlLang()
  return (path: string) => {
    const clean = path.startsWith('/') ? path : `/${path}`
    return lang.value ? `/${lang.value}${clean === '/' ? '' : clean}` : clean
  }
}

function apiBase() {
  const config = useRuntimeConfig()
  // 服务端直接打后端（生产上是内网 127.0.0.1:8080，不绕公网）；
  // 浏览器侧用 public.apiBase，留空则是相对路径，由 nginx 转发
  return import.meta.server ? config.apiBase : (config.public.apiBase || '')
}

/** 统一拆 RuoYi 的 { code, msg, data } 信封 */
/**
 * 浏览器里当前的站点语言，给买家侧的 fetch 封装（useBuyer / useOrders / useChat）当请求头用：
 * 后端 SiteLangInterceptor 读 X-Site-Lang，报错时按它翻（BuyerText）。
 *
 * 模块级变量而不是 useState：那些 fetch 多在点击事件里发，那时候拿不到 Nuxt 实例。
 * 只在客户端赋值——服务端一个进程服务所有请求，模块级变量在那边会串。
 */
let clientSiteLang = ''
export function siteLangHeaders(): Record<string, string> {
  return clientSiteLang ? { 'X-Site-Lang': clientSiteLang } : {}
}

async function call<T>(path: string, query: Record<string, unknown>): Promise<T> {
  // ⚠ 这里原来要拼一个 siteKey 上去，还有一大段「没配 siteKey」的报错兜底。
  //
  // 都删了。docs/独立站与ERP的边界.md §7.2：sitekey 那一列删掉——
  //
  //     站点前端拿它调 ERP 的 /openapi/site/** 限定数据范围用。
  //     现在前端取自己的后端、一个部署一个站，没有范围要限定。
  //
  // 前台现在打的是**同一台机器上**的站点后端（station-console），
  // 那个后端只连本站的库，物理上就取不到别的站的数据。

  const res = await $fetch<{ code: number; msg: string; data: T }>(`${apiBase()}/openapi/site${path}`, {
    query: { ...query }
  })
  if (res?.code !== 200) {
    // 同上：短标识进 statusMessage，后端原话进 message。后端的 msg 是中文，
    // 而 statusMessage 对应 HTTP 状态行（只该放 ASCII），塞中文进去还会触发 h3 告警。
    // 原话是排障的关键线索，所以不能丢——留在 message 里，由 useSiteInfo 的诊断日志读出来。
    // 页面上不显示它（502 走 app/error.vue 的笼统文案）。
    throw createError({
      statusCode: 502,
      statusMessage: 'Upstream error',
      message: res?.msg || '站点数据获取失败'
    })
  }
  return res.data
}

/**
 * 站点信息。每个页面都要用（顶栏、页脚、SEO），所以用 useAsyncData 按语言缓存，
 * 同一次渲染里不会重复请求。
 */
/**
 * classic 主题的标题字体 Fraunces（改版前那套）。只有 classic 站才加这条 <link>；
 * Archivo + Inter 那条在 nuxt.config.ts 里，所有站都有（Inter 是正文字体，两套共用）。
 */
const CLASSIC_FONTS =
  'https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600;9..144,700&display=swap'

/** 认得的主题。空或认不出来一律 esva——那是 site.css 本身那套，不需要额外样式 */
function siteTheme(site: SiteInfo | null | undefined): 'esva' | 'classic' {
  return site?.theme === 'classic' ? 'classic' : 'esva'
}

export async function useSiteInfo() {
  const lang = useUrlLang()

  // ⚠ 必须在 await 之前取。useRuntimeConfig / useRequestURL 这类要拿 Nuxt 实例的
  //   composable，只能在同步的 setup 阶段调；一旦 await 过，实例就找不到了，报的是
  //   「A composable that requires access to the Nuxt instance was called outside of...」。
  //
  //   这里踩过一次：下面那段错误诊断本来直接调 useRuntimeConfig()，结果**诊断代码自己抛异常，
  //   把真正的失败原因盖掉了**——页面上看到的是一句 Nuxt 内部错误，而不是「站点暂时不可用」。
  //   诊断代码尤其不能自己出错，它是出事时唯一的线索。
  const config = useRuntimeConfig()
  const base = apiBase()

  /*
   * ⚠ deep 只在装修预览时打开。
   *
   * Nuxt 4 起 useAsyncData 默认 `deep: false`（内部是 shallowRef），
   * 所以 `data.value` 是一个**普通对象，不是响应式代理**——改它的字段，页面纹丝不动。
   *
   * 平时这样正好：站点信息取回来就不变了，省一层 Proxy。
   * 但预览要的就是「管理台推一份新的过来，页面跟着变」，那一份必须是响应式的。
   *
   * 这个坑是本机点出来的：消息收到了、字段也改了、`h1` 就是不动。
   * 光看代码看不出来——两边都对，错在框架的默认值上。
   */
  const request = useAsyncData<SiteInfo>(
    () => `site-info-${lang.value || 'default'}`,
    () => call<SiteInfo>('/info', { lang: lang.value || undefined }),
    { watch: [lang], deep: useRoute().query.__preview === '1' }
  )

  /*
   * 站点主题挂到 <html data-theme> 上，classic 那套样式靠它生效（assets/theme-classic.css）。
   *
   * ⚠ 必须在 await 之前调 useHead，理由同上面 useRuntimeConfig 那段；
   *   所以先拿 useAsyncData 的返回值，用它的 data ref 做 computed，再 await。
   * 每个页面都经过 useSiteInfo，挂在这里整站都有，不用每个页面各写一遍。
   */
  const theme = computed(() => siteTheme(request.data.value))
  useHead({
    htmlAttrs: { 'data-theme': theme },
    link: computed(() => theme.value === 'classic' ? [{ rel: 'stylesheet', href: CLASSIC_FONTS }] : [])
  })

  const { data, error } = await request

  if (error.value || !data.value) {
    // 走到这里是真的取不到数：站点档案还没建 / 站点状态不是「运行中」
    // （后端对这两种一律回同一句「站点不可用」），或者后端连不上。
    //
    // ⚠ 页面上仍然只显示一句笼统的话——那是给海外买家看的，堆栈和后端地址对他没用，
    //   而且会暴露内部结构。但**服务端日志里要留下真实原因**，否则运维只能看到
    //   「站点暂时不可用」，分不清是后端挂了还是站点被停用了。
    if (import.meta.server) {
      // 先读 message 再读 statusMessage：细节都在 message 里（后端原话、$fetch 的
      // ECONNREFUSED 等），statusMessage 只是个短标识（'Upstream error'）。
      // 顺序反了的话，日志里就只剩标识、拿不到真正的原因。
      const cause = (error.value as any)?.message
        || (error.value as any)?.statusMessage
        || '后端没有返回数据'
      console.error(`[station-site] 取站点信息失败：${cause}`,
        `（apiBase=${base}）`)
    }
    // 页面上的文案不来自这里——503 在 app/error.vue 里走的是写死的那句英文。
    // 这个 message 只进 dev 的日志，所以放中文没问题；statusMessage 留短标识。
    throw createError({
      statusCode: 503,
      statusMessage: 'Site unavailable',
      message: '站点暂时不可用',
      fatal: true
    })
  }

  const site = data.value
  if (import.meta.client) clientSiteLang = site.lang

  // URL 上写了一个这个站没开的语言 = 这个页面不存在。
  // 不能悄悄显示默认语言的内容，那会造出无限多个内容重复的 URL
  if (lang.value && !site.langs.includes(lang.value)) {
    throw createError({
      statusCode: 404,
      statusMessage: 'Language not available',
      message: `这个站点没有 ${lang.value} 语言的页面`,
      fatal: true
    })
  }

  return site
}

/**
 * 商品列表。
 *
 * ⚠ **参数名要在这里翻译一次。** 后端收的是 `keyword` / `category`
 * （OpenApiController#products），前台一直发的是 `kw` / `cat`——名字对不上，
 * 而 Spring 对认不出来的查询参数不报错，只当成没传。症状是：
 *
 *   · 点某个分类，返回的是**全部**商品，页面上却顶着那个分类的标题；
 *   · 搜索框敲什么都返回全部，看着像没筛。
 *
 * 两边都「正常」响应 200，所以这个错在浏览器里看不出来，只有把请求参数和
 * 控制器签名摆在一起才发现。对外的 /openapi/site/** 是已经发布的接口，
 * 改后端参数名等于改契约，所以翻译放在前台这一层。
 *
 * 调用方仍然用 kw / cat：那是这个站里到处在用的名字（URL 上的 query 也是它们）。
 */
export function useProducts(query: {
  lang?: string; kw?: string; cat?: string; page?: number; size?: number
}) {
  return call<{ total: number; page: number; size: number; currency: string; rows: ProductCard[] }>(
    '/products', {
      lang: query.lang,
      keyword: query.kw,
      category: query.cat,
      page: query.page,
      size: query.size
    })
}

export function useProductDetail(code: string, lang?: string) {
  return call<ProductDetail>(`/products/${encodeURIComponent(code)}`, { lang: lang || undefined })
}

/**
 * 引导式下单的向导。**没开返回 null**，不抛——那是正常状态（页面显示「暂未开放」），
 * 不该走 502 错误页。形状见 `app/lib/wizard.ts` 的 `Wizard`。
 */
export async function fetchWizard(lang?: string): Promise<import('~/lib/wizard').Wizard | null> {
  const res = await $fetch<{ code: number; msg: string; data?: import('~/lib/wizard').Wizard }>(
    `${apiBase()}/openapi/site/wizard`, { query: { lang: lang || undefined }, ignoreResponseError: true })
  if (res?.code === 200 && res.data) return res.data
  if (res?.code === 404) return null
  throw createError({ statusCode: 502, statusMessage: 'Upstream error', message: res?.msg || '向导获取失败' })
}

/**
 * 博客文章列表的一条。后端 `/openapi/site/posts` 返回。
 *
 * ⚠ **没有 `code`，只有 `slug`。** 文章的内部编码（`BLOG0000000001`）不下发、
 * 也不出现在任何地址里——博客整个存在的理由就是自然搜索，而 URL 里的关键词
 * 是排名因素之一。详见后端 `SitePost` 的类注释。
 */
export interface PostCard {
  /** 公开地址那一段：/blog/how-to-choose-silicone-tubing */
  slug: string
  title: string
  /** 摘要。卡片正文 + meta description。可能为空（后端不兜底，由页面决定怎么办） */
  summary: string
  category: string
  coverImg: string
  /** 署名。⚠ 为空就不显示作者行——别兜底成公司名，那看着像没填 */
  author: string
  /** 发布时间，`yyyy-MM-dd HH:mm:ss`。⚠ 不是创建时间 */
  publishTime: string | null
  /**
   * 这篇文章**有译文**的语言。
   *
   * ⚠ 详情页的 hreflang 必须用它，**不能用 `site.langs`**：站点开了 8 种语言不等于
   * 这篇文章有 8 个版本。列一个没有译文的语言等于向 Google 承诺一个 404，
   * 而 hreflang 里有一个坏链接，Google 会忽略**整组**声明——
   * 也就是这个站多语言 SEO 的全部价值。
   */
  langs: string[]
}

/** 文章详情。比列表多一个正文 */
export interface PostDetail extends PostCard {
  /**
   * 正文 HTML。
   *
   * ⚠ 已经在**写入侧**过了 `RichTextCleaner` 的白名单
   * （后端 `SitePostService.savePost`），所以页面上 `v-html` 渲染是安全的
   * ——**前提是那一层过滤还在**。和商品详情同一条规矩。
   */
  content: string
}

/**
 * 博客文章列表。
 *
 * ⚠ 参数名的翻译和 `useProducts` 同一个理由：后端收 `keyword` / `category`，
 * 这个站到处用的是 `kw` / `cat`，而 Spring 对认不出来的查询参数**不报错、只当没传**
 * ——症状是「点了分类却返回全部，页面上还顶着那个分类的标题」。
 *
 * ⚠⚠ 这个接口**按语言过滤，没有默认语言回退**（和商品相反）：
 * 某篇文章没有当前语言的译文，它在这一页就是不存在的。
 * 一篇英文长文摆在日语站上对读者没价值，而且会被 Google 判成
 * 「hreflang 说是日语、内容是英文」。理由见后端 `SitePostMapper` 的类注释。
 */
export function usePosts(query: {
  lang?: string; kw?: string; cat?: string; page?: number; size?: number
}) {
  return call<{
    total: number; page: number; size: number
    rows: PostCard[]; categories: string[]
  }>('/posts', {
    lang: query.lang,
    keyword: query.kw,
    category: query.cat,
    page: query.page,
    size: query.size
  })
}

/**
 * 文章详情，按 slug 取。
 *
 * 后端对「文章不存在」「还是草稿」「这个语言没译文」回的是同一句话，
 * 所以这里也不区分——调用方一律当 404。
 */
export function usePostDetail(slug: string, lang?: string) {
  return call<PostDetail>(`/posts/${encodeURIComponent(slug)}`, { lang: lang || undefined })
}

/** 提交询盘。走浏览器侧，所以用 public.apiBase */
export async function submitInquiry(payload: Record<string, unknown>) {
  const config = useRuntimeConfig()
  const res = await $fetch<{ code: number; msg: string }>(
    `${config.public.apiBase || ''}/openapi/site/inquiry`,
    {
      method: 'POST',
      body: payload
    })
  if (res?.code !== 200) {
    throw new Error(res?.msg || '提交失败')
  }
}
