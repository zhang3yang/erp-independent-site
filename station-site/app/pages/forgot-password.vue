<!--
  忘记密码：填邮箱，收一封重置信。

  ## ⚠ 这一页永远显示同一句成功提示

  邮箱不存在、有档案但没注册过、已经注册过——三种情况后端都回
  「如果这个邮箱有账号，重置邮件已经发出」，只有第三种真的发信。

  这不是偷懒。区分开的话，这个公开接口就是一个「查这个邮箱是不是这家的客户」的
  探测器，而外贸站的客户名单本身就是要保护的东西。
  **不要**为了「体验更好」改成「该邮箱未注册」。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <AuthCard :title="t.acctForgotTitle" :lead="t.acctForgotLead"
      :error="error" :notice="done ? t.acctForgotDone : ''">
      <form v-if="!done" @submit.prevent="submit">
        <label class="field">
          <span>{{ t.acctEmail }}</span>
          <input v-model.trim="email" type="email" autocomplete="email"
            required :disabled="busy">
        </label>

        <button type="submit" class="btn btn-block" :disabled="busy">
          {{ busy ? t.acctWorking : t.acctForgotCta }}
        </button>
      </form>

      <template #foot>
        <NuxtLink :to="localePath('/login')">{{ t.acctBackToSignIn }}</NuxtLink>
      </template>
    </AuthCard>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
const site = await useSiteInfo()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)

const { forgotPassword } = useBuyer()

const email = ref('')
const busy = ref(false)
const error = ref('')
const done = ref(false)

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await forgotPassword(email.value)
    done.value = true
  } catch (e: unknown) {
    // 走到这里只有两种：邮箱格式不对（400）、发信太频繁（429）。
    // 「邮箱没注册」不会走到这里——那一支后端也回成功
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

useHead({
  title: `${t.value.acctForgotTitle} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>
