<!--
  商品卡片。列表页和首页「主推商品」共用。

  价格为 0 时不显示金额而显示「询价」——外贸报价基本都是一单一议，
  一个 0.00 会让买家以为免费或者站点没维护。

  ## 卡片上那个「+」

  照 kangwanjia-website-EN 的卡片做的，但那边是个纯装饰的 span，这里是真按钮：
  点它把商品加进询价清单，不跳转。

  ⚠ 所以卡片**不能**整块套在一个 `<a>` 里了——`<a>` 里再放 `<button>` 是无效 HTML，
    浏览器会把 DOM 拆开，点按钮时经常连带触发跳转。这里用的是「拉伸链接」：
    标题上的链接用 `::after` 铺满整张卡片，按钮再用 z-index 浮在它上面。
    好处是无障碍树里只有一个链接（读屏不会把整张卡念成一个巨大的链接名），
    但整张卡还是可点的。

  ## 那个「+」对所有人都在

  2026-09-10 当天这里短暂按 `v-if="buyer"` 藏过（配套「匿名只能询盘、下单要先登录」
  那道门），同日撤掉——理由见 SiteHeader.vue 购物车那一段。

  ⚠ 三处入口要同进同退：顶栏购物车（SiteHeader.vue）、这里、详情页
    （products/[code].vue）。只藏其中一两处的话，游客点「+」会看到打勾动画，
    然后翻遍顶栏找不到清单在哪，东西像是凭空没了。
-->
<template>
  <article class="pc">
    <div class="pc-img">
      <!-- @error：图片地址是商家在管理台填的，可能填错、可能对应的文件被删了。
           不处理的话浏览器渲染成一个裂图标，比干净的占位块难看得多 -->
      <img v-if="item.images?.length && !broken" :src="item.images[0]" :alt="item.title"
           loading="lazy" @error="broken = true">
      <div v-else class="pc-noimg"></div>
      <span v-if="item.featured" class="pill pc-tag">{{ featuredLabel }}</span>
    </div>

    <div class="pc-body">
      <h3 class="pc-title">
        <NuxtLink :to="localePath(`/products/${item.code}`)" class="pc-link">{{ item.title }}</NuxtLink>
      </h3>
      <p v-if="item.summary" class="pc-sum">{{ item.summary }}</p>

      <div class="pc-meta">
        <div class="pc-money">
          <span class="pc-price">
            {{ Number(item.price) > 0 ? money(item.price) : shop.inquire }}
          </span>
          <span v-if="item.moq" class="pc-moq">
            {{ shop.moq }} {{ item.moq }}{{ item.unit ? ' ' + localizeUnit(item.unit, lang) : '' }}
          </span>
        </div>

        <button
          type="button"
          class="pc-add"
          :class="{ 'pc-add--done': added }"
          :aria-label="addLabel"
          @click="onAdd"
        >{{ added ? '✓' : '+' }}</button>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { ProductCard as Card, SiteInfo } from '~/composables/useSite'
import { localizeUnit } from '~/lib/units'

const props = defineProps<{ item: Card; lang: string; site: SiteInfo }>()
const localePath = useLocalePath()
const shopCopy = useShopCopy(() => props.lang)
const shop = computed(() => shopCopy.value)

const { add } = useCart()

/** 图片加载失败。换商品时要重置，否则列表翻页后复用组件会一直显示占位块 */
const broken = ref(false)
watch(() => props.item.code, () => { broken.value = false })

/** 加进去之后按钮变对勾，1.4 秒后变回来 */
const added = ref(false)
let addedTimer: ReturnType<typeof setTimeout> | null = null

function onAdd() {
  add(props.item, undefined, null, props.lang)
  added.value = true
  if (addedTimer) clearTimeout(addedTimer)
  addedTimer = setTimeout(() => { added.value = false }, 1400)
}

// 翻页时组件会被复用，定时器不清掉会在新商品的卡上闪一个对勾
onBeforeUnmount(() => { if (addedTimer) clearTimeout(addedTimer) })

/*
  价格按买家选的币种换算显示。

  ⚠ 用 item.currency 作为被换算的币种，而不是写死 site.currency：
     列表接口是逐商品下发币种的，虽然现在两者恒等，但那是数据的事，不是这里该假设的。
     换算基准由 useDisplayCurrency 从 site 上取，本币不匹配时它会原样返回，不会算错。
*/
const currency = useDisplayCurrency(() => props.site)
const money = (n: number) => currency.displayMoney(n, props.lang)

// 按钮上只有一个「+」，读屏念不出它是干嘛的，所以标签要把商品名带上——
// 一屏十几张卡，光念「加入询价清单」也分不清是哪一件
const addLabel = computed(() =>
  added.value
    ? shop.value.cartAdded!
    : `${shop.value.cartAdd} — ${props.item.title}`
)

const FEATURED: Record<string, string> = {
  en: 'Featured', zh: '主推', ja: 'おすすめ', es: 'Destacado', pt: 'Destaque', ru: 'Хит',
  it: 'In evidenza', ko: '추천'
}
const featuredLabel = computed(() => FEATURED[props.lang] || FEATURED.en!)
</script>

<style scoped>
.pc {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--surface);
  transition: box-shadow .18s, transform .18s;
}
.pc:hover { box-shadow: var(--shadow); transform: translateY(-3px); }

.pc-img { position: relative; aspect-ratio: 4 / 3; background: var(--surface-muted); }
.pc-img img { width: 100%; height: 100%; object-fit: cover; }
.pc-noimg { width: 100%; height: 100%; background: linear-gradient(135deg, #ecf3f8, #dae7f0); }
.pc-tag { position: absolute; top: 12px; left: 12px; }

.pc-body { display: flex; flex-direction: column; flex: 1; padding: 16px 17px 17px; }

.pc-title { font-size: 1.02rem; font-weight: 600; line-height: 1.35; margin: 0 0 6px; }
/* 拉伸链接：整张卡可点，但无障碍树里只有这一个链接 */
.pc-link::after { content: ''; position: absolute; inset: 0; }
.pc-link:hover { color: var(--primary); }

.pc-sum {
  margin: 0 0 14px;
  font-size: 0.84rem;
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pc-meta {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
}
/* min-height 是为了让价格在一排卡片之间**对齐到同一条基线**。
   .pc-meta 是 align-items:flex-end（价格底边和「+」按钮底边齐），
   所以这一块有多高，价格就落在多高的位置——填了起订量的卡两行、
   没填的一行，价格就会一高一低。线上现在就是这样：三张卡里
   「手套」没填起订量，它的价格比左右两张低了十几个像素。

   37px = 价格行 1.05rem×1.3 ≈ 22px + 起订量行 0.72rem×1.3 ≈ 15px。
   两个行高都写死，不吃 body 的 line-height:1.65，否则这个数算不准。
   用留白而不是「没填就补一行占位文字」：占位文字读屏会念出来。 */
.pc-money {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  min-width: 0;
  min-height: 37px;
}
.pc-price {
  font-family: var(--font-display);
  color: var(--primary);
  font-weight: 600;
  font-size: 1.05rem;
  line-height: 1.3;
}
.pc-moq { font-size: 0.72rem; line-height: 1.3; color: var(--text-soft); }

.pc-add {
  position: relative;
  /* 浮在拉伸链接上面，否则点「+」会跳到详情页 */
  z-index: 1;
  flex: none;
  width: 34px;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: #fff;
  color: var(--primary);
  font-size: 1.05rem;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background .15s ease, color .15s ease, border-color .15s ease;
}
.pc-add:hover { background: var(--primary); color: #fff; border-color: var(--primary); }
.pc-add--done {
  background: #e2f4e9;
  border-color: #b5e0c6;
  color: #1f7a45;
  font-size: 0.9rem;
}
</style>
