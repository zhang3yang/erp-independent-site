import request from '@/utils/request'

// 登录方法
export function login(username, password, code, uuid) {
  const data = {
    username,
    password,
    code,
    uuid
  }
  return request({
    url: '/login',
    headers: {
      isToken: false,
      repeatSubmit: false
    },
    method: 'post',
    data: data
  })
}

export function sendSmsCode(phonenumber) {
  return request({
    url: '/login/sms/code',
    headers: { isToken: false, repeatSubmit: false },
    method: 'post',
    data: { phonenumber }
  })
}

export function loginBySms(phonenumber, code) {
  return request({
    url: '/login/sms',
    headers: { isToken: false, repeatSubmit: false },
    method: 'post',
    data: { phonenumber, code }
  })
}

// 注册方法
export function register(data) {
  return request({
    url: '/register',
    headers: {
      isToken: false
    },
    method: 'post',
    data: data
  })
}

// 获取用户详细信息
export function getInfo() {
  return request({
    url: '/getInfo',
    method: 'get'
  })
}

// 解锁屏幕
export function unlockScreen(password) {
  return request({
    url: '/unlockscreen',
    method: 'post',
    data: { password }
  })
}

// 退出方法
export function logout() {
  return request({
    url: '/logout',
    method: 'post'
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}

// 获取当前账号所属商家
export function getTenantShops() {
  return request({
    url: '/tenant/shops',
    method: 'get'
  })
}

// 选择本次登录要进入的商家
// Restore the global context before opening platform-only pages.
export function selectPlatform() {
  return request({
    url: '/tenant/platform',
    method: 'post'
  })
}

export function selectTenantShop(shopCode) {
  return request({
    url: '/tenant/select',
    method: 'post',
    data: { shopCode }
  })
}
