<!--
  点了验证邮件里的链接之后落到这一页。

  链接形如 https://<主域名>/verify-email?token=xxx，由后端拼
  （BuyerAuthService.verifyLink）——域名取自 u_s_siteinfo.domain，**不是**请求头里的 Host，
  否则任何人都能让我们发一封链接指向他站的邮件（Host header injection）。

  ## ⚠ 验证通过不自动登录

  拿到这个链接的不一定是本人：邮件被转发、邮件客户端预取链接、公司共用邮箱……
  自动登录等于「谁点到链接谁就是他」。所以这一页只把邮箱标成已验证，
  然后让他自己登一次。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <AuthCard :title="t.acctVerifyTitle"
      :error="state === 'fail' ? error : ''"
      :notice="state === 'ok' ? t.acctVerifyOk : ''">
      <p v-if="state === 'working'" class="muted">{{ t.acctVerifying }}</p>

      <NuxtLink v-if="state === 'ok'" :to="localePath('/login')" class="btn btn-block">
        {{ t.acctSignInCta }}
      </NuxtLink>

      <!-- 失败：多半是链接过期（24 小时）或者已经用过了。给一条能自己走通的路 -->
      <NuxtLink v-if="state === 'fail'" :to="localePath('/register')" class="btn btn-block">
        {{ t.acctRegisterCta }}
      </NuxtLink>
    </AuthCard>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
const site = await useSiteInfo()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)
const route = useRoute()

const { verifyEmail } = useBuyer()

const state = ref<'working' | 'ok' | 'fail'>('working')
const error = ref('')

/**
 * 挂载后才发请求，不在 SSR 里做。
 *
 * 两个理由：① 这是一个**写操作**，而 SSR 会在爬虫、链接预览、甚至某些邮件客户端
 * 预取页面时被触发——那会把令牌白白消费掉（它是一次性的），本人真点进来时就失效了；
 * ② 这一页 noindex，不需要 SSR。
 */
onMounted(async () => {
  const token = String(route.query.token || '')
  if (!token) {
    state.value = 'fail'
    error.value = t.value.acctVerifyNoToken
    return
  }
  try {
    await verifyEmail(token)
    state.value = 'ok'
  } catch (e: unknown) {
    state.value = 'fail'
    error.value = (e as Error)?.message || t.value.acctVerifyFail
  }
})

useHead({
  title: `${t.value.acctVerifyTitle} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>
