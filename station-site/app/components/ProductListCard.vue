<!--
  商品列表页的大卡片。

  照 kangwanjia-website-EN 的 `pages/product/index.vue` 里那张卡做的，
  尺寸、圆角、标签位置、整宽的「查看详情 →」按钮都按它来。

  ## 为什么不复用 ProductCard

  参考站有**两种**商品卡，不是一种：
    · 首页那张是「小图 + 价格 + 右下角一个 +」（pages/index/index.vue 的
      .product-card）——station-site 的 ProductCard.vue 就是照它做的，不动；
    · 商品列表页这张是「300px 大图 + 两行描述 + 整宽按钮」，卡上**没有价格**。

  两张卡的信息密度差得远（一页 4 张 vs 一页 2 张），塞进一个组件就得靠 prop 分叉，
  分叉出来的两套样式最后还是各写一遍。所以分成两个组件，各自照着各自那张卡。

  卡上不显示价格是参考站的取法，也符合外贸的实际：列表页的价格几乎都要按量重报，
  一个孤零零的单价会被买家当成成交价。价格在详情页给，那里有起订量和询价入口。
-->
<template>
  <article class="prod-card">
    <div class="prod-media">
      <!-- isUsableImageUrl 拦在渲染前，@error 兜挂载后：前者管得住 SSR
           （商家填了相对路径时，服务端渲染出来的就已经是占位块，不会先裂一下），
           后者管地址看着对、文件却被删了的情况。见 lib/shop.ts 的注释 -->
      <img v-if="cover && !broken" :src="cover" :alt="item.title" loading="lazy"
           @error="broken = true">
      <div v-else class="prod-noimg"></div>
      <span v-if="item.featured" class="pill prod-tag">{{ featuredLabel }}</span>
    </div>

    <div class="prod-body">
      <h3>{{ item.title }}</h3>
      <p>{{ item.summary }}</p>
      <NuxtLink :to="localePath(`/products/${item.code}`)" class="btn btn-primary btn-block">
        {{ viewLabel }} →
      </NuxtLink>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { ProductCard as Card } from '~/composables/useSite'
import { isUsableImageUrl } from '~/lib/shop'

const props = defineProps<{ item: Card; lang: string }>()
const localePath = useLocalePath()

const cover = computed(() => {
  const url = props.item.images?.[0]
  return isUsableImageUrl(url) ? url : ''
})

/** 图片加载失败。换商品时要重置，否则翻页复用组件后会一直显示占位块 */
const broken = ref(false)
watch(() => props.item.code, () => { broken.value = false })

const FEATURED: Record<string, string> = {
  en: 'Featured', zh: '主推', ja: 'おすすめ', es: 'Destacado', pt: 'Destaque', ru: 'Хит',
  it: 'In evidenza', ko: '추천'
}
const VIEW: Record<string, string> = {
  en: 'View Details', zh: '查看详情', ja: '詳細を見る', es: 'Ver detalles',
  pt: 'Ver detalhes', ru: 'Подробнее', it: 'Vedi dettagli', ko: '자세히 보기'
}
const featuredLabel = computed(() => FEATURED[props.lang] || FEATURED.en!)
const viewLabel = computed(() => VIEW[props.lang] || VIEW.en!)
</script>

<style scoped>
.prod-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.prod-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow);
}

/* 300px 定高，跟参考站一致。这张卡只在两栏的列表页用，
   一栏宽约 470px，300px 高出来正好是 3:2 左右 */
.prod-media {
  position: relative;
  height: 300px;
  background: var(--surface-muted);
}

.prod-media img { width: 100%; height: 100%; object-fit: cover; }
.prod-noimg { width: 100%; height: 100%; background: linear-gradient(135deg, #ecf3f8, #dae7f0); }

.prod-tag { position: absolute; top: 16px; left: 16px; }

.prod-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 24px 26px 28px;
}

.prod-body h3 { font-size: 1.4rem; margin-bottom: 10px; }

/* min-height 让一排卡的按钮落在同一条线上：描述一行和两行的卡混在一起时，
   没有它按钮会一高一低。摘要是商家填的，长度完全不可控。
   margin-top:auto 顶住按钮，描述比 44px 还长时也不会把按钮挤出卡外 */
.prod-body p {
  color: var(--text-muted);
  font-size: 0.9rem;
  margin: 0 0 22px;
  min-height: 44px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.prod-body .btn { margin-top: auto; }

@media (max-width: 560px) {
  .prod-media { height: 240px; }
  .prod-body { padding: 20px 20px 22px; }
  .prod-body h3 { font-size: 1.2rem; }
}
</style>
