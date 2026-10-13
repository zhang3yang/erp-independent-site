// 需要商家上下文的业务模块前缀。这些模块的后端接口都带 @ss.hasBusinessShop()：
// 商家用户走登录时带的 shopCode，平台超管不带 shop 登录时则要靠 X-Shop-Code 请求头指定操作哪个商家。
// 新增业务模块时必须在这里补一条，否则超管在顶部选完商家，该模块的接口仍会被判 403。
// 这份名单必须与后端 TenantPermissionPrefixes.ALL 里的业务模块前缀一一对应，
// 那边少一条是商家 403，这边少一条是超管 403，两边都要改。
//
// /dashboard/ 是个例外：它的接口<b>没有</b> @ss.hasBusinessShop()，因为首页是所有人
// 登录后的第一屏，抛异常会直接糊一个红条。它只是需要这个头来知道统计哪个商家——
// 少了这一条，超管选完商家首页看板还是空的，而且不报错，查起来没有方向。
export const BUSINESS_MODULE_PREFIXES = [
  '/inventory/', '/purchase/', '/production/', '/finance/', '/order/', '/site/', '/dashboard/'
]

export function isBusinessModulePath(path) {
  return !!path && BUSINESS_MODULE_PREFIXES.some(prefix => path.startsWith(prefix))
}
