/**
 * 「这个路径归买家中心还是归商城」——**全站唯一一份判据**。
 *
 * 商城在主域（首页、商品、购物车、下单、登录注册），
 * 买家中心在 `buyer.` 子域（我的订单、账号资料、地址簿管理）。
 *
 * ## 为什么放在 shared/
 *
 * 这条规则有三个消费方，跑在三个不同的地方：
 *
 * | 谁 | 在哪 | 拿它干什么 |
 * |---|---|---|
 * | `server/middleware/domain-split.ts` | Nitro | 整页请求走错 host 时 301 |
 * | `server/routes/robots.txt.ts`        | Nitro | 买家域上挡这两支 |
 * | `app/composables/useSiteHosts.ts`    | 浏览器 + SSR | 站内链接拼相对还是绝对 |
 *
 * 2026-09-10 之前它有**三份拷贝**（`server/utils/hosts.ts` 的 `BUYER_PATHS`、
 * `useSiteHosts.ts` 的 `BUYER_PAGES`、`useSiteDomains.ts` 那一套），
 * 而且两个文件各自写着「⚠ 改这份要同时改另一份」。那两份是同一次合并
 * （`fdae0b80`）两侧各写一版留下的，判据一样、写法不同——正是它们警告的那种东西。
 * 合成一份之后，漏改的可能性从「记得改三处」变成 0。
 *
 * ## ⚠ 判据是「不登录就看不到」，不是「属于账号流程」
 *
 * 用户 2026-09-09 的原话：
 *
 * > 不管你登入不登入首页都是 kangchen.vclinker.com，产品、关于我们、联系我们
 * > 都一样，**登入之后只有需要登入才能看的**才需要 buyer.
 *
 * 所以清单只有两项：`/account`（账号资料、地址簿）和 `/orders`（我的订单，
 * 后端按会话里的 buyercode 查库）。
 *
 * <b>登录、注册、找回密码、重置密码、邮箱验证不在这里。</b>这一条被改错过一次，
 * 写清楚免得第三次：那几页**不需要登录就能看**，按上面那条判据属于主域；
 * 而且用户 2026-09-08 专门确认过（原话「登入也放主域」），理由记在
 * `docs/设计稿/2026-09-08-独立站域名拆分.md` 的
 * 「登录页放在主域」一节——登录入口在每个公开页右上角，点一下跳到另一个域名
 * 去输密码，等于在教用户「在别的域名上输本站密码是正常的」。
 *
 * ## ⚠ 默认归主域是安全的那一边
 *
 * 以后加买家页面忘了加进这张表，后果只是它在主域上也能打开，而不是漏数据。
 */

/** 买家中心的路径。**白名单：清单以外的一律归主域。** */
export const BUYER_PATHS = ['/account', '/orders'] as const

/**
 * 语言前缀。和 `nuxt.config.ts` 里 `pages:extend` 用的 `[a-z-]{2,5}` 一致。
 *
 * ⚠ 这个正则会误伤：`/cart` 里的 `cart` 是 4 个小写字母、后面就是结尾，
 *   完全符合「语言段」的形状；`/login` 正好 5 个字母，同理。
 *   vue-router 不会搞错（静态路径优先级高于动态段），但这里没有路由表，
 *   只有字符串。所以 {@link isBuyerPath} 是**拿原路径和剥掉前缀的路径各判一次**，
 *   而不是先剥再判。
 */
const LANG_PREFIX = /^\/[a-z-]{2,5}(?=\/|$)/

/**
 * 这个路径归不归买家中心。
 *
 * ⚠ **原路径和剥掉语言前缀的路径各判一次**，只要有一次命中就算。
 *
 * 不能「先剥前缀再判」，因为 {@link LANG_PREFIX} 分不清语言段和短路径名：
 *
 * | 路径 | 剥完变成 | 先剥再判的结果 |
 * |---|---|---|
 * | `/orders` | `/orders`（`order` 后面是 `s`，不匹配前缀） | 对 |
 * | `/ja/orders` | `/orders` | 对 |
 * | `/cart` | `/` ← **被当成语言段吃掉了** | 差点错 |
 *
 * 各判一次就绕开了这件事：`/cart` 原路径不在清单里，剥完的 `/` 也不在，归主域，对。
 *
 * 代价是 `/xx/account` 这种「语言段不是真语言」的地址会被送到买家中心去，
 * 然后在那边 404。可以接受——它本来也是个不存在的地址。
 *
 * ⚠ 传进来的可以带 query / hash（浏览器侧的调用方常常直接拿路由的 `fullPath`），
 *   这里会先剥掉；判的只有 pathname。
 */
export function isBuyerPath(pathname: string): boolean {
  const clean = String(pathname || '').split('?')[0]!.split('#')[0]!
  return matches(clean) || matches(stripLang(clean))
}

function stripLang(pathname: string): string {
  const matched = pathname.match(LANG_PREFIX)
  if (!matched) {
    return pathname
  }
  const rest = pathname.slice(matched[0].length)
  return rest === '' ? '/' : rest
}

function matches(pathname: string): boolean {
  return BUYER_PATHS.some(p => pathname === p || pathname.startsWith(p + '/'))
}
