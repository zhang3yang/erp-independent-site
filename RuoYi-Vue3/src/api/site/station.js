import request from '@/utils/request'

/*
 * 独立站**登记**。
 *
 * ⚠ 这个模块的性质变了。依据 docs/独立站与ERP的边界.md §7：
 *
 *     ERP 的「独立站管理」是一张登记表，不是一个后台入口。它只回答
 *     「我们有哪些站、在哪、怎么进」，不含站里的任何业务数据。
 *
 * 商品、询盘、首页装修、文件资源都搬到站点自己那台服务器上了，
 * 在 console.<主域名> 那个管理台里改——那是另一个部署、另一套用户体系、另一个库。
 *
 * 所以这个文件里没有 saveSiteContent、没有 enterConsole，
 * api/site/goods.js、inquiry.js、file.js 三个文件也整个删了（§10.1）。
 */

// 站点列表（分页）
export function listSites(query) {
  return request({ url: '/site/station/list', method: 'get', params: query })
}

// 站点详情
export function getSite(siteCode) {
  return request({ url: '/site/station/' + siteCode, method: 'get' })
}

/**
 * 新增站点。站点编码由后端生成，不要在前端拼。
 *
 * ⚠ 返回体的 data.consoleSecret 是**明文跳转密钥**，这是它唯一一次出现。
 * 装那台站点服务器时要把它填进 u_s_siteinfo.consolesecret，两边必须一致。
 * 界面上要让人看得见、复制得走，但**不要存进 localStorage、不要打进 console**。
 */
export function addSite(data) {
  return request({ url: '/site/station', method: 'post', data })
}

export function updateSite(data) {
  return request({ url: '/site/station', method: 'put', data })
}

/**
 * 重置跳转密钥，返回新的明文。
 *
 * ⚠ 换完之后必须同步改那台站点服务器上的 u_s_siteinfo.consolesecret，
 * 否则那个站的「进入管理」立刻失效——而前台和站点本地登录都不受影响，
 * 所以这个故障看起来会像「只有跳转坏了」。
 */
export function rotateSecret(siteCode) {
  return request({
    url: '/site/station/' + siteCode + '/secret',
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

/**
 * 签一张进站点管理台的跳转令牌。
 *
 * 返回 { action, token, expiresIn, jti, siteName }。
 *
 * ⚠⚠ token 必须用**隐藏表单 POST** 提交到 action，绝不能拼进 URL。
 *
 * 这是新旧协议最大的差别（§4.2）。旧协议 URL 上那串票是随机的、不含信息，
 * 而且捡到也换不动（兑换要 clientSecret），所以进浏览器历史和 nginx 日志都无所谓。
 *
 * 新协议的 token 是**自包含签名令牌**——60 秒内拿到原文的人可以直接用它进后台。
 * 它一旦进了 URL，就会进浏览器历史、进书签、进 nginx access log、
 * 进任何外链的 Referer。POST 表单让它只出现在请求体里，
 * 这是 SAML 的 HTTP-POST binding 用了二十年的办法。
 *
 * 提交表单的代码见 index.vue 的 enterConsole()。
 *
 * 豁免防重复提交：同一个站「进去→返回→再进去」是常态，
 * 被当成重复提交拦下来就进不去了。
 */
export function createConsoleJump(siteCode) {
  return request({
    url: '/site/station/' + siteCode + '/console-jump',
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

/**
 * 管理站点用户：同一套跳转令牌，多一个 scope=users（2026-09-24）。
 *
 * 站点管理台的「用户管理」菜单拿掉了——那一页全是 ERP 登录名、「ERP 免密」来源，
 * 不该给海外客户看。站点只给带这个用途进来的会话开用户页面和接口，所以入口只有这里。
 * 后端要 site:station:edit。
 */
export function createUsersJump(siteCode) {
  return request({
    url: '/site/station/' + siteCode + '/users-jump',
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

/**
 * 删除登记。
 *
 * ⚠ 删的只是 ERP 这条记录。那台站点服务器上的库、商品、询盘都还在——
 * 要真正下线一个站，是去关那台机器。
 */
export function deleteSite(siteCodes) {
  return request({ url: '/site/station/' + siteCodes, method: 'delete' })
}
