<!--
  买家 3D 定制页（2026-09-29）：在商品模型上贴自己的 logo、换开机画面，保存成一个方案链接。
  设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md §7.2。

  ## 一张「干净的单独页面」

  顶栏用 minimal 模式（只剩 Logo + 语言），不放页脚、导航、购物车——买家是来做方案的。
  入口在商品页展示台下面，新标签页打开；业务员也可以直接把这个地址发给客户。

  ## 买家能动的只有两样

  logo（最多 5 个）和开机画面（logo 图、底色）。屏幕和开机键的位置是商家标的，这里不可选中；
  后端保存时也只从商品当前标注里取它们，请求里带什么都不认。

  ## 图片保存时才上传

  选图后用 blob 地址本地预览；点「保存方案」时和 JSON 一起提交（见 app/lib/customize.ts）。
  从旧方案「在此基础上修改」（?from=方案号）带过来的图是线上地址，保存时先取回成 Blob 再传一遍——
  后端只认本次上传的文件，这条不破例。

  ## 不给收录

  noindex；**不进** robots.txt 的 Disallow（同 /find-order：商品页链着它，Disallow 了爬虫反而读不到 noindex）。
-->
<template>
  <div class="cz-root">
    <SiteHeader :site="site" minimal />

    <main class="wrap cz-page">
      <!-- 商品不能定制（没模型 / 下架 / 不存在） -->
      <div v-if="!editor" class="cz-notice">
        <h1>{{ t.czUnavailableTitle }}</h1>
        <p class="muted">{{ t.czUnavailableLead }}</p>
        <NuxtLink :to="localePath('/products')" class="btn">{{ t.explore }}</NuxtLink>
      </div>

      <template v-else>
        <header class="cz-head">
          <NuxtLink :to="localePath(`/products/${encodeURIComponent(code)}`)" class="cz-back">{{ t.czBack }}</NuxtLink>
          <h1>{{ fill(t.czTitle!, { name: editor.title || code }) }}</h1>
          <p class="muted">{{ t.czLead }}</p>
        </header>

        <p v-if="modelChanged" class="cz-warn" role="status">{{ t.czModelChanged }}</p>
        <p v-if="comboGone" class="cz-warn" role="status">{{ t.czComboGone }}</p>

        <!-- 规格（2026-10-09）：只列主模型 + 配了独立模型的规格；切换会清空已贴的 logo -->
        <div v-if="comboList.length" class="cz-combos" role="group">
          <button type="button" class="cz-chip" :class="{ on: !comboCode }" :aria-pressed="!comboCode"
                  @click="pickCombo('')">{{ editor.title || code }}</button>
          <button v-for="c in comboList" :key="c.code" type="button" class="cz-chip"
                  :class="{ on: c.code === comboCode }" :aria-pressed="c.code === comboCode"
                  @click="pickCombo(c.code)">{{ c.title }}</button>
        </div>

        <div class="cz-layout">
          <div class="cz-stage">
            <div class="cz-stage-canvas">
              <!-- 换规格 = 换模型：按地址重建画布（坐标属于各自的模型） -->
              <CustomizeCanvas
                :key="comboCode + ':' + cur.model3d"
                :src="cur.model3d"
                :marks="marks"
                :placing="placing"
                :static-screen="staticScreen"
                :loading-text="t.d3Loading"
                :unavailable-text="t.d3Unavailable"
                :tip="t.czTipPlace"
                :states="states"
                v-model:power-on="powerOn"
                v-model:state-id="stateId"
                @ready="onReady"
                @place="onPlace"
                @state-status="onStateStatus"
              />
            </div>
            <!-- 模型状态（2026-10-08）：logo 自动投影到各状态；只有主状态能调 logo、模拟开机 -->
            <div v-if="states" class="cz-stage-bar">
              <button v-for="s in states" :key="s.id" type="button" class="cz-chip"
                      :class="{ on: s.id === stateId }" :aria-pressed="s.id === stateId"
                      :disabled="!canvasReady" @click="pickState(s.id)">
                <span v-if="stateLoading === s.id" class="cz-chip-spin" aria-hidden="true"></span>{{ s.name }}
              </button>
            </div>
            <p v-if="stateFailed" class="cz-hint cz-state-msg" role="alert">{{ t.czStateFailed }}</p>
            <p v-else-if="!onMain && states" class="cz-hint muted cz-state-msg">{{ fill(t.czStateLocked!, { name: states[0]!.name }) }}</p>
            <div v-if="hasBoot && onMain" class="cz-stage-bar">
              <button type="button" class="cz-chip" :class="{ on: powerOn }" :aria-pressed="powerOn"
                      @click="powerOn = !powerOn">
                {{ powerOn ? t.czSimulateOff : t.czSimulate }}
              </button>
            </div>
          </div>

          <aside class="cz-panel">
            <!-- ---------- logo ---------- -->
            <section class="cz-card">
              <div class="cz-card-head">
                <h2>{{ t.czLogos }}</h2>
                <button type="button" class="btn cz-btn-sm" :disabled="logos.length >= CUSTOM_LIMITS.maxLogos || !canvasReady || !onMain"
                        @click="logoInput?.click()">+ {{ t.czAddLogo }}</button>
              </div>
              <p class="cz-hint muted">
                {{ logos.length >= CUSTOM_LIMITS.maxLogos ? fill(t.czLogosFull!, { n: CUSTOM_LIMITS.maxLogos }) : t.czLogoHint }}
              </p>
              <input ref="logoInput" type="file" accept="image/png,image/jpeg" class="cz-file" @change="onLogoFile">

              <ul class="cz-logos">
                <li v-for="logo in logos" :key="logo.id" :class="{ placing: placingId === logo.id }">
                  <div class="cz-logo-row">
                    <img :src="logo.src" alt="" class="cz-thumb">
                    <span v-if="!logo.p" class="cz-badge">{{ t.czNotPlaced }}</span>
                    <span class="cz-spacer"></span>
                    <button type="button" class="cz-link" :disabled="!onMain" @click="placingId = placingId === logo.id ? null : logo.id">
                      {{ logo.p ? t.czMove : t.czPlace }}
                    </button>
                    <button type="button" class="cz-link cz-link-danger" :disabled="!onMain" @click="removeLogo(logo.id)">{{ t.czRemove }}</button>
                  </div>
                  <fieldset v-if="logo.p" class="cz-fields" :disabled="!onMain">
                    <label class="cz-range">
                      <span>{{ t.czSize }}</span>
                      <input v-model.number="logo.size" type="range" :min="R * 0.02" :max="R" :step="R / 500" @input="touch">
                    </label>
                    <label class="cz-range">
                      <span>{{ t.czRotate }}</span>
                      <input v-model.number="logo.rot" type="range" min="-180" max="180" step="1" @input="touch">
                      <output>{{ logo.rot }}°</output>
                    </label>
                    <label class="cz-range">
                      <span>{{ t.czOpacity }}</span>
                      <input v-model.number="logo.opacity" type="range" min="0.1" max="1" step="0.05" @input="touch">
                    </label>
                  </fieldset>
                </li>
              </ul>
            </section>

            <!-- ---------- 开机画面 ---------- -->
            <section v-if="hasBoot" class="cz-card">
              <h2>{{ t.czBootTitle }}</h2>
              <p class="cz-hint muted">{{ t.czBootLead }}</p>
              <div class="cz-boot">
                <div class="cz-boot-preview" :style="{ background: boot.bg }">
                  <img v-if="bootPreview" :src="bootPreview" alt="">
                </div>
                <div class="cz-boot-actions">
                  <span class="cz-label">{{ t.czBootLogo }}</span>
                  <div class="cz-row">
                    <button type="button" class="btn btn-ghost cz-btn-sm" @click="bootInput?.click()">{{ t.czBootPick }}</button>
                    <button v-if="bootChanged" type="button" class="cz-link" @click="resetBoot">{{ t.czBootReset }}</button>
                  </div>
                  <label class="cz-color">
                    <span class="cz-label">{{ t.czBootBg }}</span>
                    <input v-model="boot.bg" type="color" @input="touch">
                  </label>
                </div>
              </div>
              <input ref="bootInput" type="file" accept="image/png,image/jpeg" class="cz-file" @change="onBootFile">
            </section>

            <!-- ---------- 保存 ---------- -->
            <section class="cz-card">
              <h2>{{ t.czSaveTitle }}</h2>
              <form @submit.prevent="save">
                <label class="field">
                  <span>{{ t.czEmail }}</span>
                  <input v-model.trim="email" type="email" autocomplete="email" maxlength="200" :disabled="busy">
                  <small class="muted cz-small">{{ t.czEmailHint }}</small>
                </label>
                <label class="field">
                  <span>{{ t.czNote }}</span>
                  <textarea v-model="note" rows="3" :maxlength="CUSTOM_LIMITS.maxNote" :disabled="busy"></textarea>
                </label>
                <!-- 蜜罐：真人看不见也点不到，机器人会填。和询盘表单同一个字段名 -->
                <input v-model="website" name="website" type="text" tabindex="-1" autocomplete="off" class="cz-hp" aria-hidden="true">
                <label class="cz-agree">
                  <input v-model="agree" type="checkbox" required :disabled="busy">
                  <span>{{ t.czAgree }}</span>
                </label>
                <p v-if="error" class="cz-error" role="alert">{{ error }}</p>
                <button type="submit" class="btn btn-block" :disabled="busy || !agree || !canvasReady">
                  {{ busy ? t.czSaving : t.czSave }}
                </button>
              </form>
            </section>
          </aside>
        </div>

        <!-- 这台设备上保存过的方案（localStorage，只是留痕） -->
        <details v-if="localPlans.length" class="cz-history">
          <summary>{{ t.czHistory }} ({{ localPlans.length }})</summary>
          <ul>
            <li v-for="p in localPlans" :key="p.id">
              <NuxtLink :to="localePath(`/customize/plan/${p.id}`)">{{ p.title || p.code }}</NuxtLink>
              <span class="muted"> · {{ formatDate(p.at) }}</span>
            </li>
          </ul>
        </details>
      </template>
    </main>

    <!-- 保存成功 -->
    <div v-if="savedId" class="cz-modal" role="dialog" aria-modal="true" :aria-label="t.czSavedTitle" @click.self="savedId = ''">
      <div class="cz-modal-box">
        <h2>{{ t.czSavedTitle }}</h2>
        <p class="muted">{{ t.czSavedLead }}</p>
        <input ref="linkInput" class="cz-link-input" :value="savedLink" readonly @focus="($event.target as HTMLInputElement).select()">
        <div class="cz-row cz-modal-actions">
          <button type="button" class="btn" @click="copyLink">{{ copied ? t.czCopied : t.czCopy }}</button>
          <a :href="savedLink" target="_blank" rel="noopener" class="btn btn-ghost">{{ t.czOpen }}</a>
          <button type="button" class="cz-link" @click="savedId = ''">{{ t.czClose }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Marks, Vec3 } from '@station-shared/model3d/marks.js'
// 只引不带 three 的解析（three 跟着画布组件异步下）
import { parseStates } from '@station-shared/model3d/stateList.js'
import type { StateStatus } from '@station-shared/model3d/states.js'
import {
  CUSTOM_LIMITS, PLAN_ID_RE, SavePlanError, checkImage, fetchCustomPlan, fetchCustomizeEditor,
  readLocalPlans, rememberLocalPlan, savePlan, toBlob,
  type DraftBoot, type DraftLogo, type LocalPlan
} from '~/lib/customize'
import { siteLangHeaders } from '~/composables/useSite'

const site = await useSiteInfo()
const route = useRoute()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)
const config = useRuntimeConfig()
/** 浏览器侧的接口前缀（保存、读旧方案都在客户端发）。留空 = 相对路径，由 nginx 转发 */
const clientBase = config.public.apiBase || ''
const code = String(route.params.code || '')

const { data: editor } = await useAsyncData(`customize-${code}-${site.lang}`,
  () => fetchCustomizeEditor(import.meta.server ? config.apiBase : clientBase, code, site.lang))

/* ---------------- 规格（2026-10-09） ----------------
   配了独立模型的规格可以单独定制；买家的 logo 贴在哪个模型上就存在哪个模型上，不跨规格投影。
   从详情页进来带 ?combo=，从旧方案进来用方案里的规格。 */
const comboList = computed(() => editor.value?.combos || [])
const initialCombo = String(route.query.combo || '')
const comboCode = ref(comboList.value.some(c => c.code === initialCombo) ? initialCombo : '')
/** 当前定制的模型：选中的规格，或者主模型。屏幕/开机键跟着它走（跟随主模型的规格没有屏幕） */
const cur = computed(() => {
  const e = editor.value
  const c = comboList.value.find(x => x.code === comboCode.value)
  if (c) return { model3d: c.model3d, screen: c.screen, power: c.power, states: c.model3dStates }
  return { model3d: e?.model3d || '', screen: e?.screen || null, power: e?.power || null, states: e?.states }
})
const comboGone = ref(false)

/* ---------------- 面板状态 ---------------- */

const logos = ref<DraftLogo[]>([])
const merchantBootBg = computed(() => cur.value.screen?.bootBg || '#000000')
const boot = ref<DraftBoot>({ src: null, file: null, bg: merchantBootBg.value })
// 只要商家标了屏幕就能换开机画面；开机键可有可无，没有时靠「模拟开机」按钮
const hasBoot = computed(() => !!cur.value.screen)
const bootChanged = computed(() => !!boot.value.src || boot.value.bg.toLowerCase() !== merchantBootBg.value.toLowerCase())
/** 开机画面缩略图：换了就是新图，没换就是商家原来那张 */
const bootPreview = computed(() => boot.value.src || cur.value.screen?.bootSrc || '')
/** 编辑时屏幕常亮哪一张：有开机图（买家换的或商家原来的）就亮开机图，没有就亮工作界面——否则屏幕是黑的 */
const staticScreen = computed(() => (hasBoot.value ? (bootPreview.value ? 'boot' : 'run') : null))

const placingId = ref<string | null>(null)

/* 模型状态（2026-10-08）：logo 只能在主状态（第一个）上调 */
const states = computed(() => parseStates(cur.value.states))
const stateId = ref<string | null>(null)
const stateLoading = ref<string | null>(null)
const stateFailed = ref(false)
const onMain = computed(() => !states.value || !stateId.value || stateId.value === states.value[0]?.id)
function onStateStatus(id: string, status: StateStatus) {
  stateLoading.value = status === 'loading' ? id : stateLoading.value === id ? null : stateLoading.value
  stateFailed.value = status === 'failed'
}
/** 点状态按钮：先清掉上一次的失败提示（失败时展示台会把 stateId 改回去，不能拿 stateId 变化来清） */
function pickState(id: string) {
  stateFailed.value = false
  stateId.value = id
}
// 切到别的状态：正在放置的 logo 取消
watch(onMain, (main) => { if (!main) placingId.value = null })
const powerOn = ref(false)
const R = ref(1)
const canvasReady = ref(false)
const modelChanged = ref(false)
const fromId = ref('')

const email = ref('')
const note = ref('')
const agree = ref(false)
const website = ref('')
const busy = ref(false)
const error = ref('')
const savedId = ref('')
const copied = ref(false)
const localPlans = ref<LocalPlan[]>([])

/** 有没保存的改动：离开页面前提醒 */
const dirty = ref(false)
const touch = () => { dirty.value = true }

const logoInput = ref<HTMLInputElement | null>(null)
const bootInput = ref<HTMLInputElement | null>(null)
const linkInput = ref<HTMLInputElement | null>(null)

let seq = 0
const round = (v: number) => Math.round(v * 1e6) / 1e6

/** 交给画布的完整标注：商家的屏幕/开机键 + 买家的 logo/开机画面 */
const marks = computed<Marks | null>(() => {
  const e = editor.value
  if (!e) return null
  const out: Marks = { v: 1 }
  const placed = logos.value.filter(l => l.p && l.n)
  if (placed.length) {
    out.logos = placed.map(l => ({ id: l.id, src: l.src, p: l.p!, n: l.n!, size: l.size, rot: l.rot, opacity: l.opacity }))
  }
  const c = cur.value
  if (c.screen) {
    out.screen = { ...c.screen, bootBg: boot.value.bg, ...(boot.value.src ? { bootSrc: boot.value.src } : {}) }
  }
  if (c.power) out.power = c.power
  return out
})

const placing = computed(() => {
  const l = logos.value.find(x => x.id === placingId.value)
  return l ? { size: l.size } : null
})

/** 切规格：换模型。已贴的 logo 坐标属于原模型，要清空——贴过就先确认 */
function pickCombo(next: string) {
  if (next === comboCode.value) return
  const hasWork = logos.value.some(l => l.p) || bootChanged.value
  if (hasWork && !window.confirm(t.value.czComboSwitchConfirm!)) return
  for (const l of logos.value) revoke(l.src)
  logos.value = []
  placingId.value = null
  powerOn.value = false
  stateId.value = null
  canvasReady.value = false
  comboCode.value = next
  revoke(boot.value.src)
  boot.value = { src: null, file: null, bg: merchantBootBg.value }
  if (hasWork) touch()
}

/* ---------------- 画布事件 ---------------- */

function onReady({ radius }: { radius: number }) {
  R.value = radius
  canvasReady.value = true
  // 旧方案带过来的 logo 没有 size 以外的问题；新加的默认大小按半径算，这里不用补
}

function onPlace({ p, n }: { p: Vec3; n: Vec3 }) {
  const logo = logos.value.find(l => l.id === placingId.value)
  if (logo) {
    logo.p = p
    logo.n = n
    touch()
  }
  placingId.value = null
}

/* ---------------- 选图 ---------------- */

function imageError(reason: 'type' | 'size' | 'edge') {
  return reason === 'size' ? t.value.czErrSize : reason === 'edge' ? t.value.czErrEdge : t.value.czErrType
}

async function onLogoFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || logos.value.length >= CUSTOM_LIMITS.maxLogos) return
  error.value = ''
  const bad = await checkImage(file)
  if (bad) {
    error.value = imageError(bad)!
    return
  }
  const id = 'b' + ++seq
  logos.value.push({
    id, src: URL.createObjectURL(file), file,
    p: null, n: null, size: round(R.value * 0.15), rot: 0, opacity: 1
  })
  placingId.value = id
  powerOn.value = false
  touch()
}

function removeLogo(id: string) {
  const i = logos.value.findIndex(l => l.id === id)
  if (i < 0) return
  revoke(logos.value[i]!.src)
  logos.value.splice(i, 1)
  if (placingId.value === id) placingId.value = null
  touch()
}

async function onBootFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''
  const bad = await checkImage(file)
  if (bad) {
    error.value = imageError(bad)!
    return
  }
  revoke(boot.value.src)
  boot.value = { ...boot.value, src: URL.createObjectURL(file), file }
  touch()
}

function resetBoot() {
  revoke(boot.value.src)
  boot.value = { src: null, file: null, bg: merchantBootBg.value }
  touch()
}

function revoke(src: string | null | undefined) {
  if (src?.startsWith('blob:')) URL.revokeObjectURL(src)
}

/* ---------------- 保存 ---------------- */

async function save() {
  if (busy.value || !editor.value) return
  error.value = ''
  const placed = logos.value.filter(l => l.p && l.n)
  const sendBoot = hasBoot.value && bootChanged.value
  if (!placed.length && !sendBoot) {
    error.value = t.value.czNeedSomething!
    return
  }
  busy.value = true
  try {
    // 图片位置一律写 upload:N，第 N 个文件跟在 files 里。旧方案带过来的线上图先取回成 Blob
    const files: Blob[] = []
    const upload = async (item: { file: File | null; src: string | null }) => {
      files.push(item.file || await toBlob(item.src!))
      return `upload:${files.length - 1}`
    }
    const dataLogos = []
    for (const l of placed) {
      dataLogos.push({ id: l.id, src: await upload(l), p: l.p, n: l.n, size: l.size, rot: l.rot, opacity: l.opacity })
    }
    let dataBoot: Record<string, string> | undefined
    if (sendBoot) {
      dataBoot = { bg: boot.value.bg }
      if (boot.value.src) dataBoot.src = await upload(boot.value)
    }

    const id = await savePlan(clientBase, {
      goodsCode: code,
      combo: comboCode.value || undefined,
      from: fromId.value || undefined,
      logos: dataLogos,
      boot: dataBoot,
      email: email.value || undefined,
      note: note.value || undefined,
      agree: agree.value,
      website: website.value
    }, files, siteLangHeaders(), t.value.czErrFailed!)

    savedId.value = id
    copied.value = false
    dirty.value = false
    rememberLocalPlan({ id, code, title: editor.value.title || code, at: Date.now() })
    localPlans.value = readLocalPlans()
  } catch (e: unknown) {
    if (e instanceof SavePlanError) {
      // 413 是 nginx 挡的（请求体超了），不是后端的话；其余用后端原话（已按语言翻好）
      error.value = e.status === 413 ? t.value.czErrTooLarge! : e.message
    } else {
      // 只有 toBlob 会走到这里：旧方案里的某张图取不回来了
      error.value = t.value.czErrRefetch!
    }
  } finally {
    busy.value = false
  }
}

const savedLink = computed(() => {
  if (!savedId.value || import.meta.server) return ''
  return location.origin + localePath(`/customize/plan/${savedId.value}`)
})

async function copyLink() {
  try {
    await navigator.clipboard.writeText(savedLink.value)
    copied.value = true
  } catch {
    // 没有剪贴板权限（非 https、老浏览器）：选中输入框，让他自己复制
    linkInput.value?.focus()
    linkInput.value?.select()
  }
}

/* ---------------- 从旧方案接着改（?from=方案号） ---------------- */

async function loadFrom(id: string) {
  const plan = await fetchCustomPlan(clientBase, id, site.lang).catch(() => null)
  const e = editor.value
  if (!plan || !e || plan.goodsCode !== e.goodsCode || !plan.marks) return
  fromId.value = plan.planId
  // 方案定制的是规格：规格还能定制就切过去；下架了退回主模型并提示
  if (plan.comboCode) {
    if (comboList.value.some(c => c.code === plan.comboCode)) comboCode.value = plan.comboCode
    else { comboCode.value = ''; comboGone.value = true }
  } else {
    comboCode.value = ''
  }
  modelChanged.value = !comboGone.value && !!plan.model3d && plan.model3d !== cur.value.model3d
  logos.value = (plan.marks.logos || []).slice(0, CUSTOM_LIMITS.maxLogos).map(l => ({
    id: 'b' + ++seq, src: l.src, file: null,
    p: l.p, n: l.n, size: l.size, rot: l.rot, opacity: l.opacity
  }))
  const s = plan.marks.screen
  if (s && hasBoot.value) {
    boot.value = {
      // 开机图和商家现在那张一样 = 买家当时没换，别当成新图再传一遍
      src: s.bootSrc && s.bootSrc !== cur.value.screen?.bootSrc ? s.bootSrc : null,
      file: null,
      bg: s.bootBg || merchantBootBg.value
    }
  }
}

/* ---------------- 生命周期 ---------------- */

function onBeforeUnload(e: BeforeUnloadEvent) {
  if (!dirty.value) return
  e.preventDefault()
  e.returnValue = ''
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (savedId.value) savedId.value = ''
    else placingId.value = null
  }
}

onMounted(() => {
  localPlans.value = readLocalPlans()
  window.addEventListener('beforeunload', onBeforeUnload)
  window.addEventListener('keydown', onKey)
  const from = String(route.query.from || '')
  if (PLAN_ID_RE.test(from)) loadFrom(from)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.removeEventListener('keydown', onKey)
  for (const l of logos.value) revoke(l.src)
  revoke(boot.value.src)
})

function formatDate(ms: number) {
  try {
    return new Intl.DateTimeFormat(site.lang || 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(ms))
  } catch {
    return new Date(ms).toLocaleString()
  }
}

useHead({
  title: `${fill(t.value.czTitle!, { name: editor.value?.title || code })} | ${site.companyName}`,
  // ⚠ robots.txt 里**不加** Disallow，理由见文件头
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.cz-root { min-height: 100vh; background: var(--surface-muted, #f4f6f8); }
.cz-page { padding-top: 22px; padding-bottom: 48px; }

.cz-head { margin-bottom: 16px; }
.cz-head h1 { font-size: clamp(1.4rem, 2.4vw, 1.9rem); margin: 6px 0 6px; }
.cz-head p { margin: 0; max-width: 720px; }
.cz-back { font-size: .88rem; font-weight: 600; color: var(--primary); }

.cz-warn {
  margin: 0 0 14px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: #fff7e6;
  border: 1px solid #ffd591;
  color: #874d00;
  font-size: .9rem;
}

.cz-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 20px;
  align-items: start;
}

.cz-stage { position: sticky; top: 76px; }
.cz-stage-canvas { height: min(72vh, 680px); }
.cz-stage-bar { display: flex; justify-content: center; gap: 10px; margin-top: 10px; }

.cz-chip {
  border: 1.5px solid var(--text);
  background: #fff;
  color: var(--text);
  border-radius: 999px;
  padding: 7px 16px;
  font-size: .85rem;
  font-weight: 600;
}
.cz-chip.on { background: var(--brand-blue); border-color: var(--brand-blue); color: var(--ink-dark); }
.cz-stage-bar { flex-wrap: wrap; }
.cz-chip { display: inline-flex; align-items: center; gap: 6px; }
.cz-chip:disabled { opacity: .5; cursor: default; }
.cz-chip-spin { width: 11px; height: 11px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent; animation: cz-chip-spin .8s linear infinite; }
@keyframes cz-chip-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .cz-chip-spin { animation-duration: 2.4s; } }
.cz-state-msg { text-align: center; margin: 6px 0 0; }
.cz-combos { display: flex; flex-wrap: wrap; gap: 10px; margin: 0 0 14px; }
/* 非主状态时整组滑块置灰（fieldset disabled 一并禁用里面的 input） */
.cz-fields { border: 0; margin: 0; padding: 0; min-width: 0; }
.cz-fields:disabled { opacity: .5; }
.cz-link:disabled { opacity: .45; cursor: default; }

.cz-panel { display: flex; flex-direction: column; gap: 14px; }
.cz-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px;
}
.cz-card h2 { font-size: 1.02rem; margin: 0 0 4px; }
.cz-card-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.cz-hint { font-size: .82rem; margin: 4px 0 10px; }
.cz-btn-sm { padding: 8px 14px; font-size: .82rem; }
.cz-file, .cz-hp {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.cz-hp { left: -9999px; }

.cz-logos { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.cz-logos li { border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 10px; }
.cz-logos li.placing { border-color: var(--brand-blue); box-shadow: 0 0 0 2px rgba(1, 150, 250, .2); }
.cz-logo-row { display: flex; align-items: center; gap: 8px; }
.cz-thumb {
  width: 44px;
  height: 44px;
  object-fit: contain;
  border-radius: 6px;
  background: repeating-conic-gradient(#e9edf1 0% 25%, #fff 0% 50%) 50% / 12px 12px;
  border: 1px solid var(--border);
}
.cz-badge { font-size: .75rem; color: #874d00; background: #fff7e6; border-radius: 4px; padding: 2px 6px; }
.cz-spacer { flex: 1; }
.cz-link {
  border: 0;
  background: none;
  padding: 4px 2px;
  color: var(--primary);
  font-size: .84rem;
  font-weight: 600;
}
.cz-link-danger { color: #c62828; }

.cz-range { display: grid; grid-template-columns: 76px 1fr auto; align-items: center; gap: 8px; margin-top: 8px; font-size: .82rem; color: var(--text-muted); }
.cz-range input { width: 100%; accent-color: var(--brand-blue); }
.cz-range output { min-width: 42px; text-align: right; font-variant-numeric: tabular-nums; }

.cz-boot { display: flex; gap: 14px; align-items: flex-start; }
.cz-boot-preview {
  flex: 0 0 96px;
  height: 120px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border);
  overflow: hidden;
}
.cz-boot-preview img { max-width: 70%; max-height: 70%; object-fit: contain; }
.cz-boot-actions { display: flex; flex-direction: column; gap: 8px; }
.cz-label { font-size: .82rem; color: var(--text-muted); }
.cz-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cz-color { display: flex; align-items: center; gap: 10px; }
.cz-color input { width: 44px; height: 32px; padding: 0; border: 1px solid var(--border); border-radius: 6px; background: #fff; }

.cz-small { display: block; margin-top: 5px; font-size: 12px; line-height: 1.5; }
.cz-agree { display: flex; gap: 8px; align-items: flex-start; font-size: .88rem; margin: 4px 0 12px; }
.cz-agree input { margin-top: 3px; }
.cz-error { color: #c62828; font-size: .88rem; margin: 0 0 10px; }

.cz-history { margin-top: 24px; background: #fff; border: 1px solid var(--border); border-radius: var(--radius); padding: 12px 16px; }
.cz-history summary { cursor: pointer; font-weight: 600; }
.cz-history ul { margin: 10px 0 0; padding-left: 18px; }
.cz-history li { margin: 4px 0; font-size: .9rem; }

.cz-notice { text-align: center; padding: 80px 0; }
.cz-notice h1 { font-size: 1.6rem; }

.cz-modal {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(10, 13, 17, .55);
}
.cz-modal-box {
  width: min(520px, 100%);
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 22px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, .25);
}
.cz-modal-box h2 { margin: 0 0 8px; font-size: 1.25rem; }
.cz-link-input {
  width: 100%;
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font: inherit;
  font-size: .88rem;
  background: var(--surface-muted, #f4f6f8);
}
.cz-modal-actions { justify-content: flex-start; }

@media (max-width: 900px) {
  .cz-layout { grid-template-columns: 1fr; }
  .cz-stage { position: static; }
  .cz-stage-canvas { height: 56vh; min-height: 300px; }
}
</style>
