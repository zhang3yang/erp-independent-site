/**
 * 把 u_s_siteinfo.domain 拼成前台的绝对地址。
 *
 * <p>domain 这一列**约定存主机名**（`tongling.vclinker.com`），但库里那批
 * 历史值和手工灌进去的种子数据里带着协议头（`https://…`）——直接
 * `` `https://${domain}` `` 拼出来就是 `https://https//…`，点进去打不开。
 * 所以这里按有没有协议头分开处理，和后端 BuyerAuthService#siteBase 是同一套规则。</p>
 *
 * <p>⚠ 存的是**主域**，不是 `buyer.` 子域。前台分两个 host：主域放公开页，
 * `buyer.` 只放登录/注册/找回密码/重置密码/邮箱验证/账户
 * （`deploy/nginx/tongling.conf`）。这一列一度存着 `buyer.…`，那时候前台整站
 * 都在那个子域上。「访问前台」要指主域。</p>
 *
 * @param {string} domain 主域名，可带协议头、可带尾斜杠
 * @returns {string} 绝对地址；没填域名返回空串（调用方据此不显示链接）
 */
export function toFrontUrl(domain) {
  const value = (domain || '').trim().replace(/\/+$/, '')
  if (!value) return ''
  return /^https?:\/\//i.test(value) ? value : `https://${value}`
}
