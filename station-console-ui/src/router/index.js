import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '@/layout/AppShell.vue'
import { identity, loadIdentity } from '@/store/identity'
import { t, locale } from '@/i18n'
import { watch } from 'vue'
import { navPending } from '@/store/nav'

/**
 * 路由。
 *
 * **地址里没有 siteCode。**一个部署一个站，站点从服务端会话来，地址栏上根本不该出现它。
 * 出现了就会有人去改它，然后我们就得在每一层都防一遍。ERP 内置管理台那版是
 * `/station-console/:siteCode/xxx`，搬过来时这一段是有意去掉的。
 *
 * ## 「留位置」的模块 —— 一个都不剩了
 *
 * docs/独立站与ERP的边界.md §11 明确了「留位置」的确切含义：
 *
 *     菜单项 + 路由 + 一个说明用途的空页面。**不建表、不写 model、不留 TODO 接口。**
 *
 * 当初不顺手建表的理由是：表结构定下来就会有人往上写代码，而那五个模块的需求
 * **当时只有名字**。空表比没表更容易引出错的实现。
 *
 * §11 原本列了五个，**现在五个都做完了**：订单和客户 2026-09 做的，
 * 站长工具、博客、营销 2026-09-08~09 做的。每一个「为什么现在可以做了」
 * 都写在下面对应的路由上。
 *
 * ⚠ 其中**营销管理最值得读**：它做出来的东西和名字听上去的不一样——
 * 不是优惠券和群发邮件，是询盘跟进看板。那不是偷懒，是这个站
 * （B2B 询盘、价格面议、靠人跟报价单）真正丢钱的地方。
 *
 * 所以 Placeholder.vue 现在没有任何路由在用了。**先别删**：下一个「留位置」的
 * 模块出现时它还是那个正确答案，而它自己那一页写着这套做法的理由。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    /*
     * /login —— 一个空页面，**不是登录页**。
     *
     * 这个后台只能从 ERP 的「独立站管理」点「进入管理」免密进来，这里输不了密码
     * （station-console 的 SessionController / docs/独立站与ERP的边界.md §6d）。
     * 路径留着 /login 是因为它已经被三个地方硬编码了：SsoController 的重定向、
     * request.js 掉线后的跳转、以及别人的收藏夹。改路径只会多出一批 404。
     *
     * 它在 AppShell 之外：AppShell 一进来就去取站点档案（那是要会话的）。
     * blank: true 让 afterEach 连标题都不写——空页面连标签页上都不该有站名。
     */
    {
      path: '/login',
      name: 'Entry',
      component: () => import('@/views/Entry.vue'),
      meta: { public: true, blank: true }
    },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/overview' },
        // ⚠ meta.title 是**文案键**，不是标题文字：标签页上的标题也要跟着界面语言变。
        //   和左侧导航用的是同一批键（i18n/locales/zh.js 的 nav.*），别各写一份——
        //   两份迟早对不上，而表现是「菜单叫 Products，标签页叫 商品管理」。
        { path: 'overview', name: 'Overview', component: () => import('@/views/Overview.vue'), meta: { title: 'nav.overview' } },
        { path: 'decorate', name: 'Decorate', component: () => import('@/views/Decorate.vue'), meta: { title: 'nav.decorate' } },
        { path: 'goods', name: 'Goods', component: () => import('@/views/Goods.vue'), meta: { title: 'nav.goods' } },
        // 引导式下单（2026-09-28）。和首页装修一样是「站点长什么样」，权限复用 site:station:*
        { path: 'wizard', name: 'Wizard', component: () => import('@/views/Wizard.vue'), meta: { title: 'nav.wizard' } },
        { path: 'inquiry', name: 'Inquiry', component: () => import('@/views/Inquiry.vue'), meta: { title: 'nav.inquiry' } },
        { path: 'files', name: 'Files', component: () => import('@/views/Files.vue'), meta: { title: 'nav.files' } },
        // 用户管理留在菜单里的站（后端 console.users-menu=true，统领）走这一条，套着壳。
        // 和 /site-users 是同一张表（views/Users.vue），同一道守卫：没有 userAdmin 送回概览
        { path: 'users', name: 'Users', component: () => import('@/views/Users.vue'),
          meta: { title: 'nav.users', userAdmin: true } },

        // ---- 订单和客户：原来是留位置的空页，2026-09 做出来了 ----
        //
        // §11 把这两个和博客/营销/站长工具一起列在「一期不做」里，理由是「需求现在只有名字」。
        // 这两个已经不是了：前台的询价流程把订单模型定完了，u_s_inquiry.buyercode 那一列
        // 也早就为客户档案留好了钩子。下面三个仍然只有名字，仍然是空页。
        { path: 'orders', name: 'Orders', component: () => import('@/views/Orders.vue'),
          meta: { title: 'nav.orders' } },
        { path: 'customers', name: 'Customers', component: () => import('@/views/Customers.vue'),
          meta: { title: 'nav.customers' } },

        /*
         * 站长工具。原来也是留位置的空页，2026-09-08 做出来了。
         *
         * §11 把它和博客/营销一起列进「一期不做」，理由是「需求现在只有名字」。
         * 这一条对它已经不成立：前台的 SEO 规则早就定完了——useSeo.ts 的
         * canonical + hreflang、sitemap.xml.ts 把每个商品每种语言都列出来、
         * robots.txt.ts 按域区分买家中心。缺的只是「让站长能改」和
         * 「验证码 / 统计代码的注入位」，那是两件形状很清楚的事。
         *
         * 做了三块：SEO 体检、sitemap/robots 可配置、验证码 + 统计代码。
         * 刻意**没做**的两块，别当成遗漏：
         *   · 爬虫抓取日志——现有埋点走浏览器 sendBeacon，看不见不执行 JS 的爬虫，
         *     要做得在 SSR 那一层新记一份（新表 + 中间件 + 清理任务）；
         *   · Google Search Console 拉数——服务器在腾讯云，连不上 Google 的 API。
         *     所以「收录状态」这件事只能靠站长自己在境外用浏览器看 GSC，
         *     而这个模块提供的是让 GSC 能验证通过的那个验证码。
         *
         * 设计和实现记在 docs/设计稿/2026-09-08-站长工具.md。
         */
        { path: 'seo', name: 'Seo', component: () => import('@/views/Seo.vue'),
          meta: { title: 'nav.seo' } },

        /*
         * 博客管理。原来也是留位置的空页，2026-09-09 做出来了。
         *
         * §11 的理由（「需求现在只有名字」）对它同样已经不成立：这个站的 SEO 价值
         * 原来全压在商品详情页上，而商品页能覆盖的关键词就是商品名——覆盖不到
         * 「how to choose silicone tubing」这类真正带来询盘的长尾词。要什么很清楚。
         *
         * ⚠ 前台入口**不在默认导航里**：导航来自首页装修的 nav，运营要自己加一条
         *   指向 /blog。刻意不动 defaultNav()——那是「一条导航都没配」时的兜底，
         *   而已经装修过的站根本不读它；改了它只会让新装的站顶栏多一个
         *   指向空列表页的死链。这一条在 Blog.vue 页面上写着。
         *
         * 前台两页：/blog（列表）、/blog/:slug（详情），都进 sitemap。
         */
        { path: 'blog', name: 'Blog', component: () => import('@/views/Blog.vue'),
          meta: { title: 'nav.blog' } },

        /*
         * 营销管理。§11 里最后一个「留位置」的，2026-09-09 做出来了。
         *
         * ⚠ 它做成的不是「优惠券 + 邮件营销 + 落地页」那三件，而是**询盘跟进看板**。
         *   理由：这是个 B2B 询盘站——前台不收款、价格 0 表示面议、成交靠人跟出来的
         *   报价单。优惠券对「面议」没有意义；群发邮件的买家基数还小、合规代价却不小
         *   （欧盟同意横幅还欠着，见 SeoTracking.vue 上那条提示）。而漏掉一条询盘
         *   = 漏掉一单，并且漏掉是静悄悄的——那才是这个站的营销里真正丢钱的地方。
         *
         * 加的东西很少，因为库里本来就有一半：状态、内部备注、客户标签都已存在。
         * 这一轮只加了跟进记录（多条、只增不改）、下次跟进日期、负责人。
         *
         * ⚠ 不用新权限，复用 site:inquiry:*。跟进记录就是询盘数据，
         *   单开一套会造出「能看看板、却点不开背后的询盘」这种没用的中间状态。
         */
        { path: 'marketing', name: 'Marketing', component: () => import('@/views/Marketing.vue'),
          meta: { title: 'nav.marketing' } },
        /**
         * 买家 3D 定制方案（2026-09-29）：前台 /customize 上买家保存的方案，只读。
         * 权限 site:customplan:list（迁移 2026-09-29-custom-plan.sql 给 operator 加上了）。
         */
        { path: 'custom-plans', name: 'CustomPlans', component: () => import('@/views/CustomPlans.vue'),
          meta: { title: 'nav.customPlans' } }
      ]
    },
    // 输错地址回概览，不做 404 页。管理台就这么几个页面，
    // 给一个「页面不存在」的空白页不如直接把人送到能用的地方
    /*
      站点用户（2026-09-24）。不在左边菜单里、不走 AppShell 那个壳：
      用户管理那一页全是 ERP 登录名、「ERP 免密」来源，不给海外客户看。
      唯一入口是 ERP「独立站管理」的「站点用户」按钮——令牌带 scope=users，
      后端 SsoController 建会话时打上 userAdmin，再 302 到这里。
      没有那个标记的会话直接打这个地址会被下面的守卫送回概览（后端接口同样 403）。
    */
    {
      path: '/site-users',
      name: 'SiteUsers',
      component: () => import('@/views/SiteUsers.vue'),
      meta: { title: 'users.standalone.title', userAdmin: true }
    },
    { path: '/:pathMatch(.*)*', redirect: '/overview' }
  ]
})

/**
 * 没有身份就去那个空页面。
 *
 * ⚠ 这道守卫**不是安全边界**，只是体验：真正的门在后端的 AuthInterceptor 上，
 * 每一个 /api/** 都要会话 + 要权限。前端这一层拦不住直接发请求的人，
 * 它拦的是「刷新页面后看到一屏报错」这件事。
 *
 * identity 在 main.js 里已经先拉过一次了，这里只兜刷新和直接输地址两种情况。
 */
router.beforeEach(async (to, from) => {
  // 首次进来（from 还没匹配到任何路由）不算：那时整屏还在 v-loading 里
  if (from.matched.length) navPending.value = true
  if (to.meta?.public) return true
  if (identity.loaded) return guardUserAdmin(to)
  try {
    await loadIdentity()
    return guardUserAdmin(to)
  } catch (e) {
    // 401 时 request.js 那边会弹框并整页跳转，这里 return false 只是别让路由继续走
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

/** 「站点用户」那一页只给从 ERP「站点用户」按钮进来的会话，其余一律回概览 */
function guardUserAdmin(to) {
  if (to.meta?.userAdmin && !identity.userAdmin) return { path: '/overview', replace: true }
  return true
}

function applyTitle(route) {
  // 空页面不写标题：直接打开这个地址的人，连标签页上都不该看到「独立站管理台」
  if (route?.meta?.blank) {
    document.title = ''
    return
  }
  const site = t('page.console')
  document.title = route?.meta?.title ? `${t(route.meta.title)} - ${site}` : site
}

router.afterEach((to, from, failure) => {
  navPending.value = false
  if (failure) return
  applyTitle(to)
  try { sessionStorage.removeItem(CHUNK_RELOAD_KEY) } catch (e) { /* 无痕模式 */ }
  if (!to.meta?.public) schedulePrefetch()
})

/*
  第一次点菜单「没反应」（2026-09-25）。

  除 AppShell 外每一页都是懒加载的分包：第一次点某个菜单，vue-router 要先把那一页的
  JS/CSS 下载下来才切过去，**下载期间界面上什么都不变**——导航高亮不动、内容不动。
  这台机器在法国，从国内过去一个来回 ~170 ms，一页分包加依赖要 0.5～3 秒，
  看着就是「菜单点不动」；等每页都下过一次（分包缓存一年、immutable）就再也不出现，
  所以表现是「刚进来点不动，过一会儿就好了」。

  两处一起治：
  1. navPending（store/nav.js）：导航挂起期间为 true，AppShell 据此在顶部走一条进度条，
     让人知道「点到了，在加载」。
  2. schedulePrefetch：首屏停稳后趁空闲把其余各页的分包全拉下来（合计百来 KB，
     同一条 HTTP/2 连接），之后第一次点任何菜单都走本地缓存，瞬间切换。
*/

let prefetched = false
function schedulePrefetch() {
  if (prefetched) return
  prefetched = true
  const run = () => {
    for (const record of router.getRoutes()) {
      // 空页面和「站点用户」那一页普通会话用不上，不拉
      if (record.meta?.public || record.meta?.userAdmin) continue
      const loader = record.components?.default
      // 懒加载的是 () => import(...)；已经解析过的是组件对象，跳过
      if (typeof loader === 'function') loader().catch(() => {})
    }
  }
  // 等概览页自己那几个接口先走，别和它抢；不支持 requestIdleCallback 的浏览器（Safari）退回定时器
  if (window.requestIdleCallback) window.requestIdleCallback(run, { timeout: 3000 })
  else setTimeout(run, 1500)
}

/*
  分包下载失败。典型场景：页面开着的时候服务器重新部署了，旧分包的文件名已经不存在，
  点菜单一直 404、永远切不过去——又是一个「点了没反应」。
  这时按目标地址整页重开一次，拿到新的 index.html（它是 no-store 的）。
  ⚠ 用 sessionStorage 记下这次的目标，同一个地址只重开一次，防止分包真坏了时无限刷新。
*/
const CHUNK_RELOAD_KEY = 'console-chunk-reload'
router.onError((error, to) => {
  navPending.value = false
  const msg = String(error?.message || '')
  if (!/dynamically imported module|Importing a module script failed|Unable to preload CSS/i.test(msg)) return
  try {
    if (sessionStorage.getItem(CHUNK_RELOAD_KEY) === to.fullPath) return
    sessionStorage.setItem(CHUNK_RELOAD_KEY, to.fullPath)
  } catch (e) { /* 无痕模式下拿不到 sessionStorage，照样重开一次 */ }
  window.location.assign(to.fullPath)
})

/*
  切语言时把标题也重写一遍。

  ⚠ 少了这一句，切换之后整屏都变了、只有浏览器标签页还是上一种语言，
    而且要等下一次跳转才会跟上——那种不一致看着像没切干净。
*/
watch(locale, () => applyTitle(router.currentRoute.value))

export default router
