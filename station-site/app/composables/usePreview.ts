/**
 * 装修预览模式。
 *
 * 管理台的「首页装修」页右边那一列里嵌着的就是这个页面（`<主域>/?__preview=1`），
 * 点「新窗口」时也是它。管理台把**还没保存的**草稿一路推过来，这个页面照着渲染。
 *
 * ## 内嵌要 nginx 放行一次
 *
 * 主域的 nginx 发 `X-Frame-Options: SAMEORIGIN`，而管理台在 `console.` 子域上——
 * 跨源，浏览器直接拒绝内嵌。所以 nginx 那边对**带 `?__preview=1` 的那一次请求**
 * 改发 `Content-Security-Policy: frame-ancestors`，只放行管理台那个域
 * （`deploy/nginx/*` 里的 `$station_frame_csp`）。
 *
 * 没放行的部署上，管理台自己会发现嵌不进来（一直收不到下面那声 ready），
 * 盖一层说明让人改用另开窗口——顶层窗口不受这个头限制，一行配置都不用动。
 *
 * ## 推过来的是**算好的**数据，不是草稿 JSON
 *
 * 管理台先把草稿发给后端的 `/api/site/content/preview`，后端用**和公开接口
 * 完全同一段代码**算出合并结果（共享层 → 默认语言层 → 当前语言层），
 * 再把结果 postMessage 过来。
 *
 * 关键就在这儿：**合并规则只有后端那一份**。要是让这个页面自己拿草稿去合，
 * 那就是第二份实现，迟早和线上不一致——而症状是「预览里对，发出去不对」，
 * 那种问题查起来最费劲。
 *
 * ## 安全
 *
 * - 只认 `?__preview=1`：普通访客的页面上这一整套连装都不装；
 * - 只收**把我放出来的那一方**发来的消息：另开窗口时是 `window.opener`，
 *   内嵌时是 `window.parent`（`event.source` 比对），别的窗口发的一律丢掉；
 * - 预览页 `noindex`，也不发埋点（见 `useSeo` 和 `track.client.ts` 里的判断）；
 * - 收到的东西只用来渲染，不写任何存储，也不回发任何东西。
 */

/** 约定的消息类型。写死一个不像随机字符串的值，方便在别人的页面上排查 */
export const PREVIEW_MESSAGE_TYPE = 'station-decorate-preview'

/** 这次访问是不是预览。SSR 阶段也要能判，所以从路由 query 取而不是 window */
export function useIsPreview() {
  const route = useRoute()
  return computed(() => route.query.__preview === '1')
}

/**
 * 在预览页上装好接收器。只在客户端跑一次。
 *
 * @param apply 收到一份新数据时怎么用它。调用方给，因为「顶掉哪一份缓存」
 *              是页面自己的事（首页顶 `site-info-<lang>`）。
 */
export function usePreviewReceiver(apply: (payload: Record<string, unknown>) => void) {
  /*
   * 两个生命周期钩子都在 setup 顶层注册，不套在 onMounted 里面。
   * 套着写也能跑（Vue 在执行钩子时是有当前实例的），但那要多知道一条规则；
   * 摊平写，谁来读都不用先确认这一点成不成立。
   */
  let detach: (() => void) | null = null

  onMounted(() => {
    /*
     * 谁把我放出来的：另开窗口时是 opener，嵌在管理台那一列里时是 parent。
     *
     * ⚠ 顶层窗口的 `window.parent` 就是它自己，所以要和 window 比一下——
     *   不比的话，一个普通访客直接打开带 ?__preview=1 的地址，这里会把
     *   页面自己当成管理台，等着一条永远不会来的消息（无害，但白装一个监听器）。
     */
    const host = window.opener || (window.parent !== window ? window.parent : null)
    if (!host) return

    const onMessage = (event: MessageEvent) => {
      /*
       * ⚠ 比 event.source，不比 event.origin。
       *
       * 比 origin 要求这个页面知道管理台的域名，而它并不知道（站点配置里
       * 没有这一项，本机开发时更是随便一个端口）。而 source 这一条更严：
       * 只有**把我打开的那个窗口 / 嵌着我的那个页面**说的话才算数，
       * 别的标签页、别的站点上的 iframe 都发不进来。
       */
      if (event.source !== host) return
      const data = event.data
      if (!data || data.type !== PREVIEW_MESSAGE_TYPE || !data.site) return
      apply(data.site as Record<string, unknown>)
    }

    window.addEventListener('message', onMessage)
    detach = () => window.removeEventListener('message', onMessage)
    /*
     * 告诉管理台「我准备好了」。
     *
     * 没有这一步的话会丢第一帧：窗口一开 / iframe 一挂，管理台立刻 postMessage，
     * 而这个页面那时候还在加载，监听器都没装上，那条消息就没了——
     * 表现是「点了预览，窗口开了，但是空的/还是旧的」，得再改一个字才刷新。
     *
     * ⚠ 内嵌时它还兼作「嵌进来了」的信号：管理台那边等不到这一声就当是被
     *   X-Frame-Options 拦了，改提示用另开窗口。被拦掉的 iframe 从外面读不出
     *   任何状态，这一声是唯一的判据。
     *
     * targetOrigin 用 '*'：这条消息里没有任何内容，只是一声「我在了」。
     */
    host.postMessage({ type: PREVIEW_MESSAGE_TYPE, ready: true }, '*')
  })

  onBeforeUnmount(() => detach?.())
}
