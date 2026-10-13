<template>
  <div class="login">
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">{{ title }}</h3>
      <el-radio-group v-model="loginMode" class="login-mode">
        <el-radio-button value="password">密码登录</el-radio-button>
        <el-radio-button value="sms">验证码登录</el-radio-button>
      </el-radio-group>
      <template v-if="loginMode === 'password'">
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          type="text"
          size="large"
          auto-complete="off"
          placeholder="用户名/手机号"
        >
          <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          size="large"
          auto-complete="off"
          placeholder="密码"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <el-input
          v-model="loginForm.code"
          size="large"
          auto-complete="off"
          placeholder="验证码"
          style="width: 63%"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img"/>
        </div>
      </el-form-item>
      </template>
      <template v-else>
      <el-form-item prop="phonenumber">
        <el-input
          v-model="loginForm.phonenumber"
          type="tel"
          size="large"
          maxlength="11"
          auto-complete="tel"
          placeholder="手机号"
        >
          <template #prefix><svg-icon icon-class="phone" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="smsCode">
        <el-input
          v-model="loginForm.smsCode"
          size="large"
          maxlength="6"
          auto-complete="one-time-code"
          placeholder="短信验证码"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
          <template #append>
            <el-button :disabled="smsCountdown > 0 || smsSending" @click="handleSendSmsCode">
              {{ smsCountdown > 0 ? `${smsCountdown}s后重试` : '获取验证码' }}
            </el-button>
          </template>
        </el-input>
      </el-form-item>
      </template>
      <el-checkbox v-if="loginMode === 'password'" v-model="loginForm.rememberMe" style="margin:0px 0px 25px 0px;">记住密码</el-checkbox>
      <el-form-item style="width:100%;">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          style="width:100%;"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
        <div style="float: right;" v-if="register">
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div>
      </el-form-item>
    </el-form>
    <!--  底部  -->
    <div class="el-login-footer">
      <span>{{ footerContent }}</span>
    </div>
  </div>
</template>

<script setup>
import { getCodeImg, sendSmsCode } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'
import { mobileRule } from '@/utils/validate'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const router = useRouter()
const { proxy } = getCurrentInstance()
const loginMode = ref('password')
const smsCountdown = ref(0)
const smsSending = ref(false)
let smsTimer

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: "",
  phonenumber: "",
  smsCode: ""
})

const loginRules = {
  username: [{ validator: (_rule, value, callback) => {
    if (loginMode.value === 'sms' || value) callback()
    else callback(new Error('请输入您的账号'))
  }, trigger: "blur" }],
  password: [{ validator: (_rule, value, callback) => {
    if (loginMode.value === 'sms' || value) callback()
    else callback(new Error('请输入您的密码'))
  }, trigger: "blur" }],
  code: [{ validator: (_rule, value, callback) => {
    if (loginMode.value === 'sms' || value) callback()
    else callback(new Error('请输入验证码'))
  }, trigger: "change" }],
  phonenumber: [
    { required: true, trigger: "blur", message: "请输入手机号" },
    mobileRule("请输入正确的11位手机号")
  ],
  smsCode: [
    { required: true, trigger: "blur", message: "请输入短信验证码" },
    { pattern: /^\d{6}$/, trigger: "blur", message: "验证码必须是6位数字" }
  ]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      if (loginMode.value === 'sms') {
        userStore.loginBySms(loginForm.value).then(() => {
          router.replace('/tenant/select')
        }).catch(() => {
          loading.value = false
        })
        return
      }
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        router.replace('/tenant/select')
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function handleSendSmsCode() {
  proxy.$refs.loginRef.validateField('phonenumber', valid => {
    if (valid !== true) return
    smsSending.value = true
    sendSmsCode(loginForm.value.phonenumber.trim()).then(res => {
      proxy.$modal.msgSuccess(res.msg || '验证码已发送')
      smsCountdown.value = Number(res.retryAfterSeconds || 60)
      smsTimer = window.setInterval(() => {
        smsCountdown.value -= 1
        if (smsCountdown.value <= 0) {
          window.clearInterval(smsTimer)
          smsTimer = undefined
        }
      }, 1000)
    }).catch(() => {}).finally(() => {
      smsSending.value = false
    })
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCode()
getCookie()
onBeforeUnmount(() => {
  if (smsTimer) window.clearInterval(smsTimer)
})
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-image: url("../assets/images/login-background.jpg");
  background-size: cover;
}
.title {
  margin: 0px auto 30px auto;
  text-align: center;
  color: #707070;
}
.login-mode {
  display: flex;
  justify-content: center;
  width: 100%;
  margin-bottom: 20px;
}

.login-form {
  border-radius: 6px;
  background: #ffffff;
  width: 400px;
  padding: 25px 25px 5px 25px;
  z-index: 1;
  .el-input {
    height: 40px;
    input {
      height: 40px;
    }
  }
  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0px;
  }
}
.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}
.login-code {
  width: 33%;
  height: 40px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
  }
}
.el-login-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}
.login-code-img {
  height: 40px;
  padding-left: 12px;
}

html.dark .login {
  background-image: linear-gradient(rgba(0, 0, 0, 0.55), rgba(0, 0, 0, 0.55)), url("../assets/images/login-background.jpg");
  .login-form {
    background: var(--el-bg-color-overlay) !important;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
  }
}
</style>
