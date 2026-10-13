<!--
  下单回执 —— **游客那一份**，挂在商城主域上。

  ## 为什么不复用 /orders/[id]

  `/orders` 在 `shared/utils/buyerPaths.ts` 的白名单里，归 `buyer.` 子域。
  而游客的回执存在 `localStorage`（`useOrders` 的 `station-orders`），
  localStorage 按源隔离——回执写在主域，`buyer.` 上读出来永远是空。

  结账页提交成功后 `navigateTo('/orders/DD…')` 是**客户端跳转**，当场看得见；
  但只要一刷新、一收藏、一转发，`server/middleware/domain-split.ts` 就会 301 到
  `buyer.`，那边找不到这张单，页面显示「找不到这张单」。登录买家不受影响
  （他那份从服务端查）。所以游客要有一个**留在主域上**的回执地址，就是这一页。

  ⚠ 结账页因此按登录与否分流，见 `pages/checkout/index.vue` 结尾那段。
    改那里记得连这一页一起想。

  ## 这一页顺手解决的第二件事

  游客的 `/orders` 列表同样在 `buyer.` 上、同样是空的——也就是说他下第二单之后，
  第一单的地址就再也找不回来了。所以下面有一块「这台浏览器上的其他询价」，
  读的是同一份本地台账。不动 BUYER_PATHS 的前提下，这是把那条路补上的最短做法。

  ## 本地没有这张单的时候：凭邮箱找回

  下面 `v-else-if="!order"` 那一支**不是错误页**，是一个查单表单。
  下单确认信（`BuyerMailer.sendOrderReceiptAfterCommit`）里的链接就指到这一页，
  而买家点开它的设备多半不是下单那台——本地台账是空的。这时候让他报一次
  下单邮箱，走 `POST /openapi/site/order/lookup` 把单取回来，顺手记进本地台账。

  ⚠ 邮箱那一关**就是全部的鉴权**：单号是 `DD` + 流水、可枚举。所以

    - 对不上的时候只说「没找到」，**不要**分开提示「单号不存在」和「邮箱不对」——
      分开说等于确认了这个单号是真的，剩下的只是换邮箱接着试；
    - 不要自动重试、不要轮询。后端限流按 IP + 按邮箱各一层，而且 Redis 挂了会拒绝，
      替买家多打几次只会把他自己的额度用光。

  ## 「保存这张单」那一块

  下单不要求登录（刻意的，挡一道登录会掉单），代价是这份回执只活在这台浏览器上。
  所以这里给一条出路：为下单时填的那个邮箱设个密码。

  ⚠ 它之所以成立，是因为后端 `SiteOrderService.submit` 已经按邮箱
    `resolveByEmail` 建好了 `u_s_buyer` 档案、这张单的 `buyercode` 就挂在上面，
    而 `BuyerAuthService.register` 设密码走的是**同一行**。所以事后注册
    = 认领已有的档案，历史单自动出现在 `/orders` 里，不需要任何迁移代码。

  ⚠ 注册成功 ≠ 能登录，还要点验证信里的链接——这一句必须写在界面上，
    否则买家会直接去登录、看到「邮箱还没验证」，以为没注册成功又来一遍。

  ⚠ 后端对「这个邮箱注册过没有」**一律不告诉**（已有账号的走「发提醒信」那一支，
    回同一句话）。所以这里也只有一种成功文案，不要按邮箱状态分支——
    分支了，这个公开接口就成了客户名单探测器。

  ## 不给收录

  同购物车 / 结账页：页面上 `robots: noindex`，`server/routes/robots.txt.ts`
  里还有一条 `Disallow: /order-received`。两处都要：Disallow 管「别来爬」，
  noindex 管「爬到了别收」，被外链指到的地址只有后者拦得住。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap orc-page">
      <!-- 挂载前：回执在 localStorage 里，连这张单在不在都还不知道 -->
      <div v-if="!ready" aria-hidden="true">
        <div class="skeleton-block sk-head"></div>
        <div class="skeleton-block sk-body"></div>
      </div>

      <!--
        换了台设备、或者清过浏览器数据。这不是错误页——报一次下单邮箱就能把单取回来，
        理由和那三条不能破的规矩写在文件头「本地没有这张单的时候」。
      -->
      <div v-else-if="!order" class="orc-find">
        <h1>{{ t.orcFindTitle }}</h1>
        <p class="muted">{{ fill(t.orcFindLead, { ref: orderRef }) }}</p>

        <form class="find-form" @submit.prevent="findOrder">
          <label class="field">
            <span>{{ t.coEmail }}</span>
            <input v-model.trim="findEmail" type="email" autocomplete="email"
                   required :disabled="finding">
          </label>

          <!-- ⚠ 这里显示的是后端原话。它对「没这张单」和「邮箱不对」是同一句，
               别在前台按情况改写成更「有用」的提示 -->
          <p v-if="findError" class="save-err">{{ findError }}</p>

          <button type="submit" class="btn" :disabled="finding">
            {{ finding ? t.acctWorking : t.orcFindCta }}
          </button>
        </form>

        <!-- 登录着的人不用报邮箱，他那份在服务端按会话查得到 -->
        <p v-if="buyer" class="find-alt">
          <NuxtLink :to="localeHref('/orders/' + orderRef)" class="link-more">
            {{ t.orcSaveOpen }}
          </NuxtLink>
        </p>

        <NuxtLink :to="localePath('/products')" class="link-more">{{ t.explore }}</NuxtLink>
      </div>

      <template v-else>
        <!--
          ⚠ 抬头分两副面孔，按状态。

          这一页最初只在「刚提交完」那一刻出现，所以写死了一个打勾 +「我们收到了」。
          接上匿名查单之后它也会被用来看**很久以前**那张单——那时候还说「我们收到了、
          一个工作日内回复」就是假的：那张单可能昨天就确认、今天已经发货了。
          所以只有 submitted 才保留原来那副样子，其余状态换成标题 + 状态标签。
        -->
        <header class="orc-head">
          <span v-if="fresh" class="orc-tick" aria-hidden="true">✓</span>
          <h1>{{ fresh ? t.orcTitle : t.ordDetailTitle }}</h1>
          <p class="orc-ref">{{ fill(t.ordRef, { ref: order.ref }) }}</p>
          <p v-if="fresh" class="muted orc-lead">{{ t.orcLead }}</p>
          <p v-else class="orc-state">
            <OrderStatusPill :status="order.status" :lang="site.lang" />
            <span class="muted">
              {{ fill(t.ordPlacedOn, { date: formatDate(order.placedAt, site.lang) }) }}
            </span>
          </p>
        </header>

        <div class="orc-layout">
          <div class="orc-main">
            <!-- ---------- 明细 ---------- -->
            <section class="panel">
              <h2>{{ t.ordItems }}</h2>
              <ul class="sum-items">
                <li v-for="item in order.items" :key="lineKey(item)" class="sum-item">
                  <span class="sum-thumb">
                    <img v-if="item.image" :src="item.image" :alt="item.title" loading="lazy">
                    <span v-else class="sum-noimg"></span>
                    <em class="sum-qty">{{ item.qty }}</em>
                  </span>
                  <span class="sum-text">
                    <strong>{{ item.title }}</strong>
                    <small v-if="item.comboName" class="sum-combo">{{ item.comboName }}</small>
                    <small class="muted">{{ item.code }}</small>
                  </span>
                  <span class="sum-line">
                    {{ item.price > 0 ? money(item.price * item.qty) : t.inquire }}
                  </span>
                </li>
              </ul>

              <div class="sum-total">
                <span>{{ t.subtotal }}</span>
                <span class="total-value">{{ money(order.subtotal) }}</span>
              </div>
              <p v-if="order.inquireOnly" class="sum-note muted">
                {{ fill(t.cartInquireNote, { n: order.inquireOnly }) }}
              </p>
              <p class="sum-note muted">{{ t.coTotalNote }}</p>
            </section>

            <!--
              ---------- 保存这张单 ----------
              登录买家不需要这一块：这张单已经按会话挂在他账号上了，
              给他一条去买家中心的链接就行（跨 host，用 localeHref 拼绝对地址）。
            -->
            <section v-if="buyer" class="panel">
              <h2>{{ t.orcSaveTitle }}</h2>
              <p class="muted">{{ t.orcSaveSignedIn }}</p>
              <NuxtLink :to="localeHref('/orders/' + order.ref)" class="link-more">
                {{ t.orcSaveOpen }}
              </NuxtLink>
            </section>

            <section v-else class="panel panel--save">
              <h2>{{ t.orcSaveTitle }}</h2>

              <!-- 成功之后表单收起来：这时候只剩「去查邮箱」这一件事 -->
              <template v-if="saved">
                <p class="save-ok">{{ t.acctCheckMail }}</p>
                <p class="muted">{{ t.acctResendLead }}</p>
                <button type="button" class="btn btn-ghost" :disabled="busy || resent"
                        @click="resend">
                  {{ resent ? t.acctResent : t.acctResendCta }}
                </button>
              </template>

              <form v-else @submit.prevent="claim">
                <p class="muted">{{ fill(t.orcSaveLead, { email: order.contact.email }) }}</p>

                <label class="field">
                  <span>{{ t.acctPassword }}</span>
                  <!-- 邮箱不给改：它是这张单上的那个，改了就认领不到这张单了。
                       真要换个邮箱注册的话走 /register，那一页本来就是干这个的 -->
                  <input v-model="password" type="password" autocomplete="new-password"
                         required :minlength="MIN" :disabled="busy">
                  <small class="muted">{{ fill(t.acctPwdMin, { n: MIN }) }}</small>
                </label>

                <p v-if="error" class="save-err">{{ error }}</p>

                <button type="submit" class="btn" :disabled="busy">
                  {{ busy ? t.acctWorking : t.orcSaveCta }}
                </button>
              </form>
            </section>

            <!-- ---------- 这台浏览器上的其他询价 ---------- -->
            <section v-if="others.length" class="panel">
              <h2>{{ t.orcOther }}</h2>
              <ul class="other-list">
                <li v-for="o in others" :key="o.ref">
                  <NuxtLink :to="localePath('/order-received/' + o.ref)">
                    {{ fill(t.ordRef, { ref: o.ref }) }}
                  </NuxtLink>
                  <span class="muted">{{ formatDate(o.placedAt, site.lang) }}</span>
                </li>
              </ul>
            </section>
          </div>

          <!-- ---------- 你发给我们的信息 ---------- -->
          <aside class="orc-side">
            <section class="panel">
              <h2>{{ t.orcDetails }}</h2>

              <div class="mini">
                <h3>{{ t.ordContact }}</h3>
                <p>{{ order.contact.name }}</p>
                <p v-if="order.contact.company">{{ order.contact.company }}</p>
                <p>{{ order.contact.email }}</p>
                <p v-if="order.contact.phone">{{ order.contact.phone }}</p>
              </div>

              <div class="mini">
                <h3>{{ t.ordAddress }}</h3>
                <p v-if="order.address.line1">{{ order.address.line1 }}</p>
                <p v-if="order.address.line2">{{ order.address.line2 }}</p>
                <p v-if="cityLine">{{ cityLine }}</p>
                <p>{{ order.address.country }}</p>
              </div>

              <div class="mini">
                <h3>{{ t.ordShipping }}</h3>
                <p>{{ shipLabel }}</p>
              </div>

              <div class="mini">
                <h3>{{ t.ordPayment }}</h3>
                <p>{{ payLabel }}</p>
              </div>

              <div v-if="order.note" class="mini">
                <h3>{{ t.ordNote }}</h3>
                <p class="note-body">{{ order.note }}</p>
              </div>

              <!-- 从引导式下单来的单：当时逐项选了什么（logo、包装、认证这些不在商品行里） -->
              <div v-if="order.wizard?.length" class="mini">
                <h3>{{ t.wzSummary }}</h3>
                <p v-for="r in order.wizard" :key="r.group + r.field" class="note-body">
                  <span class="muted">{{ r.field }}</span> · {{ r.value }}
                </p>
              </div>
            </section>

            <p class="muted orc-local">{{ t.orcLocalOnly }}</p>

            <NuxtLink :to="localePath('/products')" class="btn btn-ghost btn-block">
              {{ t.cartKeepShopping }}
            </NuxtLink>
          </aside>
        </div>
      </template>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { cartLineKey, formatDate, formatMoney, type Order, type OrderItem } from '~/lib/shop'

const site = await useSiteInfo()
const route = useRoute()
const localePath = useLocalePath()
// 「在我的账号里打开」跨 host（/orders 归 buyer. 子域），必须是绝对地址——
// 客户端路由不经过 domain-split 中间件，相对路径会把买家中心渲染在主域下
const localeHref = useLocaleHref()
const t = useShopCopy(() => site.lang)

/** 和后端 station.buyer.password-min-length 的默认值对齐（同 register.vue） */
const MIN = 8

const orderRef = computed(() => String(route.params.code || ''))

const { restore: restoreOrders, find, sorted, lookup } = useOrders()
const { buyer, refresh: refreshBuyer, register, resendVerify } = useBuyer()

const ready = ref(false)
const order = ref<Order | null>(null)

const password = ref('')
const busy = ref(false)
const error = ref('')
const saved = ref(false)
const resent = ref(false)

/* 本地没有这张单时的「凭邮箱找回」。和上面那组是两回事，别合并状态 */
const findEmail = ref('')
const finding = ref(false)
const findError = ref('')

/**
 * ⚠ 这一页**只读本地回执**，不调 `useOrders().loadOne()`。
 *
 * loadOne 会去服务端取「活的」状态，那是 `/orders/[id]` 的活儿——那一页在
 * buyer. 上、给登录买家看进度。这一页是刚下完单的回执，要的就是这台浏览器里
 * 那一份快照；登录买家落在这儿（多半是翻到了旧书签）只给一条去买家中心的链接。
 */
onMounted(async () => {
  restoreOrders()
  order.value = find(orderRef.value)
  ready.value = true
  // 本地回执是下单时那种语言的快照。切过语言就凭回执里的邮箱去服务端换一份当前语言的；
  // 换不到（限流、断网）就还显示本地那份
  const cached = order.value
  if (cached && cached.lang !== site.lang && cached.contact?.email) {
    try {
      order.value = await lookup(orderRef.value, cached.contact.email, site.lang)
    } catch {
    }
  }
  // 不 await 在 ready 前面：这一页的主体不依赖登录状态，
  // 为一次多半会 401 的往返让整页多转一圈不值得
  try {
    await refreshBuyer()
  } catch {
    /* 问不出来就当没登录：下面那块「设个密码」对游客本来就是对的 */
  }
})

/**
 * 凭单号 + 下单邮箱把单取回来。
 *
 * ⚠ 失败只显示后端那句话，**不要**按情况改写：它对「没这张单」和「邮箱不对」
 *   故意是同一句，分开说就等于告诉对方这个单号是真的（见文件头）。
 * ⚠ 失败也**不要**自动重试：后端按 IP 和按邮箱各有一层限流，替买家多打几次
 *   只会把他自己的额度用光。
 */
async function findOrder() {
  if (finding.value || !findEmail.value) return
  finding.value = true
  findError.value = ''
  try {
    order.value = await lookup(orderRef.value, findEmail.value, site.lang)
  } catch (e: unknown) {
    findError.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    finding.value = false
  }
}

/** 同一张单的两行（同商品不同型号）靠型号编码区分，和清单用同一个判据 */
const lineKey = (item: OrderItem) => cartLineKey(item)

const money = (n: number) => formatMoney(n, order.value?.currency || site.currency, site.lang)

const cityLine = computed(() => {
  const a = order.value?.address
  if (!a) return ''
  return [a.city, a.region, a.postal].filter(Boolean).join(', ')
})

/**
 * 这张单是不是「刚提交、还没人处理」。抬头按它分两副面孔，理由见模板里那段注释。
 *
 * ⚠ 判据是状态，不是「这次是查回来的还是本地的」：本地回执的状态永远停在
 *   submitted（它没有任何机制会被推进），拿来源当判据的话，一张早就发了货的单
 *   只要本地台账里还留着一份，就会一直显示「我们收到了」。
 */
const fresh = computed(() => (order.value?.status || 'submitted') === 'submitted')

/** 这台浏览器上的其他询价，最多列五条——这一页的主角是刚下的那张单 */
const others = computed(() =>
  sorted.value.filter(o => o.ref !== orderRef.value).slice(0, 5)
)

/*
  运输 / 付款偏好落库存的是 id（sea、tt），要翻成当前语言的说法。
  ⚠ 认不出来的 id 原样显示，不要显示空白：老单子上可能有已经删掉的选项，
    一个陌生的词至少还能查，一片空白只会让人以为这一栏丢了。
*/
const SHIP_LABEL: Record<string, string> = {
  sea: 'coShipSea', air: 'coShipAir', express: 'coShipExpress', agent: 'coShipAgent'
}
const PAY_LABEL: Record<string, string> = {
  tt: 'coPayTt', lc: 'coPayLc', paypal: 'coPayPaypal', other: 'coPayOther'
}
const shipLabel = computed(() => {
  const id = order.value?.shipping || ''
  return t.value[SHIP_LABEL[id] || ''] || id
})
const payLabel = computed(() => {
  const id = order.value?.payment || ''
  return t.value[PAY_LABEL[id] || ''] || id
})

/**
 * 为这张单上的邮箱设密码 = 认领已经存在的那一行客户档案（见文件头）。
 *
 * ⚠ 成功文案只有一种。后端对「这个邮箱注册过没有」一律不表态，
 *   这里跟着不表态，否则就是把客户名单探测器做在了公开页上。
 */
async function claim() {
  const o = order.value
  if (busy.value || !o) return
  busy.value = true
  error.value = ''
  try {
    await register({
      email: o.contact.email,
      password: password.value,
      name: o.contact.name,
      company: o.contact.company,
      phone: o.contact.phone
    })
    saved.value = true
    // 密码不留在内存里
    password.value = ''
  } catch (e: unknown) {
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

async function resend() {
  const o = order.value
  if (busy.value || !o) return
  busy.value = true
  error.value = ''
  try {
    await resendVerify(o.contact.email)
    // 只允许点一次：后端对同一个邮箱有每天 5 封的上限，
    // 连点十下的结果是他自己把额度用完
    resent.value = true
  } catch (e: unknown) {
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

useHead({
  title: `${t.value.orcTitle} | ${site.companyName}`,
  // 每个访客各自的页面，不给收录。robots.txt 里还有一条 Disallow，两处都要
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.orc-page { padding: 40px 20px 30px; }

.sk-head { height: 150px; margin-bottom: 24px; }
.sk-body { height: 520px; }

/* 凭邮箱找回那一屏。窄一点、居中：它只有一个输入框，铺满整页会很空 */
.orc-find {
  max-width: 520px;
  margin: 0 auto;
  padding: 50px 26px 60px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}
.orc-find h1 { font-size: 1.6rem; margin-bottom: 10px; }
.orc-find > p { margin: 0 0 22px; font-size: 0.9rem; line-height: 1.6; }
.find-form { margin-bottom: 18px; }
.find-form .btn { width: 100%; justify-content: center; }
.find-alt { margin: 0 0 14px; font-size: 0.86rem; }

/* ---------------- 抬头 ---------------- */
.orc-head { margin-bottom: 30px; }
.orc-tick {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 14px;
  border-radius: 50%;
  background: rgba(11, 114, 171, 0.1);
  color: var(--primary);
  font-size: 1.3rem;
  font-weight: 700;
}
.orc-head h1 { font-size: clamp(1.9rem, 4vw, 2.6rem); margin-bottom: 8px; }
.orc-ref {
  margin: 0 0 10px;
  font-size: 0.86rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--primary);
}
.orc-lead { margin: 0; max-width: 56ch; }
.orc-state {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 0;
  font-size: 0.86rem;
}

/* ---------------- 版式 ---------------- */
.orc-layout {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 30px;
  align-items: start;
}
.orc-main { display: flex; flex-direction: column; gap: 20px; }
.orc-side { display: flex; flex-direction: column; gap: 16px; }

.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 24px 26px;
}
.panel h2 { font-size: 1.2rem; margin-bottom: 14px; }
.panel p { margin: 0 0 10px; font-size: 0.88rem; line-height: 1.6; }

/* 「保存这张单」是这一页唯一的行动项，给它一点重量 */
.panel--save { border-color: var(--primary-light); }
.save-ok { font-weight: 600; }
.save-err { color: #d92d20; font-size: 0.82rem; }
.panel--save .btn { margin-top: 4px; }

/* ---------------- 明细 ---------------- */
.sum-items { list-style: none; padding: 0; margin: 0 0 16px; }
.sum-item {
  display: grid;
  grid-template-columns: 54px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.sum-item:first-child { padding-top: 0; }
.sum-thumb { position: relative; width: 54px; height: 54px; }
.sum-thumb img, .sum-noimg {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: var(--radius-sm);
  background: var(--surface-alt);
}
.sum-qty {
  position: absolute;
  top: -7px;
  right: -7px;
  min-width: 19px;
  height: 19px;
  padding: 0 5px;
  border-radius: 10px;
  background: var(--primary);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
  line-height: 19px;
  text-align: center;
}
.sum-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.sum-text strong { font-size: 0.9rem; }
.sum-combo { font-size: 0.76rem; color: var(--primary); }
.sum-text small { font-size: 0.74rem; }
.sum-line { font-size: 0.88rem; font-weight: 600; white-space: nowrap; }

.sum-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding-top: 12px;
  font-weight: 700;
}
.total-value { font-size: 1.25rem; color: var(--primary); }
.sum-note { margin: 8px 0 0; font-size: 0.76rem; line-height: 1.5; }

/* ---------------- 其他询价 ---------------- */
.other-list { list-style: none; padding: 0; margin: 0; }
.other-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border);
  font-size: 0.86rem;
}
.other-list li:last-child { border-bottom: 0; }

/* ---------------- 右栏 ---------------- */
.mini + .mini { margin-top: 18px; }
.mini h3 {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 6px;
}
.mini p { margin: 0; font-size: 0.86rem; line-height: 1.55; }
.note-body { white-space: pre-wrap; }
.orc-local { margin: 0; font-size: 0.78rem; line-height: 1.6; }

@media (max-width: 900px) {
  .orc-layout { grid-template-columns: 1fr; }
}
</style>
