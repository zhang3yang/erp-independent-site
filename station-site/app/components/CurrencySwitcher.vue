<!--
  币种切换器。顶栏里紧挨着语言切换器。

  ⚠ 和语言切换器有一条关键区别：**这个不是链接，是按钮。**

  语言进 URL（Google 要索引每个语言版本，hreflang 靠它），所以那边必须是真 <a href>。
  币种反过来——进了 URL 就变成「8 种语言 × N 种币种」个内容相同的地址，
  是标准的重复内容问题。所以币种只写 Cookie、原地重渲染，不产生新地址，
  也不该被爬虫当成可跟随的链接。

  换算逻辑全在 useDisplayCurrency 里，这个组件只负责选。
-->
<template>
  <div class="cs" ref="box">
    <button
      type="button"
      class="cs-btn"
      :aria-expanded="open"
      aria-haspopup="true"
      :aria-label="fill(t.csLabel, { code: currency.current.value })"
      @click="open = !open"
    >
      <span>{{ currency.current.value }}</span>
      <svg viewBox="0 0 12 12" width="10" height="10" aria-hidden="true">
        <path d="M2 4.2 6 8.2l4-4" fill="none" stroke="currentColor" stroke-width="1.6"
          stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>

    <div v-show="open" class="cs-menu">
      <button
        v-for="code in currency.options.value"
        :key="code"
        type="button"
        :class="['cs-item', { on: code === currency.current.value }]"
        @click="pick(code)"
      >
        <span class="cs-code">{{ code }}</span>
        <!-- 本币标一下：买家要知道哪个是「真价」，别的都是换算出来的参考价 -->
        <span v-if="code === currency.base.value" class="cs-tag">{{ t.csBase }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SiteInfo } from '~/composables/useSite'
import { fill } from '~/composables/useShopCopy'

const props = defineProps<{ site: SiteInfo }>()

const currency = useDisplayCurrency(() => props.site)
const t = useShopCopy(() => props.site.lang)

const open = ref(false)
const box = ref<HTMLElement | null>(null)

function pick(code: string) {
  currency.setCurrency(code)
  open.value = false
}

/*
  点外面关掉。只在打开时挂监听、关掉就摘，理由同 SiteHeader 里那个语言下拉：
  顶栏在每个页面上，常驻一个 document 级监听没必要。

  ⚠ import.meta.server 这道判断不能少：SSR 阶段没有 document。
*/
function onDocClick(e: MouseEvent) {
  if (box.value && !box.value.contains(e.target as Node)) open.value = false
}
watch(open, isOpen => {
  if (import.meta.server) return
  if (isOpen) document.addEventListener('click', onDocClick)
  else document.removeEventListener('click', onDocClick)
})
onBeforeUnmount(() => {
  if (!import.meta.server) document.removeEventListener('click', onDocClick)
})
</script>

<style scoped>
.cs { position: relative; font-size: 13px; }
.cs-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--ink-soft);
  font: inherit;
  /* 币种码是三个等宽字母，用等宽体切换时按钮宽度不跳 */
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  white-space: nowrap;
}
.cs-btn:hover { background: var(--bg-soft); color: var(--ink); }
.cs-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  min-width: 132px;
  max-height: 320px;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, .1);
}
.cs-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 7px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-soft);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.cs-item:hover { background: var(--bg-soft); }
.cs-item.on { background: var(--brand); color: #fff; }
.cs-tag {
  font-size: 11px;
  opacity: .6;
  text-transform: uppercase;
  letter-spacing: .04em;
}
</style>
