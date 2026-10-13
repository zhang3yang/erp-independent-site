<!--
  询价清单（购物车）。

  从 kangwanjia-website-EN 的 pages/cart/index.vue 搬来，套上这个站的壳
  （SiteHeader / SiteFooter / 语言前缀 / 按站点币种格式化金额），另外三处不一样：

    1. **不登录也能往下走**。上游在这里挡了一道「结账要先登录」。
       ⚠ 2026-09-04 起这个站**有**买家账号了（app/composables/useBuyer.ts），
       但这一条不变：挡一道登录会掉单，账号是加分项不是门槛。
       （原注释的理由是「这个站没有买家账号，挡一道等于挡死」——
       前半句过时了，后半句仍然是这么做的理由。见 boundary §6c。）
    2. **价格面议的行单独处理**：不显示金额、不进合计，底下说明一句。
    3. **起订量提示**：数量低于起订量时那一行自己提示，而不是等商家回信才说。

  ## 这一页不给收录

  清单页是每个访客各自的东西，进 Google 索引没有意义，还会分掉商品页的权重。
  所以走 `robots: noindex`，也不调 useSiteSeo（那个是给要收录的页面拼 canonical /
  hreflang 的）。sitemap 里同样没有它，见 server/routes/sitemap.xml.ts。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap cart-page">
      <header class="cart-head">
        <h1>{{ t.cartTitle }}</h1>
        <p class="muted">{{ t.cartLead }}</p>
      </header>

      <!-- 挂载前：清单在 localStorage 里，还不知道有没有东西。
           先摆骨架，比先闪一下「清单是空的」再跳成有内容好 -->
      <div v-if="!ready" class="cart-layout" aria-hidden="true">
        <div class="skeleton-block sk-lines"></div>
        <div class="skeleton-block sk-summary"></div>
      </div>

      <!-- 空清单 -->
      <div v-else-if="!items.length" class="notice">
        <h2>{{ t.cartEmptyTitle }}</h2>
        <p class="muted">{{ t.cartEmptyLead }}</p>
        <NuxtLink :to="localePath('/products')" class="btn">{{ t.explore }}</NuxtLink>
      </div>

      <div v-else class="cart-layout">
        <!-- 清单行 -->
        <section class="lines" :aria-label="t.cartItemsLabel">
          <article v-for="item in items" :key="cartLineKey(item)" class="line">
            <NuxtLink :to="localePath(`/products/${item.code}`)" class="line-thumb">
              <img v-if="item.image" :src="item.image" :alt="item.title" loading="lazy">
              <span v-else class="line-noimg"></span>
            </NuxtLink>

            <div class="line-body">
              <h3>
                <NuxtLink :to="localePath(`/products/${item.code}`)">{{ item.title }}</NuxtLink>
              </h3>
              <p class="line-sub muted">
                <span class="line-code">{{ item.code }}</span>
                <!-- subtitle 存的是分类原串（筛选键），显示要过一遍分类译名 -->
                <template v-if="item.subtitle"> · {{ categoryLeaf(item.subtitle, site.categoryNames) || item.subtitle }}</template>
              </p>
              <!-- 同一个商品的两个型号在清单里是两行，不写明是哪一款的话，
                   买家看到的是两行一模一样的东西 -->
              <p v-if="item.comboName" class="line-combo">{{ item.comboName }}</p>

              <div class="stepper" role="group" :aria-label="fill(t.cartQtyFor, { name: item.title })">
                <button
                  type="button"
                  class="step"
                  :aria-label="item.qty > 1 ? t.cartDecrease : fill(t.cartRemove, { name: item.title })"
                  @click="setQty(cartLineKey(item), item.qty - 1)"
                >−</button>
                <input
                  class="step-input"
                  type="text"
                  inputmode="numeric"
                  :value="item.qty"
                  :aria-label="fill(t.cartQtyFor, { name: item.title })"
                  @change="onQtyInput(cartLineKey(item), $event)"
                >
                <button
                  type="button"
                  class="step"
                  :aria-label="t.cartIncrease"
                  :disabled="item.qty >= MAX_QTY"
                  @click="setQty(cartLineKey(item), item.qty + 1)"
                >+</button>
                <span v-if="item.unit" class="step-unit muted">{{ localizeUnit(item.unit, site.lang) }}</span>
              </div>

              <!-- 低于起订量：现在就说，别等商家回信 -->
              <p v-if="item.moq > 1 && item.qty < item.moq" class="line-moq">
                {{ fill(t.cartBelowMoq, { moq: item.moq, unit: item.unit || '' }) }}
              </p>
            </div>

            <div class="line-end">
              <button
                type="button"
                class="line-remove"
                :aria-label="fill(t.cartRemove, { name: item.title })"
                @click="remove(cartLineKey(item))"
              >×</button>

              <template v-if="item.price > 0">
                <span class="line-price">{{ money(item.price * item.qty) }}</span>
                <span v-if="item.qty > 1" class="line-each muted">
                  {{ fill(t.each, { price: money(item.price) }) }}
                </span>
              </template>
              <span v-else class="line-inquire">{{ t.inquire }}</span>
            </div>
          </article>

          <div class="lines-foot">
            <NuxtLink :to="localePath('/products')" class="link-more">{{ t.cartKeepShopping }}</NuxtLink>
            <button type="button" class="clear-all" @click="clear">{{ t.cartClear }}</button>
          </div>
        </section>

        <!-- 汇总 -->
        <aside class="summary">
          <div class="summary-card">
            <h2>{{ t.cartSummary }}</h2>

            <div class="sum-row">
              <span>{{ t.subtotal }}</span>
              <span>{{ money(totals.subtotal) }}</span>
            </div>
            <p v-if="totals.inquireOnly" class="sum-note muted">
              {{ fill(t.cartInquireNote, { n: totals.inquireOnly }) }}
            </p>

            <div class="sum-total">
              <span>{{ t.total }}</span>
              <span class="total-value">{{ money(totals.subtotal) }}</span>
            </div>
            <p class="sum-note muted">{{ t.coTotalNote }}</p>

            <NuxtLink :to="localePath('/checkout')" class="btn btn-block checkout">
              {{ t.cartCheckout }}
            </NuxtLink>

            <div class="promise">
              <div>
                <strong>{{ t.cartPromiseTitle }}</strong>
                <p>{{ t.cartPromiseBody }}</p>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { categoryLeaf } from '~/lib/categories'
import { localizeUnit } from '~/lib/units'
import { cartLineKey, formatMoney } from '~/lib/shop'

const site = await useSiteInfo()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)

const { items, totals, restore, localize, setQty, remove, clear, MAX_QTY } = useCart()

const ready = ref(false)
// 清单在 localStorage 里，只有挂载后才读得到
onMounted(() => {
  restore()
  // 清单是加入时那种语言的快照，切过语言就按当前语言把名字换一遍
  localize(site.lang)
  ready.value = true
})

/** 站点币种 + 站点语言格式化。行里也存了币种，但显示统一按站点当前的来 */
/*
  购物车按买家选的币种显示。

  ⚠ 换算到这一页为止。下一步的结算页回到本币（那边有说明），所以买家会在
    「去结算」那一下看到金额跳变——这是设计里认下的代价，靠结算页那句
    「≈ €184.00，最终以 USD 为准」把它讲明白，而不是假装没发生。
*/
const currency = useDisplayCurrency(() => site)
const money = (n: number) => currency.displayMoney(n, site.lang)

/** 手输数量：非数字就还原，越界就夹住，0 当成删除 */
function onQtyInput(code: string, e: Event) {
  const el = e.target as HTMLInputElement
  const n = parseInt(el.value, 10)
  const row = items.value.find(i => i.code === code)
  if (!Number.isFinite(n)) {
    el.value = String(row?.qty ?? 1)
    return
  }
  setQty(code, n)
  // 被夹住或者整行被删了，输入框要跟着回到真实值
  const after = items.value.find(i => i.code === code)
  if (after) el.value = String(after.qty)
}

useHead({
  title: `${t.value.cartTitle} | ${site.companyName}`,
  // 每个访客各自的页面，不给收录
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.cart-page { padding: 40px 20px 30px; }

.cart-head { margin-bottom: 36px; }
.cart-head h1 { font-size: clamp(2rem, 4vw, 2.8rem); margin-bottom: 10px; }
.cart-head p { margin: 0; }

/* 空清单 / 找不到东西时的通用提示块 */
.notice {
  text-align: center;
  padding: 60px 20px 80px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.notice h2 { font-size: 1.5rem; margin-bottom: 10px; }
.notice p { margin: 0 0 24px; }

.cart-layout {
  display: grid;
  grid-template-columns: 1fr 370px;
  gap: 30px;
  align-items: start;
}

.sk-lines { height: 320px; }
.sk-summary { height: 360px; }

/* ---------------- 清单行 ---------------- */
.lines { display: flex; flex-direction: column; gap: 18px; }

.line {
  position: relative;
  display: grid;
  grid-template-columns: 112px 1fr auto;
  gap: 22px;
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 20px 22px;
}

.line-thumb {
  display: block;
  width: 112px;
  height: 112px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface-muted);
  flex: none;
}
.line-thumb img { width: 100%; height: 100%; object-fit: cover; }
.line-noimg {
  display: block;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #ecf3f8, #dae7f0);
}

.line-body { min-width: 0; }
.line-body h3 { font-size: 1.15rem; margin-bottom: 5px; }
.line-body h3 a:hover { color: var(--primary); }
.line-sub { font-size: 0.84rem; margin: 0 0 16px; }
/* 商品编码要能被选中复制——买家常拿它去问同行 */
.line-combo {
  margin-top: 2px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--primary);
}
.line-code { font-variant-numeric: tabular-nums; }

.stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: #fff;
  overflow: hidden;
}
.step {
  width: 36px;
  height: 36px;
  border: none;
  background: none;
  font-size: 1.05rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.14s ease, color 0.14s ease;
}
.step:hover:not(:disabled) { background: var(--surface-muted); color: var(--primary); }
.step:disabled { opacity: 0.4; }
.step-input {
  width: 62px;
  border: none;
  outline: none;
  background: transparent;
  text-align: center;
  font: inherit;
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.step-unit { padding-right: 14px; font-size: 0.8rem; }

.line-moq { margin: 10px 0 0; font-size: 0.78rem; color: #96620d; }

.line-end {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  align-self: stretch;
  gap: 10px;
}
.line-remove {
  border: none;
  background: none;
  padding: 0;
  width: 26px;
  height: 26px;
  font-size: 1.3rem;
  line-height: 1;
  color: var(--text-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.15s ease;
}
.line-remove:hover { color: #d92d20; }
.line-price {
  font-family: var(--font-display);
  font-size: 1.4rem;
  font-weight: 600;
  color: var(--primary);
  white-space: nowrap;
}
.line-each { font-size: 0.76rem; }
.line-inquire { font-size: 0.86rem; font-weight: 600; color: var(--text-muted); }

.lines-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 4px;
}
.clear-all {
  border: none;
  background: none;
  padding: 0;
  font-size: 0.85rem;
  color: var(--text-soft);
  text-decoration: underline;
}
.clear-all:hover { color: #d92d20; }

/* ---------------- 汇总 ---------------- */
.summary { position: sticky; top: 88px; }
.summary-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 28px 26px;
}
.summary-card h2 { font-size: 1.4rem; margin-bottom: 20px; }

.sum-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  font-size: 0.92rem;
  margin-bottom: 12px;
}
.sum-note { margin: 0 0 12px; font-size: 0.78rem; line-height: 1.5; }

.sum-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
  font-family: var(--font-display);
  font-size: 1.4rem;
  font-weight: 600;
}
.total-value { color: var(--primary); }

.checkout { margin-top: 18px; }

.promise {
  margin-top: 20px;
  padding: 16px 18px;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
}
.promise strong { display: block; font-size: 0.86rem; margin-bottom: 4px; }
.promise p { margin: 0; font-size: 0.78rem; line-height: 1.55; color: var(--text-muted); }

/* ---------------- 响应式 ---------------- */
@media (max-width: 980px) {
  .cart-layout { grid-template-columns: 1fr; }
  .summary { position: static; }
}

@media (max-width: 640px) {
  .line { grid-template-columns: 80px 1fr; gap: 14px; padding: 16px; }
  .line-thumb { width: 80px; height: 80px; }
  /* 金额掉到下面一行，× 钉在卡片右上角 */
  .line-end {
    grid-column: 1 / -1;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
  .line-remove { position: absolute; top: 10px; right: 10px; }
  .line-body h3 { font-size: 1rem; padding-right: 26px; }
  .line-price { font-size: 1.2rem; }
}
</style>
