<!--
  3D 展示台的沙盒页。

  对应 kangwanjia-website-EN 的 pages/3d-demo.vue。用途只有一个：
  **不依赖任何商品数据也能看到展示台长什么样**。

  商品详情页上的展示台只在商品配了 .glb 模型时才出现（见 pages/products/[code].vue），
  而现在后端还没有那个字段——没有这一页的话，这套 3D 代码要等到第一个商品配上模型
  才第一次被人看到，中间坏了没人知道。

  这里渲染的是 lib/product3d/placeholderModel.ts 里那台程序化的替身模型
  （一台气压按摩仪），它**只属于这一页**。

  不给收录：这是给开发和商家自己看的，不是给买家的内容。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap demo">
      <div class="section-head">
        <h2>{{ shop.d3Title }}<span class="accent">{{ shop.d3TitleAccent }}</span></h2>
        <p>{{ shop.d3Lead }}</p>
      </div>

      <div class="showcase-viewport">
        <span class="showcase-floor" aria-hidden="true"></span>
        <Product3DViewer
          ref="viewer"
          v-model:active-view="viewIndex"
          v-model:auto-rotate="autoSpin"
          :views="views"
          :loading-text="shop.d3Loading"
          :unavailable-text="shop.d3Unavailable"
        />
      </div>

      <div class="showcase-caption">
        <h3>{{ currentView.label }}</h3>
        <p>{{ currentView.note }}</p>
      </div>

      <div class="showcase-controls">
        <button
          type="button"
          class="showcase-btn"
          :class="{ 'is-on': autoSpin }"
          :aria-pressed="autoSpin"
          @click="autoSpin = !autoSpin"
        >{{ autoSpin ? shop.d3AutoOn : shop.d3AutoOff }}</button>
        <button type="button" class="showcase-btn" @click="resetView">{{ shop.d3Reset }}</button>
        <div class="showcase-dots">
          <button
            v-for="(view, i) in views"
            :key="view.key"
            type="button"
            class="showcase-dot"
            :class="{ active: i === viewIndex }"
            :aria-label="view.label"
            @click="viewIndex = i"
          ></button>
        </div>
      </div>

      <p class="demo-note muted">
        这一页用的是程序化替身模型（<code>app/lib/product3d/placeholderModel.ts</code>）。
        商品详情页只在商品配了 <code>.glb</code> 模型时才显示展示台，不会用这个替身。
      </p>
    </div>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { buildViews, type ViewKey } from '~/lib/product3d/views'

const site = await useSiteInfo()
const shopCopy = useShopCopy(() => site.lang)
const shop = computed(() => shopCopy.value)

const viewIndex = ref(0)
const autoSpin = ref(true)
const viewer = ref<{ reset: () => void } | null>(null)

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

const views = computed(() =>
  buildViews(
    (key: ViewKey) => shop.value[`d3View${cap(key)}`] || key,
    (key: ViewKey) => shop.value[`d3View${cap(key)}Note`] || ''
  )
)
const currentView = computed(() => views.value[viewIndex.value] || views.value[0]!)

function resetView() {
  if (viewIndex.value === 0) viewer.value?.reset()
  else viewIndex.value = 0
}

useHead({
  title: `3D · ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.demo { padding: 46px 20px 40px; }

.showcase-viewport {
  position: relative;
  height: 520px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 50% 0%, rgba(175, 211, 235, 0.42) 0%, rgba(91, 99, 105, 0) 62%),
    linear-gradient(160deg, #575f65 0%, #6893b0 100%);
  touch-action: none;
}
.showcase-floor {
  position: absolute;
  left: 50%;
  bottom: 56px;
  transform: translateX(-50%);
  width: 420px;
  height: 58px;
  border-radius: 50%;
  background: radial-gradient(50% 50% at 50% 50%, rgba(186, 218, 240, 0.5) 0%, rgba(186, 218, 240, 0) 70%);
  filter: blur(6px);
  pointer-events: none;
}

.showcase-caption { margin-top: 24px; text-align: center; min-height: 74px; }
.showcase-caption h3 { font-size: 1.1rem; margin: 0 0 6px; }
.showcase-caption p { margin: 0 auto; max-width: 560px; font-size: 0.88rem; color: var(--text-muted); }

.showcase-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 14px;
}
.showcase-btn {
  padding: 9px 18px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.82rem;
  transition: color 0.2s, border-color 0.2s, background 0.2s;
}
.showcase-btn:hover { color: var(--primary); border-color: var(--primary-light); }
.showcase-btn.is-on { color: var(--primary); border-color: var(--primary-light); background: var(--surface-muted); }

.showcase-dots { display: flex; gap: 8px; }
.showcase-dot {
  width: 9px;
  height: 9px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--border);
  transition: background 0.2s, transform 0.2s;
}
.showcase-dot.active { background: var(--primary); transform: scale(1.45); }

.demo-note { margin-top: 40px; font-size: 0.82rem; text-align: center; }
.demo-note code {
  background: var(--surface-muted);
  padding: 1px 6px;
  border-radius: 5px;
  font-size: 0.92em;
}

@media (max-width: 760px) {
  .showcase-viewport { height: 340px; }
  .showcase-floor { width: 260px; bottom: 40px; }
}
</style>
