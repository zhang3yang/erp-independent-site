<!--
  询价单的状态标签。从 kangwanjia-website-EN 的 components/OrderStatusPill.vue 搬来，
  文案改成按站点语言取（上游走 vue-i18n 的 $t，这个站没装那个模块）。

  图标不是装饰：四个状态各有一个符号（时钟 / 对勾 / 箭头 / 叉），
  配色相近的两个状态（已确认和已发货都是蓝的）靠它区分，
  色觉障碍的访客也能读出来。
-->
<template>
  <span class="status" :class="`status--${meta.tone}`">
    <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
      <path
        :d="meta.icon"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { statusMeta, type OrderStatus } from '~/lib/shop'

const props = defineProps<{ status: OrderStatus; lang: string }>()

const t = useShopCopy(() => props.lang)

const meta = computed(() => statusMeta(props.status))

const LABEL_KEYS: Record<OrderStatus, string> = {
  submitted: 'ordStatusSubmitted',
  confirmed: 'ordStatusConfirmed',
  shipped: 'ordStatusShipped',
  delivered: 'ordStatusDelivered',
  cancelled: 'ordStatusCancelled'
}

const label = computed(() => t.value[LABEL_KEYS[props.status]] || props.status)
</script>

<style scoped>
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding: 6px 13px 6px 10px;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

/* 一个状态一个色调，不看文字也能读出状态 */
.status--amber { background: #fdf1dc; color: #96620d; }
.status--blue { background: #e4edfd; color: #2d5bab; }
.status--green { background: #e2f4e9; color: #1f7a45; }
.status--grey { background: var(--surface-muted); color: var(--text-muted); }
</style>
