import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { t, locale } from '@/i18n'
import { translateServerText } from '@/i18n/server'

/**
 * 管理台的 HTTP 客户端。
 *
 * 和 ERP 前端那份 request.js 的三点不同，每一点都是这套架构的直接结果：
 *
 * 1. **不带 token。**身份是一个 HttpOnly 的会话 Cookie，浏览器自动带上，JS 读不到它——
 *    这正是我们要的：XSS 也偷不走会话。所以这里没有 Authorization 头，也没有 token 刷新。
 *
 * 2. **baseURL 是 `/api`，同源。**生产环境下 Nginx 把前端静态文件和管理台后端放在同一个
 *    域名下。同源不是省事，是设计的一部分：跨域的话 SameSite=Lax 的 Cookie 带不过去。
 *
 * 3. **401 跳 /login，那是一个空页面。**不是登录页——这个后台只能从 ERP 的
 *    「独立站管理」点「进入管理」免密进来（station-console 的 SessionController）。
 *    掉线的人在那一页看到「已退出登录」一句话，要再进来得回 ERP 再点一次。
 */
const service = axios.create({
  baseURL: '/api',
  timeout: 30000,
  // 同源请求其实默认就带 Cookie，写出来是为了让「靠 Cookie 认身份」这件事在代码里看得见
  withCredentials: true
})

service.interceptors.request.use(
  config => config,
  error => Promise.reject(error)
)

/** 掉线提示只弹一次：一个页面同时发好几个请求，全 401 的话会叠出一屏对话框 */
let expiredNotified = false

service.interceptors.response.use(
  response => {
    // 业务码在响应体里，HTTP 一律 200 —— 和 ERP 同一套约定。
    // 两套判据混用是「有时候能拿到错误提示、有时候拿到空」的根源。
    const data = response.data
    // 少数接口回的是纯文本（比如装修内容的保存），拿不到对象时直接放行
    if (!data || typeof data !== 'object') return data
    // 后端的话是中文，先换成界面语言：下面弹的错误、以及页面里 `r.msg` 那种成功提示都读它。见 i18n/server.js
    if (typeof data.msg === 'string') data.msg = translateServerText(data.msg)

    const code = data.code ?? 200
    if (code === 200) return data
    if (code === 401) {
      if (!response.config?.silentAuth) handleExpired(data.msg)
      return Promise.reject(new Error(data.msg || t('net.expired')))
    }
    /*
      data.msg 在上面已经换过语言了（i18n/server.js）。后端新写了一句、那边的表里还没有的话，
      这里显示的是中文原话——和加翻译之前一样，不会是一句半真半假的提示。
    */
    ElMessage({ message: data.msg || t('net.failed'), type: 'error' })
    return Promise.reject(new Error(data.msg || t('net.failed')))
  },
  error => {
    // HTTP 层面的错误（401/403 来自 AuthInterceptor）同样带着中文 msg
    if (typeof error.response?.data?.msg === 'string') error.response.data.msg = translateServerText(error.response.data.msg)
    // HTTP 层面的 401/403 来自 AuthInterceptor，业务层面的在上面那一支。
    //
    // ⚠ 那一层现在回的是**真实的 HTTP 状态码**，不是 200+body.code——
    //   nginx 的 access log 和监控要能看出「有人在被拒」，而业务失败对监控来说是正常流量。
    if (error.response?.status === 401) {
      // ⚠ silentAuth：调用方自己会处理「没登录」这件事，别弹框、别整页跳转。
      //
      //   两个地方用它：启动时那次 loadIdentity()，和 /login 那个空页面上的那次。
      //   直接打开这个地址的人本来就没有会话，那是常态；弹一个「登录状态已失效」
      //   的框、再整页跳到 /login（也就是跳回他自己）只会转圈。
      if (!error.config?.silentAuth) handleExpired(error.response.data?.msg)
      return Promise.reject(error)
    }
    if (error.response?.status === 403) {
      // 权限不够。**不要把人踢出去**——403 多数时候只是「这一个动作你不能做」
      // （有查看权限、没有删除权限），为此销毁会话是过度反应。
      ElMessage({ message: error.response.data?.msg || t('net.noPermi'), type: 'error' })
      return Promise.reject(error)
    }
    let message = error.message
    if (message === 'Network Error') message = t('net.offline')
    else if (message.includes('timeout')) message = t('net.timeout')
    ElMessage({ message, type: 'error', duration: 5000 })
    return Promise.reject(error)
  }
)

function handleExpired(msg) {
  if (expiredNotified) return
  expiredNotified = true
  // 这句话要给出**操作路径**，不是「请重新登录」：本站没有登录页，
  // 重新进来只有一条路——回众联 ERP 的独立站管理页面，再点一次【进入管理】。
  // 不说全的话，人会在这个域名上找一个不存在的登录框。
  //
  // ⚠ 和两处保持同一句话：后端 AuthInterceptor 的 401 msg（多数时候弹的是它）、
  //   /login 那个空页面上的第二行。三处不一致，人会以为是两种不同的故障。
  /*
    ⚠ 这一处**不用**后端那句 msg，除非界面本来就是中文。

    和上面那条通用错误相反，而理由正是那条注释的另一面：这句话是一个封闭的、
    我们自己写好的固定句子（后端 AuthInterceptor 里那句和它一字不差），
    所以翻得了。一个法国站长掉线时最需要看懂的恰恰是这一句——
    它给的是「怎么再进来」的操作路径，看不懂就卡在这儿了。

    中文界面仍然优先显示 msg：那是后端那一份，两处措辞万一分了叉，
    以后端为准更接近事实。
  */
  const expiredText = locale.value === 'zh' ? (msg || t('net.expiredBody')) : t('net.expiredBody')
  ElMessageBox.alert(
    expiredText,
    t('net.expiredTitle'),
    { confirmButtonText: t('common.gotIt'), type: 'warning', showClose: false }
  ).finally(() => {
    // ⚠ 这里以前跳的是 /entry-required.html?reason=expired（那张「请从 ERP 进入」
    //   的静态页），后来 §6a 改成了本站的登录页。两个都没了：
    //   现在去的是 /login 上那个**空页面**。
    //
    //   redirect 仍然带着：从 ERP 再点一次进来之后，SsoController 落地在首页，
    //   但如果他是在这一页上刷新的（会话其实还在），空页面会用它把人送回原来那一页。
    //
    //   整页跳转而不是 router.push：会话已经没了，把整个应用重新载一遍最干净——
    //   否则内存里还留着上一个人的身份和一堆已经取到的数据。
    const back = encodeURIComponent(location.pathname + location.search)
    window.location.href = `/login?reason=logged-out&redirect=${back}`
  })
}

export default service
