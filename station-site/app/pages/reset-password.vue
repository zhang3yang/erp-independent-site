<!--
  用重置信里的链接设新密码。

  链接形如 https://<主域名>/reset-password?token=xxx，1 小时有效、一次性
  （比验证链接的 24 小时短得多——它能直接改密码）。

  ## ⚠ 重置成功也不自动登录

  和 verify-email 同一个理由：拿到链接的不一定是本人（转发、预取、共用邮箱）。
  重置成功之后让他用新密码登一次。

  ## 顺带把邮箱标成已验证

  后端在重置成功时把 emailverified 置 1：他能收到这封信，就证明这个邮箱是他的。
  留一个「密码重置好了但因为没验证还是登不进去」的状态毫无意义。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <AuthCard :title="t.acctResetTitle" :lead="done ? '' : t.acctResetLead"
      :error="error" :notice="done ? t.acctResetDone : ''">
      <form v-if="!done" @submit.prevent="submit">
        <label class="field">
          <span>{{ t.acctNewPassword }}</span>
          <input v-model="password" type="password" autocomplete="new-password"
            required :minlength="MIN" :disabled="busy || !token">
          <small class="muted">{{ fill(t.acctPwdMin, { n: MIN }) }}</small>
        </label>

        <button type="submit" class="btn btn-block" :disabled="busy || !token">
          {{ busy ? t.acctWorking : t.acctResetCta }}
        </button>
      </form>

      <NuxtLink v-else :to="localePath('/login')" class="btn btn-block">
        {{ t.acctSignInCta }}
      </NuxtLink>

      <template #foot>
        <NuxtLink :to="localePath('/forgot-password')">{{ t.acctForgotTitle }}</NuxtLink>
      </template>
    </AuthCard>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
const site = await useSiteInfo()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)
const route = useRoute()

const { resetPassword } = useBuyer()

/** 和后端 station.buyer.password-min-length 的默认值对齐 */
const MIN = 8

const password = ref('')
const busy = ref(false)
const error = ref('')
const done = ref(false)

/**
 * 令牌只从地址栏读，不存起来。
 *
 * ⚠ 别把它写进 localStorage 或者 sessionStorage「以防刷新丢了」——
 * 那等于把一个能改密码的凭据留在浏览器里，而它本来只活一小时。
 */
const token = computed(() => String(route.query.token || ''))

// 没带令牌就直接说清楚，别让人填完密码再报错
onMounted(() => {
  if (!token.value) error.value = t.value.acctResetNoToken
})

async function submit() {
  if (busy.value || !token.value) return
  busy.value = true
  error.value = ''
  try {
    await resetPassword(token.value, password.value)
    done.value = true
  } catch (e: unknown) {
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

useHead({
  title: `${t.value.acctResetTitle} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.field small {
  display: block;
  margin-top: 6px;
  font-size: 12px;
}
</style>
