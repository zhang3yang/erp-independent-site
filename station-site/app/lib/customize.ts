/**
 * 买家 3D 定制方案（2026-09-29）：接口、本机方案记录、编辑器里的小工具。
 * 设计：docs/superpowers/specs/2026-09-29-buyer-3d-customize-design.md。
 *
 * ## 图片只在保存时上传
 *
 * 买家选的 logo 先用 blob 地址在本机预览，点「保存方案」时和 JSON 一起 multipart 提交。
 * JSON 里图片位置只能写 `upload:N`（第 N 个文件）——后端拒收任何网址，包括本站的。
 * 所以「在此基础上修改」时旧方案里的图要先取回成 Blob、再当本次上传发一遍（`toBlob`）。
 *
 * ## 为什么 API 地址要调用方传进来
 *
 * `useRuntimeConfig()` 只能在组件 setup 里调，保存是在点击事件里、await 之后发生的，
 * 那时候已经拿不到 Nuxt 实例。页面在 setup 里取一次 base 传进来。
 */
import type { MarkPower, MarkScreen, Marks, Vec3 } from '@station-shared/model3d/marks.js'
import { corsUrl } from '@station-shared/model3d/marks.js'

/** 和后端 CustomPlanService 同一套上限 */
export const CUSTOM_LIMITS = {
  maxLogos: 5,
  maxFileBytes: 2 * 1024 * 1024,
  maxEdge: 4096,
  maxNote: 500
}

export const PLAN_ID_RE = /^[A-Za-z0-9]{16}$/

/** GET /customize/{code} */
export interface CustomizeEditor {
  goodsCode: string
  title: string
  image: string | null
  model3d: string
  screen: MarkScreen | null
  power: MarkPower | null
  /** 模型状态（2026-10-08）：第一个是主模型，其余各一个 glb；不足 2 个为 null。交给 parseStates */
  states?: unknown
  /** 配了独立模型的规格（2026-10-09）。own = 单独标注；screen/power 只在单独标注时有 */
  combos?: CustomizeCombo[]
}

export interface CustomizeCombo {
  code: string
  title: string
  image: string | null
  model3d: string
  /** 每个独立规格自己的三态模型列表 */
  model3dStates?: unknown
  own: boolean
  screen: MarkScreen | null
  power: MarkPower | null
}

/** GET /customize/plan/{id}（公开视图，没有邮箱和备注） */
export interface CustomPlanView {
  planId: string
  goodsCode: string
  title: string | null
  /** 商品现在还能不能定制（没下架、还挂着模型）——决定显不显示「在此基础上修改」 */
  available: boolean
  currentModel3d: string | null
  model3d: string
  marks: Marks | null
  /** 保存方案时的模型状态快照（名字已按语言换好）；交给 parseStates */
  states?: unknown
  /** 定制的规格（2026-10-09）；空 = 主模型。comboTitle 为空 = 规格已删 / 停用 */
  comboCode?: string | null
  comboTitle?: string | null
  /** 规格现在还能不能定制（「在此基础上修改」要不要带上它） */
  comboAvailable?: boolean
  createTime: string
  parentId: string | null
}

/** 编辑器里的一个 logo。src 是 blob 地址（新选的）或线上地址（从旧方案带过来的） */
export interface DraftLogo {
  id: string
  src: string
  file: File | null
  p: Vec3 | null
  n: Vec3 | null
  size: number
  rot: number
  opacity: number
}

export interface DraftBoot {
  src: string | null
  file: File | null
  bg: string
}

type Envelope<T> = { code: number; msg: string; data?: T }

/** 读定制页数据。商品不能定制时返回 null（页面显示「暂不支持定制」），其余错误抛 */
export async function fetchCustomizeEditor(base: string, code: string, lang?: string) {
  const res = await $fetch<Envelope<CustomizeEditor>>(
    `${base}/openapi/site/customize/${encodeURIComponent(code)}`,
    { query: { lang: lang || undefined }, ignoreResponseError: true })
  if (res?.code === 200 && res.data) return res.data
  if (res?.code === 404) return null
  throw createError({ statusCode: 502, statusMessage: 'Upstream error', message: res?.msg || '定制数据获取失败' })
}

/** 读方案。不存在返回 null */
export async function fetchCustomPlan(base: string, id: string, lang?: string) {
  if (!PLAN_ID_RE.test(id)) return null
  const res = await $fetch<Envelope<CustomPlanView>>(
    `${base}/openapi/site/customize/plan/${encodeURIComponent(id)}`,
    { query: { lang: lang || undefined }, ignoreResponseError: true })
  if (res?.code === 200 && res.data) return res.data
  if (res?.code === 404) return null
  throw createError({ statusCode: 502, statusMessage: 'Upstream error', message: res?.msg || '方案获取失败' })
}

/** 保存失败时带着后端原话（已按语言翻好）抛出去；413 = 反代挡掉了太大的请求 */
export class SavePlanError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

/**
 * 提交方案。`data` 里的图片位置已经是 `upload:N`，`files[N]` 是对应文件。
 * ⚠ 不自动重试：后端按 IP 限流，替买家多打几次只会把他自己的额度用光。
 */
export async function savePlan(base: string, data: Record<string, unknown>, files: Blob[],
  headers: Record<string, string>, fallbackMsg: string) {
  const form = new FormData()
  form.append('data', JSON.stringify(data))
  files.forEach((f, i) => {
    const ext = f.type === 'image/jpeg' ? 'jpg' : 'png'
    form.append('files', f, `logo-${i}.${ext}`)
  })
  let res: Envelope<{ planId: string }>
  try {
    res = await $fetch<Envelope<{ planId: string }>>(`${base}/openapi/site/customize/plan`, {
      method: 'POST',
      body: form,
      headers
    })
  } catch (e: unknown) {
    const status = (e as { statusCode?: number; response?: { status?: number } })?.statusCode
      ?? (e as { response?: { status?: number } })?.response?.status ?? 0
    throw new SavePlanError(fallbackMsg, status)
  }
  if (res?.code !== 200 || !res.data?.planId) {
    throw new SavePlanError(res?.msg || fallbackMsg, res?.code || 0)
  }
  return res.data.planId
}

/** 线上图片取回成 Blob（从旧方案带过来的 logo 要重新上传一次） */
export async function toBlob(src: string): Promise<Blob> {
  const r = await fetch(corsUrl(src), { mode: 'cors' })
  if (!r.ok) throw new Error('fetch failed: ' + r.status)
  return await r.blob()
}

/** 前端先验一遍：类型、大小、边长。返回 null = 通过，否则是不通过的原因键 */
export async function checkImage(file: File): Promise<'type' | 'size' | 'edge' | null> {
  if (file.type !== 'image/png' && file.type !== 'image/jpeg') return 'type'
  if (file.size > CUSTOM_LIMITS.maxFileBytes) return 'size'
  const url = URL.createObjectURL(file)
  try {
    const img = new Image()
    await new Promise<void>((resolve, reject) => {
      img.onload = () => resolve()
      img.onerror = () => reject(new Error('decode'))
      img.src = url
    })
    if (img.naturalWidth > CUSTOM_LIMITS.maxEdge || img.naturalHeight > CUSTOM_LIMITS.maxEdge) return 'edge'
    return null
  } catch {
    return 'type'
  } finally {
    URL.revokeObjectURL(url)
  }
}

/* ---------------- 这台设备上的方案 ---------------- */

const HISTORY_KEY = 'station-custom-plans'
const HISTORY_MAX = 10

export interface LocalPlan {
  id: string
  code: string
  title: string
  at: number
}

/** 读写都包 try/catch：隐私模式、禁用存储时照样能用，只是不记 */
export function readLocalPlans(): LocalPlan[] {
  try {
    const raw = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    return Array.isArray(raw)
      ? raw.filter(p => p && PLAN_ID_RE.test(p.id) && typeof p.code === 'string').slice(0, HISTORY_MAX)
      : []
  } catch {
    return []
  }
}

export function rememberLocalPlan(plan: LocalPlan) {
  try {
    const list = [plan, ...readLocalPlans().filter(p => p.id !== plan.id)].slice(0, HISTORY_MAX)
    localStorage.setItem(HISTORY_KEY, JSON.stringify(list))
  } catch {
    // 存不了就不记
  }
}
