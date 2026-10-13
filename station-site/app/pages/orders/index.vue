<!--
  「我的询价」列表。

  从 kangwanjia-website-EN 的 pages/orders/index.vue 搬来，去掉了登录门，
  并且**在页面上明说这份列表只存在这台浏览器里**——上游那个站看起来像账号中心，
  真站上让买家以为「我的订单都在这」，换台电脑发现全没了，是更糟的体验。

  ## 2026-09-06：两份合起来了

  这一页曾经只是一份 localStorage 台账。现在是**服务端为准 + 本地回执兜底**：

    · 登录了  → 列表来自 u_s_order（`useBuyer().myOrders()`，按会话里的客户编码查），
                换台电脑也在。页面上那句提示改成 `ordAccountList`。
    · 没登录  → 还是本地回执，页面上照旧明说「只存在这台浏览器里」（`ordLocalOnly`）。

  **下单不要求登录**，所以本地回执删不掉——它是匿名买家唯一能看到自己刚提交了什么的
  地方。合并规则是「同一个单号服务端赢」，所以买家用同一个邮箱注册之后，
  本地那份会被服务端那份逐条盖掉，不会出现两张一样的卡片。

  规则和取舍都在 `useOrders.ts` 的文件头。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap ord-page">
      <header class="ord-head">
        <h1>{{ t.ordTitle }}</h1>
        <p class="muted">{{ t.ordLead }}</p>
      </header>

      <!-- 挂载前：记录在 localStorage 里 -->
      <div v-if="!ready" class="grid grid-3" aria-hidden="true">
        <div v-for="n in 3" :key="n" class="skeleton-block sk-card"></div>
      </div>

      <!-- 一条都没有 -->
      <div v-else-if="!sorted.length" class="notice">
        <h2>{{ t.ordEmptyTitle }}</h2>
        <p class="muted">{{ t.ordEmptyLead }}</p>
        <NuxtLink :to="shopUrl(localePath('/products'))" class="btn">{{ t.explore }}</NuxtLink>
      </div>

      <template v-else>
        <!-- 如实说这份列表从哪来：登录了是账号里的（换设备也在），
             没登录就只是这台浏览器上的留痕 -->
        <p class="local-note muted">{{ fromAccount ? t.ordAccountList : t.ordLocalOnly }}</p>

        <div class="grid grid-3">
          <article v-for="order in sorted" :key="order.ref" class="card">
            <div class="card-top">
              <span class="card-ref">{{ fill(t.ordRef, { ref: order.ref }) }}</span>
              <OrderStatusPill :status="order.status" :lang="site.lang" />
            </div>

            <h2 class="card-date">{{ formatDateShort(order.placedAt, site.lang) }}</h2>

            <ul class="card-items">
              <li v-for="item in order.items.slice(0, 3)" :key="item.code" class="card-item">
                <span class="card-thumb">
                  <img v-if="item.image" :src="item.image" :alt="item.title" loading="lazy">
                  <span v-else class="card-noimg"></span>
                </span>
                <span class="card-item-text">
                  <strong>{{ item.title }}</strong>
                  <span v-if="item.comboName" class="oi-combo">{{ item.comboName }}</span>
                  <small class="muted">
                    {{ fill(t.qty, { n: item.qty }) }}{{ item.unit ? ' ' + localizeUnit(item.unit, site.lang) : '' }}
                  </small>
                </span>
              </li>
              <!-- 卡片只放得下三行，剩下的折成一句 -->
              <li v-if="order.items.length > 3" class="card-more muted">
                +{{ order.items.length - 3 }}
              </li>
            </ul>

            <div class="card-total">
              <span>{{ t.subtotal }}</span>
              <span class="card-amount">{{ formatMoney(order.subtotal, order.currency, site.lang) }}</span>
            </div>

            <NuxtLink :to="localePath(`/orders/${order.ref}`)" class="btn btn-ghost btn-block">
              {{ t.ordView }}
            </NuxtLink>
          </article>
        </div>
      </template>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { localizeUnit } from '~/lib/units'
import { formatDateShort, formatMoney } from '~/lib/shop'

const site = await useSiteInfo()
const localePath = useLocalePath()
// 这一页在 buyer. 子域上，指回商城的链接必须是主域绝对地址——
// 客户端路由不经过 domain-split 中间件，相对路径会把商城页面渲染在买家域下
const { shopUrl } = useSiteHosts()
const t = useShopCopy(() => site.lang)

const { sorted, fromAccount, restore, load } = useOrders()

const ready = ref(false)
onMounted(() => {
  // 先把本地回执同步读出来渲染，别让整页等一个网络往返。
  // 服务端那份（只有登录了才有）随后补进来，同一个单号会盖掉本地那份
  restore()
  ready.value = true
  void load()
})

useHead({
  title: `${t.value.ordTitle} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.ord-page { padding: 40px 20px 30px; }

.ord-head { margin-bottom: 26px; }
.ord-head h1 { font-size: clamp(2rem, 4vw, 2.8rem); margin-bottom: 10px; }
.ord-head p { margin: 0; }

.local-note {
  margin: 0 0 26px;
  padding: 12px 16px;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  line-height: 1.55;
}

.notice {
  text-align: center;
  padding: 60px 20px 80px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.notice h2 { font-size: 1.5rem; margin-bottom: 10px; }
.notice p { margin: 0 0 24px; }

.sk-card { height: 300px; }

.card {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 22px;
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.card-ref {
  font-size: 0.74rem;
  letter-spacing: 0.04em;
  color: var(--text-soft);
  font-variant-numeric: tabular-nums;
}
.card-date { font-size: 1.15rem; margin-bottom: 16px; }

.card-items {
  list-style: none;
  padding: 0;
  margin: 0 0 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}
.card-item { display: grid; grid-template-columns: 40px 1fr; gap: 10px; align-items: center; }
.card-thumb {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-muted);
}
.card-thumb img { width: 100%; height: 100%; object-fit: cover; }
.card-noimg { display: block; width: 100%; height: 100%; background: linear-gradient(135deg, #ecf3f8, #dae7f0); }
.card-item-text { display: flex; flex-direction: column; min-width: 0; }
.card-item-text strong {
  font-size: 0.86rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-item-text small { font-size: 0.72rem; }
.card-more { font-size: 0.78rem; padding-left: 50px; }

.card-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
  margin-bottom: 16px;
  border-top: 1px solid var(--border);
  font-size: 0.88rem;
}
.card-amount {
  font-family: var(--font-display);
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--primary);
}
.oi-combo {
  display: block;
  margin-top: 2px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--primary);
}
</style>
