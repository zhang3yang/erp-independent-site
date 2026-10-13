/**
 * 购物车 / 下单请求的数据模型。
 *
 * 从 kangwanjia-website-EN 的 data/orders.ts 搬来，但有一处**语义上的改动**，
 * 值得单独说清楚：
 *
 * ## 这不是「支付」，是「下单请求」
 *
 * 上游那个站的结账页有一个假的支付环节：选支付宝/微信/PayPal，画一个装饰性的
 * 二维码，转 1.5 秒的圈，然后订单就变成「已付款」。那在一个纯演示站上无所谓。
 *
 * 这个站是真的会上线、真的有海外买家进来的。做一个看起来能付款、其实什么都没发生的
 * 页面，等于欺骗买家——他会以为钱付了、货在路上。所以结账流程改成外贸站本来的样子：
 *
 *   买家填收货信息和需求 → 提交 → 落一张真实的站点订单（POST /openapi/site/order）
 *   → 商家收到一封带明细的邮件 → 人工回报价单和付款方式
 *
 * 少掉的只有「在线付款」这一步，那一步本来也不存在（后端没有支付网关）。
 *
 * ## 订单存在哪（2026-09-06 改）
 *
 * **服务端**：`u_s_order` + `u_s_orderitem`，后端 `SiteOrderService`。
 * 这一段原来写的是「localStorage，后端没有订单表」，那句话已经不成立——
 * 订单表和整条链路（下单/列表/详情/状态流转/买家自查）都有了。
 *
 * localStorage 还留着，但降级成**本地回执**：下单不要求登录，匿名买家得有个地方
 * 看到自己刚提交了什么。登录之后服务端那份会按单号把它盖掉。
 * 两份怎么合的，见 app/composables/useOrders.ts 的文件头。
 *
 * 所以 status 里那几个状态（confirmed / shipped / delivered）现在**真的会出现**了——
 * 运营在管理台推进订单，买家详情页的时间轴就跟着点亮。
 */

/* ---------------- 金额 ---------------- */

/**
 * 币种符号。站点的 currency 是 ERP 里配的三字母码。
 *
 * 用 Intl.NumberFormat 而不是自己拼 `$${n}`：日元没有小数位、欧元在有些地区
 * 符号在后面，硬拼出来的金额在那些语言下就是错的。
 */
export function formatMoney(amount: number, currency: string, lang?: string) {
  const code = (currency || 'USD').toUpperCase()
  try {
    return new Intl.NumberFormat(intlLocale(lang), {
      style: 'currency',
      currency: code,
      // 大宗外贸报价常有小数（单价 12.50），但整数金额不该显示成 1,200.00
      minimumFractionDigits: Number.isInteger(amount) ? 0 : 2,
      maximumFractionDigits: 2
    }).format(amount)
  } catch {
    // 币种码填错了（ERP 里是人工录的）不能让整页崩掉
    return `${code} ${amount.toFixed(2)}`
  }
}

/**
 * 站点语言 → BCP 47。日期和金额都按它格式化。
 *
 * 始终显式传，不用运行时默认值：SSR 的默认 locale 是服务器的，
 * 和访客看到的语言不一样，会造成 hydration 前后两种写法。
 */
export function intlLocale(lang?: string) {
  const map: Record<string, string> = {
    zh: 'zh-CN', en: 'en-US', ja: 'ja-JP', ko: 'ko-KR',
    es: 'es-ES', pt: 'pt-BR', ru: 'ru-RU', fr: 'fr-FR',
    de: 'de-DE', it: 'it-IT', ar: 'ar', vi: 'vi-VN', th: 'th-TH'
  }
  return map[lang || 'en'] || 'en-US'
}

/* ---------------- 购物车 ---------------- */

export interface CartItem {
  /** 标题、型号名是按哪种语言取的。切语言后购物车页按它判断要不要重新取一遍（useCart.localize） */
  lang?: string
  /**
   * 商品编码。
   *
   * ⚠ **它不再是行标识**：同一个商品选了两个不同型号是两行（价格不同）。
   * 行标识请用 {@link cartLineKey}。
   */
  code: string
  /** 选中的型号编码。没选、或这个商品没有型号时为空 */
  comboCode?: string
  /** 型号名称，显示在标题下面。只做展示——提交时服务端会按 comboCode 重新查 */
  comboName?: string
  title: string
  /** 名称下面那行小字，用商品分类 */
  subtitle: string
  /**
   * 加入时的单价。0 表示**价格面议**——外贸站大量商品是这样，
   * 不能当成免费，也不能算进小计，见 cartTotals()
   */
  price: number
  currency: string
  image: string
  qty: number
  /** 起订量。低于它的数量在购物车里会被提示 */
  moq: number
  unit: string
}

/**
 * 购物车里一行的标识。
 *
 * <p>商品编码 + 型号编码。同一个商品的两个型号价格不同，合成一行就没法分别计价了。
 * 用 NUL 当分隔符：型号编码里可能有 &、-、: 这些，唯独不会有它。</p>
 *
 * <p>历史数据（型号功能上线之前存进 localStorage 的那些行）没有 comboCode，
 * 算出来是「编码 + 分隔符 + 空」，和「没选型号」是同一个键——正是想要的。</p>
 */
export function cartLineKey(item: Pick<CartItem, 'code' | 'comboCode'>) {
  return `${item.code}\u0000${item.comboCode || ''}`
}

export const MAX_QTY = 99999

/** 购物车合计。面议的行单独计数，不进金额。 */
export function cartTotals(items: CartItem[]) {
  let subtotal = 0
  let quoted = 0
  let inquireOnly = 0
  for (const i of items) {
    if (i.price > 0) {
      subtotal += i.price * i.qty
      quoted++
    } else {
      inquireOnly++
    }
  }
  return {
    subtotal,
    /** 有报价的行数 */
    quoted,
    /** 面议的行数。>0 时页面要说明「总价不含面议商品」 */
    inquireOnly,
    units: items.reduce((n, i) => n + i.qty, 0)
  }
}

/* ---------------- 下单请求 ---------------- */

/**
 * 状态词表。
 *
 * 本地只会产生 `submitted`——没有后端能推进状态。其余三个是给 station-console
 * 将来有了订单接口时用的，现在留着是因为状态样式和时间轴都按它们做好了，
 * 到时候不用再回来改一遍 UI。
 */
export type OrderStatus = 'submitted' | 'confirmed' | 'shipped' | 'delivered' | 'cancelled'

/** 时间轴上的四个点，按顺序 */
export type OrderStage = 'submitted' | 'confirmed' | 'shipped' | 'delivered'

export interface OrderItem {
  code: string
  /** 下单时选的型号编码。「再询一次」靠它把同一款放回清单 */
  comboCode?: string
  /** 型号名称快照。商家改名不影响这张历史单 */
  comboName?: string
  title: string
  subtitle: string
  image: string
  /** 下单当时的单价，0 = 面议。订单不会重新定价 */
  price: number
  currency: string
  qty: number
  unit: string
}

export interface OrderContact {
  name: string
  email: string
  company?: string
  phone?: string
}

export interface OrderAddress {
  line1: string
  line2?: string
  city: string
  region?: string
  postal: string
  country: string
}

export interface OrderEvent {
  stage: OrderStage
  /** ISO 时间戳 */
  at: string
}

export interface Order {
  /** 下单时浏览的语言。本地回执里的商品名是这种语言的；和当前语言不一样时页面会去服务端换一份 */
  lang?: string
  /** 对外的单号，也是路由参数：'RFQ-2026-1043' */
  ref: string
  placedAt: string
  status: OrderStatus
  items: OrderItem[]
  currency: string
  /** 有报价那部分的小计。面议的行不在里面 */
  subtotal: number
  /** 面议的行数，详情页要显示「另有 n 项待报价」 */
  inquireOnly: number
  contact: OrderContact
  address: OrderAddress
  /** 运输方式偏好的 id，见 SHIPPING_PREFS */
  shipping: string
  /** 付款方式偏好的 id，见 PAYMENT_PREFS */
  payment: string
  /** 买家的补充说明 */
  note?: string
  /**
   * 从引导式下单来的单：当时逐项选了什么（按下单语言的文字）。
   * 本地回执由向导页自己拼，服务端那份来自 `u_s_order.wizardjson`。普通购物车的单没有这一项
   */
  wizard?: Array<{ group: string; field: string; value: string }>
  /** 只记真的发生过的阶段，最早的在前 */
  events: OrderEvent[]
}

/* ---------------- 结账页的选项 ---------------- */

/**
 * 运输方式**偏好**，不是已经定下来的运费。
 *
 * 上游那三个选项带价格（免运费 / $39 / $129）。外贸的运费按目的港、体积重、
 * 当期舱位算，站上写死一个数字必然是错的，而且写了就等于承诺了。
 * 所以这里只收「你倾向哪种」，报价单里再给真实运费。
 */
export const SHIPPING_PREFS = ['sea', 'air', 'express', 'agent'] as const
export type ShippingPref = (typeof SHIPPING_PREFS)[number]

/** 付款方式偏好。同样只是偏好——站上不收款 */
export const PAYMENT_PREFS = ['tt', 'lc', 'paypal', 'other'] as const
export type PaymentPref = (typeof PAYMENT_PREFS)[number]

/* ---------------- 状态样式 ---------------- */

interface StatusMeta {
  /** 驱动状态标签的配色 class：`status--<tone>` */
  tone: 'amber' | 'blue' | 'green' | 'grey'
  /** 24x24 描边图标的 path */
  icon: string
}

/** 每个图标都是「一个圆环 + 一个符号」，保证四个标签看着是一套 */
const RING = 'M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6Z'

const STATUS: Record<OrderStatus, StatusMeta> = {
  submitted: { tone: 'amber', icon: `${RING} M12 7.4v4.9l3.3 1.9` },
  confirmed: { tone: 'blue', icon: `${RING} m8.4 12.1 2.6 2.6 4.6-5.4` },
  shipped: { tone: 'blue', icon: `${RING} M8.4 12h7.2m-2.9-2.9L15.6 12l-2.9 2.9` },
  delivered: { tone: 'green', icon: `${RING} m8.4 12.1 2.6 2.6 4.6-5.4` },
  cancelled: { tone: 'grey', icon: `${RING} m9.4 9.4 5.2 5.2m0-5.2-5.2 5.2` }
}

export function statusMeta(status: OrderStatus) {
  return STATUS[status] ?? STATUS.submitted
}

/* ---------------- 时间轴 ---------------- */

const STAGES: OrderStage[] = ['submitted', 'confirmed', 'shipped', 'delivered']

export interface TimelineStep {
  stage: OrderStage
  /** 已经发生过就是 ISO 时间戳，否则 null */
  at: string | null
  done: boolean
  /** 走到的最远一步——进度条停在这里 */
  current: boolean
}

/**
 * 四个阶段全列出来，走到过的标 done。
 * 取消掉的单保留它确实走过的阶段，其余显示成没发生。
 */
export function orderTimeline(order: Order): TimelineStep[] {
  const reached = new Map(order.events.map(e => [e.stage, e.at]))
  const done = STAGES.filter(s => reached.has(s))
  const lastDone = done.length ? done[done.length - 1] : null
  return STAGES.map(stage => ({
    stage,
    at: reached.get(stage) ?? null,
    done: reached.has(stage),
    current: stage === lastDone
  }))
}

/* ---------------- 格式化 ---------------- */

/** '2026年3月14日' / 'March 14, 2026' */
export function formatDate(iso: string, lang?: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString(intlLocale(lang), { year: 'numeric', month: 'long', day: 'numeric' })
}

/** 订单卡片上那行短日期 */
export function formatDateShort(iso: string, lang?: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString(intlLocale(lang), { year: 'numeric', month: 'short', day: 'numeric' })
}

/** 时间轴上带时刻的写法 */
export function formatDateTime(iso: string, lang?: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString(intlLocale(lang), {
    year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'
  })
}

/** 一张单上的总件数 */
export function orderUnits(order: Order) {
  return order.items.reduce((n, i) => n + i.qty, 0)
}

/**
 * 这个图片地址有没有可能加载成功。
 *
 * ## 为什么需要它，而不是只靠 <img @error>
 *
 * `@error` 只能抓到**水合之后**才发生的加载失败。SSR 的页面里，浏览器解析 HTML
 * 时就开始下图，等 Vue 水合上去把监听器挂好，失败事件早就过去了——事件不会重放，
 * 于是 `broken` 永远是 false，兜底分支永远走不到。
 *
 * 线上现在就踩着这个：装修里 `about.image` 存的是 `assets/sample/about.svg`，
 * 一个**相对路径**（装修表单该存完整 URL 或 / 开头的路径）。它 404，但错误事件
 * 在水合前就过去了，所以 `is-solo` 那个「没配图就退成整宽卡」的分支不生效：
 * 图容器占着 70% 宽却是空的，文字卡 60% 宽 + margin-top:-15% 往上压，
 * 结果压到了上一屏的能力卡上面。
 *
 * 拦在这里的好处是它在**服务端**就生效：服务端渲染出来的 HTML 已经是正确版式，
 * 没有「先错一下再跳对」的抖动，爬虫看到的也是对的那一版。
 *
 * 只认两种形状：
 *   · `http://` / `https://` —— 上传到对象存储后拿到的完整地址，正常情况都是这个；
 *   · `/` 开头 —— 同源绝对路径，本机开发和 provider=local 时会是这个。
 *
 * 其余一律当没配。`data:` 也不放行：装修表单不产生 data URI，
 * 出现了更可能是有人往里贴了别的东西。
 */
export function isUsableImageUrl(url?: string | null): boolean {
  if (!url) return false
  const v = String(url).trim()
  if (!v) return false
  const lower = v.toLowerCase()
  return lower.startsWith('http://') || lower.startsWith('https://') || v.startsWith('/')
}

/*
 * 这里原来有 nextOrderRef()，2026-09-06 删了。
 *
 * 它在本机递增出 'RFQ-2026-1043'，**不是全局唯一的**（两台浏览器各自从 1001 开始数），
 * 而且服务端从来没收到过那个号——它只存在买家浏览器的 localStorage 里。
 * 那时候的询盘正文里却写着「see request RFQ-2026-1043」，商家照着查什么都查不到。
 *
 * 现在单号由后端发：`u_s_sequence` 的 order 序列，'DD' + 10 位流水，
 * 提交成功才有（`CodeGenerator.nextOrderCode`）。
 */
