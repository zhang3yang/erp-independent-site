<!--
  凭「单号 + 下单邮箱」找回一张询价单。**这是站上唯一一个查单入口**（页脚那条链接指向它）。

  ## 为什么需要这一页

  游客下单不要求登录，回执只存在他当时那台浏览器里。跨设备的退路本来有两条：
  确认信里那条 `/order-received/{单号}` 的链接，以及那一页在本地找不到时弹出的
  邮箱表单。但那两条**都要求他手上有那条 URL** —— 只记得单号、信被删了或者进了
  垃圾箱的人，在站上找不到任何入口。这一页把那个口子补上。

  ## 它自己不查库

  提交时调的是 `useOrders().lookup()`，和 `/order-received/[code]` 那一页里的
  「找回这张单」**完全同一个函数**：查成功会把单子记进本地台账，所以这里只要跳过去，
  那一页就能直接渲染，不会再问一次邮箱。

  ⚠ 别在这里另写一份取数逻辑。两处分头实现的话，「取回来要记进本地台账」
    这种约定迟早只在一边成立。

  ## ⚠ 失败只说一句话，而且不要重试

  后端对「单号不存在」和「邮箱不对」回的是同一句 404 —— 单号是 `DD` + 流水、可枚举，
  分开说等于确认了这个单号是真的。所以这里原样显示后端那句话，不要按情况改写。

  同样**不要**自动重试或者轮询：后端按 IP（10 次/小时、30 次/天）和按邮箱
  （20 次/天）各有一层限流，替买家多打几次只会把他自己的额度用光。

  ## 不给收录

  页面上 `robots: noindex`。

  ⚠ 但**不往 `robots.txt` 里加 Disallow**，这一点和购物车/结账页不同：
    那几页是「每个访客各自的内容」，连爬都不必爬；这一页是一个静态表单，而且
    **页脚每一页都链着它**。Disallow 会让爬虫读不到页面、因此也读不到 noindex，
    反而可能只凭外链把这个地址收进去。让它爬得到、看见 noindex，才是想要的结果。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <AuthCard :title="t.orcFindTitle" :lead="t.orcTrackLead" :error="error">
      <form @submit.prevent="submit">
        <label class="field">
          <span>{{ t.orcTrackRef }}</span>
          <!-- 单号不是邮箱也不是密码，别让浏览器拿历史输入去填它 -->
          <input v-model.trim="orderRef" type="text" autocomplete="off" spellcheck="false"
                 placeholder="DD0000000123" required :disabled="busy">
          <small class="muted">{{ t.orcTrackRefHint }}</small>
        </label>

        <label class="field">
          <span>{{ t.coEmail }}</span>
          <input v-model.trim="email" type="email" autocomplete="email"
                 required :disabled="busy">
        </label>

        <button type="submit" class="btn btn-block" :disabled="busy">
          {{ busy ? t.acctWorking : t.orcFindCta }}
        </button>
      </form>

      <template #foot>
        {{ t.acctNoAccount }}
        <NuxtLink :to="localeHref('/register')">{{ t.acctRegisterCta }}</NuxtLink>
      </template>
    </AuthCard>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
// fill 从 useShopCopy.ts 导出，Nuxt 自动导入（其它页面也是这么用的，不写 import）
const site = await useSiteInfo()
const localePath = useLocalePath()
// 注册页在主域上，但这一页在 buyer. 子域上也可能被打开（页脚哪都有），
// 所以站内链接统一走 localeHref，跨 host 时它给绝对地址
const localeHref = useLocaleHref()
const t = useShopCopy(() => site.lang)

const { lookup } = useOrders()

/* ⚠ 变量不能叫 ref：那是 Vue 自动导入的那个 ref()，同作用域里会被遮蔽 */
const orderRef = ref('')
const email = ref('')
const busy = ref(false)
const error = ref('')

/**
 * 查回来就跳到回执页。
 *
 * ⚠ 跳转前 `lookup()` 已经把单子记进本地台账了，所以 `/order-received/{单号}`
 *   打开时能直接渲染，不会再问一次邮箱。这两页共用同一个函数就是为了这个。
 */
async function submit() {
  if (busy.value || !orderRef.value || !email.value) return
  busy.value = true
  error.value = ''
  // 单号统一成大写：库里存的是 DD 开头的大写流水号。当前排序规则不区分大小写，
  // 所以这一步不是必需的，但它让这一页不依赖那个细节
  const code = orderRef.value.toUpperCase()
  try {
    await lookup(code, email.value, site.lang)
    await navigateTo(localePath(`/order-received/${encodeURIComponent(code)}`))
  } catch (e: unknown) {
    // 后端原话。⚠ 它对「没这张单」和「邮箱不对」是同一句，别在这里改写成更「有用」的提示
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

useHead({
  title: `${t.value.orcFindTitle} | ${site.companyName}`,
  // 一个静态表单，收录没有意义。⚠ robots.txt 里**不加** Disallow，理由见文件头
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.field small {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
