import { reactive, readonly } from 'vue'
import request from '@/utils/request'

/**
 * 当前身份。整个应用只有这一份。
 *
 * 用一个 reactive 对象而不是 Pinia：要存的东西就这么多，而且是**只读**的——
 * 它由服务端会话决定，前端除了在启动和登录时拉一次之外不会去改它。
 * 为这点东西引一个状态管理库，只会让「这个值是谁改的」变得更难回答。
 */
const state = reactive({
  loaded: false,
  siteCode: '',
  siteName: '',
  langs: ['en'],
  /**
   * 管理台界面的默认语言（站点设置里选的那个），zh / en / fr。
   *
   * ⚠ 和上面的 langs 不是一回事：那个是**前台**对买家开放的语言，这个是
   *   管理台自己的界面语言。而且它只是默认值——个人在顶栏切过之后以自己那份为准，
   *   见 i18n/index.js。
   */
  consoleLang: '',
  currency: 'USD',
  userName: '',
  displayName: '',
  /** 本站的角色编码（admin / operator）。⚠ 和 ERP 的角色没有任何关系 */
  roleCode: '',
  perms: [],
  /**
   * 能不能开「站点用户」那一页（/site-users）。只有从 ERP「独立站管理」的「站点用户」按钮
   * 进来的会话是 true——用户管理不在左边菜单里，也不认角色权限，见后端 ConsoleIdentity.userAdmin
   */
  userAdmin: false,
  /**
   * 左边菜单里摆不摆「用户管理」。按部署开关（后端 console.users-menu）：统领 true、法国站 false。
   * 摆出来还要 userAdmin 为 true 才显示——开关开着的站，站长进来时后端就给会话打上了 userAdmin
   */
  usersMenu: false,
  /** 这次是怎么进来的。只有 'erp'（从 ERP 免密进）一个值了——本站没有登录页 */
  via: '',
  /**
   * ERP 首页地址，顶栏那个「回众联 ERP」链接用。没配就不显示。
   *
   * ⚠ 没配的代价比看起来大：本站没有登录页，退出或掉线之后要再进来只能回 ERP
   * 点一次「进入管理」。这个链接不显示的话，人得自己知道 ERP 的地址。
   * 部署时把 console.erp-home-url 配上。
   */
  erpHomeUrl: ''
  // ⚠ siteKey 没有了：那一列删了（docs/独立站与ERP的边界.md §7.2）。
  //   前台取的是自己这台机器上的后端，没有范围要限定。
})

export const identity = readonly(state)

/**
 * 拉一次身份。拉不到（401）说明没有会话，调用方负责把人送去 /login 那个空页面。
 *
 * @param silent 传 true 时，401 不弹「登录状态已失效」的框、不整页跳转。
 *               <b>启动时那一次和空页面上那一次要传 true</b>：直接打开这个地址的人
 *               本来就没有会话，那是常态，不该弹一个说他「失效」了的框——
 *               他压根没进来过。
 */
export async function loadIdentity(silent = false) {
  const res = await request({ url: '/me', method: 'get', silentAuth: silent })
  Object.assign(state, res.data || {})
  if (!Array.isArray(state.langs) || !state.langs.length) state.langs = ['en']
  if (!Array.isArray(state.perms)) state.perms = []
  state.loaded = true
  return state
}

/** 退出或掉线时清掉，免得内存里还留着上一个人的身份和一堆已经取到的数据 */
export function clearIdentity() {
  Object.assign(state, {
    loaded: false, siteCode: '', siteName: '', langs: ['en'], consoleLang: '', currency: 'USD',
    userName: '', displayName: '', roleCode: '', perms: [], via: '', userAdmin: false, usersMenu: false
  })
}

/**
 * 有没有某个权限。
 *
 * <h3>⚠ 它的含义变了</h3>
 * 这里以前写着：
 *
 * > 这只用来决定界面上显示不显示某个按钮，**不是授权**。真正的授权发生在 ERP 侧：
 * > 那边每个接口都拿 userCode 现查当下的权限……让 ERP 做唯一的权威，
 * > 管理台不维护第二份会过期的真相。
 *
 * **ERP 不再参与任何一次取数了**（要点 1）。所以这份 perms 就是授权依据本身——
 * 界面显隐和后端放行用的是同一份，都来自本站的 u_s_role。
 *
 * 后端的 AuthInterceptor 仍然会独立判一遍（前端拦不住直接发请求的人），
 * 但两边现在读的是同一个来源，不会再出现「这边放行、那边 403」的对不上。
 *
 * 代价是会话期的滞后：站长收了某人的权限，那个人已经建立的会话里这份还是旧的。
 * 要立刻生效，把那个人停用即可。
 */
export function hasPermi(value) {
  if (!value) return true
  const need = Array.isArray(value) ? value : [value]
  if (!need.length) return true
  // 站长角色的 perms 就是一个 '*'，见建库脚本里那两行出厂角色
  if (state.perms.includes('*')) return true
  return need.some(item => state.perms.includes(item))
}
