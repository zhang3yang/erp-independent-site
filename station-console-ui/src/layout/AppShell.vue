<template>
  <div class="console" v-loading="loading" :element-loading-text="$t('top.loadingSite')">
    <template v-if="site.siteCode">
      <header class="console-top">
        <div class="brand">
          <span class="brand-mark">{{ $t('top.brand') }}</span>
          <span class="brand-name" :title="site.siteName">{{ site.siteName }}</span>
          <!--
            ⚠ 显示翻译过的状态，但 statusType 仍然按**库里那个中文值**判色：
              状态是存在 u_s_siteinfo.status 里的中文字符串，不是枚举码。
              两边都改成看译文的话，英文界面下三个状态会全是灰的。
          -->
          <el-tag size="small" :type="statusType(site.status)" effect="dark">{{ statusText(site.status) }}</el-tag>
        </div>

        <div class="top-actions">
          <el-link v-if="frontUrl" type="primary" :href="frontUrl" target="_blank" rel="noopener"
            :underline="false" class="front-link">
            <el-icon><Link /></el-icon>{{ $t('top.visitSite') }}
          </el-link>
          <el-divider direction="vertical" />

          <!--
            界面语言。

            ⚠ 这个下拉改的是**你自己**看到的语言，存在你这台浏览器里；
              站点默认语言在「站点概览 → 站点设置 → 管理台语言」那一栏，是站长设的。
              两层的分工见 src/i18n/index.js——少了这一层，站长把站点默认切成法语时，
              所有业务员的管理台也一起变法语。

            摆在顶栏而不是某个设置页里：要用它的人恰恰是看不懂当前这一屏的那个人，
            他没法先读懂「设置」两个字再点进去找。
          -->
          <el-dropdown @command="onLangCommand">
            <span class="who lang-pick" :title="$t('top.language')">
              {{ currentLangLabel }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled class="lang-note">{{ $t('top.languageMine') }}</el-dropdown-item>
                <el-dropdown-item v-for="item in LOCALES" :key="item.code" :command="item.code" divided>
                  {{ item.label }}
                  <el-icon v-if="item.code === locale" class="el-icon--right"><Check /></el-icon>
                </el-dropdown-item>
                <!--
                  「跟随站点默认」。⚠ 只在**自己选过**语言时出现：没选过的人本来就在
                    跟着站点默认走，给他一个「跟随」按钮是噪音。

                    没有这一项的时候，个人选择一旦做出就再也撤不掉了——站点设置里
                    写着 Français、自己这屏是中文，而界面上没有任何地方能把两者合回去。
                    「站点设置里那两块对不上」就是这么来的。
                -->
                <el-dropdown-item v-if="localeChosen" command="__site" divided class="lang-follow">
                  {{ $t('top.languageFollowSite', { lang: siteDefaultLangLabel }) }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-divider direction="vertical" />

          <el-dropdown @command="onUserCommand">
            <span class="who">
              {{ identity.displayName }}
              <el-tag size="small" effect="plain" class="role-tag">{{ roleLabel }}</el-tag>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <!--
                  ⚠ 「修改密码」删了：本站不存密码，也没有登录页——唯一入口是从 ERP
                    点「进入管理」免密进来。一个谁都用不上的密码没有「修改」这件事。
                    见 station-console 的 SessionController。
                -->
                <el-dropdown-item command="logout">{{ $t('top.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!--
            「回众联 ERP」。⚠ 这个链接是**回得去的那条路**，不只是省事：
            本站没有登录页，退出或掉线之后要再进来，只能回 ERP 再点一次「进入管理」。
          -->
          <el-link v-if="identity.erpHomeUrl" type="primary" :href="identity.erpHomeUrl"
            :underline="false" class="front-link">
            <el-icon><Back /></el-icon>{{ $t('top.backErp') }}
          </el-link>
        </div>
      </header>

      <div class="console-body">
        <nav class="console-nav">
          <router-link v-for="item in visibleNav" :key="item.path" class="nav-item"
            :class="{ on: isActive(item.path), soon: item.soon }" :to="'/' + item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ $t(item.title) }}</span>
            <!--
              「留位置」的模块标一个灰点，让人一眼看出它还没做，
              而不是点进去才发现是空的（§11）。
            -->
            <span v-if="item.soon" class="soon-dot" :title="$t('nav.soon')">·</span>
          </router-link>

          <!--
            ⚠ 这里以前有一句「去 ERP 改站点档案 →」，因为那时候站点档案（域名、语言、
              到期日）整个在 ERP 那边，管理台只读。

            现在档案一分为二（§7.2）：
              · 站里的业务参数（语言、币种、前台开关、询盘通知邮箱）在**本站**的
                「站点概览」页里改；
              · 登记信息（域名、服务器 IP、跳转密钥）留在 ERP 的「独立站管理」。

            所以那个链接改成说清楚它去改的是哪一半。
          -->
          <div class="nav-foot" v-if="identity.erpHomeUrl">
            <el-link type="primary" :href="identity.erpHomeUrl" :underline="false" style="font-size:12px">
              {{ $t('nav.erpFoot') }}
            </el-link>
          </div>
        </nav>

        <main class="console-main">
          <!--
            切页面时的进度条。第一次点某个菜单要先下载那一页的分包，
            没有它的话那一两秒里界面纹丝不动，看着像菜单坏了（原因见 router 的 navPending）。
          -->
          <div v-if="navPending" class="nav-progress" />
          <!--
            子页面不再接 siteCode prop。站点由服务端会话决定，前端从头到尾都拿不到、
            也不需要拿一个可以被改的站点编码——这是搬出 ERP 时有意去掉的一层。
          -->
          <router-view />
        </main>
      </div>

      <!--
        在线咨询的悬浮球。挂在这里（AppShell 之内）**不是 App.vue**：
        /login 那个空页面在 AppShell 之外，一个还没登录的人不该看到一个聊天球，
        点了也只会一路 401。
      -->
      <ChatDock />
    </template>
  </div>
</template>

<script setup name="AppShell">
import { ref, computed, provide, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Link, Back, ArrowDown, Check, Odometer, Brush, Goods, Message, FolderOpened,
  List, Avatar, EditPen, Promotion, Compass, User, Guide, MagicStick
} from '@element-plus/icons-vue'
import ChatDock from '@/components/ChatDock.vue'
import { getSite, logout as apiLogout } from '@/api/console'
import { identity, clearIdentity } from '@/store/identity'
import { navPending } from '@/store/nav'
import { toFrontUrl } from '@/utils/siteUrl'
import { t, locale, localeChosen, setLocale, resetLocale, LOCALES } from '@/i18n'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const site = ref({})

/**
 * 左侧导航。
 *
 * ⚠ 这个数组和 router/index.js 里的路由表是**两份**，改一处不会带动另一处。
 *   订单和客户做出来的那次就漏了这里：路由已经指向真页面，菜单还挂着「还没做」的灰点，
 *   结果页面在、但看上去像没做。以后加菜单项两个文件都要改。
 *
 * 十一项**全部都做了**：docs/独立站与ERP的边界.md §11 当初列的五个「留位置」
 * 模块，到 2026-09-09 全部落地，所以这个数组里再没有 soon 灰点。
 *
 * ⚠ 别再往这里加 soon: true 的占位项。当初那么摆是因为「这几个模块是产品的形状，
 *   摆着能让人知道将来会有什么」；现在没有待做的模块了，再摆一个空位只会让人
 *   点进去发现是空的。真有新模块时，连同 Placeholder.vue 一起用回来
 *   （那个组件特意留着没删，它自己那一页写着这套做法的理由）。
 */
/*
 * ⚠ title 现在是**文案键**不是文字，模板里 $t(item.title) 才成字。
 *   这个数组是模块级常量：写死文字的话，切语言时整屏都变了，左边菜单还是中文。
 */
const navItems = [
  { path: 'overview', title: 'nav.overview', icon: Odometer },
  { path: 'decorate', title: 'nav.decorate', icon: Brush },
  { path: 'goods', title: 'nav.goods', icon: Goods },
  // 买家 3D 定制方案（2026-09-29）：挨着商品，方案都是某个商品的
  { path: 'custom-plans', title: 'nav.customPlans', icon: MagicStick },
  // 引导式下单（2026-09-28）：前台 /configure 那一页的参数在这里配
  { path: 'wizard', title: 'nav.wizard', icon: Guide },
  { path: 'inquiry', title: 'nav.inquiry', icon: Message },
  { path: 'files', title: 'nav.files', icon: FolderOpened },
  // 用户管理 2026-09-24 从法国站拿掉了：那一页全是 ERP 登录名、「ERP 免密」来源，不给海外客户看，
  // 那边唯一入口是 ERP「独立站管理」的「站点用户」按钮，落到 /site-users（不走这个壳），见 router。
  // 统领这种站方自己人在用的站要留着（2026-09-27）：后端 console.users-menu=true 时才摆出来
  { path: 'users', title: 'nav.users', icon: User, usersMenu: true },
  // 订单和客户 2026-09 做出来了，灰点去掉——§11 把它们列进「一期不做」的那条已经不成立
  { path: 'orders', title: 'nav.orders', icon: List },
  { path: 'customers', title: 'nav.customers', icon: Avatar },
  // 站长工具 2026-09-08 做出来了（SEO 体检 / sitemap+robots / 验证码+统计代码），
  // 灰点同样去掉。⚠ 图标别再和「站点概览」共用 Odometer——两个菜单项同一个图标，
  // 在只看图标的窄屏上分不出来
  { path: 'seo', title: 'nav.seo', icon: Compass },
  // 博客 2026-09-09 做出来了，灰点也去掉
  { path: 'blog', title: 'nav.blog', icon: EditPen },
  // 营销 2026-09-09 做出来了，§11 那五个「留位置」的到此全部做完，灰点全没了。
  // ⚠ 它做出来的是**询盘跟进看板**，不是优惠券/群发邮件——理由见 router/index.js
  { path: 'marketing', title: 'nav.marketing', icon: Promotion }
]

/** 「用户管理」那一项要本部署开了开关、且这个会话能管用户，两样都有才摆 */
const visibleNav = computed(() =>
  navItems.filter(item => !item.usersMenu || (identity.usersMenu && identity.userAdmin)))

const roleLabel = computed(() =>
  ({ admin: t('role.admin'), operator: t('role.operator') }[identity.roleCode] || identity.roleCode || ''))

/** 顶栏那个下拉上显示的是**当前语言自己的名字**（中文 / English / Français） */
const currentLangLabel = computed(() =>
  (LOCALES.find(item => item.code === locale.value) || LOCALES[0]).label)

/** 站点默认语言的名字。/api/me 下发的 consoleLang，空着按 zh */
const siteDefaultLangLabel = computed(() =>
  (LOCALES.find(item => item.code === (identity.consoleLang || 'zh')) || LOCALES[0]).label)

/**
 * 下拉里的两类命令：挑一种语言，或者「跟随站点默认」。
 *
 * ⚠ 跟随走的是 resetLocale 不是 setLocale：后者会把这一次也记成「他自己的选择」，
 *   那样下次站长改了站点默认，这个人还是跟不上——等于按了「跟随」却没跟上。
 */
function onLangCommand(command) {
  if (command === '__site') resetLocale(identity.consoleLang)
  else setLocale(command)
}

/**
 * 载入站点档案。
 *
 * 顶栏要显示状态和前台地址，装修页要读 contentJson，概览页要改语言和币种——
 * 这些都不在 /api/me 里（那份只装身份），所以进来先取一次完整档案。
 *
 * 这里**不带任何站点参数**：一个部署一个站，站点由后端从本库读。
 * ERP 内置管理台那版要在这一步做「siteCode 是 URL 上来的，得校验一遍」的闸，
 * 搬出来之后那道闸消失了——不是省掉了，是它防的那个东西不存在了。
 */
/*
 * ⚠ 要 return 那个 Promise。装修页「放弃草稿」之后要等这一趟拉回新档案
 * （draftjson 已被后端清空）才能重填表单，不等的话它会把刚放弃的草稿又载一遍，
 * 看起来像「放弃没生效」。不用返回值的调用点不受影响。
 */
function load() {
  loading.value = true
  return getSite()
    .then(r => { site.value = r.data || {} })
    .finally(() => { loading.value = false })
}

/** 子页面拿站点信息用 inject，不用一层层传 prop */
provide('stationSite', site)
provide('reloadStationSite', load)

onMounted(load)

// 站点只有「建设中 / 运行中 / 已停用」三态（这是**站点侧**那个 status，
// 管的是前台开不开；ERP 那边还有一个同名的，管的是发不发跳转令牌——
// 两个决定，不是一个值的副本，见 §7.2）
const statusType = status => ({ 运行中: 'success', 建设中: 'warning', 已停用: 'info' }[status] || 'info')

/*
  状态的译文。⚠ 库里存的是中文字符串本身（'运行中'），不是一个枚举码——
  所以这里按中文值查键，查不到就原样显示（存量库里出现过别的值）。
  ⚠ 别反过来把库里那三个值改成英文码：ERP 那边、SQL 脚本、后端 isPublicOpen()
    读的都是这三个中文串。
*/
const STATUS_KEY = {
  建设中: 'site.status.building',
  运行中: 'site.status.running',
  已停用: 'site.status.disabled'
}
const statusText = status => (STATUS_KEY[status] ? t(STATUS_KEY[status]) : (status || ''))

/** 前台地址：域名带不带协议头都能拼对（见 utils/siteUrl），没填就不显示 */
const frontUrl = computed(() => toFrontUrl(site.value.domain))

/** 空路径（刚进来还没 redirect 完）算在概览上，免得导航一个都不亮 */
const isActive = path => route.path === '/' + path || (path === 'overview' && route.path === '/')

function onUserCommand(command) {
  if (command === 'logout') doLogout()
}

/**
 * 退出。
 *
 * 会话由后端的 /api/auth/logout 销毁，前端清掉身份、跳到 /login——
 * 那是一个**空页面**（Entry.vue），上面只有一句「已退出登录」。
 * 要再进来只能回 ERP 再点一次「进入管理」，所以退出这个动作比有登录页时重一些。
 *
 * ⚠ 不主动跳去 identity.erpHomeUrl：把人从这个域名甩到另一个域名上，
 *   会让「我刚才是不是退出成功了」变得说不清。留在本站说一句话，
 *   要回 ERP 由他自己点顶栏那个链接（退出前它一直在那儿）。
 */
function doLogout() {
  apiLogout()
    .catch(() => {})
    .finally(() => {
      clearIdentity()
      // replace 不是 push：退出之后按浏览器后退不该回到管理台里面去
      router.replace({ path: '/login', query: { reason: 'logged-out' } })
    })
}
</script>

<style scoped>
/*
  管理台占满整个视口：它是从 ERP 里跳进来的一个独立界面，路由上不挂 Layout，
  所以这里要自己撑起整屏，也自己管滚动。
*/
.console {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page);
  z-index: 10;
}

.console-top {
  flex: 0 0 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  background: #1f2d3d;
  color: #fff;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.brand-mark {
  padding: 3px 8px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 12px;
  letter-spacing: 1px;
  flex: none;
}
.brand-name {
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.top-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}
/* 当前操作人 + 角色。共用电脑时一眼看得出登的是谁、是什么角色 */
.who {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  max-width: 14em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 语言下拉。和当前用户共用 .who 的排版，但不用截断 */
.lang-pick {
  max-width: none;
}
/* 「跟随站点默认」比三个语言名弱一档：它是退回默认，不是又一种语言 */
:deep(.lang-follow) {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 下拉里那句「只改你自己」是说明不是选项，压小压灰，也不可点 */
:deep(.lang-note) {
  font-size: 12px;
  line-height: 1.4;
  max-width: 15em;
  white-space: normal;
}

/* 角色标签在深色顶栏上，默认的浅色描边看不清 */
.role-tag {
  --el-tag-bg-color: rgba(255, 255, 255, 0.14);
  --el-tag-border-color: rgba(255, 255, 255, 0.3);
  --el-tag-text-color: rgba(255, 255, 255, 0.92);
}
/* 顶栏是深色的，Element 的 link 默认色在上面看不清 */
.top-actions :deep(.el-button.is-link),
.top-actions .front-link {
  color: rgba(255, 255, 255, 0.82);
}
.top-actions :deep(.el-button.is-link:hover),
.top-actions .front-link:hover {
  color: #fff;
}
.top-actions :deep(.el-divider--vertical) {
  border-color: rgba(255, 255, 255, 0.25);
}
.front-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.console-body {
  flex: 1;
  display: flex;
  min-height: 0;
}
.console-nav {
  flex: 0 0 168px;
  display: flex;
  flex-direction: column;
  padding: 12px 0;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  height: 44px;
  color: var(--el-text-color-regular);
  text-decoration: none;
  border-left: 3px solid transparent;
}
.nav-item:hover {
  background: var(--el-fill-color-light);
}
.nav-item.on {
  color: var(--el-color-primary);
  border-left-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 600;
}
/*
  「留位置」的模块压暗一点，再加一个灰点。点进去那一页会说清楚为什么是空的（§11）。
  不做成禁用：那三个页面是能点的，而且应该能点——点进去看到的说明本身就是有用的信息。
*/
.nav-item.soon {
  color: var(--el-text-color-secondary);
}
.soon-dot {
  margin-left: auto;
  color: var(--el-text-color-placeholder);
  font-weight: 700;
}
.nav-foot {
  margin-top: auto;
  padding: 12px 20px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 内容区自己滚，顶栏和侧边导航固定不动 */
.console-main {
  flex: 1;
  min-width: 0;
  overflow: auto;
  position: relative;
}

/*
  切页面进度条。延迟 150ms 才显出来：已经缓存过的页面几十毫秒就切完了，
  那种情况下闪一下反而像出了什么事。
*/
.nav-progress {
  position: sticky;
  top: 0;
  z-index: 5;
  height: 3px;
  margin-bottom: -3px;
  overflow: hidden;
  opacity: 0;
  animation: nav-progress-in 0s linear 150ms forwards;
}
.nav-progress::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 40%;
  background: var(--el-color-primary);
  animation: nav-progress-run 1s ease-in-out infinite;
}
@keyframes nav-progress-in {
  to { opacity: 1; }
}
@keyframes nav-progress-run {
  from { transform: translateX(-100%); }
  to { transform: translateX(250%); }
}
</style>
