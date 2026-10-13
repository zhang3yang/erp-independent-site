/**
 * 买家账号：注册 / 登录 / 退出 / 邮箱验证 / 找回密码 / 我的资料 / 收货地址簿 / 我的订单。
 *
 * 后端在 `/openapi/site/account/**`（station-console 的 `BuyerAccountController`），
 * 表是 `u_s_buyer`——**站点自己的客户档案**，和 ERP 的客户没有任何关系
 * （docs/独立站与ERP的边界.md §6c）。
 *
 * ## 这一套推翻了原来那句「这个站没有买家账号」
 *
 * cart/index.vue、orders/index.vue 的页头注释里都写着「这个站没有买家账号
 * （§6c 一期不实现）」，于是「我的订单」只是一份 localStorage 台账，
 * orders/index.vue 里还专门有一句提示告诉买家「这份列表只存在这台浏览器里」。
 *
 * 现在有账号了。**询盘仍然不要求登录**，下单则改成要登录了（商家的决定）：
 * 匿名访客只能发询盘，加入清单和结账的入口在前台一律不渲染。
 *
 * ⚠ 这一道是**只在前端拦的**。`POST /openapi/site/order` 和
 *   `/openapi/site/inquiry` 两个接口一个字都没改，仍然接受匿名请求——
 *   直接敲 /cart、/checkout 的地址照样能下单。要真挡住得在后端加会话校验，
 *   那是另一笔。现在这个改动的目的是引导，不是权限。
 *
 * 原来的取舍写在这里，将来想改回去时值得先读一遍：挡一道登录会掉单，
 * 外贸站尤其如此（见 cart/index.vue 第 1 条）。
 *
 * ## ⚠ 会话在一个 HttpOnly Cookie 里，JS 读不到
 *
 * 所以这个 composable **没法在客户端判断「有没有登录」**，只能问后端
 * （`GET /account/me`，401 = 没登录）。不要试图存一个 `isLoggedIn` 到
 * localStorage 里当缓存——那个值会和真实会话不一致（会话 30 天滑动过期、
 * 也可能被停用），然后表现成「界面显示已登录，一点就 401」。
 *
 * Cookie 是 HttpOnly 的理由和管理台那边一样：XSS 也偷不走会话。
 *
 * ## ⚠ 全部走浏览器侧，不参与 SSR
 *
 * 买家相关的页面一律 `noindex`（每个访客各自的东西，收录没有意义），
 * 所以不需要 SSR。而且 SSR 时要把访客的 Cookie 从 Nuxt 服务端转发给后端，
 * 那是一整套额外的东西（`useRequestHeaders(['cookie'])` + 逐个接口传），
 * 为一个不需要收录的页面做那件事不值得。
 */

import type { OrderStatus } from '~/lib/shop'
import { siteLangHeaders } from '~/composables/useSite'
import { isBuyerPath } from '#shared/utils/buyerPaths'

/** 后端 `profile()` 下发的字段。⚠ 没有内部标签和备注，那些是运营看的 */
export interface BuyerProfile {
  email: string
  name: string | null
  company: string | null
  phone: string | null
  country: string | null
  displayName: string
  orderCount: number | null
  inquiryCount: number | null
  /**
   * 头像：`preset:<色块id>`、256px 的 JPEG data URL，或 null（画首字母字块）。
   *
   * ⚠ 存在服务端（`u_s_buyer.avatar`），不是 localStorage——参考站那句
   * 「nothing is uploaded anywhere」在这个站上不成立，见 useAvatar.ts 的头注释。
   */
  avatar: string | null
}

/**
 * 收货地址簿里的一条。
 *
 * ⚠ 字段名和 `u_s_order` 的目的地那一组、以及结账页表单里的那几个**是同一套**
 *   （contactName / phone / addrLine1 / addrLine2 / city / region / postal / country）。
 *   对齐是有意的：地址簿选一条 → 填进结账表单 → 落进订单那几列，中间没有映射层。
 *   加字段时三处一起看，别让谁漂开。
 *
 * ⚠ `id` 是后端自增主键，连号可猜。所有增删改查的归属都在**服务端按会话判**
 *   （SiteBuyerAddressMapper 每条 SQL 的 where 里都有 buyercode），前端不要
 *   自己拿 id 做任何权限判断——那没有意义。
 */
export interface BuyerAddress {
  id: number
  /** 买家给这条起的名字，如 Warehouse。选填，空的话前台显示国家 + 城市 */
  label: string | null
  contactName: string
  phone: string | null
  addrLine1: string | null
  addrLine2: string | null
  city: string | null
  region: string | null
  postal: string | null
  country: string
  isDefault: boolean
  createTime?: string | null
}

/** 提交一条地址时发的内容。带 `id` 是改，不带是新增——后端同一个接口 */
export type BuyerAddressForm = Omit<BuyerAddress, 'id' | 'isDefault' | 'createTime'> & {
  id?: number | null
}

/**
 * 后端的状态词 → 前台那套状态 id。
 *
 * 两边的词表是**一一对应**的，只是一边是中文常量（`SiteOrder.STATUS_*`，
 * 因为它要直接显示在管理台的表格里），一边是 id（`app/lib/shop.ts` 的 `OrderStatus`，
 * 因为前台要按访客语言翻译成 8 种语言）。
 *
 * 在这里转，不在页面里转：页面用的是前台原来那套 id，于是
 * `OrderStatusPill`、`statusMeta`、那条四段时间轴全都不用改一个字。
 */
const STATUS_MAP: Record<string, OrderStatus> = {
  待确认: 'submitted',
  已确认: 'confirmed',
  已发货: 'shipped',
  已完成: 'delivered',
  已取消: 'cancelled'
}

/**
 * 认不出来的状态一律当成「已提交」——那是时间轴的第一格，不会显示成空白。
 *
 * ⚠ 导出是给 `useOrders().lookup()` 用的：匿名查单那条路不走 callAccount，
 *   但拿回来的是同一个后端形状，状态同样要从中文词转成前台 id。
 *   别在页面里调它——页面拿到的 BuyerOrder 应该已经是转好的了。
 */
export function toOrderStatus(raw: string): OrderStatus {
  return STATUS_MAP[raw] || 'submitted'
}

/** 「我的订单」列表里的一张单。字段和后端 `SiteOrderService.buyerView` 对齐 */
export interface BuyerOrder {
  orderCode: string
  /** 下单时浏览的语言（u_s_order.lang）。老单是 null */
  lang?: string | null
  /** ⚠ 已经转成前台那套 id 了，不是后端的中文原值。见 STATUS_MAP */
  status: OrderStatus
  currency: string
  subtotal: number
  inquireOnly: number
  createTime: string | null
  confirmTime: string | null
  shipTime: string | null
  finishTime: string | null
  contactName: string | null
  company: string | null
  phone: string | null
  city: string | null
  region: string | null
  country: string | null
  shippingPref: string | null
  paymentPref: string | null
  buyerNote: string | null
  /** 引导式下单的选配（下单语言的文字）。只在详情里有，普通购物车的单是空数组 */
  wizard?: Array<{ group: string; field: string; value: string }>
  items: Array<{
    goodsCode: string
    goodsName: string
    /** 下单时选的型号。后端 buyerView 一直下发，这两行以前漏在类型里了 */
    comboCode: string | null
    comboName: string | null
    subtitle: string | null
    imgUrl: string | null
    qty: number
    unit: string | null
    price: number | null
    lineTotal: number | null
  }>
}

/**
 * 统一拆 `{ code, msg, data }` 信封。
 *
 * ⚠ 和 useSite.ts 里那个 `call()` 有两处不同，都是刻意的：
 *
 *   1. **失败抛普通 Error，不抛 createError**。`createError` 会触发 Nuxt 的错误页，
 *      而这些是表单提交——「密码不对」应该显示在表单上方，不该把整页替换成一个错误页。
 *   2. **带 credentials**。会话在 Cookie 里，不带就永远是未登录。同源请求下浏览器
 *      默认会带，写出来是为了让「靠 Cookie 认身份」这件事在代码里看得见；
 *      跨根域时它也必要（但那时候还要后端放开 CORS 凭证，见 WebMvcConfig 的注释）。
 */
async function callAccount<T>(path: string, options: {
  method?: 'GET' | 'POST'
  body?: Record<string, unknown>
} = {}): Promise<T> {
  const config = useRuntimeConfig()
  const base = config.public.apiBase || ''
  const res = await $fetch<{ code: number; msg: string; data?: T }>(
    `${base}/openapi/site/account${path}`,
    {
      method: options.method || 'GET',
      headers: siteLangHeaders(),
      body: options.body,
      credentials: 'include',
      // ⚠ 不让 $fetch 在非 2xx 时抛它自己的错：后端用 401/403/429 表达
      //   「没登录」「被停用」「太频繁」，而那些 msg 是要显示给买家看的。
      //   默认行为会把响应体丢掉，只留一个 "FetchError: 401"
      ignoreResponseError: true
    })
  if (res?.code !== 200) {
    // 后端的 msg 已经按 X-Site-Lang 翻好；没有 msg 时抛空串，页面退回自己那句 acctFailed
    throw new Error(res?.msg || '')
  }
  return res.data as T
}

/**
 * 当前买家。整个应用一份（`useState`，SSR 安全）。
 *
 * - `null` = 确认没登录
 * - `undefined` = 还没问过后端
 *
 * 区分这两个状态是必要的：顶栏在「还没问过」时不该闪一下「登录」再跳成买家名字。
 */
export function useBuyerState() {
  return useState<BuyerProfile | null | undefined>('buyer', () => undefined)
}

export function useBuyer() {
  const buyer = useBuyerState()
  // 当前 URL 里的语言（默认语言时是空串，后端会退回站点默认语言）。
  // 注册、登录、找回密码都带上它：验证信、重置信按这种语言写，信里的链接也带这个前缀。
  // ⚠ 在 setup 里取一次存下来：这些函数多在点击事件里调，那时候 useRoute 已经拿不到 Nuxt 实例
  const urlLang = useUrlLang()

  /**
   * 认下一个买家：写进共享状态，**顺手把显示币种清回站点本位币**。
   *
   * 登录之后顶栏不再渲染币种切换器（商家要的，见 SiteHeader.vue），而币种偏好
   * 存的是一条一年期 Cookie。只在 login() 里清是不够的：这个改动上线之前就已经
   * 登录着、并且挑过别的币种的人不会再走一次 login()，价格会永远停在那个币种上，
   * 而换回去的按钮已经没了。所以 refresh() 问出「已登录」时也清一次。
   * 清的动作是幂等的，Cookie 本来就空着时什么都不做。
   *
   * ⚠ 这里的 `await nextTick()` 不能省。Nuxt 的 useCookie 是在调度器里回写
   *   document.cookie 的，而 login() 的下一步就是 afterLogin() 那个跨域整页跳转
   *   （login.vue 的 go()）。不等这一下，跳转可能赶在回写前面，Cookie 原样留着。
   */
  async function adoptBuyer(profile: BuyerProfile) {
    buyer.value = profile
    resetDisplayCurrency()
    await nextTick()
  }

  /**
   * 问一次后端「我是谁」。
   *
   * @param force 已经问过就不再问；顶栏挂载、账户页进入时传 false 即可
   */
  async function refresh(force = false) {
    if (!force && buyer.value !== undefined) return buyer.value
    try {
      await adoptBuyer(await callAccount<BuyerProfile>('/me'))
    } catch {
      // 401 是常态（大多数访客没登录），不打日志、不报错
      buyer.value = null
    }
    return buyer.value
  }

  async function register(payload: {
    email: string
    password: string
    name?: string
    company?: string
    phone?: string
  }) {
    // ⚠ 注册成功**不等于**能登录了：还要点邮件里的验证链接。
    //   页面上必须说清楚这一步，否则买家会直接去登录然后看到「邮箱还没验证」
    await callAccount('/register', { method: 'POST', body: { ...payload, lang: urlLang.value } })
  }

  async function login(email: string, password: string) {
    const profile = await callAccount<BuyerProfile>('/login', {
      method: 'POST',
      body: { email, password, lang: urlLang.value }
    })
    await adoptBuyer(profile)
    return profile
  }

  async function logout() {
    try {
      await callAccount('/logout', { method: 'POST' })
    } finally {
      // 后端挂了也要在界面上退出：Cookie 那边即使还在，下一次请求也会 401
      buyer.value = null
    }
  }

  const verifyEmail = (token: string) =>
    callAccount<{ email: string }>('/verify-email', { method: 'POST', body: { token } })

  const resendVerify = (email: string) =>
    callAccount('/resend-verify', { method: 'POST', body: { email, lang: urlLang.value } })

  const forgotPassword = (email: string) =>
    callAccount('/password/forgot', { method: 'POST', body: { email, lang: urlLang.value } })

  const resetPassword = (token: string, password: string) =>
    callAccount('/password/reset', { method: 'POST', body: { token, password } })

  async function changePassword(oldPassword: string, newPassword: string) {
    await callAccount('/password/change', {
      method: 'POST',
      body: { oldPassword, newPassword }
    })
    // 后端改完密码就把会话作废了（怀疑密码泄露时，留着旧会话等于让攻击者继续待着），
    // 所以这里必须同步清掉，不然界面显示已登录、一点就 401
    buyer.value = null
  }

  /**
   * 改自己的联系人 / 公司 / 电话 / 国家。
   *
   * ⚠ 改不了邮箱：那是账号的身份（登录名、找回密码的去处）。后端也不收这个字段。
   *
   * 后端回的是改完之后的完整档案，直接换掉本地那份——顶栏显示的是 displayName，
   * 而那个值是「公司名 → 联系人 → 邮箱」推出来的，改完公司名它会跟着变。
   */
  async function updateProfile(patch: {
    name?: string | null
    company?: string | null
    phone?: string | null
    country?: string | null
  }) {
    const profile = await callAccount<BuyerProfile>('/profile', { method: 'POST', body: patch })
    buyer.value = profile
    return profile
  }

  /**
   * 设头像。传 `preset:<id>`、data URL，或 null 清掉。
   *
   * ⚠ 和 updateProfile 分开的两个接口：那张表单每次保存都会把姓名/公司/电话/国家
   *   一起发一遍，头像跟着走的话改个电话号就要重传几十 KB 的图；而且色块是
   *   点一下就生效，和「填完表单按保存」不是同一种交互。
   */
  async function updateAvatar(avatar: string | null) {
    const profile = await callAccount<BuyerProfile>('/avatar', {
      method: 'POST',
      body: { avatar }
    })
    buyer.value = profile
    return profile
  }

  /* ---------------- 收货地址簿 ---------------- */

  /**
   * 我的收货地址。默认的排最前（后端排的，前端不要再排一次）。
   *
   * ⚠ 没登录时这个接口回 401，callAccount 会抛错。调用方要么先确认 buyer 有值，
   *   要么自己 catch——**不要**把它当成「返回空数组」，那会让「没登录」看起来像
   *   「地址簿是空的」，然后买家在结账页找不到自己存过的地址。
   */
  const myAddresses = () => callAccount<BuyerAddress[]>('/addresses')

  /** 新增或修改。带 `id` 是改。`makeDefault` 顺手设为默认（第一条一定是默认的） */
  const saveAddress = (form: BuyerAddressForm, makeDefault = false) =>
    callAccount<BuyerAddress>(`/addresses?makeDefault=${makeDefault ? 'true' : 'false'}`, {
      method: 'POST',
      body: form as unknown as Record<string, unknown>
    })

  /** 设默认。后端回的是设完之后的整份地址簿，直接拿它替换本地列表 */
  const setDefaultAddress = (id: number) =>
    callAccount<BuyerAddress[]>(`/addresses/${id}/default`, { method: 'POST' })

  /**
   * 删一条。同样回整份地址簿。
   *
   * ⚠ 用 POST /delete 不是 DELETE 动词：后端给 /openapi/site/** 放开的跨域方法只有
   *   GET / POST / OPTIONS（WebMvcConfig）。生产上同源无所谓，开发机上是跨域的——
   *   用 DELETE 会「本机调不通、线上是好的」，那种差异最难查。
   */
  const removeAddress = (id: number) =>
    callAccount<BuyerAddress[]>(`/addresses/${id}/delete`, { method: 'POST' })

  /** 我的订单列表。状态在这里从后端的中文词转成前台的 id，见 STATUS_MAP */
  async function myOrders(page = 1, size = 10) {
    const res = await callAccount<{ total: number; rows: BuyerOrder[] }>(
      `/orders?page=${page}&size=${size}`)
    return {
      total: res?.total || 0,
      rows: (res?.rows || []).map(o => ({ ...o, status: toOrderStatus(String(o.status)) }))
    }
  }

  /** `lang`：商品名要哪种语言的。不传按下单时的语言（后端 SiteOrderService#localizedItems） */
  async function myOrder(orderCode: string, lang?: string) {
    const o = await callAccount<BuyerOrder>(
      `/orders/${encodeURIComponent(orderCode)}${lang ? `?lang=${encodeURIComponent(lang)}` : ''}`)
    return { ...o, status: toOrderStatus(String(o.status)) }
  }

  /**
   * 登录成功之后往哪走。**域名拆分开着的时候，这一跳要绕一下买家域。**
   *
   * ## 为什么要绕
   *
   * 商城在主域，买家中心（我的订单、账号资料、地址簿管理）在 buyer. 子域。
   * `BUYER_SESSION` 是 host-only 的：刚才那次登录只在**主域**上种了 Cookie，
   * buyer. 上一点身份都没有。直接跳过去就是「刚登录完却显示未登录」。
   *
   * 所以登录成功后先去买家域的 handoff 接口报到一次：它用一张 60 秒的一次性票
   * 换回**同一把钥匙**，在 buyer. 上种自己那份 Cookie，然后 302 到真正的目的地。
   *
   * 两个域从此装着同一个令牌、指向同一个 Redis 键——**在任意一边退出，
   * 两边一起失效**，不需要任何同步代码。设计见
   * `docs/设计稿/2026-09-08-独立站域名拆分.md`。
   *
   * ## 为什么不管目的地在哪边，都绕这一下
   *
   * 就算这次是去 `/checkout`（在主域），也照绕：handoff 会 302 到 buyer. 上的
   * `/checkout`，再被 domain-split 中间件 301 回主域。多两跳，换来的是
   * **一次登录把两个域都种上**，之后 30 天里来回走都不用再交接。
   *
   * 判断「这个路径归哪个域」需要买家路径清单，那份清单在
   * `shared/utils/buyerPaths.ts`——服务端的 301 和浏览器侧用的是**同一份**
   * （2026-09-10 之前它有三份拷贝，见那个文件的头注释）。
   *
   * ## ⚠ 票签不出来时不要卡住用户
   *
   * Redis 抖一下 `ticket` 会是 null。那时候**照常跳**，只是 buyer. 上没有会话，
   * 买家进买家中心时会被要求再登一次——那时候主域认得他，无感换回来。
   * 登录本身已经成功了，不该因为交接失败给用户看一个失败的登录。
   */
  async function afterLogin(to: string) {
    const domains = useSiteHosts()
    if (!domains.enabled) {
      // 单域部署（本机开发）：站内路由过去就行
      await navigateTo(to, { replace: true })
      return
    }
    let ticket: string | null = null
    try {
      const res = await callAccount<{ ticket: string }>('/handoff-ticket', { method: 'POST' })
      // 后端签不出票时回的是字符串 "null"（Map.of 不收 null 值），这里一并当作没有
      ticket = res?.ticket && res.ticket !== 'null' ? res.ticket : null
    } catch {
      // 交接拿不到票不是登录失败，忽略，走下面的兜底
    }
    if (!ticket) {
      // ⚠ 兜底这一跳也要定 host。`to` 是站内路径，直接 navigateTo 是客户端路由，
      //   /account 会渲染在**主域**上——页面打得开、数据也能取到（主域有会话），
      //   但地址栏是错的，而且那一页里所有 shopUrl(...) 都会退化成相对路径。
      const href = isBuyerPath(to) && domains.buyerUrl ? domains.buyerUrl + to : to
      await navigateTo(href, { external: href !== to, replace: true })
      return
    }
    // ⚠ external: true —— 这是跨域名的整页跳转，不是站内路由
    // ⚠ replace：不留下登录页那条历史记录。留着的话按「后退」会回到 /login，
    //   而那一页 onMounted 判出已登录又把人送回来，后退键就废了
    await navigateTo(
      `${domains.buyerUrl}/openapi/site/account/handoff`
        + `?t=${encodeURIComponent(ticket)}&to=${encodeURIComponent(to)}`,
      { external: true, replace: true }
    )
  }

  return {
    buyer,
    refresh,
    register,
    login,
    afterLogin,
    logout,
    verifyEmail,
    resendVerify,
    forgotPassword,
    resetPassword,
    changePassword,
    updateProfile,
    updateAvatar,
    myAddresses,
    saveAddress,
    setDefaultAddress,
    removeAddress,
    myOrders,
    myOrder
  }
}
