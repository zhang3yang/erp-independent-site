<!--
  结账页 —— 在这个站上是「发送询价单」。

  从 kangwanjia-website-EN 的 pages/checkout/index.vue 搬来：三步式面板 + 右侧汇总
  + 提交按钮的结构照原样，但**支付那一步换掉了**，理由写在 app/lib/shop.ts 的头注释里，
  这里只说结论：

    上游那一步是假的（选支付宝/微信、画一个装饰性二维码、转 1.5 秒圈、订单变成已付款）。
    这个站会真上线、真有海外买家进来，做一个看着能付款其实什么都没发生的页面就是骗人。
    所以第三步改成收「运输和付款倾向」，提交后商家收到带明细的邮件，人工回报价单。
    少掉的只有在线支付，那一步后端本来也没有。

  ## 2026-09-06：提交改走订单接口，不再走询盘接口

  原来是把整张清单 buildMessage() 拼成纯文本塞进询盘的 message 字段。那一列上限
  2000 字且直接截断，所以清单一长就会**丢掉尾巴上的商品行**（连商家的通知邮件里也没有）。
  现在走 POST /openapi/site/order，落 u_s_order + u_s_orderitem，一行都不丢。

  ⚠ 请求体里只发商品编码和数量，价格由服务端从 u_s_sitegoods 现查现算。
    清单在 localStorage 里，改一个数字就能把单价变成 0.01。

  下单**仍然不要求登录**——挡一道登录会掉单。登录只决定「换台电脑还看不看得到」。

  ## 2026-09-08：登录的人可以从收货地址簿里选一条

  地址原来只存在这台浏览器的 localStorage（下面的 BUYER_KEY），换台电脑就得重填。
  现在登录的人多两样：目的地那一栏上面有个下拉能选地址簿里的一条，下面有个勾
  能把这次填的存回去（u_s_buyeraddr，account.vue 里管理）。

  ⚠ localStorage 那份**没删**：没登录的人仍然靠它免于重填。
  ⚠ 两份都有时的规则是「本地填过的不覆盖，空着的才用服务端的」——
    本地那份是他上一单亲手填的，拿档案盖掉会让人以为自己填错了。

  ## 2026-09-10：提交成功后的回执页按登录与否分流

  登录买家去 `buyer./orders/{code}`（服务端查库），游客去主域的
  `/order-received/{code}`（读本地回执）。**游客不能送去 `/orders`**：那一支
  归 buyer. 子域，而本地回执按源隔离写在主域上，一刷新就变成「找不到这张单」。
  理由和整条链路写在 `pages/order-received/[code].vue` 的文件头。

  ## 三件事的顺序不能换

  落库成功 → 记本地回执 → 清空清单。反过来的话，网络失败时买家会看到一张「已发送」的
  单子而商家什么都没收到，或者清单被清空了但单子没提交出去——两种都比直接报错糟。

  ## 不给收录

  同购物车页：`robots: noindex`，不进 sitemap。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap co-page">
      <header class="co-head">
        <NuxtLink :to="localePath('/cart')" class="co-back link-more">{{ t.coBackToCart }}</NuxtLink>
        <h1>{{ t.coTitle }}</h1>
        <p class="muted">{{ t.coLead }}</p>
      </header>

      <!-- 挂载前：清单在 localStorage 里 -->
      <div v-if="!ready" class="co-layout" aria-hidden="true">
        <div class="skeleton-block sk-steps"></div>
        <div class="skeleton-block sk-summary"></div>
      </div>

      <!-- 没东西可询价 -->
      <div v-else-if="!items.length" class="notice">
        <h2>{{ t.coEmptyTitle }}</h2>
        <p class="muted">{{ t.coEmptyLead }}</p>
        <NuxtLink :to="localePath('/products')" class="btn">{{ t.explore }}</NuxtLink>
      </div>

      <form v-else class="co-layout" novalidate @submit.prevent="submit">
        <div class="co-steps">
          <!-- ---------- 1. 联系方式 ---------- -->
          <section class="panel">
            <div class="panel-head">
              <span class="step-no">{{ fill(t.coStep, { n: 1 }) }}</span>
              <h2>{{ t.coContactTitle }}</h2>
              <p class="panel-sub muted">{{ t.coContactLead }}</p>
            </div>

            <div class="grid-2">
              <label class="field">
                <span>{{ t.coName }} *</span>
                <input v-model.trim="form.name" maxlength="100" autocomplete="name"
                       :class="{ bad: errors.name }">
                <em v-if="errors.name" class="err">{{ errors.name }}</em>
              </label>
              <label class="field">
                <span>{{ t.coEmail }} *</span>
                <input v-model.trim="form.email" type="email" maxlength="150" autocomplete="email"
                       :class="{ bad: errors.email }">
                <em v-if="errors.email" class="err">{{ errors.email }}</em>
              </label>
            </div>

            <div class="grid-2">
              <label class="field">
                <span>{{ t.coCompany }}</span>
                <input v-model.trim="form.company" maxlength="200" autocomplete="organization">
              </label>
              <label class="field">
                <span>{{ t.coPhone }}</span>
                <input v-model.trim="form.phone" maxlength="50" autocomplete="tel">
              </label>
            </div>
          </section>

          <!-- ---------- 2. 目的地 ---------- -->
          <section class="panel">
            <div class="panel-head">
              <span class="step-no">{{ fill(t.coStep, { n: 2 }) }}</span>
              <h2>{{ t.coAddressTitle }}</h2>
              <p class="panel-sub muted">{{ t.coAddressLead }}</p>
            </div>

            <!--
              登录的人：从地址簿里选一条。没登录、或者地址簿是空的，这一段不出现。
              选中之后下面那几栏被填上，仍然可以改——改完存不存回地址簿看那个勾。
            -->
            <label v-if="savedAddresses.length" class="field addr-pick">
              <span>{{ t.coPickAddress }}</span>
              <select v-model="pickedAddressId" @change="onPickAddress">
                <option v-for="a in savedAddresses" :key="a.id" :value="a.id">
                  {{ addrOption(a) }}
                </option>
                <option value="new">{{ t.coPickAddressNone }}</option>
              </select>
            </label>

            <div class="grid-2">
              <label class="field">
                <span>{{ t.coCountry }} *</span>
                <input v-model.trim="form.country" maxlength="80" autocomplete="country-name"
                       :class="{ bad: errors.country }">
                <em v-if="errors.country" class="err">{{ errors.country }}</em>
              </label>
              <label class="field">
                <span>{{ t.coCity }}</span>
                <input v-model.trim="form.city" maxlength="80" autocomplete="address-level2">
              </label>
            </div>

            <label class="field">
              <span>{{ t.coLine1 }} <em class="opt">{{ t.coOptional }}</em></span>
              <input v-model.trim="form.line1" maxlength="200" autocomplete="address-line1">
            </label>

            <div class="grid-3">
              <label class="field">
                <span>{{ t.coLine2 }} <em class="opt">{{ t.coOptional }}</em></span>
                <input v-model.trim="form.line2" maxlength="200" autocomplete="address-line2">
              </label>
              <label class="field">
                <span>{{ t.coRegion }} <em class="opt">{{ t.coOptional }}</em></span>
                <input v-model.trim="form.region" maxlength="80" autocomplete="address-level1">
              </label>
              <label class="field">
                <span>{{ t.coPostal }} <em class="opt">{{ t.coOptional }}</em></span>
                <input v-model.trim="form.postal" maxlength="30" autocomplete="postal-code">
              </label>
            </div>

            <!-- 只对登录的人显示：没登录的话没有地址簿可存 -->
            <label v-if="buyer" class="check">
              <input v-model="saveToBook" type="checkbox">
              <span>{{ t.coSaveAddress }}</span>
            </label>
          </section>

          <!-- ---------- 3. 运输与付款倾向 ---------- -->
          <section class="panel">
            <div class="panel-head">
              <span class="step-no">{{ fill(t.coStep, { n: 3 }) }}</span>
              <h2>{{ t.coPrefsTitle }}</h2>
              <p class="panel-sub muted">{{ t.coPrefsLead }}</p>
            </div>

            <ul class="pick-list">
              <li v-for="p in shippingOptions" :key="p.id">
                <label class="pick" :class="{ on: p.id === form.shipping }">
                  <input v-model="form.shipping" type="radio" name="shipping" :value="p.id" class="pick-radio">
                  <span class="pick-mark" aria-hidden="true"></span>
                  <span class="pick-body">
                    <strong>{{ p.label }}</strong>
                    <span class="pick-note muted">{{ p.note }}</span>
                  </span>
                </label>
              </li>
            </ul>

            <ul class="pick-list pick-list--pay">
              <li v-for="p in paymentOptions" :key="p.id">
                <label class="pick" :class="{ on: p.id === form.payment }">
                  <input v-model="form.payment" type="radio" name="payment" :value="p.id" class="pick-radio">
                  <span class="pick-mark" aria-hidden="true"></span>
                  <span class="pick-body">
                    <strong>{{ p.label }}</strong>
                    <span class="pick-note muted">{{ p.note }}</span>
                  </span>
                </label>
              </li>
            </ul>

            <label class="field note-field">
              <span>{{ t.coNoteTitle }}</span>
              <textarea v-model.trim="form.note" rows="4" maxlength="800"
                        :placeholder="t.coNoteHint"></textarea>
            </label>

            <!-- 蜜罐。后端 submitOrder 里 website 非空就当机器人：回一个格式对得上、
                 但库里不存在的假单号，既不落库也不告诉它蜜罐露馅了。
                 用 CSS 挪出视口而不是 display:none——后者稍好一点的爬虫会跳过 -->
            <div class="trap" aria-hidden="true">
              <input v-model="form.website" type="text" name="website" tabindex="-1" autocomplete="off">
            </div>
          </section>
        </div>

        <!-- ---------- 汇总 ---------- -->
        <aside class="co-summary">
          <div class="summary-card">
            <div class="summary-head">
              <h2>{{ t.coSummaryTitle }}</h2>
              <NuxtLink :to="localePath('/cart')" class="link-more">{{ t.coSummaryEdit }}</NuxtLink>
            </div>

            <ul class="sum-items">
              <li v-for="item in items" :key="cartLineKey(item)" class="sum-item">
                <span class="sum-thumb">
                  <img v-if="item.image" :src="item.image" :alt="item.title" loading="lazy">
                  <span v-else class="sum-noimg"></span>
                  <em class="sum-qty">{{ item.qty }}</em>
                </span>
                <span class="sum-text">
                  <strong>{{ item.title }}</strong>
                  <!-- 同一个商品的两个型号在这里是两行，不写明型号就分不清 -->
                  <small v-if="item.comboName" class="sum-combo">{{ item.comboName }}</small>
                  <small class="muted">{{ item.code }}</small>
                </span>
                <span class="sum-line">
                  {{ item.price > 0 ? money(item.price * item.qty) : t.inquire }}
                </span>
              </li>
            </ul>

            <div class="sum-row">
              <span>{{ t.subtotal }}</span>
              <span>{{ money(totals.subtotal) }}</span>
            </div>
            <p v-if="totals.inquireOnly" class="sum-note muted">
              {{ fill(t.cartInquireNote, { n: totals.inquireOnly }) }}
            </p>

            <!--
              ⚠ 这一页的金额一律是**站点本币**，不跟顶栏的币种切换器走。

              站上不收款，订单是「下单请求」，金额必须有一个稳定口径——否则汇率一变，
              这张单到底是 €184 还是 $200 就成了糊涂账，运营在管理台也没法把一堆
              不同币种的订单加出总额。落库的 u_s_order.currency 也是本币。

              买家从购物车过来会看到金额跳变（那边是按他选的币种显示的）。
              下面 sum-approx 那一行就是为此存在的：把「你看到的那个数」原样摆出来，
              并说清哪个才作数。藏起来或者不说，才是真的坑人。
            -->
            <div class="sum-total">
              <span>{{ t.total }}</span>
              <span class="total-value">{{ money(totals.subtotal) }}</span>
            </div>
            <p v-if="approxLine" class="sum-approx">{{ approxLine }}</p>
            <p class="sum-note muted">{{ t.coTotalNote }}</p>

            <button class="btn btn-block submit" type="submit" :disabled="sending">
              <span v-if="sending" class="spinner" aria-hidden="true"></span>
              {{ sending ? t.coSubmitting : t.coSubmit }}
            </button>

            <p v-if="failed" class="send-err">
              {{ t.coFailed }}
              <a v-if="site.contact?.email" :href="`mailto:${site.contact.email}`">
                {{ site.contact.email }}
              </a>
            </p>

            <!-- 这一句是这一页最重要的一句话：本站不收款 -->
            <p class="disclaimer">{{ t.coDisclaimer }}</p>
          </div>
        </aside>
      </form>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { PAYMENT_PREFS, SHIPPING_PREFS, cartLineKey, formatMoney } from '~/lib/shop'
import type { BuyerAddress } from '~/composables/useBuyer'

const site = await useSiteInfo()
const localePath = useLocalePath()
/*
  ⚠ 提交成功后的跳转要用它，不能用 localePath。

  登录买家去 /orders/{code}，那一支归 buyer. 子域（shared/utils/buyerPaths.ts）。
  相对路径交给 vue-router 的话是一次客户端跳转，根本不发请求到服务器，
  server/middleware/domain-split.ts 那条 301 永远不会触发——结果是买家中心
  被渲染在商城主域下，地址栏和 canonical 都不对，而页面看着完全正常。
*/
const localeHref = useLocaleHref()
const t = useShopCopy(() => site.lang)

const { items, totals, restore: restoreCart, localize: localizeCart, clear } = useCart()
const { submit: submitOrder, restore: restoreOrders } = useOrders()

/*
  ⚠ 这一页刻意**不换算**：money 一律用站点本币。理由写在模板里 sum-total 上面。
    别顺手改成 currency.displayMoney——那样订单金额就跟着买家的选择漂了。
*/
const money = (n: number) => formatMoney(n, site.currency, site.lang)

/*
  「≈ €184.00，最终以 USD 为准」。

  只在买家确实切了别的币种时才出现：没切的时候这一行是废话，
  而废话会稀释掉旁边那句真正要读的付款说明。
*/
const currency = useDisplayCurrency(() => site)
const approxLine = computed(() => {
  if (currency.current.value === currency.base.value) return ''
  const converted = currency.convert(totals.value.subtotal)
  if (converted.currency === currency.base.value) return ''
  const approx = formatMoney(converted.amount, converted.currency, site.lang)
  return `≈ ${approx} · ${fill(t.value.coChargedIn!, { currency: site.currency })}`
})

/* ---------------- 选项 ---------------- */
const SHIP_COPY: Record<string, [string, string]> = {
  sea: ['coShipSea', 'coShipSeaNote'],
  air: ['coShipAir', 'coShipAirNote'],
  express: ['coShipExpress', 'coShipExpressNote'],
  agent: ['coShipAgent', 'coShipAgentNote']
}
const PAY_COPY: Record<string, [string, string]> = {
  tt: ['coPayTt', 'coPayTtNote'],
  lc: ['coPayLc', 'coPayLcNote'],
  paypal: ['coPayPaypal', 'coPayPaypalNote'],
  other: ['coPayOther', 'coPayOtherNote']
}

const shippingOptions = computed(() =>
  SHIPPING_PREFS.map(id => ({
    id,
    label: t.value[SHIP_COPY[id]![0]]!,
    note: t.value[SHIP_COPY[id]![1]]!
  }))
)
const paymentOptions = computed(() =>
  PAYMENT_PREFS.map(id => ({
    id,
    label: t.value[PAY_COPY[id]![0]]!,
    note: t.value[PAY_COPY[id]![1]]!
  }))
)

/* ---------------- 表单 ---------------- */
const form = reactive({
  name: '', email: '', company: '', phone: '',
  country: '', city: '', line1: '', line2: '', region: '', postal: '',
  shipping: SHIPPING_PREFS[0] as string,
  payment: PAYMENT_PREFS[0] as string,
  note: '',
  website: '' // 蜜罐
})

const errors = reactive<Record<string, string>>({})
const sending = ref(false)
const failed = ref(false)
const ready = ref(false)

/**
 * 上一次填过的联系方式和目的地。
 *
 * 外贸客户往往一周内问好几批货，每次重填一遍公司地址是纯浪费。
 * ⚠ 只存联系方式，**不存备注**——上一单的「目标价 8 美元」跟到下一单上是会出事的。
 */
const BUYER_KEY = 'station-buyer'

function loadBuyer() {
  try {
    const raw = localStorage.getItem(BUYER_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (!saved || typeof saved !== 'object') return
    for (const k of ['name', 'email', 'company', 'phone',
                     'country', 'city', 'line1', 'line2', 'region', 'postal',
                     'shipping', 'payment'] as const) {
      if (typeof saved[k] === 'string' && saved[k]) form[k] = saved[k]
    }
  } catch {
    /* 坏数据：当成没填过 */
  }
}

function saveBuyer() {
  try {
    const { note, website, ...keep } = form
    localStorage.setItem(BUYER_KEY, JSON.stringify(keep))
  } catch {
    /* 隐私模式：下次还得重填，不影响这一单 */
  }
}

/* ---------------- 登录用户的收货地址簿 ---------------- */

/**
 * ⚠ 上面那份 localStorage 台账**没有被取代**。
 *
 * 下单不要求登录（页头第一条），没登录的人仍然只有它。登录的人两份都有，
 * 规则是「本地填过的不覆盖，空着的才用服务端的」——本地那份是他上一单**亲手填的**，
 * 拿档案盖掉会让人以为自己填错了。
 *
 * 地址簿本身仍然是加分项：换台电脑登录，这里就能选出上次那个货代仓库。
 */
const { buyer, refresh: refreshBuyer, myAddresses, saveAddress } = useBuyer()

const savedAddresses = ref<BuyerAddress[]>([])
/** 选中的那条地址簿条目；'new' = 不用地址簿里的，自己填 */
const pickedAddressId = ref<number | 'new'>('new')
/** 提交成功后把这次填的地址存回地址簿。⚠ 只对登录的人有意义 */
const saveToBook = ref(false)

/** 下拉里一条的显示名：起过备注名就用它，否则用「国家, 城市 — 收件人」 */
function addrOption(a: BuyerAddress) {
  const place = [a.country, a.city].filter(Boolean).join(', ')
  return a.label ? `${a.label} — ${place}` : `${place} — ${a.contactName}`
}

/** 把地址簿的一条填进表单。⚠ 不动 email：那是账号邮箱，不是收件信息 */
function applyAddress(a: BuyerAddress) {
  pickedAddressId.value = a.id
  form.country = a.country || ''
  form.city = a.city || ''
  form.line1 = a.addrLine1 || ''
  form.line2 = a.addrLine2 || ''
  form.region = a.region || ''
  form.postal = a.postal || ''
  // 联系人和电话只在空着的时候填：u_s_order 上只有一个联系人字段，
  // 而「回复找谁」和「收件人」经常不是同一个人（采购 vs 仓库）。
  // 他已经填了名字就别替他改主意，空着的话用收件人顶上总比空着强
  if (!form.name) form.name = a.contactName || ''
  if (!form.phone) form.phone = a.phone || ''
  // 选了地址簿里现成的一条，默认就不再重复存一遍
  saveToBook.value = false
}

function onPickAddress() {
  if (pickedAddressId.value === 'new') {
    saveToBook.value = true
    return
  }
  const picked = savedAddresses.value.find(a => a.id === pickedAddressId.value)
  if (picked) applyAddress(picked)
}

/**
 * 问一次「我是谁」，登录的话把档案和地址簿取回来。
 *
 * ⚠ 不 await 在 onMounted 里挡住 ready：这一页的主体（清单、金额）不依赖登录状态，
 *   为了一次可能 401 的往返让整页多转一圈不值得。
 */
async function loadAccount() {
  await refreshBuyer()
  if (!buyer.value) return

  // 空着的才填，见 BUYER_KEY 那段注释
  if (!form.name) form.name = buyer.value.name || ''
  if (!form.email) form.email = buyer.value.email || ''
  if (!form.company) form.company = buyer.value.company || ''
  if (!form.phone) form.phone = buyer.value.phone || ''

  try {
    savedAddresses.value = await myAddresses()
  } catch {
    // 地址簿取不回来（网络、会话刚过期）就当没有：表单照样能填、单照样能下
    return
  }

  if (!savedAddresses.value.length) {
    // 还没存过地址：这一单填的正好用来开张。勾默认打上，但看得见、能取消
    saveToBook.value = true
    return
  }
  // 本地已经有上一单的地址了就别自动盖掉（同上）；空着才用默认地址填
  if (!form.country && !form.city && !form.line1) {
    const def = savedAddresses.value.find(a => a.isDefault) || savedAddresses.value[0]
    if (def) applyAddress(def)
  }
}

/**
 * 下单成功之后把这次的地址存回地址簿。
 *
 * ⚠ 整个函数在 try 里吞异常：单**已经落库了**，这一步失败只是地址簿少一条，
 *   绝不能让它把买家挡在「已发送」页面之外，或者让他以为单没提交出去。
 *
 * ⚠ 选了现成一条又勾了保存 = 更新那一条（带 id），不是再插一条。
 *   label 要原样带回去：后端那条 update 是直接赋值不 coalesce，不带就等于把备注名清空了。
 */
async function rememberAddress() {
  if (!saveToBook.value || !buyer.value) return
  const picked = typeof pickedAddressId.value === 'number'
    ? savedAddresses.value.find(a => a.id === pickedAddressId.value)
    : undefined
  try {
    await saveAddress({
      id: picked?.id,
      label: picked?.label ?? '',
      contactName: form.name,
      phone: form.phone,
      addrLine1: form.line1,
      addrLine2: form.line2,
      city: form.city,
      region: form.region,
      postal: form.postal,
      country: form.country
    })
  } catch {
    /* 地址簿少一条而已，单已经提交成功了 */
  }
}

onMounted(() => {
  restoreCart()
  localizeCart(site.lang)
  restoreOrders()
  loadBuyer()
  ready.value = true
  void loadAccount()
})

/* ---------------- 提交 ---------------- */

function validate() {
  for (const k of Object.keys(errors)) delete errors[k]
  if (!form.name) errors.name = t.value.coBadName!
  // 和后端同一条规则（StationOpenServiceImpl 里那个正则），
  // 免得前端放过去、后端再报一次错
  if (!form.email || !/^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/.test(form.email)) {
    errors.email = t.value.coBadEmail!
  }
  if (!form.country) errors.country = t.value.coBadCountry!
  return !Object.keys(errors).length
}

/*
 * ---------------------------------------------------------------------------
 * 这里原来有 buildMessage() 和 enLabel()，2026-09-06 删了
 * ---------------------------------------------------------------------------
 *
 * 那两个函数把收货地址、运输付款偏好、N 行商品明细、买家备注**拼成一段纯文本**，
 * 塞进询盘的 message 字段提交。因为后端那一列上限 2000 字而且是直接截断，
 * 前台还得自己算预算、主动丢掉明细的尾巴，并在断口处写一句
 * 「... 3 more item(s), see request RFQ-2026-1043」。
 *
 * 两个后果，都是真的在丢东西：
 *
 *   1. 清单一长，尾巴上的商品行就进不了库、也进不了商家收到的通知邮件。
 *      商家不知道买家还问了什么，买家以为问了。
 *   2. 那句话里的单号是**前台本机递增**出来的，只存在买家浏览器的 localStorage 里，
 *      服务端从来没收到过。商家照着它去查，什么都查不到。
 *
 * 现在走 POST /openapi/site/order：这些本来就是结构化数据，后端有列装它们
 * （u_s_order + u_s_orderitem），一行都不会丢，单号也由后端发。
 * 详细取舍见 app/composables/useOrders.ts 的文件头。
 */

async function submit() {
  if (sending.value || !items.value.length) return
  if (!validate()) {
    // 滚到第一个出错的字段。表单有三屏高，不滚的话买家看不到红字，
    // 只会觉得按钮点了没反应
    document.querySelector('.field input.bad')?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    return
  }

  sending.value = true
  failed.value = false

  // 单号不在这里算了——由后端发（DD + 流水），提交成功才有。
  // 以前是本机递增一个 RFQ-2026-1043，服务端从来没见过那个号
  let orderCode: string | null
  try {
    orderCode = await submitOrder(items.value, {
      contact: { name: form.name, email: form.email, company: form.company, phone: form.phone },
      address: {
        line1: form.line1, line2: form.line2, city: form.city,
        region: form.region, postal: form.postal, country: form.country
      },
      shipping: form.shipping,
      payment: form.payment,
      note: form.note,
      currency: site.currency,
      lang: site.lang,
      website: form.website
    })
  } catch {
    failed.value = true
    sending.value = false
    return
  }

  // 被限流（后端回 429）。coFailed 那句话是「请重试，或直接邮件联系我们」，
  // 对这种情况也说得通——而且退路（邮箱）就显示在它旁边。
  //
  // ⚠ 蜜罐命中时后端回的是 200 + 一个库里不存在的假单号，这里看起来和成功一样，
  //   那是故意的：回错误等于告诉机器人蜜罐露馅了。真人不会填到那个隐藏输入框。
  if (!orderCode) {
    failed.value = true
    sending.value = false
    return
  }

  // 顺序：落库成功了才记回执，记完才清空清单。
  // 反过来的话网络失败时买家会看到一张「已发送」的单子，而商家什么都没收到——
  // 那比直接报错糟得多。回执由 useOrders().submit() 内部记，这里不用再记一次
  saveBuyer()
  // 存地址簿放在这里、而且 await：跳走之后 Nuxt 会把这个组件卸掉，
  // 不等的话这次请求可能连发都没发出去。它自己吞异常，不会挡住下面的跳转
  await rememberAddress()
  clear()
  sending.value = false

  /*
    回执页按登录与否分流，两条路都不能换：

      登录买家 → buyer./orders/{code}，服务端查库，换台设备也看得到、状态是活的。
      游客     → 主域 /order-received/{code}，读这台浏览器里的本地回执。

    ⚠ 游客**不能**送去 /orders/{code}。那一支归 buyer. 子域，而本地回执写在
      主域的 localStorage 里、按源隔离——客户端跳过去当场看着是好的，一刷新
      被 301 到 buyer. 就变成「找不到这张单」。详见 pages/order-received/[code].vue。
  */
  const href = localeHref(buyer.value ? `/orders/${orderCode}` : `/order-received/${orderCode}`)
  // 跨 host 时 href 是绝对地址，必须走整页跳转：vue-router 只认当前 origin，
  // 交给它会把地址栏拼成 /https://buyer.…
  await navigateTo(href, { external: /^https?:/.test(href) })
}

useHead({
  title: `${t.value.coTitle} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.co-page { padding: 40px 20px 30px; }

.co-head { margin-bottom: 34px; }
.co-back { display: inline-block; margin-bottom: 12px; }
.co-head h1 { font-size: clamp(2rem, 4vw, 2.8rem); margin-bottom: 10px; }
.co-head p { margin: 0; }

.notice {
  text-align: center;
  padding: 60px 20px 80px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.notice h2 { font-size: 1.5rem; margin-bottom: 10px; }
.notice p { margin: 0 0 24px; }

.co-layout {
  display: grid;
  grid-template-columns: 1fr 370px;
  gap: 30px;
  align-items: start;
}
.sk-steps { height: 620px; }
.sk-summary { height: 480px; }

/* ---------------- 步骤面板 ---------------- */
.co-steps { display: flex; flex-direction: column; gap: 20px; }

.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 26px 26px 12px;
}
.panel-head { margin-bottom: 20px; }
.step-no {
  display: inline-block;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--primary);
  margin-bottom: 6px;
}
.panel-head h2 { font-size: 1.35rem; margin-bottom: 6px; }
.panel-sub { margin: 0; font-size: 0.86rem; }

.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0 18px; }
@media (max-width: 700px) {
  .grid-2, .grid-3 { grid-template-columns: 1fr; }
}

.field .opt { font-style: normal; color: var(--text-soft); font-size: 0.92em; }
.field input.bad, .field textarea.bad { border-color: #d92d20; }
.err { display: block; margin-top: 5px; font-style: normal; font-size: 0.78rem; color: #d92d20; }

/* 地址簿：下拉 + 「存回地址簿」那个勾。两个都只对登录用户出现 */
.addr-pick select { cursor: pointer; }
.check {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 2px 0 4px;
  font-size: 13px;
  color: var(--text-muted);
}
.check input { width: auto; }

/* ---------------- 单选卡片 ---------------- */
.pick-list {
  list-style: none;
  padding: 0;
  margin: 0 0 6px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.pick-list--pay { margin-top: 12px; }
@media (max-width: 700px) {
  .pick-list { grid-template-columns: 1fr; }
}

.pick {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  height: 100%;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.pick:hover { border-color: var(--primary-light); }
.pick.on { border-color: var(--primary); background: rgba(11, 114, 171, 0.04); }

/* 真的 radio 藏起来但仍在无障碍树里，键盘和读屏都还能用 */
.pick-radio {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
}
.pick-mark {
  flex: none;
  width: 17px;
  height: 17px;
  margin-top: 3px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.pick.on .pick-mark {
  border-color: var(--primary);
  border-width: 5px;
}
/* 键盘 focus 时要看得出焦点在哪——藏了原生 radio 就得自己画 */
.pick-radio:focus-visible + .pick-mark {
  box-shadow: 0 0 0 3px rgba(11, 114, 171, 0.3);
}
.pick-body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.pick-body strong { font-size: 0.92rem; }
.pick-note { font-size: 0.78rem; line-height: 1.45; }

.note-field { margin-top: 18px; }

/* 蜜罐：挪出视口，不用 display:none */
.trap { position: absolute; left: -9999px; width: 1px; height: 1px; overflow: hidden; }

/* ---------------- 汇总 ---------------- */
.co-summary { position: sticky; top: 88px; }
.summary-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 26px 24px;
}
.summary-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.summary-head h2 { font-size: 1.3rem; }

.sum-items {
  list-style: none;
  padding: 0;
  margin: 0 0 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  /* 清单可以很长，右栏不能跟着长到几屏 */
  max-height: 300px;
  overflow-y: auto;
}
.sum-item { display: grid; grid-template-columns: 46px 1fr auto; gap: 12px; align-items: center; }
.sum-thumb {
  position: relative;
  width: 46px;
  height: 46px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-muted);
}
.sum-thumb img { width: 100%; height: 100%; object-fit: cover; }
.sum-noimg { display: block; width: 100%; height: 100%; background: linear-gradient(135deg, #ecf3f8, #dae7f0); }
.sum-qty {
  position: absolute;
  right: -4px;
  bottom: -4px;
  min-width: 18px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--primary);
  color: #fff;
  font-size: 0.62rem;
  font-style: normal;
  font-weight: 700;
  text-align: center;
  line-height: 18px;
}
.sum-combo {
  display: block;
  font-weight: 600;
  color: var(--primary);
}
.sum-text { display: flex; flex-direction: column; min-width: 0; }
.sum-text strong {
  font-size: 0.86rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sum-text small { font-size: 0.72rem; }
.sum-line { font-size: 0.84rem; white-space: nowrap; }

.sum-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  font-size: 0.92rem;
  margin-bottom: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}
.sum-note { margin: 0 0 10px; font-size: 0.76rem; line-height: 1.5; }
/*
  换算参考价。比 sum-note 显眼一点：买家刚从购物车过来，看到的是另一个数字，
  这一行是解释那个跳变的唯一地方，灰到看不见等于没写。
*/
.sum-approx {
  margin: 0 0 8px;
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--ink-soft);
}

.sum-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 600;
}
.total-value { color: var(--primary); }

.submit { margin-top: 16px; }

.send-err { margin: 12px 0 0; font-size: 0.82rem; color: #b3261e; }
.send-err a { text-decoration: underline; }

.disclaimer {
  margin: 16px 0 0;
  padding: 14px 16px;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  font-size: 0.76rem;
  line-height: 1.55;
  color: var(--text-muted);
}

.spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  .spinner { animation-duration: 2.4s; }
}

@media (max-width: 980px) {
  .co-layout { grid-template-columns: 1fr; }
  .co-summary { position: static; }
}
</style>
