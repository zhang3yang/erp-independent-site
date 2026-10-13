<!--
  引导式下单（下单向导），2026-09-28。

  照嘉立创 PCB 在线下单页的交互：左边步骤条（滚到哪组亮哪组、必填都答了的打勾），
  中间一组一组选参数，右边固定一栏汇总 + 检查 + 提交。设计：
  docs/superpowers/specs/2026-09-28-order-wizard-design.md

  ## 这一页只负责渲染和体验，把关在后端

  分组、字段、选项全部来自 `/openapi/site/wizard`（商家在管理台「引导下单」里配的），
  页面上一个参数都没写死。显隐、灰掉、必填、起订量的规则在 `app/lib/wizard.ts`，
  和后端 `OrderWizardService.resolve` 同一套——前台算是为了及时提示，
  后端再算一遍才作数（公网上的 JS 谁都能改）。

  ## 提交走的还是 POST /openapi/site/order

  请求体多一个 `wizard.answers`，后端据此自己算订单行、忽略 items。所以限流、蜜罐、
  回执邮件、回执页、买家中心全部是现成的那一套。联系方式和目的地那一块照抄结账页的字段，
  上一单填过的（localStorage `station-buyer`，和结账页同一个键）自动带出来。

  ## 不给收录

  和购物车 / 结账页一样 noindex：这一页的内容就是那份向导，没有可被搜索的正文。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <div class="wrap wz-page">
      <!-- 没开 -->
      <div v-if="!wizard" class="notice">
        <h1>{{ t.wzClosedTitle }}</h1>
        <p class="muted">{{ t.wzClosedLead }}</p>
        <NuxtLink :to="localePath('/products')" class="btn">{{ t.explore }}</NuxtLink>
      </div>

      <template v-else>
        <header class="wz-head">
          <h1>{{ wizard.title || t.wzNav }}</h1>
          <p v-if="wizard.lead" class="muted">{{ wizard.lead }}</p>
        </header>

        <form class="wz-layout" novalidate @submit.prevent="submit">
          <!-- ---------- 左：步骤条 ---------- -->
          <nav class="wz-steps" :aria-label="t.wzStepsLabel">
            <a v-for="(s, i) in steps" :key="s.code" :href="`#wz-${s.code}`"
               :class="['wz-step', { on: activeStep === s.code, done: s.done && activeStep !== s.code }]"
               @click.prevent="goStep(s.code)">
              <span class="wz-dot" aria-hidden="true">{{ s.done && activeStep !== s.code ? '✓' : i + 1 }}</span>
              <span class="wz-step-name">{{ s.title }}</span>
            </a>
          </nav>

          <!-- ---------- 中：分组 ---------- -->
          <div class="wz-main">
            <section v-for="g in visibleGroups" :id="`wz-${g.code}`" :key="g.code" class="wz-group">
              <div class="wz-group-head">
                <h2>{{ g.title }}</h2>
                <span v-if="g.sub" class="muted">{{ g.sub }}</span>
              </div>

              <div v-for="f in g.fields.filter(x => vis.fields.has(x.code))" :key="f.code"
                   :class="['wz-row', { bad: fieldIssue(f.code) }]" :data-field="f.code">
                <div class="wz-label">
                  <span>
                    <b v-if="f.required" class="req" aria-hidden="true">*</b>{{ f.title }}
                  </span>
                  <span v-if="f.help" class="wz-help" tabindex="0" :aria-label="f.help">?
                    <span class="wz-tip" role="tooltip">{{ f.help }}</span>
                  </span>
                </div>

                <div class="wz-ctl">
                  <!-- 选项类：卡片 / 按钮 / 色块 -->
                  <div v-if="isOptionField(f)" :class="['wz-opts', `wz-opts--${f.type}`]"
                       :role="f.multi ? 'group' : 'radiogroup'" :aria-label="f.title">
                    <button v-for="o in f.options" :key="o.code" type="button"
                            :class="['wz-opt', `wz-opt--${f.type}`, { sel: isPicked(f, o.code), dis: !optionOpen(o, vis.live) }]"
                            :aria-pressed="isPicked(f, o.code)"
                            :aria-disabled="!optionOpen(o, vis.live)"
                            :title="optionOpen(o, vis.live) ? '' : t.wzLocked"
                            @click="pick(f, o)">
                      <span v-if="o.badge" class="wz-badge">{{ o.badge }}</span>
                      <template v-if="f.type === 'cards'">
                        <!-- 没图也没图标就不留图位：一个空灰框看着像图片坏了 -->
                        <span v-if="optImage(o) || o.icon" class="wz-pic">
                          <img v-if="optImage(o)" :src="optImage(o)" :alt="o.title" loading="lazy">
                          <span v-else class="wz-icon" aria-hidden="true">{{ o.icon }}</span>
                        </span>
                        <span class="wz-opt-name">{{ o.title }}</span>
                        <span v-if="o.desc" class="wz-opt-desc">{{ o.desc }}</span>
                      </template>
                      <template v-else-if="f.type === 'swatch'">
                        <i class="wz-swatch" :style="{ background: o.color || 'transparent' }" aria-hidden="true"></i>
                        {{ o.title }}
                      </template>
                      <template v-else>{{ o.title }}</template>
                    </button>
                  </div>

                  <!-- 数字 -->
                  <div v-else-if="f.type === 'number'" class="wz-stepper">
                    <button type="button" :aria-label="`- ${f.title}`" @click="bump(f, -1)">−</button>
                    <input :value="answers[f.code] ?? ''" type="number" inputmode="numeric"
                           :min="f.min" :max="f.max" :aria-label="f.title"
                           @input="setNumber(f, ($event.target as HTMLInputElement).value)">
                    <button type="button" :aria-label="`+ ${f.title}`" @click="bump(f, 1)">+</button>
                  </div>

                  <!-- 文本 -->
                  <textarea v-else-if="f.type === 'text'" class="wz-input" rows="3" maxlength="1000"
                            :value="(answers[f.code] as string) || ''" :aria-label="f.title"
                            @input="setValue(f.code, ($event.target as HTMLTextAreaElement).value)"></textarea>

                  <!-- 日期 -->
                  <input v-else-if="f.type === 'date'" class="wz-input wz-date" type="date" :min="today"
                         :value="(answers[f.code] as string) || ''" :aria-label="f.title"
                         @input="setValue(f.code, ($event.target as HTMLInputElement).value)">

                  <!-- 商品 × 型号 数量表 -->
                  <div v-else-if="f.type === 'goods'" class="wz-goods">
                    <p v-if="!goodsRows(wizard, f, answers).length" class="wz-empty">{{ t.wzEmptyGoods }}</p>
                    <div v-for="g2 in goodsRows(wizard, f, answers)" :key="g2.code" class="wz-goods-row">
                      <div class="wz-goods-name">
                        <img v-if="g2.image" :src="resizedImage(g2.image, 96)" :alt="g2.title" loading="lazy">
                        <strong>{{ g2.title }}</strong>
                        <span class="muted">{{ t.wzQty }} {{ rowTotal(f.code, g2.code) }}</span>
                      </div>
                      <div class="wz-cells">
                        <label v-for="c in (g2.combos.length ? g2.combos : [{ code: '', title: t.wzQty }])" :key="c.code" class="wz-cell">
                          <span>{{ c.title }}</span>
                          <input type="number" min="0" inputmode="numeric" placeholder="0"
                                 :value="cellValue(f.code, g2.code, c.code)"
                                 :aria-label="`${g2.title} ${c.title}`"
                                 @input="setCell(f.code, g2.code, c.code, ($event.target as HTMLInputElement).value)">
                        </label>
                      </div>
                    </div>
                  </div>

                  <p v-if="f.hint" class="wz-hint">{{ f.hint }}</p>
                  <p v-if="fieldIssue(f.code)" class="wz-err">{{ fieldIssue(f.code) }}</p>
                </div>
              </div>
            </section>

            <!-- ---------- 最后一组：联系方式与收货（固定，不在 schema 里） ---------- -->
            <section id="wz-contact" class="wz-group">
              <div class="wz-group-head">
                <h2>{{ t.wzContactStep }}</h2>
                <span class="muted">{{ t.wzContactSub }}</span>
              </div>

              <div class="grid-2">
                <label class="field">
                  <span>{{ t.coName }} *</span>
                  <input v-model.trim="form.name" maxlength="100" autocomplete="name" :class="{ bad: errors.name }">
                  <em v-if="errors.name" class="wz-err">{{ errors.name }}</em>
                </label>
                <label class="field">
                  <span>{{ t.coEmail }} *</span>
                  <input v-model.trim="form.email" type="email" maxlength="150" autocomplete="email" :class="{ bad: errors.email }">
                  <em v-if="errors.email" class="wz-err">{{ errors.email }}</em>
                </label>
              </div>
              <div class="grid-2">
                <label class="field">
                  <span>{{ t.coCompany }}</span>
                  <input v-model.trim="form.company" maxlength="200" autocomplete="organization">
                </label>
                <label class="field">
                  <span>{{ t.coPhone }}</span>
                  <input v-model.trim="form.phone" maxlength="50" autocomplete="tel">
                </label>
              </div>
              <div class="grid-2">
                <label class="field">
                  <span>{{ t.coCountry }} *</span>
                  <input v-model.trim="form.country" maxlength="80" autocomplete="country-name" :class="{ bad: errors.country }">
                  <em v-if="errors.country" class="wz-err">{{ errors.country }}</em>
                </label>
                <label class="field">
                  <span>{{ t.coCity }}</span>
                  <input v-model.trim="form.city" maxlength="80" autocomplete="address-level2">
                </label>
              </div>
              <div class="grid-2">
                <label class="field">
                  <span>{{ t.coLine1 }} <em class="opt">{{ t.coOptional }}</em></span>
                  <input v-model.trim="form.line1" maxlength="200" autocomplete="address-line1">
                </label>
                <label class="field">
                  <span>{{ t.coPostal }} <em class="opt">{{ t.coOptional }}</em></span>
                  <input v-model.trim="form.postal" maxlength="30" autocomplete="postal-code">
                </label>
              </div>

              <div class="wz-row wz-row--plain">
                <div class="wz-label"><span>{{ t.coPrefsTitle }}</span></div>
                <div class="wz-ctl">
                  <div class="wz-opts">
                    <button v-for="p in shippingOptions" :key="p.id" type="button"
                            :class="['wz-opt', { sel: form.shipping === p.id }]" :title="p.note"
                            @click="form.shipping = p.id">{{ p.label }}</button>
                  </div>
                  <div class="wz-opts wz-opts--gap">
                    <button v-for="p in paymentOptions" :key="p.id" type="button"
                            :class="['wz-opt', { sel: form.payment === p.id }]" :title="p.note"
                            @click="form.payment = p.id">{{ p.label }}</button>
                  </div>
                </div>
              </div>

              <label class="field">
                <span>{{ t.coNoteTitle }}</span>
                <textarea v-model.trim="form.note" rows="4" maxlength="800" :placeholder="t.coNoteHint"></textarea>
              </label>

              <!-- 蜜罐，和结账页同一个字段名。见后端 submitOrder -->
              <div class="trap" aria-hidden="true">
                <input v-model="form.website" type="text" name="website" tabindex="-1" autocomplete="off">
              </div>
            </section>
          </div>

          <!-- ---------- 右：汇总 ---------- -->
          <aside class="wz-side">
            <div class="wz-card">
              <h3>{{ t.wzSummary }}</h3>
              <div class="wz-kv"><span>{{ t.wzLeadTime }}</span><span>{{ t.wzLeadTimeValue }}</span></div>
              <div v-if="shipToLabel" class="wz-kv"><span>{{ t.wzShipTo }}</span><span>{{ shipToLabel }}</span></div>

              <ul v-if="rows.length" class="wz-sum">
                <li v-for="r in rows" :key="r.group + r.field">
                  <span>{{ r.field }}</span><span>{{ r.value }}</span>
                </li>
              </ul>
              <p v-else class="muted wz-sum-empty">{{ t.wzSummaryEmpty }}</p>

              <div class="wz-total">
                <span>{{ t.wzTotalQty }}</span><b>{{ totalQty }}</b>
              </div>
              <!--
                预估总价：只算商品（单价 × 数量），定制项加价是预留字段、不计入，运费也不在里面——
                wzPriceNote 那句话必须留着，不然买家会把它当成最终价。
                ⚠ 金额按站点本币显示，和结算页同一个理由（下单落的是本币）；访客在顶栏切了币种时，
                  下面补一行「≈ 换算后的数」，口径照抄 checkout 的 approxLine。
              -->
              <div class="wz-total wz-total--price">
                <span>{{ t.wzPrice }}<small class="muted">{{ t.wzPriceNote }}</small></span>
                <b>{{ est.subtotal > 0 ? money(est.subtotal) : t.wzPriceValue }}</b>
              </div>
              <p v-if="approxLine" class="wz-approx">{{ approxLine }}</p>
              <p v-if="est.onRequest && est.subtotal > 0" class="wz-approx muted">
                {{ fill(t.cartInquireNote, { n: est.onRequest }) }}
              </p>

              <ul v-if="checked" :class="['wz-issues', issueList.length ? 'bad' : 'good']" aria-live="polite">
                <li v-for="(m, i) in issueList" :key="i">{{ m }}</li>
                <li v-if="!issueList.length">{{ t.wzOk }}</li>
              </ul>

              <button type="button" class="btn btn-ghost btn-block" @click="runCheck(true)">{{ t.wzCheck }}</button>
              <button type="submit" class="btn btn-block wz-submit" :disabled="sending">
                <span v-if="sending" class="spinner" aria-hidden="true"></span>
                {{ sending ? t.wzSubmitting : t.wzSubmit }}
              </button>
              <label class="wz-agree">
                <input v-model="agree" type="checkbox">
                <span>{{ t.wzAgree }}</span>
              </label>
              <p v-if="agreeMissing" class="wz-err">{{ t.wzAgreeNeeded }}</p>
              <p v-if="failMsg" class="wz-err">
                {{ failMsg }}
                <a v-if="site.contact?.email" :href="`mailto:${site.contact.email}`">{{ site.contact.email }}</a>
              </p>
            </div>

            <div class="wz-card wz-share">
              <div>
                <strong>{{ t.wzShare }}</strong>
                <small class="muted">{{ t.wzShareLead }}</small>
              </div>
              <button type="button" class="btn btn-ghost" @click="share">{{ t.wzShareBtn }}</button>
            </div>
          </aside>
        </form>
      </template>
    </div>

    <div :class="['wz-toast', { show: toastMsg }]" role="status">{{ toastMsg }}</div>
    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
import { PAYMENT_PREFS, SHIPPING_PREFS, formatMoney } from '~/lib/shop'
import type { CartItem } from '~/lib/shop'
import { resizedImage } from '~/lib/imageUrl'
import {
  type Answers, type GoodsQty, type WizardField, type WizardOption,
  check, estimate, goodsRows, isOptionField, optionOpen, orderLines, picked, prune, summaryRows, visibility
} from '~/lib/wizard'

const site = await useSiteInfo()
const localePath = useLocalePath()
const localeHref = useLocaleHref()
const t = useShopCopy(() => site.lang)

const { data: wizardData } = await useAsyncData(`wizard-${site.lang}`, () => fetchWizard(site.lang))
const wizard = computed(() => wizardData.value || null)

/* ---------------- 答案 ---------------- */
const answers = reactive<Answers>({})
const vis = computed(() => wizard.value ? visibility(wizard.value, answers) : { fields: new Set<string>(), groups: new Set<string>(), live: {} })
const visibleGroups = computed(() => (wizard.value?.groups || []).filter(g => vis.value.groups.has(g.code)))
const rows = computed(() => wizard.value ? summaryRows(wizard.value, answers, site.lang === 'zh' || site.lang === 'ja' ? '、' : ', ') : [])
/** 预估：数量合计、有标价那部分的金额、面议行数。算法在 lib/wizard.ts 的 estimate，和后端 repriceLines 同口径 */
const est = computed(() => wizard.value ? estimate(wizard.value, answers) : { subtotal: 0, onRequest: 0, qty: 0 })
const totalQty = computed(() => est.value.qty)

/*
  金额一律按站点本币（结算页同一条规矩，见 checkout 的 sum-total 注释）；
  访客切了显示币种时补一行 ≈ 换算值，并说清以哪个币种为准。
*/
const baseCurrency = computed(() => wizard.value?.currency || site.currency)
const money = (n: number) => formatMoney(n, baseCurrency.value, site.lang)
const currency = useDisplayCurrency(() => site)
const approxLine = computed(() => {
  if (!est.value.subtotal || currency.current.value === currency.base.value) return ''
  const converted = currency.convert(est.value.subtotal)
  if (converted.currency === currency.base.value) return ''
  return `≈ ${formatMoney(converted.amount, converted.currency, site.lang)} · ${fill(t.value.coChargedIn!, { currency: baseCurrency.value })}`
})
const today = new Date().toISOString().slice(0, 10)

function isPicked(f: WizardField, code: string) {
  return picked(answers[f.code]).includes(code)
}

function optImage(o: WizardOption) {
  if (o.image) return resizedImage(o.image, 240)
  const g = o.goods ? wizard.value?.goods[o.goods] : undefined
  return g?.image ? resizedImage(g.image, 240) : ''
}

function pick(f: WizardField, o: WizardOption) {
  if (!optionOpen(o, vis.value.live)) {
    toast(t.value.wzLocked!)
    return
  }
  if (f.multi) {
    const list = picked(answers[f.code])
    answers[f.code] = list.includes(o.code) ? list.filter(x => x !== o.code) : [...list, o.code]
  } else {
    // 非必填的单选可以再点一下取消；必填的点了就一直选着
    answers[f.code] = answers[f.code] === o.code && !f.required ? undefined : o.code
  }
  // 联动：目的国家 → 插头、认证。只改存在的字段，值要是那个字段真有的选项
  if (isPicked(f, o.code)) applySets(o)
  afterChange()
}

function applySets(o: WizardOption) {
  const fields = wizard.value?.groups.flatMap(g => g.fields) || []
  for (const [key, value] of Object.entries(o.sets || {})) {
    const target = fields.find(x => x.code === key)
    if (!target?.options) continue
    const codes = (Array.isArray(value) ? value : [value]).filter(v => target.options!.some(x => x.code === v))
    if (!codes.length) continue
    answers[key] = target.multi ? codes : codes[0]
  }
}

function setValue(code: string, value: string) {
  answers[code] = value || undefined
  afterChange()
}

function setNumber(f: WizardField, raw: string) {
  const n = Math.floor(Number(raw))
  answers[f.code] = raw === '' || !Number.isFinite(n) ? undefined : Math.max(0, n)
  afterChange()
}

function bump(f: WizardField, d: number) {
  const now = Number(answers[f.code]) || 0
  const next = Math.min(f.max ?? 9999999, Math.max(f.min ?? 0, now + d))
  answers[f.code] = next
  afterChange()
}

function cellValue(field: string, goods: string, combo: string) {
  const v = (answers[field] as GoodsQty | undefined)?.[goods]?.[combo]
  return v ? v : ''
}

function setCell(field: string, goods: string, combo: string, raw: string) {
  const n = Math.max(0, Math.floor(Number(raw) || 0))
  const all = { ...((answers[field] as GoodsQty) || {}) }
  all[goods] = { ...(all[goods] || {}), [combo]: n }
  answers[field] = all
  afterChange()
}

function rowTotal(field: string, goods: string) {
  const cells = (answers[field] as GoodsQty | undefined)?.[goods] || {}
  return Object.values(cells).reduce((s, n) => s + (Number(n) || 0), 0)
}

/* ---------------- 检查 ---------------- */
const checked = ref(false)
const issueMap = ref<Record<string, string>>({})
const issueList = ref<string[]>([])

function fieldIssue(code: string) {
  return checked.value ? issueMap.value[code] || '' : ''
}

function runCheck(scroll: boolean) {
  if (!wizard.value) return false
  checked.value = true
  const fields = wizard.value.groups.flatMap(g => g.fields)
  const title = (code: string) => fields.find(f => f.code === code)?.title || code
  const map: Record<string, string> = {}
  const list: string[] = []
  for (const i of check(wizard.value, answers)) {
    let msg = ''
    if (i.kind === 'required') msg = fill(t.value.wzRequired!, { field: title(i.field) })
    else if (i.kind === 'range') msg = fill(t.value.wzRange!, { field: title(i.field), min: i.min ?? 0, max: i.max ?? 0 })
    else if (i.kind === 'moq') msg = fill(t.value.wzMoq!, { option: i.option || '', min: i.min ?? 0, now: i.now ?? 0 })
    else msg = t.value.wzNoLines!
    if (i.field && !map[i.field]) map[i.field] = msg
    list.push(msg)
  }
  issueMap.value = map
  issueList.value = list
  if (list.length && scroll) {
    nextTick(() => document.querySelector('.wz-row.bad')?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
  }
  return !list.length
}

/* ---------------- 联系方式（照结账页） ---------------- */
const SHIP_COPY: Record<string, string> = { sea: 'coShipSea', air: 'coShipAir', express: 'coShipExpress', agent: 'coShipAgent' }
const PAY_COPY: Record<string, string> = { tt: 'coPayTt', lc: 'coPayLc', paypal: 'coPayPaypal', other: 'coPayOther' }
const shippingOptions = computed(() => SHIPPING_PREFS.map(id => ({ id, label: t.value[SHIP_COPY[id]!]!, note: t.value[`${SHIP_COPY[id]}Note`] || '' })))
const paymentOptions = computed(() => PAYMENT_PREFS.map(id => ({ id, label: t.value[PAY_COPY[id]!]!, note: t.value[`${PAY_COPY[id]}Note`] || '' })))

const form = reactive({
  name: '', email: '', company: '', phone: '',
  country: '', city: '', line1: '', postal: '',
  shipping: SHIPPING_PREFS[0] as string,
  payment: PAYMENT_PREFS[0] as string,
  note: '',
  website: ''
})
const errors = reactive<Record<string, string>>({})

/** 和结账页同一个键：上一单填过的联系方式，这里也带出来。⚠ 不存备注，理由见结账页 */
const BUYER_KEY = 'station-buyer'
function loadBuyer() {
  try {
    const saved = JSON.parse(localStorage.getItem(BUYER_KEY) || 'null')
    if (!saved || typeof saved !== 'object') return
    for (const k of ['name', 'email', 'company', 'phone', 'country', 'city', 'line1', 'postal', 'shipping', 'payment'] as const) {
      if (typeof saved[k] === 'string' && saved[k]) form[k] = saved[k]
    }
  } catch { /* 坏数据：当没填过 */ }
}
function saveBuyer() {
  try {
    const prev = JSON.parse(localStorage.getItem(BUYER_KEY) || '{}') || {}
    const { note, website, ...keep } = form
    localStorage.setItem(BUYER_KEY, JSON.stringify({ ...prev, ...keep }))
  } catch { /* 隐私模式 */ }
}

/** 向导里选的目的国家（字段编码约定为 country），用来带出收货国家、显示在汇总栏 */
const shipToLabel = computed(() => {
  const f = wizard.value?.groups.flatMap(g => g.fields).find(x => x.code === 'country')
  const code = answers.country as string | undefined
  return (code && f?.options?.find(o => o.code === code)?.title) || form.country || ''
})
watch(shipToLabel, (label, old) => {
  // 收货国家空着、或者还是上一次自动带出来的那个，才跟着改；他亲手改过的不动
  if (label && (!form.country || form.country === old)) form.country = label
})

/* ---------------- 草稿 & 分享链接 ---------------- */
/*
  草稿存 localStorage，刷新不丢；分享链接把答案编码进 #c=…（hash 不发给服务器，
  不进 nginx 日志）。两者只存**向导答案**，联系方式不进链接——链接会被转发。
*/
const DRAFT_KEY = 'station-wizard-draft'
function encode(a: Answers) {
  return btoa(unescape(encodeURIComponent(JSON.stringify(a))))
}
function restoreAnswers() {
  let loaded: Answers | null = null
  try {
    const m = location.hash.match(/[#&]c=([^&]+)/)
    if (m) loaded = JSON.parse(decodeURIComponent(escape(atob(decodeURIComponent(m[1]!)))))
  } catch { /* 坏链接：当没有 */ }
  if (!loaded) {
    try { loaded = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null') } catch { loaded = null }
  }
  if (loaded && typeof loaded === 'object') {
    for (const [k, v] of Object.entries(loaded)) answers[k] = v as Answers[string]
    // 草稿 / 分享链接可能是向导改版之前存的：已经不能选的值当场退掉
    dropLocked()
  }
}
/**
 * 条件变了之后，已经选上、但现在灰掉的选项自动退掉。
 *
 * 不退的话页面上它还高亮着（比如从 OEM 切回品牌现货，「丝印」仍是选中样子），
 * 提交时又会被 prune 悄悄丢掉——看到的和交上去的不一致。后端对灰的选项是直接拒的，同一个口径。
 * 退掉一个可能让后面的条件也跟着变，所以多走几遍直到不再变。
 */
function dropLocked() {
  const w = wizard.value
  if (!w) return
  for (let pass = 0; pass < 5; pass++) {
    let changed = false
    const { live } = visibility(w, answers)
    for (const f of w.groups.flatMap(g => g.fields)) {
      if (!isOptionField(f)) continue
      const cur = picked(answers[f.code])
      const keep = cur.filter(code => {
        const o = f.options?.find(x => x.code === code)
        return !o || optionOpen(o, live)
      })
      if (keep.length !== cur.length) {
        answers[f.code] = f.multi ? keep : keep[0]
        changed = true
      }
    }
    if (!changed) break
  }
}

let draftTimer: ReturnType<typeof setTimeout> | undefined
function afterChange() {
  dropLocked()
  if (checked.value) runCheck(false)
  clearTimeout(draftTimer)
  draftTimer = setTimeout(() => {
    try { localStorage.setItem(DRAFT_KEY, JSON.stringify(answers)) } catch { /* 隐私模式 */ }
  }, 400)
}
async function share() {
  if (!wizard.value) return
  const url = `${location.origin}${location.pathname}#c=${encodeURIComponent(encode(prune(wizard.value, answers)))}`
  history.replaceState(history.state, '', url)
  try {
    await navigator.clipboard.writeText(url)
    toast(t.value.wzShareDone!)
  } catch {
    toast(t.value.wzShareFallback!)
  }
}

/* ---------------- 步骤条 ---------------- */
const steps = computed(() => {
  const w = wizard.value
  if (!w) return []
  const issues = check(w, answers)
  const bad = new Set(issues.map(i => i.field))
  const list = visibleGroups.value.map(g => {
    const visibleFields = g.fields.filter(f => vis.value.fields.has(f.code))
    const done = visibleFields.every(f => !bad.has(f.code)) &&
      visibleFields.some(f => answers[f.code] !== undefined)
    return { code: g.code, title: g.title, done }
  })
  list.push({ code: 'contact', title: t.value.wzContactStep!, done: !!(form.name && form.email && form.country) })
  return list
})
const activeStep = ref('')
function goStep(code: string) {
  document.getElementById(`wz-${code}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function spy() {
  let cur = steps.value[0]?.code || ''
  for (const s of steps.value) {
    const el = document.getElementById(`wz-${s.code}`)
    if (el && el.getBoundingClientRect().top < 160) cur = s.code
  }
  activeStep.value = cur
}

/* ---------------- 提交 ---------------- */
const agree = ref(false)
const agreeMissing = ref(false)
const sending = ref(false)
const failMsg = ref('')
const { submit: submitOrder, restore: restoreOrders } = useOrders()
const { buyer, refresh: refreshBuyer } = useBuyer()

function validateContact() {
  for (const k of Object.keys(errors)) delete errors[k]
  if (!form.name) errors.name = t.value.coBadName!
  if (!form.email || !/^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/.test(form.email)) errors.email = t.value.coBadEmail!
  if (!form.country) errors.country = t.value.coBadCountry!
  return !Object.keys(errors).length
}

async function submit() {
  if (sending.value || !wizard.value) return
  const okWizard = runCheck(true)
  const okContact = validateContact()
  if (!okWizard) return
  if (!okContact) {
    nextTick(() => document.querySelector('#wz-contact .bad')?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
    return
  }
  agreeMissing.value = !agree.value
  if (!agree.value) return

  const w = wizard.value
  // 本地回执用的行：单价取向导接口下发的那份（商品 / 型号价），小计仍以服务端回的为准，见 useOrders.record
  const items: CartItem[] = orderLines(w, answers).map(l => ({
    code: l.goods.code,
    comboCode: l.combo?.code,
    comboName: l.combo?.title,
    title: l.goods.title,
    subtitle: '',
    price: l.price,
    currency: baseCurrency.value,
    image: l.goods.image || '',
    qty: l.qty,
    moq: 0,
    unit: l.goods.unit || ''
  }))

  sending.value = true
  failMsg.value = ''
  let orderCode: string | null
  try {
    orderCode = await submitOrder(items, {
      contact: { name: form.name, email: form.email, company: form.company, phone: form.phone },
      address: { line1: form.line1, city: form.city, postal: form.postal, country: form.country },
      shipping: form.shipping,
      payment: form.payment,
      note: form.note,
      currency: site.currency,
      lang: site.lang,
      website: form.website,
      wizard: { answers: prune(w, answers) as Record<string, unknown>, rows: rows.value }
    })
  } catch (e) {
    // 后端复核不过的那句话（按买家语言）原样给他看：它说清了是哪一项
    failMsg.value = (e as Error)?.message || t.value.coFailed!
    sending.value = false
    return
  }
  if (!orderCode) {
    failMsg.value = t.value.coFailed!
    sending.value = false
    return
  }

  saveBuyer()
  try { localStorage.removeItem(DRAFT_KEY) } catch { /* 隐私模式 */ }
  sending.value = false
  // 回执页分流和结账页一样：登录买家去 buyer./orders，游客去主域 /order-received
  const href = localeHref(buyer.value ? `/orders/${orderCode}` : `/order-received/${orderCode}`)
  await navigateTo(href, { external: /^https?:/.test(href) })
}

/* ---------------- 杂项 ---------------- */
const toastMsg = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined
function toast(msg: string) {
  toastMsg.value = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMsg.value = '' }, 2600)
}

onMounted(() => {
  restoreAnswers()
  loadBuyer()
  restoreOrders()
  void refreshBuyer()
  spy()
  window.addEventListener('scroll', spy, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('scroll', spy))

useHead({
  title: `${wizard.value?.title || t.value.wzNav} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.wz-page { padding: 34px 20px 40px; }
.wz-head { margin-bottom: 24px; }
.wz-head h1 { font-size: clamp(1.8rem, 3.6vw, 2.5rem); margin-bottom: 8px; }
.wz-head p { margin: 0; max-width: 720px; }

.notice {
  text-align: center; padding: 60px 20px 80px;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
}
.notice h1 { font-size: 1.5rem; margin-bottom: 10px; }
.notice p { margin: 0 auto 24px; max-width: 560px; }

.wz-layout {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 340px;
  gap: 18px;
  align-items: start;
}

/* ---------- 步骤条 ---------- */
.wz-steps {
  position: sticky; top: 90px;
  display: flex; flex-direction: column;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 10px 0;
}
.wz-step {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 8px; text-align: center; font-size: 0.8rem; color: var(--text-muted);
  text-decoration: none; position: relative;
}
.wz-step:not(:last-child)::after {
  content: ''; position: absolute; bottom: -6px; left: 50%; width: 1px; height: 12px; background: var(--border);
}
.wz-dot {
  width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center;
  font-size: 0.72rem; font-weight: 700; border: 2px solid var(--border); background: var(--surface);
}
.wz-step.on { color: var(--primary); font-weight: 600; }
.wz-step.on .wz-dot { border-color: var(--primary); color: var(--primary); box-shadow: 0 0 0 4px var(--accent-soft, rgba(11, 114, 171, 0.12)); }
.wz-step.done .wz-dot { border-color: #15803d; background: #15803d; color: #fff; }

/* ---------- 分组 ---------- */
.wz-main { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.wz-group {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 0 22px 14px; scroll-margin-top: 90px;
}
.wz-group-head {
  display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap;
  margin: 0 -22px 6px; padding: 14px 22px;
  background: var(--surface-muted); border-radius: var(--radius) var(--radius) 0 0;
}
.wz-group-head h2 { font-size: 1.05rem; margin: 0; }
.wz-group-head .muted { font-size: 0.8rem; }

.wz-row {
  display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 14px;
  padding: 14px 0; border-bottom: 1px dashed var(--border);
}
.wz-row:last-child { border-bottom: 0; }
.wz-row--plain { padding-top: 4px; }
.wz-label { display: flex; align-items: flex-start; gap: 6px; padding-top: 8px; font-weight: 600; font-size: 0.9rem; }
.req { color: #d92d20; margin-right: 2px; }
.wz-help {
  position: relative; flex: none; display: inline-grid; place-items: center;
  width: 16px; height: 16px; margin-top: 2px; border-radius: 50%;
  background: var(--border); color: var(--text-muted); font-size: 0.68rem; cursor: help;
}
.wz-tip {
  display: none; position: absolute; z-index: 20; left: 22px; top: -8px; width: 280px;
  padding: 10px 12px; border-radius: 8px; background: var(--ink-dark, #0a0d11); color: #e6ebf0;
  font-weight: 400; font-size: 0.78rem; line-height: 1.55; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}
.wz-help:hover .wz-tip, .wz-help:focus .wz-tip { display: block; }
.wz-ctl { min-width: 0; }
.wz-hint { margin: 6px 0 0; font-size: 0.78rem; color: var(--text-muted); }
.wz-err { display: block; margin: 6px 0 0; font-size: 0.8rem; font-style: normal; color: #b3261e; }

/* ---------- 选项 ---------- */
.wz-opts { display: flex; flex-wrap: wrap; gap: 8px; }
.wz-opts--gap { margin-top: 10px; }
.wz-opt {
  position: relative; min-width: 72px; padding: 8px 14px;
  border: 1px solid var(--border); border-radius: var(--radius-sm, 6px);
  background: var(--surface); color: inherit; font: inherit; font-size: 0.88rem;
  cursor: pointer; transition: border-color 0.15s, background 0.15s;
}
.wz-opt:hover:not(.dis) { border-color: var(--primary-light, var(--primary)); }
.wz-opt.sel { border-color: var(--primary); color: var(--primary); background: var(--accent-soft, rgba(11, 114, 171, 0.06)); }
.wz-opt.sel::after {
  content: '✓'; position: absolute; right: 0; bottom: 0; width: 15px; height: 15px;
  font-size: 10px; line-height: 15px; text-align: center; color: #fff;
  background: var(--primary); border-radius: 5px 0 5px 0;
}
.wz-opt.dis { cursor: not-allowed; color: var(--text-soft); background: var(--surface-muted); border-style: dashed; }
.wz-opt:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.wz-badge {
  position: absolute; top: -9px; right: -6px; padding: 0 7px; font-size: 0.68rem; line-height: 18px;
  border-radius: 9px; color: #fff; background: #e0463b;
}
.wz-opt--cards { width: 140px; padding: 10px 8px 9px; display: flex; flex-direction: column; align-items: center; gap: 3px; }
.wz-pic {
  width: 100%; height: 72px; margin-bottom: 4px; border-radius: 6px; overflow: hidden;
  display: grid; place-items: center; background: var(--surface-muted);
}
.wz-pic img { width: 100%; height: 100%; object-fit: cover; }
.wz-icon { font-size: 1.7rem; }
.wz-opt-name { font-weight: 600; font-size: 0.85rem; line-height: 1.3; }
.wz-opt-desc { font-size: 0.72rem; color: var(--text-muted); line-height: 1.35; }
.wz-opt--swatch { display: inline-flex; align-items: center; gap: 7px; }
.wz-swatch { width: 15px; height: 15px; border-radius: 50%; border: 1px solid rgba(0, 0, 0, 0.15); }

/* ---------- 数字 / 文本 ---------- */
.wz-stepper { display: inline-flex; border: 1px solid var(--border); border-radius: var(--radius-sm, 6px); overflow: hidden; }
.wz-stepper button { width: 36px; border: 0; background: var(--surface-muted); color: inherit; font-size: 1rem; cursor: pointer; }
.wz-stepper input { width: 80px; border: 0; text-align: center; padding: 7px 0; font: inherit; background: var(--surface); color: inherit; }
.wz-input {
  width: 100%; padding: 8px 10px; border: 1px solid var(--border); border-radius: var(--radius-sm, 6px);
  font: inherit; background: var(--surface); color: inherit;
}
.wz-date { width: auto; }

/* ---------- 商品 × 型号 ---------- */
.wz-goods { display: flex; flex-direction: column; gap: 10px; }
.wz-empty { margin: 0; padding: 14px; text-align: center; font-size: 0.85rem; color: var(--text-muted); background: var(--surface-muted); border-radius: 6px; }
.wz-goods-row { border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px; }
.wz-goods-name { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.wz-goods-name img { width: 40px; height: 40px; border-radius: 6px; object-fit: cover; }
.wz-goods-name .muted { margin-left: auto; font-size: 0.82rem; }
.wz-cells { display: flex; flex-wrap: wrap; gap: 8px; }
.wz-cell { display: flex; flex-direction: column; gap: 4px; font-size: 0.75rem; color: var(--text-muted); }
.wz-cell input {
  width: 92px; padding: 6px 8px; border: 1px solid var(--border); border-radius: 6px;
  font: inherit; font-size: 0.9rem; text-align: center; background: var(--surface); color: var(--text);
}

/* ---------- 联系方式（照结账页） ---------- */
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; padding-top: 14px; }
.field .opt { font-style: normal; color: var(--text-soft); font-size: 0.92em; }
.field input.bad { border-color: #d92d20; }
.trap { position: absolute; left: -9999px; width: 1px; height: 1px; overflow: hidden; }

/* ---------- 汇总 ---------- */
.wz-side { position: sticky; top: 90px; display: flex; flex-direction: column; gap: 12px; }
.wz-card {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
  box-shadow: var(--shadow-sm); padding: 18px;
}
.wz-card h3 { font-size: 1rem; margin: 0 0 10px; }
.wz-kv { display: flex; justify-content: space-between; gap: 12px; padding: 3px 0; font-size: 0.84rem; }
.wz-kv span:first-child { color: var(--text-muted); }
.wz-sum { list-style: none; margin: 10px 0 0; padding: 0; max-height: 42vh; overflow: auto; font-size: 0.82rem; }
.wz-sum li { display: flex; justify-content: space-between; gap: 10px; padding: 5px 0; border-bottom: 1px dashed var(--border); }
.wz-sum li span:first-child { color: var(--text-muted); flex: none; max-width: 45%; }
.wz-sum li span:last-child { text-align: right; overflow-wrap: anywhere; }
.wz-sum-empty { margin: 10px 0 0; font-size: 0.84rem; }
.wz-total { display: flex; justify-content: space-between; align-items: baseline; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border); }
.wz-total--price { border-top: 0; padding-top: 0; }
.wz-total span { display: flex; flex-direction: column; font-size: 0.86rem; }
.wz-total small { font-size: 0.72rem; }
.wz-total b { font-size: 1.2rem; color: var(--primary); }
.wz-total--price b { font-size: 1.4rem; white-space: nowrap; }
.wz-approx { margin: 4px 0 0; font-size: 0.76rem; text-align: right; }
.wz-issues { list-style: none; margin: 12px 0 0; padding: 10px 12px; border-radius: 8px; font-size: 0.8rem; }
.wz-issues.bad { background: #fdecea; color: #b3261e; }
.wz-issues.good { background: #e8f5ec; color: #15803d; }
.wz-issues li + li { margin-top: 4px; }
.wz-side .btn-ghost { margin-top: 14px; }
.wz-submit { margin-top: 10px; }
.wz-agree { display: flex; gap: 8px; align-items: flex-start; margin-top: 12px; font-size: 0.76rem; line-height: 1.5; color: var(--text-muted); }
.wz-agree input { margin-top: 3px; }
.wz-share { display: flex; align-items: center; gap: 12px; }
.wz-share div { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.wz-share small { font-size: 0.74rem; }
.wz-share .btn { flex: none; margin-left: auto; padding: 8px 12px; }
.spinner {
  display: inline-block; width: 14px; height: 14px; margin-right: 6px; vertical-align: -2px;
  border: 2px solid rgba(255, 255, 255, 0.5); border-top-color: #fff; border-radius: 50%;
  animation: wzspin 0.8s linear infinite;
}
@keyframes wzspin { to { transform: rotate(360deg); } }

.wz-toast {
  position: fixed; left: 50%; bottom: 28px; transform: translateX(-50%); z-index: 60;
  padding: 10px 18px; border-radius: 8px; background: var(--ink-dark, #0a0d11); color: #fff;
  font-size: 0.84rem; opacity: 0; pointer-events: none; transition: opacity 0.2s;
}
.wz-toast.show { opacity: 1; }

/* ---------- 窄屏：步骤条横过来，汇总落到下面 ---------- */
@media (max-width: 1100px) {
  .wz-layout { grid-template-columns: minmax(0, 1fr); }
  .wz-steps {
    top: 0; z-index: 10; flex-direction: row; overflow-x: auto; padding: 6px;
    border-radius: 0 0 var(--radius) var(--radius);
  }
  .wz-step { flex: none; flex-direction: row; padding: 6px 10px; }
  .wz-step::after { display: none; }
  .wz-side { position: static; }
}
@media (max-width: 640px) {
  .wz-group { padding: 0 14px 10px; }
  .wz-group-head { margin: 0 -14px 6px; padding: 12px 14px; }
  .wz-row { grid-template-columns: minmax(0, 1fr); gap: 6px; }
  .wz-label { padding-top: 0; }
  .wz-opt--cards { width: calc(50% - 4px); }
  .grid-2 { grid-template-columns: 1fr; }
  .wz-tip { left: auto; right: -8px; top: 22px; width: min(280px, 80vw); }
}
</style>
