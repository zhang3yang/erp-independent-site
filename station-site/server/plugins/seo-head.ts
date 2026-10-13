/**
 * 把站点验证码和统计代码注入 `<head>`。
 *
 * ## 为什么是 Nitro 的 render:html 钩子，不是 app.vue 里 useHead
 *
 * `useSiteInfo()` 在取数失败时会 `throw createError({ fatal: true })`。把这件事搬到
 * `app.vue` 就等于让**根组件**背上那个失败路径——而统计代码取不到时，正确的行为是
 * 没有统计，不是整站 503。
 *
 * 这个钩子还有两个顺带的好处：**一个页面文件都不用改**（验证码要在所有页面上，
 * 包括 cart / login 那些不调 `useSiteSeo` 的），以及它天然只在服务端跑一次。
 *
 * ## ⚠ 这里的正则校验是防线，不是重复劳动
 *
 * 后端保存时已经卡过一遍（`SiteSeoService`）。这里再卡一遍，因为库里的值可能是
 * **别人用 SQL 直接改的**（部署时手工补配置是常态），而这里正是把那个值拼进
 * `<script src="...">` 的地方。
 *
 * 「统计只存 ID、不给自由粘贴代码的框」这条安全前提，靠的就是这一次校验。
 * 校验在 `server/utils/seoConfig.ts` 的 `normalizeSeo` 里，不合法一律置 null（不注入）。
 *
 * ## ⚠ 买家域什么都不注入
 *
 * 买家中心（我的订单、账号资料、地址簿）是每个买家各自的东西，把它们的地址发给
 * Google Analytics 不是这个功能该做的事。所以这个域直接跳过。
 *
 * ⚠ 这里原来写的是「买家域整站 `Disallow: /`」——**那句已经不成立了**：2026-09-10 起
 * 买家域只挡 `/account` 和 `/orders` 两条（见 `server/routes/robots.txt.ts` 文件头）。
 * 「不注入统计」这个决定没变，变的只是 robots 的挡法。2026-09-18 订正。
 *
 * ## ⚠ 欧盟合规：这一块还没做完
 *
 * GA4 会写 `_ga` Cookie，在 ePrivacy 下属于「非必要 Cookie」，欧盟访客要**先同意**
 * 才能加载。这个站开的语言里 es / pt / it 都指向欧盟。
 *
 * **同意横幅还没做**（2026-09-08 与用户确认，先只做开关）。所以：填了 GA4/GTM 就是
 * 无条件加载，对欧盟流量而言是有风险的。管理台那个输入框旁边写着这句话，
 * 这里也写一遍——将来做同意横幅时，改的就是这个文件。
 */
import { onBuyerHost } from '../utils/hosts'
import { getSeoConfig } from '../utils/seoConfig'

/**
 * 往 HTML 属性里放之前再转义一次。
 *
 * 值已经过了 `normalizeSeo` 的字符集校验（字母数字加 `- _ = .`），所以这一步
 * 理论上什么都不会改。留着它的理由：**将来有人放宽那个正则时，这里还站着**。
 * 一道防线的价值不在它今天挡住了什么。
 */
function attr(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export default defineNitroPlugin(nitroApp => {
  nitroApp.hooks.hook('render:html', async (html, { event }) => {
    // 见文件头：买家域什么都不注入
    if (onBuyerHost(event)) return

    let seo
    try {
      seo = await getSeoConfig(event)
    } catch (e: any) {
      // getSeoConfig 自己已经兜住了取数失败（回默认值），走到这里说明是别的意外。
      // ⚠ 绝不能让它冒出去：这个钩子在**每一次 SSR 渲染**上，抛异常就是整站白屏，
      //   而它做的事只是往 head 里加几行可选的东西。
      console.warn(`[station-site] 注入站长工具配置时出错，本次跳过：${e?.message || e}`)
      return
    }

    const tags: string[] = []

    // ---- 站点验证 ----
    //
    // ⚠ 只存验证码那个值、在这里拼成标签。存整个 <meta> 标签就等于开了一个
    //   「往 head 里塞任意 HTML」的口子，而那几个框是给运营用的。
    //
    // 三家都放在所有页面上（严格来说只有首页需要）：谁都不吃亏，
    // 而「只在首页注入」要多一个判断当前是不是首页的分支，那个分支还得处理
    // 语言前缀（/ja/ 也是首页）。不值得。
    if (seo.googleVerify) {
      tags.push(`<meta name="google-site-verification" content="${attr(seo.googleVerify)}">`)
    }
    if (seo.bingVerify) {
      tags.push(`<meta name="msvalidate.01" content="${attr(seo.bingVerify)}">`)
    }
    if (seo.baiduVerify) {
      tags.push(`<meta name="baidu-site-verification" content="${attr(seo.baiduVerify)}">`)
    }

    // ---- GA4 ----
    //
    // ⚠ 和 GTM 同时填会重复计数（GTM 容器里通常也挂着一个 GA4 代码）。
    //   这里**不替谁做决定**，两个都注入——静默丢掉一个会让人对着一个
    //   「配了但没数」的界面查很久。管理台上有提示。
    if (seo.ga4Id) {
      const id = attr(seo.ga4Id)
      tags.push(`<script async src="https://www.googletagmanager.com/gtag/js?id=${id}"></script>`)
      tags.push(`<script>window.dataLayer=window.dataLayer||[];`
        + `function gtag(){dataLayer.push(arguments);}`
        + `gtag('js',new Date());gtag('config','${id}');</script>`)
    }

    // ---- GTM ----
    if (seo.gtmId) {
      const id = attr(seo.gtmId)
      tags.push(`<script>(function(w,d,s,l,i){w[l]=w[l]||[];`
        + `w[l].push({'gtm.start':new Date().getTime(),event:'gtm.js'});`
        + `var f=d.getElementsByTagName(s)[0],j=d.createElement(s),`
        + `dl=l!='dataLayer'?'&l='+l:'';j.async=true;`
        + `j.src='https://www.googletagmanager.com/gtm.js?id='+i+dl;`
        + `f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer','${id}');</script>`)
    }

    // ---- 百度统计 ----
    //
    // ⚠ hm.baidu.com 对海外访客慢、甚至连不上，而这个站的访客在境外。
    //   所以它默认留空，管理台上也写着「只对国内流量有意义」。
    //   用 async 的方式插入（百度官方那段代码就是这么写的），
    //   连不上时不会阻塞首屏。
    if (seo.baiduHmId) {
      const id = attr(seo.baiduHmId)
      tags.push(`<script>var _hmt=_hmt||[];(function(){`
        + `var hm=document.createElement("script");`
        + `hm.src="https://hm.baidu.com/hm.js?${id}";`
        + `var s=document.getElementsByTagName("script")[0];`
        + `s.parentNode.insertBefore(hm,s);})();</script>`)
    }

    if (tags.length) html.head.push(...tags)
  })
})
