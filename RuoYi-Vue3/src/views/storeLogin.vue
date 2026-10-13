<!--
  /store-login —— 独立站专用登录页。

  给「一商家一站、在 ERP 里只有独立站管理权限」的外部客户用。只有账号、密码、登录按钮，
  没有验证码、没有「众联 ERP」字样、没有那张背景图。点登录后账号密码换站点跳转令牌，
  表单 POST 到站点 /sso，页面直接变成站点后台——全程不进 ERP 的任何页面。
  设计：docs/superpowers/specs/2026-09-22-store-login-page-design.md

  ⚠ 不走 utils/request.js：它会把中文错误弹出来、401 时弹「登录状态已过期」，
    这一页的人一句中文都不该看到。直接用 axios，错误自己渲染。

  ⚠ 令牌走 POST 表单，不进 URL——utils/consoleJump.js 顶部有理由。
-->
<template>
  <main class="store-login">
    <section class="card" aria-labelledby="store-login-title">
      <div class="lang">
        <button type="button" :class="{ on: lang === 'en' }" @click="lang = 'en'">EN</button>
        <span aria-hidden="true">·</span>
        <button type="button" :class="{ on: lang === 'fr' }" @click="lang = 'fr'">FR</button>
      </div>

      <h1 id="store-login-title">{{ t.title }}</h1>
      <p class="sub">{{ t.subtitle }}</p>

      <form novalidate @submit.prevent="submit">
        <label class="field">
          <span>{{ t.username }}</span>
          <input
            v-model.trim="form.username"
            type="text"
            name="username"
            autocomplete="username"
            autocapitalize="off"
            spellcheck="false"
            :disabled="loading"
            required
          />
        </label>
        <label class="field">
          <span>{{ t.password }}</span>
          <input
            v-model="form.password"
            type="password"
            name="password"
            autocomplete="current-password"
            :disabled="loading"
            required
          />
        </label>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button type="submit" class="submit" :disabled="loading || !form.username || !form.password">
          {{ loading ? t.signingIn : t.signIn }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup name="StoreLogin">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import { submitConsoleJump } from '@/utils/consoleJump'

const MESSAGES = {
  en: {
    title: 'Store Admin',
    subtitle: 'Sign in to manage your online store.',
    username: 'Username',
    password: 'Password',
    signIn: 'Sign in',
    signingIn: 'Signing in…',
    // 后端给的英文原样显示；其他情况（限流的中文提示、网络错）用这句
    failed: 'Sign-in failed. Please try again later.'
  },
  fr: {
    title: 'Administration de la boutique',
    subtitle: 'Connectez-vous pour gérer votre boutique en ligne.',
    username: "Nom d'utilisateur",
    password: 'Mot de passe',
    signIn: 'Se connecter',
    signingIn: 'Connexion…',
    failed: 'Échec de la connexion. Veuillez réessayer plus tard.'
  }
}

/** 默认语言跟浏览器：法语浏览器直接看法语，其余英文。切换只记在本页，不落存储 */
const lang = ref(String(navigator.language || '').toLowerCase().startsWith('fr') ? 'fr' : 'en')
const t = computed(() => MESSAGES[lang.value])

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

// 没登录态时全局的动态标题不会跑，标签页会挂着「众联ERP系统」——这一页自己管标题
onMounted(() => { document.title = t.value.title })
watch(lang, () => { document.title = t.value.title })

/**
 * 后端为这个接口写的提示都是英文（纯 ASCII）；限流之类的全局提示是中文，
 * 这里的人看不懂，统一换成本页的通用提示。
 */
function displayError(msg) {
  const text = String(msg || '')
  return text && /^[\x20-\x7e]+$/.test(text) ? text : t.value.failed
}

async function submit() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    const res = await axios.post(
      import.meta.env.VITE_APP_BASE_API + '/station/login',
      { username: form.username, password: form.password },
      { timeout: 15000, headers: { 'Content-Type': 'application/json' } }
    )
    const data = res.data || {}
    if (data.code !== 200 || !data.action || !data.token) {
      error.value = displayError(data.msg)
      return
    }
    // 页面被 POST 带走，loading 不复位，免得离开前按钮闪一下
    submitConsoleJump({ action: data.action, token: data.token, target: '_self' })
  } catch (e) {
    error.value = displayError(e?.response?.data?.msg)
  } finally {
    if (error.value) loading.value = false
  }
}
</script>

<style scoped>
.store-login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: #f4f6f9;
  color: #1f2933;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
.card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 36px 32px 32px;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
  position: relative;
}
.lang {
  position: absolute;
  top: 14px;
  right: 16px;
  font-size: 12px;
  color: #9aa5b1;
  display: flex;
  gap: 6px;
  align-items: center;
}
.lang button {
  border: 0;
  background: none;
  padding: 2px 4px;
  color: #9aa5b1;
  cursor: pointer;
  font: inherit;
}
.lang button.on {
  color: #1f2933;
  font-weight: 600;
}
h1 {
  margin: 8px 0 4px;
  font-size: 22px;
  font-weight: 600;
}
.sub {
  margin: 0 0 24px;
  font-size: 14px;
  color: #6b7785;
}
.field {
  display: block;
  margin-bottom: 16px;
}
.field span {
  display: block;
  font-size: 13px;
  margin-bottom: 6px;
  color: #3e4c59;
}
.field input {
  width: 100%;
  box-sizing: border-box;
  height: 42px;
  padding: 0 12px;
  border: 1px solid #d5dbe1;
  border-radius: 8px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.15s;
}
.field input:focus {
  border-color: #2f6fed;
  box-shadow: 0 0 0 3px rgba(47, 111, 237, 0.15);
}
.error {
  margin: 0 0 14px;
  font-size: 13px;
  color: #c0392b;
}
.submit {
  width: 100%;
  height: 44px;
  border: 0;
  border-radius: 8px;
  background: #2f6fed;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
.submit:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
