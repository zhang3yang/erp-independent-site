<!--
  /login —— 一个**空页面**。

  这一页以前是登录页（登录名 + 密码 + 「登 录」按钮）。那一版整个撤掉了：
  这个后台只能从 ERP 的「独立站管理」点「进入管理」免密进来，这里输不了密码。
  依据和取舍见 station-console 的 SessionController 类注释、
  docs/独立站与ERP的边界.md §6d。

  **为什么是空的，而不是一句「请从 ERP 进入」**

  空是要求本身。顺着说一句「请从 ERP 进入」，等于告诉任何扫到这个域名的人
  「这后面有一个后台，入口在另一个系统上」——那是一句白送的情报。
  直接打开它的人看到的应该是一个什么都没有的页面。

  **唯一的例外**：带 reason 码来的时候（退出、掉线、从 ERP 跳过来被拒），
  显示「出了什么事」+「该怎么做」两行。
  不然「账号被停用」表现成一个白屏，那个人不知道该找谁，站长也不知道他为什么进不来。

  **「该怎么做」那一行是固定的一句话，不是提示语，是操作路径**：

      回到众联 ERP 的独立站管理页面，重新点【进入管理】

  这一页做不了任何事——没有表单，也没有一个能点的链接（ERP 的地址要有会话才知道，
  而这一页恰恰是给没有会话的人看的）。所以它必须把**人要自己走的那条路**说全：
  回哪个系统、到哪个页面、点哪个按钮。少说一环，来问的人就多一个。

  ⚠ 全部用文本插值渲染，绝不 v-html——地址栏是任何人都能改的。
-->
<template>
  <div class="entry">
    <div v-if="reasonText" class="line">
      <p class="what">
        {{ reasonText }}
        <span v-if="detail" class="detail">{{ detail }}</span>
      </p>
      <!-- 操作路径。⚠ 这一行别改成「请重新登录」——这里没有可以登录的地方 -->
      <p class="how" v-html="$t('entry.how')"></p>
    </div>
  </div>
</template>

<script setup name="Entry">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { identity, loadIdentity } from '@/store/identity'
import { t } from '@/i18n'
import { translateServerText } from '@/i18n/server'

const route = useRoute()
const router = useRouter()

/**
 * 那三个原因码——只说「出了什么事」，「该怎么做」由模板里那句固定的操作路径接着说。
 *
 * 用码不用文字：码是一个封闭集合，地址栏上塞什么都只会落到「什么都不显示」
 * 那一支上。detail 是服务端产生的一句人话（「你在本站的账号已被停用」之类）。
 */
const REASONS = {
  'no-token': 'entry.reason.noToken',
  'rejected': 'entry.reason.rejected',
  'logged-out': 'entry.reason.loggedOut'
}
const reasonText = computed(() => {
  const key = REASONS[String(route.query.reason || '')]
  return key ? t(key) : ''
})
// 后端那句是中文，换成界面语言（见 i18n/server.js）
const detail = computed(() => translateServerText(String(route.query.detail || '')))

/**
 * `?redirect=` 只接受**本站的一条站内路径**，其余一律回概览。
 *
 * 三种要挡掉的值，每一种都真的会出现：
 *
 * 1. `//evil.com` 或 `https://evil.com` —— 地址栏和邮件里的链接谁都能造。
 *    router.replace 拿到这种串会真的把人送出站（协议相对地址），
 *    那就是一个开放重定向：一个 console.<客户域名> 上的链接把人甩到别处去。
 * 2. `/login…` —— **会转圈**。掉线时 request.js 用当前地址拼 redirect，
 *    而当前地址本身可能就是 /login（比如 /login?redirect=%2Flogin%3F…），
 *    replace 到同一个路由不会重新触发这个 onMounted，人就卡在空白页上了。
 * 3. 空串 / 不以 / 开头的相对串。
 */
function safeRedirect(raw) {
  const value = String(raw || '')
  if (!value.startsWith('/')) return '/overview'
  if (value.startsWith('//')) return '/overview'
  if (value === '/login' || value.startsWith('/login?') || value.startsWith('/login/')) return '/overview'
  return value
}

/**
 * 已经有会话的人不该停在这一页。
 *
 * 从 ERP 跳进来之后在这一页刷新、或者手敲了 /login，都会走到这里——
 * 这时候把他送回管理台，而不是给他看一个空白页。
 *
 * ⚠ silent：没有会话是这一页的常态，不该弹「登录状态已失效」的框，
 *   也不该整页跳转（那会跳回自己，转圈）。
 */
onMounted(async () => {
  // main.js 启动时已经拉过一次了，成功的话别再多打一个 /api/me
  if (identity.loaded) {
    router.replace(safeRedirect(route.query.redirect))
    return
  }
  try {
    await loadIdentity(true)
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    // 没有会话。什么都不做——这一页本来就是空的
  }
})
</script>

<style scoped>
/*
  一整屏底色，中间两行字（有 reason 的时候才有）。
  不放 logo、不放站点名、不放「去 ERP」的链接：那些都是情报。
*/
.entry {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color-page);
}
.line {
  max-width: 420px;
  padding: 0 24px;
  text-align: center;
}
/* 出了什么事。次要信息，压暗 */
.what {
  margin: 0;
  font-size: 13px;
  line-height: 22px;
  color: var(--el-text-color-secondary);
}
.detail {
  display: block;
  margin-top: 6px;
  color: var(--el-text-color-placeholder);
}
/*
  该怎么做。**这一行是这一页唯一有用的内容**，所以它比上面那行显眼——
  人在这一页上要做的决定只有一个：去哪儿、点什么。
*/
.how {
  margin: 14px 0 0;
  font-size: 14px;
  line-height: 24px;
  color: var(--el-text-color-primary);
}
</style>
