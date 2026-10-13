<!--
  买家登录。

  ⚠ 这一页**不是必经之路**：不登录也能浏览、加清单、下单、发询盘
  （cart/index.vue 第 1 条：挡一道登录等于挡死单）。登录的用处是
  「换台电脑也能看到自己的订单」。

  后端 POST /openapi/site/account/login，成功后种一个 HttpOnly 的
  BUYER_SESSION Cookie（30 天滑动过期）。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <AuthCard :title="t.acctSignIn" :lead="t.acctSignInLead" :error="error">
      <form @submit.prevent="submit">
        <label class="field">
          <span>{{ t.acctEmail }}</span>
          <input v-model.trim="email" type="email" autocomplete="email"
            required :disabled="busy">
        </label>

        <label class="field">
          <span>{{ t.acctPassword }}</span>
          <input v-model="password" type="password" autocomplete="current-password"
            required :disabled="busy">
        </label>

        <button type="submit" class="btn btn-block" :disabled="busy">
          {{ busy ? t.acctWorking : t.acctSignInCta }}
        </button>
      </form>

      <template #foot>
        <NuxtLink :to="localeHref('/forgot-password')">{{ t.acctForgot }}</NuxtLink>
        <br>
        {{ t.acctNoAccount }}
        <NuxtLink :to="localeHref('/register')">{{ t.acctRegisterCta }}</NuxtLink>
      </template>
    </AuthCard>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
const site = await useSiteInfo()
/**
 * 这一页在**主域**上（登录页放主域是用户 2026-09-08 明确定的，理由见
 * shared/utils/buyerPaths.ts 的头注释），而登录后要去的地方多半在
 * buyer. 上（/account、/orders）——两个 host 的分工见那个文件。
 *
 * - localeHref：拼语言前缀 + 定 host，给页脚那两个链接用
 * - localePath：只拼语言前缀、**不定 host**。登录后那一跳要的是站内路径，
 *   定 host 的活儿交给 afterLogin 的跨域交接，见 {@link go}
 */
const localeHref = useLocaleHref()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)
const route = useRoute()

const { login, buyer, afterLogin } = useBuyer()

const email = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

/**
 * 已经登录的人别停在这一页。
 *
 * ⚠ 只在挂载后判：会话是 HttpOnly Cookie，服务端渲染这一页时也拿不到
 * （买家相关的取数一律走浏览器侧，见 useBuyer 的头注释）。
 */
onMounted(async () => {
  const { refresh } = useBuyer()
  await refresh()
  if (buyer.value) await go()
})

/**
 * `?redirect=` 只接受**站内路径**，认不出来的一律返回 null（调用方回账户页）。
 *
 * 挡的是开放重定向：一个看起来是我们自己域名的链接，点下去人被送到别人站上，
 * 而且是在「刚输完密码」这个最愿意相信页面的时刻。三条都必须挡：
 *
 *   1. **不以 / 开头** —— `https://evil.com` 这种直接就是外站地址；
 *   2. **以 // 开头** —— 协议相对地址，`//evil.com` 在浏览器里就是外站；
 *   3. **含反斜杠或空白** —— 这一条是原来漏掉的：浏览器会把 URL 里的 `\`
 *      规范化成 `/`，也会**丢掉** tab / 换行，于是 `/\evil.com` 和
 *      `/{tab}/evil.com` 最终都等于 `//evil.com`，但都能过前两条检查。
 *
 * 第 3 条以前不致命是因为跳转走的是 `router.replace`——vue-router 只认当前 origin，
 * 把这种串当站内路径处理，顶多跳到一个 404。改成 {@link go} 里那个
 * `navigateTo(..., { external: true })` 之后它就变成真的会出站了，所以必须补上。
 */
function safeRedirect(raw: string): string | null {
  return /^\/(?![/\\])[^\\\s]*$/.test(raw) ? raw : null
}

/**
 * 登录后去哪。**返回站内路径，不定 host。**
 *
 * ⚠ 别在这里把地址拼成绝对的。定 host 的活儿在 {@link go} 里由 afterLogin 做：
 *   它把浏览器导到买家域的 handoff 接口，目的地是那个接口的 `to` 参数，
 *   而后端的 safePath 只接受 `/` 开头的站内路径，绝对地址一律兜底回首页——
 *   表现是「登录成功之后莫名其妙落在首页」。
 *
 * `?redirect=` 读出来的那个自带语言前缀，原样返回，不再过一遍 localePath。
 */
function after() {
  const raw = safeRedirect(String(route.query.redirect || ''))
  return raw || localePath('/account')
}

/**
 * 跳到 {@link after} 指的地方，**中间绕一趟买家域的会话交接**。
 *
 * ⚠ 不能直接 `navigateTo(..., { external: true })`。`BUYER_SESSION` 是 host-only 的：
 *   刚才那次登录只在主域上种了 Cookie，buyer. 上一点身份都没有，整页跳过去就是
 *   **刚输完密码却显示未登录**。afterLogin 会先取一张 60 秒的一次性票，
 *   把同一把钥匙交给 buyer.，再 302 到目的地。见 useBuyer.ts 的 afterLogin。
 *
 * ⚠ 这条路 2026-09-10 断过一次：afterLogin 写好了，这里却还留着直接 navigateTo，
 *   整个交接从来没被调用过。改这个函数前先确认 afterLogin 仍然是唯一的出口。
 */
function go() {
  return afterLogin(after())
}

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await login(email.value, password.value)
    await go()
  } catch (e: unknown) {
    // 后端的 msg 就是要给买家看的那句话（「邮箱或密码不正确」「邮箱还没验证……」），
    // 直接显示。⚠ 别在这里自己判断原因再换一句——后端刻意让「邮箱不存在」和
    // 「密码错」回同一句，前台再加工就可能把那个区别泄出来
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

useHead({
  title: `${t.value.acctSignIn} | ${site.companyName}`,
  // 每个访客各自的页面，不给收录
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>
