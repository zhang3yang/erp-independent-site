<!--
  询价单详情。

  从 kangwanjia-website-EN 的 pages/orders/[id].vue 搬来：时间轴、明细、右侧信息卡
  的结构照原样，去掉登录门，并且把「重新下单」改成「再询一次」（放回询价清单）、
  把「下载发票」改成「存一份」——这个站没有发票，存下来的是这张询价单本身。

  ## 时间轴的四格现在真的会亮（2026-09-06）

  以前这里写着「本地台账推不动状态，永远只有第一格亮」。**接上了**：
  后端 `SiteOrderService.buyerView` 下发 createTime / confirmTime / shipTime /
  finishTime 四个时间戳，运营在管理台推进订单状态，买家这一页的时间轴就跟着点亮。
  转换在 `useOrders.ts` 的 `fromServer()` 里。

  ⚠ 但这**只对登录买家成立**。匿名买家看到的是本地回执，那份仍然永远停在第一格——
  服务端根本没法把状态推给一个没有身份的浏览器。后面三格摆在那里是在告诉他
  接下来会发生什么，同时也是在提示「注册一个账号就能看到进度」。

  ## 明细要单独取一次

  列表接口不下发订单行（避免 N+1），所以这一页对登录买家会再打一次
  `/account/orders/{code}`。取不到就退回本地回执，见 `useOrders().loadOne()`。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap od-page">
      <!-- 挂载前：连这张单在不在都还不知道 -->
      <div v-if="!ready" aria-hidden="true">
        <div class="skeleton-block sk-bar"></div>
        <div class="skeleton-block sk-body"></div>
      </div>

      <!-- 没有这张单 -->
      <div v-else-if="!order" class="notice">
        <h1>{{ t.ordMissingTitle }}</h1>
        <p class="muted">{{ fill(t.ordMissingLead, { ref: orderRef }) }}</p>
        <NuxtLink :to="localePath('/orders')" class="btn">{{ t.ordMissingCta }}</NuxtLink>
      </div>

      <template v-else>
        <header class="od-head">
          <div class="od-title">
            <NuxtLink :to="localePath('/orders')" class="back" :aria-label="t.ordDetailBack">←</NuxtLink>
            <div>
              <p class="od-ref">{{ fill(t.ordRef, { ref: order.ref }) }}</p>
              <h1>{{ t.ordDetailTitle }}</h1>
              <p class="od-date muted">
                {{ fill(t.ordPlacedOn, { date: formatDate(order.placedAt, site.lang) }) }}
              </p>
            </div>
          </div>

          <div class="od-actions">
            <button type="button" class="btn btn-ghost" @click="download">
              {{ t.ordDownload }}
            </button>
            <button type="button" class="btn" @click="requestAgain">
              {{ t.ordReorder }}
            </button>
          </div>
        </header>

        <p v-if="note" class="action-note" role="status">{{ note }}</p>

        <div class="od-layout">
          <!-- ---------- 左：进度 + 明细 ---------- -->
          <div class="od-main">
            <section class="panel">
              <div class="panel-head">
                <h2>{{ t.ordProgress }}</h2>
                <OrderStatusPill :status="order.status" :lang="site.lang" />
              </div>

              <ol class="track">
                <li
                  v-for="step in timeline"
                  :key="step.stage"
                  class="tstep"
                  :class="{ 'tstep--done': step.done }"
                >
                  <span class="tdot" aria-hidden="true">
                    <svg v-if="step.done" viewBox="0 0 24 24" width="13" height="13">
                      <path
                        d="m6.5 12.4 3.6 3.6L17.5 8"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2.6"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      />
                    </svg>
                  </span>
                  <span class="ttext">
                    <strong>{{ t[`ordStage${cap(step.stage)}`] }}</strong>
                    <span class="twhen muted">
                      {{ step.at ? formatDateTime(step.at, site.lang) : t.ordPending }}
                    </span>
                    <span class="tnote muted">{{ t[`ordStage${cap(step.stage)}Note`] }}</span>
                  </span>
                </li>
              </ol>
            </section>

            <section class="panel">
              <div class="panel-head">
                <h2>{{ t.ordItems }}</h2>
              </div>

              <article v-for="item in order.items" :key="item.code" class="line">
                <NuxtLink :to="shopUrl(localePath(`/products/${item.code}`))" class="line-thumb">
                  <img v-if="item.image" :src="item.image" :alt="item.title" loading="lazy">
                  <span v-else class="line-noimg"></span>
                </NuxtLink>
                <div class="line-body">
                  <h3>
                    <NuxtLink :to="shopUrl(localePath(`/products/${item.code}`))">{{ item.title }}</NuxtLink>
                    <span v-if="item.comboName" class="oi-combo">{{ item.comboName }}</span>
                  </h3>
                  <p class="line-sub muted">{{ item.code }}</p>
                  <p class="line-qty muted">
                    {{ fill(t.qty, { n: item.qty }) }}{{ item.unit ? ' ' + localizeUnit(item.unit, site.lang) : '' }}
                  </p>
                </div>
                <span class="line-price">
                  {{ item.price > 0 ? money(item.price * item.qty) : t.inquire }}
                </span>
              </article>
            </section>
          </div>

          <!-- ---------- 右：汇总 + 联系与目的地 ---------- -->
          <aside class="od-side">
            <section class="panel">
              <div class="panel-head"><h2>{{ t.ordSummary }}</h2></div>

              <div class="sum-row">
                <span>{{ t.quoted }}</span>
                <span>{{ money(order.subtotal) }}</span>
              </div>
              <div v-if="order.inquireOnly" class="sum-row">
                <span>{{ fill(t.ordInquireOnly, { n: order.inquireOnly }) }}</span>
                <span class="muted">—</span>
              </div>
              <div class="sum-total">
                <span>{{ t.total }}</span>
                <span class="total-value">{{ money(order.subtotal) }}</span>
              </div>
              <p class="sum-note muted">{{ t.coTotalNote }}</p>
            </section>

            <section class="panel">
              <div class="mini-head"><h2>{{ t.ordContact }}</h2></div>
              <address class="addr">
                <strong>{{ order.contact.name }}</strong>
                <span v-if="order.contact.company">{{ order.contact.company }}</span>
                <span>{{ order.contact.email }}</span>
                <span v-if="order.contact.phone">{{ order.contact.phone }}</span>
              </address>

              <hr class="rule">

              <div class="mini-head"><h2>{{ t.ordAddress }}</h2></div>
              <address class="addr">
                <span v-if="order.address.line1">
                  {{ order.address.line1 }}<template v-if="order.address.line2">, {{ order.address.line2 }}</template>
                </span>
                <span v-if="cityLine">{{ cityLine }}</span>
                <span>{{ order.address.country }}</span>
              </address>

              <hr class="rule">

              <div class="mini-head"><h2>{{ t.ordShipping }}</h2></div>
              <p class="pref">{{ t[SHIP_LABEL[order.shipping] || ''] || order.shipping }}</p>

              <div class="mini-head"><h2>{{ t.ordPayment }}</h2></div>
              <p class="pref">{{ t[PAY_LABEL[order.payment] || ''] || order.payment }}</p>

              <template v-if="order.note">
                <hr class="rule">
                <div class="mini-head"><h2>{{ t.ordNote }}</h2></div>
                <p class="pref note-body">{{ order.note }}</p>
              </template>

              <!-- 从引导式下单来的单：当时逐项选了什么（后端 u_s_order.wizardjson，按下单语言） -->
              <template v-if="order.wizard?.length">
                <hr class="rule">
                <div class="mini-head"><h2>{{ t.wzSummary }}</h2></div>
                <p v-for="r in order.wizard" :key="r.group + r.field" class="pref">
                  <span class="muted">{{ r.field }}</span> · {{ r.value }}
                </p>
              </template>
            </section>

            <NuxtLink :to="shopUrl(localePath('/#inquiry'))" class="support-link link-more">
              {{ t.ordSupport }}
            </NuxtLink>
          </aside>
        </div>
      </template>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { localizeUnit } from '~/lib/units'
import {
  formatDate,
  formatDateTime,
  formatMoney,
  orderTimeline,
  type Order
} from '~/lib/shop'

const site = await useSiteInfo()
const route = useRoute()
const localePath = useLocalePath()
// 这一页在 buyer. 子域上，指回商城的链接必须是主域绝对地址——
// 客户端路由不经过 domain-split 中间件，相对路径会把商城页面渲染在买家域下
const { shopUrl } = useSiteHosts()
const t = useShopCopy(() => site.lang)

const orderRef = computed(() => String(route.params.id || ''))

const { loadOne, restore: restoreOrders } = useOrders()
const { addLine, restore: restoreCart } = useCart()

const ready = ref(false)
const note = ref('')

/**
 * 这一页要**完整明细**，而列表接口不下发订单行（避免 N+1），
 * 所以登录用户这里会再取一次 `/account/orders/{code}`。
 * 没登录、或者这张单不是他的，`loadOne` 退回本地回执。
 */
const order = ref<Order | null>(null)

onMounted(async () => {
  restoreOrders()
  // 「再询一次」会往清单里写，所以清单必须先读出来——
  // 否则后读的 restore 会把这一页刚加进去的东西盖掉
  restoreCart()
  order.value = await loadOne(orderRef.value, site.lang)
  ready.value = true
})

const timeline = computed(() => (order.value ? orderTimeline(order.value) : []))

const money = (n: number) => formatMoney(n, order.value?.currency || site.currency, site.lang)

const cityLine = computed(() => {
  const a = order.value?.address
  if (!a) return ''
  return [a.city, a.region, a.postal].filter(Boolean).join(', ')
})

const SHIP_LABEL: Record<string, string> = {
  sea: 'coShipSea', air: 'coShipAir', express: 'coShipExpress', agent: 'coShipAgent'
}
const PAY_LABEL: Record<string, string> = {
  tt: 'coPayTt', lc: 'coPayLc', paypal: 'coPayPaypal', other: 'coPayOther'
}

/** 'submitted' → 'Submitted'，用来拼文案键 ordStageSubmitted */
const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

function flash(message: string) {
  note.value = message
  setTimeout(() => (note.value = ''), 3600)
}

/** 把每一行按原数量放回询价清单 */
function requestAgain() {
  const o = order.value
  if (!o) return
  for (const item of o.items) {
    addLine(
      {
        code: item.code,
        // 型号要带回去，否则「再询一次」询的是另一款——价格也就不是他上次那个了
        comboCode: item.comboCode,
        comboName: item.comboName,
        title: item.title,
        subtitle: item.subtitle,
        price: item.price,
        currency: item.currency,
        image: item.image,
        // 历史单里没存起订量。填 1 只是不再提示，不会挡住提交——
        // 真的低于起订量，商家回报价时会说
        moq: 1,
        unit: item.unit
      },
      item.qty
    )
  }
  flash(t.value.ordReordered!)
}

/** 纯文本副本，在浏览器里生成——没有后端可以出 PDF */
function asText(o: Order) {
  const pad = (label: string, value: string) => `${label.padEnd(34)}${value.padStart(14)}`
  const addr = [o.address.line1, o.address.line2, cityLine.value, o.address.country]
    .filter(Boolean)
  return [
    `${site.companyName} — ${t.value.ordDetailTitle}`,
    '='.repeat(48),
    `${t.value.dlRef!.padEnd(8)}${o.ref}`,
    `${t.value.dlSent!.padEnd(8)}${formatDate(o.placedAt, site.lang)}`,
    '',
    t.value.dlItems!,
    '-'.repeat(48),
    ...o.items.map(i =>
      pad(
        `${i.code} ${i.title} x${i.qty}${i.unit ? ' ' + localizeUnit(i.unit, site.lang) : ''}`,
        i.price > 0 ? formatMoney(i.price * i.qty, o.currency, site.lang) : t.value.dlOnRequest!
      )
    ),
    '-'.repeat(48),
    pad(t.value.dlSubtotal!, formatMoney(o.subtotal, o.currency, site.lang)),
    '',
    t.value.dlContact!,
    '-'.repeat(48),
    o.contact.name,
    ...(o.contact.company ? [o.contact.company] : []),
    o.contact.email,
    ...(o.contact.phone ? [o.contact.phone] : []),
    '',
    t.value.dlShipTo!,
    '-'.repeat(48),
    ...addr,
    '',
    ...(o.note ? [t.value.dlNotes!, '-'.repeat(48), o.note, ''] : []),
    // 存下来的这份也要带上这句，免得日后被当成一张形式发票
    t.value.dlNotInvoice!
  ].join('\n')
}

function download() {
  const o = order.value
  if (!o) return
  const blob = new Blob([asText(o)], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${o.ref}.txt`
  a.click()
  URL.revokeObjectURL(url)
  flash(t.value.ordDownloaded!)
}

useHead(() => ({
  title: `${t.value.ordDetailTitle} ${orderRef.value} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
}))
</script>

<style scoped>
.od-page { padding: 34px 20px 30px; }

.sk-bar { height: 70px; margin-bottom: 20px; }
.sk-body { height: 460px; }

.notice {
  text-align: center;
  padding: 60px 20px 80px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.notice h1 { font-size: 1.7rem; margin-bottom: 10px; }
.notice p { margin: 0 0 24px; }

.od-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}
.od-title { display: flex; gap: 16px; align-items: flex-start; }
.back {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 38px;
  height: 38px;
  margin-top: 16px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--surface);
  color: var(--text-muted);
  font-size: 1.1rem;
}
.back:hover { color: var(--primary); border-color: var(--primary-light); }
.od-ref {
  margin: 0 0 4px;
  font-size: 0.74rem;
  letter-spacing: 0.04em;
  color: var(--text-soft);
  font-variant-numeric: tabular-nums;
}
.od-head h1 { font-size: clamp(1.7rem, 3vw, 2.3rem); }
.od-date { margin: 6px 0 0; font-size: 0.86rem; }
.od-actions { display: flex; gap: 10px; flex-wrap: wrap; }

.action-note {
  margin: 0 0 18px;
  padding: 11px 16px;
  background: #e2f4e9;
  color: #1f7a45;
  border-radius: var(--radius-sm);
  font-size: 0.84rem;
}

.od-layout {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;
  align-items: start;
}
.od-main { display: flex; flex-direction: column; gap: 20px; }
.od-side { display: flex; flex-direction: column; gap: 20px; }

.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 24px 26px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}
.panel-head h2 { font-size: 1.25rem; }

/* ---------------- 时间轴 ---------------- */
.track { list-style: none; padding: 0; margin: 0; }
.tstep {
  position: relative;
  display: grid;
  grid-template-columns: 26px 1fr;
  gap: 14px;
  padding-bottom: 22px;
}
/* 竖线连到下一格，最后一格不画 */
.tstep:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 26px;
  bottom: 0;
  width: 2px;
  background: var(--border);
}
.tstep--done:not(:last-child)::before { background: var(--primary-light); }
.tstep:last-child { padding-bottom: 0; }

.tdot {
  position: relative;
  z-index: 1;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid var(--border);
  background: var(--surface);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tstep--done .tdot { border-color: var(--primary); background: var(--primary); }

.ttext { display: flex; flex-direction: column; gap: 2px; padding-top: 2px; }
.ttext strong { font-size: 0.95rem; }
.tstep:not(.tstep--done) .ttext strong { color: var(--text-soft); font-weight: 500; }
.twhen { font-size: 0.78rem; }
.tnote { font-size: 0.8rem; }

/* ---------------- 明细 ---------------- */
.line {
  display: grid;
  grid-template-columns: 74px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 14px 0;
  border-top: 1px solid var(--border);
}
.line:first-of-type { border-top: 0; padding-top: 0; }
.line-thumb {
  display: block;
  width: 74px;
  height: 74px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface-muted);
}
.line-thumb img { width: 100%; height: 100%; object-fit: cover; }
.line-noimg { display: block; width: 100%; height: 100%; background: linear-gradient(135deg, #ecf3f8, #dae7f0); }
.line-body { min-width: 0; }
.line-body h3 { font-size: 1rem; margin-bottom: 3px; }
.line-body h3 a:hover { color: var(--primary); }
.line-sub, .line-qty { margin: 0; font-size: 0.8rem; }
.line-price { font-weight: 600; color: var(--primary); white-space: nowrap; }

/* ---------------- 右栏 ---------------- */
.sum-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 0.9rem;
  margin-bottom: 10px;
}
.sum-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 600;
}
.total-value { color: var(--primary); }
.sum-note { margin: 10px 0 0; font-size: 0.76rem; line-height: 1.5; }

.mini-head { margin-bottom: 10px; }
.mini-head h2 {
  font-family: var(--font-body);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-soft);
}
.addr {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-style: normal;
  font-size: 0.88rem;
  line-height: 1.55;
}
.pref { margin: 0 0 16px; font-size: 0.88rem; }
.note-body { white-space: pre-line; }
.rule { border: 0; border-top: 1px solid var(--border); margin: 18px 0; }

.support-link { text-align: center; }

@media (max-width: 980px) {
  .od-layout { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .line { grid-template-columns: 56px 1fr; }
  .line-thumb { width: 56px; height: 56px; }
  .line-price { grid-column: 1 / -1; }
}
.oi-combo {
  display: block;
  margin-top: 2px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--primary);
}
</style>
