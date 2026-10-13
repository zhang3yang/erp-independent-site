<!--
  顶栏：Logo、导航、币种/语言切换、购物车、买家入口。

  导航项来自装修内容（商家在管理台配的），一条都没配时后端返回内置默认
  （Home / Products / About Us / Contact，地址是 / 和 /products 这种根相对路径）。

  ⚠ 装修里存的地址**不带语言前缀**，前缀在这里拼。这是刻意的分工：商家不该关心
  URL 结构，改天前缀方案变了也不用去动每个站已经配好的导航。

  ## 顶栏在登录前后是两副面孔

  未登录：Logo + 导航 + 币种/语言。**没有购物车**。
  已登录：Logo + 币种/语言 + 购物车 + 头像，导航整条收起来，回首页点 Logo。

  收导航是商家要的：登录买家是来下单的，不是来看「关于我们」的，顶栏留给
  购物车和账号。代价是他想翻商品得 Logo → 首页 → 产品，多一跳。

  ⚠ 收导航靠的是 `v-if="!buyer"`，**不能**写成 `v-if="buyer === null"`。
    `buyer` 在服务端恒为 undefined（会话是 HttpOnly Cookie，SSR 不参与，
    见 useBuyer.ts 头注释），写成 `=== null` 的话 SSR 输出里一条导航链接都没有，
    爬虫就发现不了内页了。现在这个写法服务端照常渲染完整导航，代价是登录用户
    打开页面时导航会先出现、等 /account/me 回来再消失——那一下抖动没法消掉，
    除非把会话判断搬到服务端。
-->
<template>
  <header class="hd">
    <div class="wrap hd-in">
      <NuxtLink :to="localeHref('/')" class="hd-brand">
        <!-- 过一遍 isUsableImageUrl：填了相对路径时不渲染 img，
             直接退到下面的公司名文字，而不是在页头挂一个裂图标 -->
        <img v-if="isUsableImageUrl(site.logo)" :src="site.logo" :alt="site.companyName" class="hd-logo">
        <span v-else class="hd-name">{{ site.companyName }}</span>
      </NuxtLink>

      <!-- 登录后整条收起来，见文件头「两副面孔」 -->
      <nav v-if="!buyer && !minimal" class="hd-nav">
        <NuxtLink v-for="item in site.nav" :key="item.url + item.title" :to="navTo(item.url)">
          {{ item.title }}
        </NuxtLink>
      </nav>

      <!--
        右侧一组。`margin-left: auto` 在**这个容器**上，不在 .hd-nav 上——
        登录后导航不渲染，靠导航把这组顶到右边的话整组会塌到 Logo 旁边。
      -->
      <div class="hd-right">
        <!--
          币种切换器。只在开了两种以上币种、且后端下发了汇率时渲染。
          放在语言切换器前面：币种是买家更常动的那一个（价格看不懂比语言看不懂更挡事）。

          ⚠ 登录后整个收起来（`&& !buyer`）：已登录买家的价格、购物车、订单都按站点本位币走，
            顶栏再摆一个显示币种只会让人以为能换结算币。暂时先在前端藏掉，换算逻辑没动。
            判断沿用和导航同一条规矩——`!buyer` 不能写成 `buyer === null`（理由见文件头）。
        -->
        <CurrencySwitcher v-if="currency.enabled.value && !buyer && !minimal" :site="site" />

        <!--
          语言切换器。只开了一种语言就不显示，省得占地方。

          ⚠⚠ 面板用 v-show 不是 v-if，而且里面必须是真的 <a href>（NuxtLink 渲染成 a）。
              这两条都是 SEO 硬约束：Google 靠这些链接 + hreflang 发现其它语言版本。
              改成 v-if，SSR 输出里就没有这些链接了；改成 @click 跳转，连 href 都没有。
              两种改法在页面上都看不出区别，代价要几个月后从收录量里才看得到。
        -->
        <div v-if="site.langs.length > 1" class="hd-lang" ref="langBox">
          <button
            type="button"
            class="hd-lang-btn"
            :aria-expanded="langOpen"
            aria-haspopup="true"
            @click="langOpen = !langOpen"
          >
            <span>{{ labelOf(site.lang) }}</span>
            <svg viewBox="0 0 12 12" width="10" height="10" aria-hidden="true">
              <path d="M2 4.2 6 8.2l4-4" fill="none" stroke="currentColor" stroke-width="1.6"
                stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <div v-show="langOpen" class="hd-lang-menu">
            <NuxtLink
              v-for="code in site.langs"
              :key="code"
              :to="switchTo(code)"
              :class="['hd-lang-item', { on: code === site.lang }]"
              :hreflang="code"
              @click="langOpen = false"
            >{{ labelOf(code) }}</NuxtLink>
          </div>
        </div>

        <!--
          引导式下单入口（2026-09-28）。后端 wizard.enabled 才出现，管理台「引导下单」里开关。
          买家中心那个域上不放：/configure 在主域，和购物车同一个理由（草稿存在主域的 localStorage）。
          登录前后都显示——它和导航不一样，是下单入口，登录买家正是来下单的。
        -->
        <NuxtLink v-if="site.wizard?.enabled && !onBuyerHost && !minimal" :to="localeHref('/configure')" class="hd-wizard">
          {{ shopCopy.wzNav }}
        </NuxtLink>

        <!--
          购物车 + 买家入口。单独一层是为了那道间距：不管购物车在不在，
          这一组和语言切换器之间的距离都一样（间距在 .hd-user 上，不在购物车上）。
        -->
        <div v-if="!minimal" class="hd-user">
          <!-- 询价清单入口。角标上的数字来自 localStorage，只能挂载后才知道，
               所以 restore 放在 onMounted 里；在那之前角标不渲染，服务端和客户端
               首帧一致，不会 hydration mismatch -->
          <!-- ⚠ 买家中心那个域上不显示购物车。
               购物车存在**主域**的 localStorage 里，而 localStorage 是按源隔离的，
               在 buyer. 上读出来永远是空——图标挂在那儿只会让人以为东西丢了。
               要买东西回主域，顶栏的站名和导航都链过去。 -->
          <!-- ⚠ 购物车**对游客也显示**。2026-09-10 当天短暂加过一道「匿名只能询盘、
               下单要先登录」的门，同日撤掉：强制注册才能买在欧盟站站不住（超出履约
               必要的个人数据处理），而结账页本来就是按匿名写的、后端
               /openapi/site/order 也从来没要求过会话——那道门只是把入口藏起来，
               挡掉的是转化不是风险。游客下单后的回执见 pages/order-received/[code].vue。
               真要再加回来的话，三处入口必须一起藏：这里、ProductCard.vue、
               products/[code].vue。只藏图标的话，游客点「+」会看到打勾动画
               然后找不到任何入口，东西像是凭空没了。 -->
          <NuxtLink
            v-if="!onBuyerHost"
            :to="localeHref('/cart')"
            class="hd-cart"
            :aria-label="cartLabel"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
              <path
                d="M3.4 4.2h2.3l2.1 10.1a1.6 1.6 0 0 0 1.6 1.3h7.6a1.6 1.6 0 0 0 1.6-1.2l1.5-6.2H6.2M10 19.4a1.1 1.1 0 1 1-2.2 0 1.1 1.1 0 0 1 2.2 0Zm8 0a1.1 1.1 0 1 1-2.2 0 1.1 1.1 0 0 1 2.2 0Z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <span v-if="cartCount" class="hd-cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
          </NuxtLink>

          <!--
            买家账号入口。
            ⚠ 会话是 HttpOnly Cookie，JS 读不到，只能问后端（useBuyer.refresh）——
              所以在问出结果之前**什么都不渲染**（buyer === undefined 那一支）。
              先显示「登录」再跳成买家名字会闪一下，而且服务端渲染时必然是未登录，
              直接渲染会 hydration mismatch。
          -->
          <!--
            登录之后显示头像，不显示名字：公司名动辄二十几个字符，在顶栏里只能截断成
            「Shenzhen Kangwan…」，既认不出是谁又占掉半条导航。头像 + title 两样都解决。
            头像的三种形态（传的图 / 色块 / 首字母）见 app/composables/useAvatar.ts。
          -->
          <NuxtLink v-if="buyer" :to="localeHref('/account')" class="hd-avatar"
            :title="`${buyer.displayName} · ${buyer.email}`" :aria-label="shopCopy.acctMyAccount">
            <UserAvatar :src="buyer.avatar" :initials="buyerInitials" :size="34" />
          </NuxtLink>
          <NuxtLink v-else-if="buyer === null" :to="localeHref('/login')" class="hd-acct">
            {{ shopCopy.acctSignIn }}
          </NuxtLink>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import type { SiteInfo } from '~/composables/useSite'
import { isUsableImageUrl } from '~/lib/shop'

/**
 * minimal：只留 Logo + 语言切换（买家 3D 定制页那种「干净的单独页面」用，2026-09-29）。
 * 导航、币种、引导下单、购物车、账号入口都不渲染。
 */
const props = defineProps<{ site: SiteInfo; minimal?: boolean }>()

const route = useRoute()
/**
 * ⚠ 用 localeHref 而不是 localePath：顶栏在**两个 host 上都会渲染**
 * （主域的公开页和 buyer. 的账号页共用它），所以每个链接都可能是跨 host 的——
 * 从主域点「Sign in」要去 buyer.，从登录页点 Logo 要回主域。
 * 同 host 时它的返回值和 localePath 完全一样，见 composables/useSiteHosts.ts。
 */
const localeHref = useLocaleHref()

const { count: cartCount, restore: restoreCart } = useCart()

/**
 * 当前页在不在买家中心那个域上。用来收掉购物车图标——购物车是主域的
 * localStorage，按源隔离，在 buyer. 上恒为空。
 *
 * ⚠ 不是 ref：域名在整个页面生命周期里不会变，而且 SSR 和客户端算出来是同一个值
 *   （`useRequestURL()` 两边都可用），所以 hydration 不会 mismatch。
 */
const { onBuyerHost } = useSiteHosts()
const shopCopy = useShopCopy(() => props.site.lang)
const cartLabel = computed(() => shopCopy.value.cartTitle!)

/** 买家身份。undefined = 还没问过后端，那时候顶栏这一格不渲染 */
const { buyer, refresh: refreshBuyer } = useBuyer()

/** 顶栏头像的首字母。联系人名优先，没有就退到 displayName（公司名 → 邮箱） */
const buyerInitials = computed(() => initialsOf(buyer.value?.name || buyer.value?.displayName))

onMounted(() => {
  restoreCart()
  // ⚠ 每个页面都有顶栏，所以这里必须是「问过就不再问」的（refresh 默认行为），
  //   否则每次换页都多一个 /account/me 请求
  refreshBuyer()
})

/** 装修里的地址是根相对的（/products、/#about），锚点不加前缀会跳错页 */
function navTo(url: string) {
  if (!url) return localeHref('/')
  if (/^https?:\/\//i.test(url)) return url
  // /#about 这种：前缀要加在 # 前面
  const [path, hash] = url.split('#')
  const prefixed = localeHref(path || '/')
  return hash ? `${prefixed}#${hash}` : prefixed
}

/**
 * 切语言时**留在当前页**，而不是回首页。
 *
 * 一个访客在看某个商品，切到日语却被扔回首页，等于让他重新找一遍——
 * 这是多语站最常见也最恼人的一个毛病。
 */
function switchTo(code: string) {
  // 去掉现有前缀，拿到不带语言的路径。
  //
  // ⚠ 剥前缀要**照站点开的语言列表**认，不能用 /^\/[a-z-]{2,5}(?=\/|$)/ 硬剥：
  //   `/login` 里 `login` 正好 5 个字母、后面就是结尾，那条正则会把整段吃掉，
  //   于是在登录页上切语言会被扔回首页。而前台劈成两个 host 之后这个 bug 更明显——
  //   /login 在 buyer. 上，被剥成 / 之后就是 buyer. 的根路径，nginx 再 301 到主域，
  //   表现成「切了个语言，人跑到另一个域名的首页去了」。
  const first = route.path.split('/')[1] || ''
  const bare = props.site.langs.includes(first)
    ? route.path.slice(first.length + 1) || '/'
    : route.path
  const withQuery = bare + (route.fullPath.includes('?') ? route.fullPath.slice(route.fullPath.indexOf('?')) : '')
  return code === props.site.defaultLang
    ? withQuery
    : `/${code}${withQuery === '/' ? '' : withQuery}`
}

const NAMES: Record<string, string> = {
  en: 'English', zh: '中文', 'zh-cn': '简体中文', 'zh-tw': '繁體中文',
  ja: '日本語', ko: '한국어', ru: 'Русский', es: 'Español', fr: 'Français',
  de: 'Deutsch', pt: 'Português', it: 'Italiano', ar: 'العربية',
  vi: 'Tiếng Việt', th: 'ไทย', id: 'Bahasa', tr: 'Türkçe', nl: 'Nederlands'
}
// 用各语言自己的写法而不是中文名：切换器是给海外访客看的，
// 一个日本人认得「日本語」，不认得「日语」
const labelOf = (code: string) => NAMES[code] || code.toUpperCase()

/* ---------------- 语言下拉的开合 ---------------- */

const langOpen = ref(false)
const langBox = ref<HTMLElement | null>(null)

/*
  点外面关掉。只在打开时挂监听，关掉就摘——顶栏在每一个页面上，
  常驻一个 document 级监听没必要。

  ⚠ 监听器只在客户端挂：SSR 阶段没有 document，onMounted 之外碰它会报错。
*/
function onDocClick(e: MouseEvent) {
  if (langBox.value && !langBox.value.contains(e.target as Node)) langOpen.value = false
}
watch(langOpen, open => {
  if (import.meta.server) return
  if (open) document.addEventListener('click', onDocClick)
  else document.removeEventListener('click', onDocClick)
})
onBeforeUnmount(() => {
  if (!import.meta.server) document.removeEventListener('click', onDocClick)
})
// Esc 关掉。下拉是键盘可达的，就得能用键盘退出来
onMounted(() => {
  window.addEventListener('keydown', e => {
    if (e.key === 'Escape') langOpen.value = false
  })
})

/* ---------------- 币种 ---------------- */

// 这里只用它的 enabled 判断要不要渲染；换算和选中态都在 CurrencySwitcher 里
const currency = useDisplayCurrency(() => props.site)
</script>

<style scoped>
/* 2026-09-21 从白色翻成深色，理由见文件头的改版说明：
   logo 自带一块不透明的黑底，压在白顶栏上像贴纸。
   ⚠ color 设在容器上，底下的链接靠 `a { color: inherit }` 继承，
     所以导航不用逐条改颜色；显式写了颜色的那几个（购物车、账号、语言）在下面各自改。 */
.hd {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(10, 13, 17, .92);
  color: #fff;
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(255, 255, 255, .10);
}
.hd-in {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 62px;
}
.hd-brand { display: flex; align-items: center; }
.hd-logo { max-height: 38px; width: auto; }
.hd-name { font-size: 19px; font-weight: 650; letter-spacing: -.01em; }
.hd-nav {
  display: flex;
  gap: 22px;
  font-size: 15px;
}
.hd-nav a:hover { color: var(--primary-light); }

/* ⚠ 靠右是**这一组**的事，不是导航的事。以前 margin-left:auto 挂在 .hd-nav 上，
   现在导航登录后就不渲染了，还挂在那儿的话整组会塌到 Logo 边上。 */
.hd-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  /* 这一组自己也能换行。窄屏靠 .hd-in 的 flex-wrap 把整组甩到第二行就够用了
     （360px 上验过），这条是兜底：站点开了四五种币种、语言名又长的时候，
     光这一组就可能超过一行宽。换行时靠右对齐，不然第二行会跑到左边去 */
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: 8px;
}
/* 购物车 + 账号这一组和语言切换器之间留一道。间距挂在组上，
   所以未登录（组里只有「登录」）和已登录（购物车 + 头像）看起来一样宽松 */
.hd-wizard {
  padding: 7px 14px;
  border-radius: 999px;
  background: var(--primary);
  color: #fff;
  font-size: 0.84rem;
  font-weight: 600;
  white-space: nowrap;
  text-decoration: none;
  transition: background 0.15s;
}
.hd-wizard:hover { background: var(--primary-dark); color: #fff; }
.hd-user {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: 12px;
}
.hd-cart {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: var(--text-on-dark);
  transition: background 0.15s ease, color 0.15s ease;
}
.hd-cart:hover { background: rgba(255, 255, 255, .10); color: var(--primary-light); }

/* 买家账号入口。买家名字可能很长（公司名），截断而不是把顶栏挤变形 */
.hd-acct {
  max-width: 160px;
  padding: 6px 10px;
  border-radius: var(--radius, 10px);
  font-size: 14px;
  color: var(--text-on-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease;
}
.hd-acct:hover { background: rgba(255, 255, 255, .10); color: var(--primary-light); }
.hd-avatar {
  display: inline-flex;
  border-radius: 50%;
  /* 悬停时描一圈，让它看起来是可点的——头像本身没有 hover 态 */
  box-shadow: 0 0 0 0 rgba(11, 114, 171, 0);
  transition: box-shadow .15s ease;
}
.hd-avatar:hover { box-shadow: 0 0 0 3px rgba(11, 114, 171, .18); }
.hd-cart-badge {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--primary);
  color: #fff;
  font-size: 10.5px;
  font-weight: 700;
  line-height: 17px;
  text-align: center;
}

.hd-lang { position: relative; font-size: 13px; }
.hd-lang-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--text-on-dark);
  font: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.hd-lang-btn:hover { background: rgba(255, 255, 255, .10); color: #fff; }
.hd-lang-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  min-width: 148px;
  max-height: 320px;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, .1);
}
.hd-lang-item {
  display: block;
  padding: 7px 10px;
  border-radius: 6px;
  color: var(--ink-soft);
  white-space: nowrap;
}
.hd-lang-item:hover { background: var(--bg-soft); }
.hd-lang-item.on { background: var(--brand); color: #fff; }

@media (max-width: 760px) {
  .hd-in { height: auto; padding-top: 10px; padding-bottom: 10px; flex-wrap: wrap; gap: 10px 16px; }
  /* 导航换到第二行占满宽度。靠右由 .hd-right 自己的 margin-left:auto 管，
     所以这里不用（也不能）再给 .hd-lang 加 margin-left:auto——那会把
     购物车和头像挤到语言切换器右边贴着，间距就没了 */
  .hd-nav { order: 3; width: 100%; overflow-x: auto; }
  .hd-user { margin-left: 8px; }
}
</style>
