/**
 * **服务端**这一侧的域名判断：当前请求打在哪个 host 上、两个 host 的地址是什么。
 * 中间件（`server/middleware/domain-split.ts`）、`robots.txt`、`sitemap.xml`
 * 和 `server/plugins/seo-head.ts` 共用。
 *
 * 「哪些路径归买家中心」**不在这里**，在 `shared/utils/buyerPaths.ts`——
 * 那一份浏览器侧也要用（站内链接拼相对还是绝对），两边必须是同一套判据，
 * 所以只留一份。浏览器侧的入口是 `app/composables/useSiteHosts.ts`。
 *
 * 设计见 `docs/设计稿/2026-09-08-独立站域名拆分.md`。
 *
 * ## 一句话
 *
 * 商城在主域（首页、商品、购物车、下单、登录注册），
 * 买家中心在 `buyer.` 子域（我的订单、账号资料、地址簿管理）。
 *
 * ## ⚠ 两个变量少一个，整套逻辑就不生效
 *
 * `enabled` 要求 `siteUrl` 和 `buyerUrl` 都拿得到、而且不相等。少一个就当单域，
 * 中间件放行、robots/sitemap 不按域区分——**本机开发就是走这条路**，
 * 不配 `NUXT_PUBLIC_BUYER_URL`，行为和拆分之前完全一样。
 *
 * 这是有意的：让阶段一的代码可以先上线而不改变任何行为，
 * 等生产的环境变量到位再切换。回退也不用回滚代码，改环境变量就行。
 */

import type { H3Event } from 'h3'

/** 站点的两个域名。取不到的那个是空串 */
export interface SiteHosts {
  /** 商城主域的 host（不含端口） */
  site: string
  /** 买家中心的 host（不含端口） */
  buyer: string
  /** 两个都拿到了、而且不相等，域名拆分才生效 */
  enabled: boolean
}

function hostOf(url: unknown): string {
  if (typeof url !== 'string' || url === '') {
    return ''
  }
  try {
    return new URL(url).hostname
  } catch {
    // 配错了就当没配。这里不抛——抛了整个站起不来，而域名拆分只是个路由规则
    return ''
  }
}

export function siteHosts(): SiteHosts {
  const config = useRuntimeConfig()
  const site = hostOf(config.public.siteUrl)
  const buyer = hostOf(config.public.buyerUrl)
  return { site, buyer, enabled: site !== '' && buyer !== '' && site !== buyer }
}

/** 当前请求打的是不是买家中心那个域名。没开启拆分时恒为 false */
export function onBuyerHost(event: H3Event): boolean {
  const hosts = siteHosts()
  return hosts.enabled && getRequestURL(event).hostname === hosts.buyer
}

/*
 * 路径判据（`isBuyerPath`）**曾经也在这个文件里**，2026-09-10 搬到
 * `shared/utils/buyerPaths.ts` 了：浏览器侧要用同一套规则决定站内链接拼相对还是
 * 绝对，而那时候 app/ 那边另有一份自己的拷贝。要用就从那个模块显式 import，
 * 别在这里再放一份。
 */
