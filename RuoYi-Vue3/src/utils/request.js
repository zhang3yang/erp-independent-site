import axios from 'axios'
import { ElNotification , ElMessageBox, ElMessage, ElLoading } from 'element-plus'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { tansParams, blobValidate } from '@/utils/ruoyi'
import cache from '@/plugins/cache'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'
import useBusinessShopStore from '@/store/modules/businessShop'
import { isBusinessModulePath } from '@/utils/businessShop'

let downloadLoadingInstance
// 是否显示重新登录
export let isRelogin = { show: false }
// 库存创建请求的幂等键缓存：成功后清除，网络失败时保留，便于安全重试。
const inventoryIdempotencyKeys = new Map()

function isInventoryCreateRequest(config) {
  return config.method === 'post' &&
    (config.url === '/inventory/document' ||
     config.url === '/inventory/purchase-inbound/direct' ||
     config.url === '/inventory/purchase-inbound/create' ||
     config.url === '/inventory/outbound/create' ||
     config.url === '/inventory/transfer/create')
}

/*
 * 「请求进行中」去重。
 *
 * 下面请求拦截器里原有的 sessionObj 那套只覆盖 1000ms 固定窗口，可单据保存经常
 * 超过 1 秒——请求还在飞，窗口一过第二次点击就被放行了。页面层的 submitForm 又
 * 普遍没有提交中标志，双击就真发两次。这里改按「同一请求没返回就不许再发」拦，
 * 覆盖整个请求周期，和 sessionObj 的时间窗互补（后者继续管请求完成后的连点）。
 */
const pendingSubmits = new Map()
// 兜底上限：万一哪条响应路径漏了清理，也不能让这个表单永久提交不了
const PENDING_SUBMIT_MAX_AGE = 60 * 1000

function submitFingerprintOf(config) {
  const data = typeof config.data === 'object' ? JSON.stringify(config.data) : config.data
  return `${config.url || ''}|${data || ''}`
}

function releasePendingSubmit(config) {
  if (config && config.__submitFingerprint) {
    pendingSubmits.delete(config.__submitFingerprint)
  }
}

function inventoryRequestFingerprint(config) {
  const raw = `${config.url}|${JSON.stringify(config.params || {})}|${typeof config.data === 'object' ? JSON.stringify(config.data) : String(config.data || '')}`
  let hash = 2166136261
  for (let i = 0; i < raw.length; i++) {
    hash ^= raw.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return `${config.url}|${(hash >>> 0).toString(36)}`
}

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: import.meta.env.VITE_APP_BASE_API,
  // 超时
  timeout: 10000
})

// request拦截器
service.interceptors.request.use(config => {
  if (isInventoryCreateRequest(config) && !config.headers?.['Idempotency-Key']) {
    const fingerprint = inventoryRequestFingerprint(config)
    let key = inventoryIdempotencyKeys.get(fingerprint)
    if (!key) {
      key = `web-${(Math.random().toString(36).slice(2) + Date.now().toString(36)).slice(0, 90)}`
      inventoryIdempotencyKeys.set(fingerprint, key)
    }
    config.headers['Idempotency-Key'] = key
    config.__inventoryIdempotencyFingerprint = fingerprint
  }
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // 是否需要防止数据重复提交
  const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
  // 间隔时间(ms)，小于此时间视为重复提交
  const interval = (config.headers || {}).interval || 1000
  if (getToken() && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + getToken() // 让每个请求携带自定义token 请根据实际情况自行修改
  }
  // get请求映射params参数
  const userStore = useUserStore()
  if (userStore.platformAdmin && !userStore.shopCode && isBusinessModulePath(config.url)) {
    const targetShopCode = useBusinessShopStore().shopCode
    if (targetShopCode) config.headers['X-Shop-Code'] = targetShopCode
  }
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
    // 先按「进行中」拦一道。注意这里被拦下时不曾登记指纹，也就不会误清掉正在飞的那一笔。
    const fingerprint = submitFingerprintOf(config)
    const startedAt = pendingSubmits.get(fingerprint)
    if (startedAt !== undefined && Date.now() - startedAt < PENDING_SUBMIT_MAX_AGE) {
      const message = '数据正在处理，请勿重复提交'
      console.warn(`[${config.url}]: ` + message)
      return Promise.reject(new Error(message))
    }
    pendingSubmits.set(fingerprint, Date.now())
    config.__submitFingerprint = fingerprint

    const requestObj = {
      url: config.url,
      data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
      time: new Date().getTime()
    }
    const requestSize = Object.keys(JSON.stringify(requestObj)).length // 请求数据大小
    const limitSize = 5 * 1024 * 1024 // 限制存放数据5M
    if (requestSize >= limitSize) {
      console.warn(`[${config.url}]: ` + '请求数据大小超出允许的5M限制，无法进行防重复提交验证。')
      return config
    }
    const sessionObj = cache.session.getJSON('sessionObj')
    if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
      cache.session.setJSON('sessionObj', requestObj)
    } else {
      const s_url = sessionObj.url                // 请求地址
      const s_data = sessionObj.data              // 请求数据
      const s_time = sessionObj.time              // 请求时间
      if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
        const message = '数据正在处理，请勿重复提交'
        console.warn(`[${s_url}]: ` + message)
        return Promise.reject(new Error(message))
      } else {
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
  }
  return config
}, error => {
    // 必须 return，否则这个 reject 会被丢掉，请求阶段的错误既传不到调用方也不会被捕获
    return Promise.reject(error)
})

// 响应拦截器
service.interceptors.response.use(res => {
    // 放在最前面：下面按 code 分支有多个 return，写在任一分支里都会漏掉其他出口
    releasePendingSubmit(res.config)
    if (res.config?.__inventoryIdempotencyFingerprint) {
      inventoryIdempotencyKeys.delete(res.config.__inventoryIdempotencyFingerprint)
    }
    // 未设置状态码则默认成功状态
    const code = res.data.code || 200
    // 获取错误信息
    const msg = errorCode[code] || res.data.msg || errorCode['default']
    // 二进制数据则直接返回
    if (res.request.responseType ===  'blob' || res.request.responseType ===  'arraybuffer') {
      return res.data
    }
    if (code === 401) {
      if (!isRelogin.show) {
        isRelogin.show = true
        ElMessageBox.confirm('登录状态已过期，您可以继续留在该页面，或者重新登录', '系统提示', { confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning', closeOnClickModal: false }).then(() => {
          isRelogin.show = false
          useUserStore().logOut().then(() => {
            location.href = '/index'
          })
      }).catch(() => {
        isRelogin.show = false
      })
    }
      return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
    } else if (code === 500) {
      ElMessage({ message: msg, type: 'error' })
      return Promise.reject(new Error(msg))
    } else if (code === 601) {
      ElMessage({ message: msg, type: 'warning' })
      return Promise.reject(new Error(msg))
    } else if (code !== 200) {
      ElNotification.error({ title: msg })
      return Promise.reject('error')
    } else {
      return  Promise.resolve(res.data)
    }
  },
  error => {
    // 网络失败、超时、被取消都要放行下一次提交，否则用户重试不了
    releasePendingSubmit(error.config)
    console.log('err' + error)
    let { message } = error
    if (message == "Network Error") {
      message = "后端接口连接异常"
    } else if (message.includes("timeout")) {
      message = "系统接口请求超时"
    } else if (message.includes("Request failed with status code")) {
      message = "系统接口" + message.slice(-3) + "异常"
    }
    ElMessage({ message: message, type: 'error', duration: 5 * 1000 })
    return Promise.reject(error)
  }
)

// 通用下载方法
export function download(url, params, filename, config) {
  downloadLoadingInstance = ElLoading.service({ text: "正在下载数据，请稍候", background: "rgba(0, 0, 0, 0.7)", })
  return service.post(url, params, {
    transformRequest: [(params) => { return tansParams(params) }],
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    responseType: 'blob',
    ...config
  }).then(async (data) => {
    const isBlob = blobValidate(data)
    if (isBlob) {
      const blob = new Blob([data])
      saveAs(blob, filename)
    } else {
      const resText = await data.text()
      const rspObj = JSON.parse(resText)
      const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default']
      ElMessage.error(errMsg)
    }
    downloadLoadingInstance.close()
  }).catch((r) => {
    console.error(r)
    ElMessage.error('下载文件出现错误，请联系管理员！')
    downloadLoadingInstance.close()
  })
}

export default service
