/**
 * 询价单 / 订单。
 *
 * ## 2026-09-06：从「纯本地台账」改成「服务端为准 + 本地回执兜底」
 *
 * 改之前这个文件的头注释写着「存 localStorage，没有后端」，那句话已经不成立很久了：
 * `station-console` 里 `POST /openapi/site/order` → `SiteOrderService` → `u_s_order`
 * + `u_s_orderitem` 是完整的（下单、列表、详情、状态流转、买家按身份自查、
 * 管理台订单管理全都有），只有前台没接上。
 *
 * 不接上的代价不是「少个功能」，是**在丢数据**：结账页原来把整张清单
 * `buildMessage()` 拼成纯文本塞进询盘的 `message`，而那一列上限 2000 字、
 * 后端直接截断，所以清单一长，尾巴上的商品行就进不了库、也进不了通知邮件——
 * 商家不知道买家还问了什么，买家以为问了。
 *
 * ## 现在的两个来源
 *
 * | | 谁能看到 | 存哪 | 权威性 |
 * |---|---|---|---|
 * | **服务端** | 登录过的买家 | `u_s_order`，按会话里的 `buyerCode` 查 | ✅ 唯一真相 |
 * | **本地回执** | 所有人，包括匿名 | localStorage | 只是「我刚提交过什么」的留痕 |
 *
 * **下单不要求登录**——挡一道登录会掉单，外贸站尤其如此。所以匿名买家提交完
 * 也得能看到自己刚发了什么，本地回执删不掉。
 *
 * ## 2026-09-10：匿名买家也能从服务端取回自己那张单
 *
 * 本地回执有个硬伤：**只在下单的那台浏览器里**。换台设备、清一次浏览器数据，
 * 游客就再也找不到自己的单了。所以补了两样东西：
 *
 *   1. 下单后后端给买家发一封确认信（`BuyerMailer.sendOrderReceiptAfterCommit`），
 *      信里有单号和一条指回 `/order-received/{code}` 的链接；
 *   2. `POST /openapi/site/order/lookup`：凭**单号 + 下单邮箱**取回那张单，
 *      前台这边是下面的 `lookup()`。
 *
 * ⚠ 邮箱那一关就是全部的鉴权（单号是流水号、可枚举），所以后端那个接口限流很紧、
 *   而且 Redis 挂了会直接拒绝。前台不要为了「体验好」去自动重试或者轮询——
 *   那会把真买家自己的额度用光。
 *
 * 两份合并的规则很简单：**同一个单号，服务端那份赢**。买家用同一个邮箱注册之后，
 * 后端 `SiteOrderService.submit()` 里的 `resolveByEmail` 会把历史订单挂到他名下，
 * 于是本地那份回执会被服务端那份逐条盖掉，不会出现两张一样的卡片。
 *
 * ## 单号不再是前台生成的
 *
 * 以前是 `nextOrderRef()` 在本机递增出 `RFQ-2026-1043`，那个号**服务端从来没收到过**，
 * 而询盘正文里还写着「see request RFQ-2026-1043」——商家照着查什么都查不到。
 * 现在单号由后端发（`DD` + 流水，`u_s_sequence`），提交成功才有。
 */

import {
  type CartItem,
  type Order,
  type OrderEvent,
  type OrderItem,
  type OrderStage
} from '~/lib/shop'
import type { BuyerOrder } from '~/composables/useBuyer'
import { toOrderStatus } from '~/composables/useBuyer'
import { siteLangHeaders } from '~/composables/useSite'

const STORAGE_KEY = 'station-orders'

/** 结账页在清单之外额外收集的东西 */
export interface RequestOptions {
  /** 买家下单时浏览的语言。后端按它写回执邮件、按它换买家中心里的商品名 */
  lang: string
  contact: { name: string; email: string; company?: string; phone?: string }
  address: {
    line1: string
    line2?: string
    city: string
    region?: string
    postal: string
    country: string
  }
  /** SHIPPING_PREFS 里的 id */
  shipping: string
  /** PAYMENT_PREFS 里的 id */
  payment: string
  note?: string
  /** 站点币种。⚠ 只是提交失败时本地回执的兜底值，成功后一律用服务端回的那个 */
  currency: string
  /** 蜜罐。表单上那个隐藏输入框，人填不到它 */
  website?: string
  /**
   * 从引导式下单提交（`pages/configure.vue`）。有它时：
   *   - 后端**忽略请求体里的 items**，按 `answers` 复核后自己算订单行（`OrderWizardService.resolve`）；
   *   - `items` 参数只用来记本地回执，`rows` 也记进回执，回执页据此显示当时的选配。
   */
  wizard?: {
    answers: Record<string, unknown>
    rows: Array<{ group: string; field: string; value: string }>
  }
}

/** 后端 `POST /openapi/site/order` 回的那几个字段 */
interface OrderReceipt {
  orderCode: string
  status: string
  currency: string
  subtotal: number
  inquireOnly: number
}

/** 粗略的形状检查——写坏的记录不能进 UI */
function isOrder(o: unknown): o is Order {
  const x = o as Order
  return (
    !!x &&
    typeof x.ref === 'string' &&
    typeof x.placedAt === 'string' &&
    Array.isArray(x.items) &&
    Number.isFinite(x.subtotal)
  )
}

/**
 * 服务端那张单 → 前台的 Order。
 *
 * ⚠ 有三个字段服务端**不下发**，这是故意的（`SiteOrderService.buyerView`）：
 * `email`、`addrLine1/2`、`postal`。买家自己填的详细地址不回显，
 * 少一个能被撞单号的人看到的东西。所以这里补空串，详情页对空值是有兜底的。
 */
function fromServer(o: BuyerOrder): Order {
  const stages: Array<[OrderStage, string | null]> = [
    ['submitted', o.createTime],
    ['confirmed', o.confirmTime],
    ['shipped', o.shipTime],
    ['delivered', o.finishTime]
  ]
  const events: OrderEvent[] = stages
    .filter(([, at]) => !!at)
    .map(([stage, at]) => ({ stage, at: String(at) }))

  const items: OrderItem[] = (o.items || []).map(i => ({
    code: i.goodsCode,
    comboCode: i.comboCode || undefined,
    comboName: i.comboName || undefined,
    title: i.goodsName,
    subtitle: i.subtitle || '',
    image: i.imgUrl || '',
    price: Number(i.price) || 0,
    currency: o.currency,
    qty: Number(i.qty) || 0,
    unit: i.unit || ''
  }))

  return {
    ref: o.orderCode,
    ...(o.lang ? { lang: o.lang } : {}),
    placedAt: o.createTime || new Date().toISOString(),
    // ⚠ 已经在 useBuyer 里从后端的中文词转成前台 id 了，这里不要再转一次
    status: o.status,
    items,
    currency: o.currency,
    subtotal: Number(o.subtotal) || 0,
    inquireOnly: Number(o.inquireOnly) || 0,
    contact: {
      name: o.contactName || '',
      email: '',
      ...(o.company ? { company: o.company } : {}),
      ...(o.phone ? { phone: o.phone } : {})
    },
    address: {
      line1: '',
      city: o.city || '',
      ...(o.region ? { region: o.region } : {}),
      postal: '',
      country: o.country || ''
    },
    shipping: o.shippingPref || '',
    payment: o.paymentPref || '',
    ...(o.buyerNote ? { note: o.buyerNote } : {}),
    ...(o.wizard?.length ? { wizard: o.wizard } : {}),
    events
  }
}

export function useOrders() {
  /** 本地回执。匿名买家唯一能看到的东西 */
  const local = useState<Order[]>('orders-local', () => [])
  /** 服务端那份。只有登录了才有 */
  const server = useState<Order[]>('orders-server', () => [])
  const restored = useState<boolean>('orders-restored', () => false)
  const loading = useState<boolean>('orders-loading', () => false)

  const { buyer, refresh, myOrders, myOrder } = useBuyer()

  function persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(local.value))
    } catch {
      /* 隐私模式：这次会话内还在内存里 */
    }
  }

  /** 只能在挂载后调，否则 hydration 会 mismatch（同 useCart.restore） */
  function restore() {
    if (restored.value) return
    restored.value = true
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) local.value = parsed.filter(isOrder)
    } catch {
      /* 坏 JSON：当成没有记录 */
    }
  }

  /**
   * 读本地回执 + 登录了就再拉服务端那份。
   *
   * 服务端那一步**失败不抛**：网络抖一下不该让「我的询价」整页变成错误页，
   * 本地回执还在，降级显示它比什么都不显示好。
   */
  async function load() {
    restore()
    loading.value = true
    try {
      await refresh()
      if (!buyer.value) {
        server.value = []
        return
      }
      const res = await myOrders(1, 50)
      server.value = (res.rows || []).map(fromServer)
    } catch {
      server.value = []
    } finally {
      loading.value = false
    }
  }

  /** 同一个单号服务端赢；新的在前 */
  const sorted = computed(() => {
    const byRef = new Map<string, Order>()
    for (const o of local.value) byRef.set(o.ref, o)
    for (const o of server.value) byRef.set(o.ref, o)
    return [...byRef.values()].sort(
      (a, b) => +new Date(b.placedAt) - +new Date(a.placedAt)
    )
  })

  const count = computed(() => sorted.value.length)

  /** 这份列表是不是来自账号（页面上要如实告诉买家换设备看不看得到） */
  const fromAccount = computed(() => !!buyer.value && server.value.length > 0)

  function find(ref: string) {
    return sorted.value.find(o => o.ref === ref) ?? null
  }

  /**
   * 取一张单的**完整**明细。
   *
   * 列表接口不下发订单行（避免 N+1），所以详情页必须单独取一次。
   * 没登录、或者这张单不是他的，就退回本地回执。
   */
  /**
   * `lang` 是当前页面语言（2026-09-22 起）：登录买家从服务端按这种语言取商品名；
   * 游客只有本地回执，回执是下单时那种语言的——语言不一样时凭回执里的邮箱去服务端换一份，
   * 换不到（限流、网络）就还用本地那份，不会白屏。
   */
  async function loadOne(ref: string, lang?: string) {
    restore()
    const cached = find(ref)

    // ⚠ 不能因为「本地已经有这张单」就直接返回。本地回执的状态**永远是 submitted**，
    //   它没有任何机制会被推进——买家回来看进度，看到的就会一直是第一格。
    //   所以只要登录着就一定去服务端取一次，那边的状态才是活的。
    //
    //   对匿名访客这里多一次 /account/me（401，很快，而且顶栏本来也会问一次，
    //   refresh() 有缓存不会重复发），换来的是登录用户看到的进度是真的。
    try {
      await refresh()
      if (!buyer.value) {
        if (cached && lang && cached.lang !== lang && cached.contact?.email) {
          try {
            return await lookup(ref, cached.contact.email, lang)
          } catch {
            return cached
          }
        }
        return cached
      }
      const full = fromServer(await myOrder(ref, lang))
      // 塞回缓存，返回列表再点进来就不用重取
      server.value = [...server.value.filter(o => o.ref !== ref), full]
      return full
    } catch {
      // 网络失败、或者这张单不是他的（后端一律回 404）→ 退回本地回执。
      // 退回而不是报错：匿名买家刚下的单只存在本地，这是唯一能给他看的东西
      return cached
    }
  }

  /**
   * 匿名查单：凭**单号 + 下单邮箱**从服务端取回一张单。
   *
   * 给「换了台设备、手上只有那封确认信」的游客用。取回来之后**顺手记进本地台账**，
   * 这台设备下次再打开就不用再报一次邮箱了。
   *
   * ⚠ 不带 `credentials`：这条路本来就是给没有会话的人走的。带上去只会让
   *   「登录着的人查一个别人转发给他的单号」多一层没必要的歧义——
   *   那种情况该走 /orders/{code}，那边按会话查。
   *
   * ⚠ 服务端**不下发 email**（`SiteOrderService.buyerView` 是故意的），
   *   但这里把刚才报对的那个邮箱填回去。回执页那块「保存这张单」要拿它去设密码，
   *   不填的话，从邮件链接过来的人会看到一个没有邮箱的注册块，那条路就白铺了。
   *   填回去是安全的：他刚刚证明了这个邮箱就是这张单上的。
   *
   * @throws 后端回的那句话。404 = 单号或邮箱对不上（两种**不区分**，防的是撞单号），
   *         429 = 查得太频繁
   */
  /** `lang`：商品名要哪种语言的（后端按它换，见 SiteOrderService#localizedItems） */
  async function lookup(orderCode: string, email: string, lang?: string): Promise<Order> {
    const config = useRuntimeConfig()
    const base = config.public.apiBase || ''
    const res = await $fetch<{ code: number; msg: string; data?: BuyerOrder }>(
      `${base}/openapi/site/order/lookup`,
      {
        method: 'POST',
        headers: siteLangHeaders(),
        // 后端用 404/429 表达「对不上」「太频繁」，那两句 msg 是要显示给买家看的。
        // 默认行为会把响应体丢掉，只留一个 "FetchError: 404"
        ignoreResponseError: true,
        body: { orderCode, email, lang: lang || undefined }
      })

    if (res?.code !== 200 || !res.data) {
      throw new Error(res?.msg || '')
    }

    const full = fromServer({ ...res.data, status: toOrderStatus(String(res.data.status)) })
    full.contact.email = email
    if (lang) full.lang = lang

    // 记进本地台账。同一个单号以刚取回的这份为准
    restore()
    local.value = [...local.value.filter(o => o.ref !== full.ref), full]
    persist()
    return full
  }

  /**
   * 提交一张单。
   *
   * ⚠ 请求体里**只发商品编码、型号编码和数量**。价格、商品名、单位、小计全部由服务端
   * 从 `u_s_sitegoods` 现查现算（`SiteOrderService.repriceLines`）——
   * 清单在 localStorage 里，改一个数字就能把单价变成 0.01，信前端传的价格
   * 等于把报价单交给买家自己填。
   *
   * @returns 成功返回后端发的单号；被限流返回 null（调用方要提示「稍后再试」）
   * @throws  其余失败一律抛，调用方**不要**在这之后记回执、也不要清空清单——
   *          反过来的话买家会看到一张「已发送」的单子而商家什么都没收到
   */
  async function submit(items: CartItem[], options: RequestOptions): Promise<string | null> {
    if (!items.length) throw new Error('')

    const config = useRuntimeConfig()
    const base = config.public.apiBase || ''
    const res = await $fetch<{ code: number; msg: string; data?: OrderReceipt }>(
      `${base}/openapi/site/order`,
      {
        method: 'POST',
        headers: siteLangHeaders(),
        // 会话在 HttpOnly Cookie 里。带上它，后端就能把这张单直接挂到
        // 登录买家名下；不带也能下单（匿名），只是要靠邮箱事后认领
        credentials: 'include',
        // 后端用 429 表达「太频繁」，那个 msg 是要显示给买家看的。
        // 默认行为会把响应体丢掉，只留一个 "FetchError: 429"
        ignoreResponseError: true,
        body: {
          contactName: options.contact.name,
          email: options.contact.email,
          company: options.contact.company,
          phone: options.contact.phone,
          addrLine1: options.address.line1,
          addrLine2: options.address.line2,
          city: options.address.city,
          region: options.address.region,
          postal: options.address.postal,
          country: options.address.country,
          shippingPref: options.shipping,
          paymentPref: options.payment,
          buyerNote: options.note,
          lang: options.lang,
          // ⚠ 型号只发编码。价格照旧由服务端按 comboCode 现查
          //   （SiteOrderService.repriceLines），前端这份 price 一样不作数
          // 向导来的单不发 items：后端按答案自己算，发了也会被忽略，不如不发免得让人以为它算数
          items: options.wizard ? [] : items.map(i => ({ goodsCode: i.code, comboCode: i.comboCode || null, qty: i.qty })),
          ...(options.wizard ? { wizard: { answers: options.wizard.answers } } : {}),
          website: options.website || ''
        }
      })

    if (res?.code === 429) return null
    if (res?.code !== 200 || !res.data?.orderCode) {
      throw new Error(res?.msg || '')
    }

    record(items, options, res.data)
    return res.data.orderCode
  }

  /**
   * 记一份本地回执。**只在提交成功之后调**（`submit` 里已经这么做了）。
   *
   * ⚠ 金额三项用的是**服务端回的那份**，不是本地 `cartTotals()` 算的。
   * 服务端会重新定价，两边对不上时以它为准——否则买家看到的小计和商家收到的不一样。
   * 行内单价还是本地那份（服务端不回明细），所以极端情况下「各行相加 ≠ 小计」，
   * 那也是对的：小计才是权威。登录之后这份回执会被服务端那份整条盖掉。
   */
  function record(items: CartItem[], options: RequestOptions, receipt: OrderReceipt): Order {
    const now = new Date().toISOString()
    const order: Order = {
      ref: receipt.orderCode,
      lang: options.lang,
      placedAt: now,
      status: 'submitted',
      // 快照一份，不是引用：清单后面被改、被清空，都不该动到已经发出去的单子
      items: items.map(i => ({
        code: i.code,
        comboCode: i.comboCode,
        comboName: i.comboName,
        title: i.title,
        subtitle: i.subtitle,
        image: i.image,
        price: i.price,
        currency: receipt.currency || options.currency,
        qty: i.qty,
        unit: i.unit
      })),
      currency: receipt.currency || options.currency,
      subtotal: Number(receipt.subtotal) || 0,
      inquireOnly: Number(receipt.inquireOnly) || 0,
      contact: { ...options.contact },
      address: { ...options.address },
      shipping: options.shipping,
      payment: options.payment,
      ...(options.note ? { note: options.note } : {}),
      ...(options.wizard?.rows.length ? { wizard: options.wizard.rows } : {}),
      events: [{ stage: 'submitted', at: now }]
    }

    local.value = [order, ...local.value.filter(o => o.ref !== order.ref)]
    persist()
    return order
  }

  return { sorted, count, loading, fromAccount, restore, load, find, loadOne, lookup, submit }
}
