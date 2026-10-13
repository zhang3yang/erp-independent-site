<!--
  买家 3D 定制方案查看页（2026-09-29）：只读，可转、可模拟开机。
  设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md §7.3。

  - 画的是方案里的**快照**（模型地址 + 标注），不是商品现在的样子：商家之后挪了屏幕、换了模型，
    发出去的链接不变。模型换过的话顶部提示一句。
  - 链接谁拿到都能看，所以后端这个接口不下发邮箱和备注，这里也就没有。
  - 「在此基础上修改」→ /customize/{商品号}?from={方案号}：保存会得到一个**新**链接，这一份不动。
  - noindex；不进 robots.txt 的 Disallow（同定制页）。
-->
<template>
  <div class="cz-root">
    <SiteHeader :site="site" minimal />

    <main class="wrap cz-page">
      <div v-if="!plan" class="cz-notice">
        <h1>{{ t.czPlanNotFound }}</h1>
        <p class="muted">{{ t.czPlanNotFoundLead }}</p>
        <NuxtLink :to="localePath('/products')" class="btn">{{ t.explore }}</NuxtLink>
      </div>

      <template v-else>
        <header class="cz-head">
          <span class="eyebrow">{{ t.czPlanTitle }}</span>
          <h1>{{ plan.title || plan.goodsCode }}</h1>
          <p class="muted">{{ fill(t.czPlanMeta!, { id: plan.planId, date: createdAt }) }}</p>
          <p v-if="plan.comboCode" class="muted">{{ fill(t.czComboLabel!, { name: plan.comboTitle || plan.comboCode }) }}</p>
        </header>

        <p v-if="outdated" class="cz-warn" role="status">{{ t.czPlanOutdated }}</p>

        <div class="cz-stage-canvas">
          <CustomizeCanvas
            :src="plan.model3d"
            :marks="plan.marks"
            :static-screen="hasBoot ? (plan.marks?.screen?.bootSrc ? 'boot' : 'run') : null"
            :loading-text="t.d3Loading"
            :unavailable-text="t.d3Unavailable"
            :states="states"
            v-model:power-on="powerOn"
            v-model:state-id="stateId"
            @state-status="onStateStatus"
          />
        </div>

        <!-- 模型状态（2026-10-08）：保存方案时的快照，logo 自动投影到各状态 -->
        <div v-if="states" class="cz-states">
          <button v-for="s in states" :key="s.id" type="button" class="cz-chip"
                  :class="{ on: s.id === stateId }" :aria-pressed="s.id === stateId" @click="pickState(s.id)">
            <span v-if="stateLoading === s.id" class="cz-chip-spin" aria-hidden="true"></span>{{ s.name }}
          </button>
          <p v-if="stateFailed" class="cz-state-msg" role="alert">{{ t.czStateFailed }}</p>
        </div>

        <div class="cz-actions">
          <button v-if="hasBoot && onMain" type="button" class="cz-chip" :class="{ on: powerOn }" :aria-pressed="powerOn"
                  @click="powerOn = !powerOn">
            {{ powerOn ? t.czSimulateOff : t.czSimulate }}
          </button>
          <span class="cz-spacer"></span>
          <button type="button" class="btn btn-ghost" @click="copyLink">{{ copied ? t.czCopied : t.czCopy }}</button>
          <NuxtLink v-if="plan.available" class="btn"
                    :to="localePath(`/customize/${encodeURIComponent(plan.goodsCode)}?from=${plan.planId}`) + (plan.comboCode && plan.comboAvailable ? `&combo=${encodeURIComponent(plan.comboCode)}` : '')">
            {{ t.czEditFrom }}
          </NuxtLink>
          <NuxtLink v-if="plan.available" class="cz-link"
                    :to="localePath(`/products/${encodeURIComponent(plan.goodsCode)}`)">
            {{ t.czViewProduct }}
          </NuxtLink>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { fetchCustomPlan } from '~/lib/customize'
import { parseStates } from '@station-shared/model3d/stateList.js'
import type { StateStatus } from '@station-shared/model3d/states.js'

const site = await useSiteInfo()
const route = useRoute()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)
const config = useRuntimeConfig()
const id = String(route.params.id || '')

const { data: plan } = await useAsyncData(`custom-plan-${id}-${site.lang}`,
  () => fetchCustomPlan(import.meta.server ? config.apiBase : (config.public.apiBase || ''), id, site.lang))

const powerOn = ref(false)
/* 模型状态：方案快照里的；屏幕只在主状态 */
const states = computed(() => parseStates(plan.value?.states))
const stateId = ref<string | null>(null)
const stateLoading = ref<string | null>(null)
const stateFailed = ref(false)
const onMain = computed(() => !states.value || !stateId.value || stateId.value === states.value[0]?.id)
/** 点状态按钮：先清掉上一次的失败提示（失败时展示台会把 stateId 改回去，不能拿 stateId 变化来清） */
function pickState(id: string) {
  stateFailed.value = false
  stateId.value = id
}
function onStateStatus(sid: string, status: StateStatus) {
  stateLoading.value = status === 'loading' ? sid : stateLoading.value === sid ? null : stateLoading.value
  stateFailed.value = status === 'failed'
}
const copied = ref(false)
const hasBoot = computed(() => !!plan.value?.marks?.screen)
/** 商品现在的模型和方案做的时候不是同一个 */
const outdated = computed(() => !!plan.value?.currentModel3d && plan.value.currentModel3d !== plan.value.model3d)

const createdAt = computed(() => {
  const raw = plan.value?.createTime
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw)
  try {
    return new Intl.DateTimeFormat(site.lang || 'en', { dateStyle: 'medium' }).format(d)
  } catch {
    return d.toLocaleDateString()
  }
})

async function copyLink() {
  try {
    await navigator.clipboard.writeText(location.href)
    copied.value = true
  } catch {
    // 没有剪贴板权限：地址就在地址栏里
  }
}

useHead({
  title: `${t.value.czPlanTitle} · ${plan.value?.title || id} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.cz-root { min-height: 100vh; background: var(--surface-muted, #f4f6f8); }
.cz-page { padding-top: 22px; padding-bottom: 48px; }
.cz-head { margin-bottom: 14px; }
.cz-head h1 { font-size: clamp(1.4rem, 2.4vw, 1.9rem); margin: 6px 0; }
.cz-head p { margin: 0; font-variant-numeric: tabular-nums; }

.cz-warn {
  margin: 0 0 14px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: #fff7e6;
  border: 1px solid #ffd591;
  color: #874d00;
  font-size: .9rem;
}

.cz-stage-canvas { height: min(70vh, 640px); }

.cz-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 14px; }
.cz-spacer { flex: 1; }
.cz-chip {
  border: 1.5px solid var(--text);
  background: #fff;
  color: var(--text);
  border-radius: 999px;
  padding: 7px 16px;
  font-size: .85rem;
  font-weight: 600;
}
.cz-states { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 12px; }
.cz-chip { display: inline-flex; align-items: center; gap: 6px; }
.cz-chip-spin { width: 11px; height: 11px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent; animation: cz-chip-spin .8s linear infinite; }
@keyframes cz-chip-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .cz-chip-spin { animation-duration: 2.4s; } }
.cz-state-msg { flex-basis: 100%; text-align: center; margin: 4px 0 0; font-size: .82rem; color: var(--danger, #c0392b); }
.cz-chip.on { background: var(--brand-blue); border-color: var(--brand-blue); color: var(--ink-dark); }
.cz-link { color: var(--primary); font-weight: 600; font-size: .9rem; }

.cz-notice { text-align: center; padding: 80px 0; }
.cz-notice h1 { font-size: 1.6rem; }

@media (max-width: 900px) {
  .cz-stage-canvas { height: 56vh; min-height: 300px; }
  .cz-spacer { display: none; }
}
</style>
