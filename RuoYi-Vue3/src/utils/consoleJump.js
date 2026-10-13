/**
 * 把独立站跳转令牌 POST 到站点的 /sso。
 *
 * ⚠ 令牌必须走 POST 表单，绝不能拼进 URL。
 *
 * docs/独立站与ERP的边界.md §4.2：自包含签名令牌本身就是凭据——60 秒内拿到原文的人
 * 可以直接进后台。它一旦进了 URL，就会进浏览器历史、进书签、进 nginx access log、
 * 进任何外链的 Referer。POST 表单让它只出现在请求体里，这是 SAML 的 HTTP-POST binding
 * 用了二十年的办法。
 *
 * 表单是动态建的、提交完立刻拆掉：令牌只活 60 秒，没必要让它在 DOM 里多待一秒，
 * 留着的话任何一个能读 DOM 的扩展或注入脚本都拿得到它。
 * 真实的 <form> 元素发起的提交不会被浏览器当弹窗拦掉，所以开新窗口也不需要
 * window.open 那套「先占句柄」的把戏。
 *
 * @param {object} jump
 * @param {string} jump.action 站点的 /sso 地址（console-jump 接口返回的 action）
 * @param {string} jump.token  签名令牌（同上返回的 token）
 * @param {string} [jump.target='_blank'] 列表页开新窗口；登录直达用 '_self' 在当前窗口走
 */
export function submitConsoleJump({ action, token, target = '_blank' }) {
  if (!action || !token) {
    throw new Error('没拿到跳转令牌')
  }
  const form = document.createElement('form')
  form.method = 'post'
  form.action = action
  form.target = target
  form.style.display = 'none'

  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = 'token'
  input.value = token
  form.appendChild(input)

  document.body.appendChild(form)
  try {
    form.submit()
  } finally {
    form.remove()
  }
}
