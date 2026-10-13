/**
 * 站长工具配置的服务端取数。`robots.txt`、`sitemap.xml` 和注入 `<head>` 的
 * Nitro 插件三处共用。
 *
 * 抽出来的理由和 `hosts.ts` 一样：同一套「取数 + 兜底 + 校验」写三遍，迟早有一遍漏改。
 * 而这里漏改的后果比域名判据更难看见——统计脚本注入那一处校验漏了，
 * 就等于「只存 ID」那条安全前提整个失效。
 *
 * ## ⚠ 这里的校验不是多余的
 *
 * 后端在保存时已经卡过一遍正则（`SiteSeoService`）。这里还要再卡一遍，因为：
 *
 *   · 库里的值可能是**别人用 SQL 直接改的**（部署时手工补配置是常态）；
 *   · 这里正是把那个值拼进 `<script src="...">` 的地方。
 *
 * 换句话说：保存时那次校验是给人的提示，**这一次才是防线**。
 *
 * ⚠ 正则和 `SiteSeo.java` 里那几个 `Pattern` 是同一套，改一处必须改另一处。
 *
 * ## ⚠ 缓存 60 秒，所以改配置不是「立刻」生效
 *
 * 装修内容那边的承诺是「保存后刷新站点立刻生效」，这一块**做不到**，也不该做到：
 * 注入 `<head>` 的插件在**每一次 SSR 渲染**时都要读配置，不缓存的话每个页面
 * 多一次后端往返（页面本身已经有一次了，等于翻倍）。
 *
 * 60 秒的代价是站长改完验证码后最多等一分钟才能点「验证」。写在这里，
 * 免得下一个人以为是坏了。
 */

import type { H3Event } from 'h3'

/** `/openapi/site/info` 里那个 `seo` 子对象。字段和 `u_s_seo` 一一对应 */
export interface SeoConfig {
  noindexAll: number
  disallowPaths: string | null
  robotsExtra: string | null
  sitemapOn: number
  sitemapMax: number
  homeFreq: string
  goodsFreq: string
  googleVerify: string | null
  bingVerify: string | null
  baiduVerify: string | null
  ga4Id: string | null
  gtmId: string | null
  baiduHmId: string | null
}

/**
 * 兜底配置。
 *
 * ⚠ 每一个值都等于**这个模块存在之前前台的原有行为**：允许收录、生成 sitemap、
 * 首页 daily、商品 weekly、什么都不注入。
 *
 * 所以「后端连不上」、「老库没跑迁移」、「跑了但没配」三种情况表现完全一致，
 * 而且都不是坏的——站照常，只是少一个可选功能。这是刻意的：
 * 一个 SEO 配置项取不到，绝不该让页面 503。
 */
export const SEO_DEFAULTS: SeoConfig = {
  noindexAll: 0,
  disallowPaths: null,
  robotsExtra: null,
  sitemapOn: 1,
  sitemapMax: 5000,
  homeFreq: 'daily',
  goodsFreq: 'weekly',
  googleVerify: null,
  bingVerify: null,
  baiduVerify: null,
  ga4Id: null,
  gtmId: null,
  baiduHmId: null
}

/** 站点信息里这三处以外的字段，服务端这几个用不上，所以只声明用得上的 */
export interface ServerSiteInfo {
  langs?: string[]
  defaultLang?: string
  seo?: Partial<SeoConfig>
}

const FREQ = new Set(['always', 'hourly', 'daily', 'weekly', 'monthly', 'yearly', 'never'])

/** 见文件头：和 SiteSeo.java 里的 Pattern 同一套，改一处必须改另一处 */
const GA4_ID = /^G-[A-Z0-9]{4,20}$/
const GTM_ID = /^GTM-[A-Z0-9]{4,12}$/
const BAIDU_HM_ID = /^[0-9a-f]{32}$/
const VERIFY_TOKEN = /^[A-Za-z0-9\-_=.]{8,100}$/

/**
 * sitemap 商品条数的硬上限。
 *
 * 和 `SiteSeo.SITEMAP_MAX_CEILING` 同一个值。前后端各卡一次的理由同上——
 * 这个值决定的是「往后端翻多少页」，一个被 SQL 改成 999999 的配置
 * 会让 sitemap 请求把商品接口翻上一万六千页。
 */
const SITEMAP_MAX_CEILING = 6000

/**
 * 把后端给的 `seo` 对象过一遍：补默认值、夹越界值、**丢掉格式不合法的 ID**。
 *
 * ⚠ 不合法的值一律置 null（= 不注入），不是「原样放过去让浏览器报错」。
 * 这是这个文件存在的核心理由，见文件头。
 */
export function normalizeSeo(raw: Partial<SeoConfig> | undefined | null): SeoConfig {
  if (!raw) return { ...SEO_DEFAULTS }

  const pick = (v: unknown, re: RegExp): string | null => {
    if (typeof v !== 'string') return null
    const s = v.trim()
    // 空 = 没配，不是「配了个空的」。不打日志：这是最常见的正常状态
    if (!s) return null
    if (re.test(s)) return s
    // ⚠ 这一行日志很重要：一个被丢掉的 ID 在页面上**没有任何症状**——
    //   统计就是不上报而已。运营会以为接好了，然后一个月后发现没有数据。
    console.warn(`[station-site] 站长工具配置里有一个格式不合法的值，已忽略（不注入）：${s.slice(0, 40)}`)
    return null
  }

  const num = (v: unknown, def: number): number => {
    const n = typeof v === 'number' ? v : Number(v)
    return Number.isFinite(n) ? n : def
  }

  const max = Math.min(Math.max(1, Math.floor(num(raw.sitemapMax, 5000))), SITEMAP_MAX_CEILING)

  return {
    noindexAll: num(raw.noindexAll, 0) === 1 ? 1 : 0,
    disallowPaths: typeof raw.disallowPaths === 'string' ? raw.disallowPaths : null,
    robotsExtra: typeof raw.robotsExtra === 'string' ? raw.robotsExtra : null,
    sitemapOn: num(raw.sitemapOn, 1) === 1 ? 1 : 0,
    sitemapMax: max,
    homeFreq: typeof raw.homeFreq === 'string' && FREQ.has(raw.homeFreq) ? raw.homeFreq : 'daily',
    goodsFreq: typeof raw.goodsFreq === 'string' && FREQ.has(raw.goodsFreq) ? raw.goodsFreq : 'weekly',
    googleVerify: pick(raw.googleVerify, VERIFY_TOKEN),
    bingVerify: pick(raw.bingVerify, VERIFY_TOKEN),
    baiduVerify: pick(raw.baiduVerify, VERIFY_TOKEN),
    ga4Id: pick(raw.ga4Id, GA4_ID),
    gtmId: pick(raw.gtmId, GTM_ID),
    baiduHmId: pick(raw.baiduHmId, BAIDU_HM_ID)
  }
}

/**
 * `disallowPaths` 拆成一条条路径。口径和 `SiteSeo#disallowList()` 一致：
 * 去空、去重、不带 `/` 的补上。
 *
 * ⚠ 这里还要再挡一次带空白字符的：robots.txt 是按行解析的，一条带换行的路径
 * 会把一行拆成两行、造出一条没人打算写的指令。后端保存时挡过，但库里的值
 * 可能是 SQL 改的。
 */
export function disallowList(cfg: SeoConfig): string[] {
  if (!cfg.disallowPaths) return []
  const out: string[] = []
  for (const raw of cfg.disallowPaths.split(',')) {
    let p = raw.trim()
    if (!p) continue
    if (/\s/.test(p)) continue
    if (!p.startsWith('/')) p = '/' + p
    if (!out.includes(p)) out.push(p)
  }
  return out
}

/**
 * `robotsExtra` 清成能安全按行输出的样子。
 *
 * 统一换行、去掉控制字符、掐掉长度。同样是「后端挡过一次，这里再挡一次」。
 */
export function robotsExtraLines(cfg: SeoConfig): string[] {
  if (!cfg.robotsExtra) return []
  return cfg.robotsExtra
    .replace(/\r\n?/g, '\n')
    // eslint-disable-next-line no-control-regex
    .replace(/[\x00-\x09\x0B-\x1F\x7F]/g, '')
    .slice(0, 1800)
    .split('\n')
    .map(l => l.trimEnd())
}

// ---------------------------------------------------------------------
// 取数与缓存
// ---------------------------------------------------------------------

interface CacheEntry {
  at: number
  info: ServerSiteInfo
}

/** 见文件头那段「缓存 60 秒」的说明 */
const TTL_MS = 60_000

let cache: CacheEntry | null = null

/**
 * 取一次站点信息（服务端用）。
 *
 * ⚠ **不带 lang 参数**，所以拿到的是站点默认语言那一份。这个函数只给
 * robots / sitemap / head 注入用，它们要的东西（语言列表、seo 配置）都和语言无关。
 *
 * ⚠ **页面里不要用它。** 页面走 `useSiteInfo()`（`app/composables/useSite.ts`）——
 * 那边按语言用 `useAsyncData` 缓存，而且取不到数时会 `throw createError` 让页面
 * 落到错误页。这里刻意相反：取不到就回 null，由调用方按默认值降级。
 *
 * 取数失败**不抛异常**：这几个调用点里最要紧的是注入 `<head>`，
 * 它跑在每一次 SSR 渲染上。让它抛异常等于「后端抖一下、整站白屏」。
 */
export async function fetchServerSiteInfo(event: H3Event): Promise<ServerSiteInfo | null> {
  const now = Date.now()
  if (cache && now - cache.at < TTL_MS) return cache.info

  const config = useRuntimeConfig(event)
  try {
    const res = await $fetch<{ code: number; data: ServerSiteInfo }>(
      `${config.apiBase}/openapi/site/info`)
    if (res?.code !== 200 || !res.data) return null
    cache = { at: now, info: res.data }
    return res.data
  } catch (e: any) {
    // ⚠ 打日志但不抛。站点状态不是「运行中」时后端也回非 200，那是正常状态
    //   （建设中的站），不是故障——所以这里用 warn 不用 error。
    console.warn(`[station-site] 服务端取站点信息失败，站长工具配置按默认值走：${e?.message || e}`)
    return null
  }
}

/**
 * 直接取一份可用的站长工具配置。**永不失败**，取不到就回默认值。
 *
 * robots.txt 和 head 注入用它；sitemap 要顺带用语言列表，所以那边自己调
 * `fetchServerSiteInfo` 再 `normalizeSeo`，省一次请求。
 */
export async function getSeoConfig(event: H3Event): Promise<SeoConfig> {
  const info = await fetchServerSiteInfo(event)
  return normalizeSeo(info?.seo)
}

/** 测试和排查用：让下一次取数重新打后端 */
export function clearSeoConfigCache() {
  cache = null
}
