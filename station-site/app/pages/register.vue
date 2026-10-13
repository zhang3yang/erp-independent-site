<!--
  买家注册。

  ## 注册做的其实是「认领」

  u_s_buyer 里绝大多数行是**发询盘/下单时自动长出来的**（按邮箱），没有密码。
  用同一个邮箱注册时，后端设的是**已有那一行**的密码——所以一个先问过价、
  后来才注册的人，登录进去立刻能看到自己之前的询盘和订单。

  ## ⚠ 注册成功 ≠ 能登录

  还要点邮件里的验证链接。这一步必须在界面上说清楚，否则买家会直接去登录，
  然后看到「邮箱还没验证」，以为注册没成功又注册一遍。

  ## ⚠ 这一页不告诉你「这个邮箱注册过没有」

  已经有账号的邮箱，后端也回同一句「验证邮件已发出」（只是实际发的是一封
  「你已经有账号了」的提醒信）。区分开的话，这个公开接口就成了一个
  「查这个邮箱是不是这家的客户」的探测器——对外贸站来说客户名单本身就是要保护的。
  所以**不要**在这里加一个「邮箱是否可用」的实时校验。
-->
<template>
  <div>
    <SiteHeader :site="site" />

    <AuthCard :title="t.acctRegister" :lead="t.acctRegisterLead"
      :error="error" :notice="done ? t.acctCheckMail : ''">
      <form v-if="!done" @submit.prevent="submit">
        <label class="field">
          <span>{{ t.acctEmail }}</span>
          <input v-model.trim="form.email" type="email" autocomplete="email"
            required :disabled="busy">
        </label>

        <label class="field">
          <span>{{ t.acctPassword }}</span>
          <input v-model="form.password" type="password" autocomplete="new-password"
            required :minlength="MIN" :disabled="busy">
          <small class="muted">{{ fill(t.acctPwdMin, { n: MIN }) }}</small>
        </label>

        <label class="field">
          <span>{{ t.acctName }}</span>
          <input v-model.trim="form.name" type="text" autocomplete="name" :disabled="busy">
        </label>

        <label class="field">
          <span>{{ t.acctCompany }}</span>
          <input v-model.trim="form.company" type="text" autocomplete="organization"
            :disabled="busy">
        </label>

        <label class="field">
          <span>{{ t.acctPhone }}</span>
          <input v-model.trim="form.phone" type="tel" autocomplete="tel" :disabled="busy">
        </label>

        <button type="submit" class="btn btn-block" :disabled="busy">
          {{ busy ? t.acctWorking : t.acctRegisterCta }}
        </button>
      </form>

      <!-- 注册成功之后：这一页只剩「去查邮箱」这一件事，表单收起来 -->
      <div v-else>
        <p class="muted resend-lead">{{ t.acctResendLead }}</p>
        <button type="button" class="btn btn-ghost btn-block"
          :disabled="busy || resent" @click="resend">
          {{ resent ? t.acctResent : t.acctResendCta }}
        </button>
      </div>

      <template #foot>
        {{ t.acctHaveAccount }}
        <NuxtLink :to="localePath('/login')">{{ t.acctSignInCta }}</NuxtLink>
      </template>
    </AuthCard>

    <SiteFooter :site="site" />
  </div>
</template>

<script setup lang="ts">
// fill 从 useShopCopy.ts 导出，Nuxt 自动导入（其它页面也是这么用的，不写 import）
const site = await useSiteInfo()
const localePath = useLocalePath()
const t = useShopCopy(() => site.lang)

const { register, resendVerify } = useBuyer()

/** 和后端 station.buyer.password-min-length 的默认值对齐 */
const MIN = 8

const form = reactive({ email: '', password: '', name: '', company: '', phone: '' })
const busy = ref(false)
const error = ref('')
const done = ref(false)
const resent = ref(false)

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await register({ ...form })
    done.value = true
  } catch (e: unknown) {
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

async function resend() {
  busy.value = true
  error.value = ''
  try {
    await resendVerify(form.email)
    // 只允许点一次：后端对同一个邮箱有每天 5 封的上限，
    // 让人连点十下的结果是他自己把额度用完
    resent.value = true
  } catch (e: unknown) {
    error.value = (e as Error)?.message || t.value.acctFailed
  } finally {
    busy.value = false
  }
}

useHead({
  title: `${t.value.acctRegister} | ${site.companyName}`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }]
})
</script>

<style scoped>
.field small {
  display: block;
  margin-top: 6px;
  font-size: 12px;
}
.resend-lead {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 21px;
}
</style>
