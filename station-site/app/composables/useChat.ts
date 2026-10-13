/**
 * 在线咨询：右下角那个悬浮窗的状态和轮询。
 *
 * 后端在 `/openapi/site/chat/**`（station-console 的 `SiteChatController`），
 * 表是 `u_s_chat` / `u_s_chatmessage`。
 *
 * ## ⚠ 匿名就能聊，邮箱选填
 *
 * 这一条和询盘、下单是同一条立场（见 `cart/index.vue` 第 1 条：
 * 「挡一道登录会掉单，外贸站尤其如此」）。**不要**给这个窗口加一道登录，
 * 也不要把邮箱改成必填——问一句「多少钱」之前要先填表的人，多数会直接走掉。
 *
 * 留了邮箱的会被挂到 `u_s_buyer` 客户档案上（后端 `resolveByEmail`），
 * 运营之后能邮件跟进；没留的清了浏览器数据就找不回对话，那是这个取舍的代价，
 * 不是缺陷。
 *
 * ## ⚠ 身份在一个 HttpOnly Cookie 里（CHAT_VISITOR），JS 读不到
 *
 * 所以这里**没有**任何 `localStorage.setItem('chatToken', …)`。那个令牌拿到就能
 * 读走整条对话，放进 JS 能读的地方等于把它交给任何一个 XSS；放进 URL 会让它
 * 原样进 nginx 的 access log。两条都别做——后端也压根不收 token 参数。
 *
 * ## ⚠ 轮询，不是 WebSocket
 *
 *   窗口开着   3 秒一次增量（带 sinceId）
 *   窗口收起  30 秒一次，只看有没有未读
 *   页面不可见 一律暂停
 *
 * 最后一条不是优化，是必须的：不暂停的话，买家开着的后台标签页会整天打服务器，
 * 而那种负载在日志里看起来和正常流量一样，很难查。
 *
 * 设计文档：docs/设计稿/2026-09-04-在线咨询.md
 */

import { siteLangHeaders } from '~/composables/useSite'

export interface ChatMessage {
  id: number
  sender: '买家' | '运营'
  senderUser: string | null
  content: string
  createTime: string
}

interface OpenResult {
  chatCode: string | null
  messages: ChatMessage[]
  unread: number
  email: string | null
}

interface PollResult {
  messages: ChatMessage[]
  unread: number
}

/** 窗口开着时的轮询间隔 */
const OPEN_MS = 3000
/** 收起时的轮询间隔。只是看看有没有人回，慢一点没关系 */
const IDLE_MS = 30000

/**
 * 统一拆 `{ code, msg, data }` 信封。
 *
 * 写法照 `useBuyer.ts` 的 `callAccount`，两处不同也一样：失败抛普通 `Error`
 * （不是 `createError`——聊天发不出去不该把整页换成错误页），并且带 `credentials`
 * （身份就在 Cookie 里，不带就永远是一个新访客）。
 */
async function callChat<T>(path: string, options: {
  method?: 'GET' | 'POST'
  body?: Record<string, unknown>
  query?: Record<string, unknown>
} = {}): Promise<T> {
  const config = useRuntimeConfig()
  const base = config.public.apiBase || ''
  const res = await $fetch<{ code: number; msg: string; data?: T }>(
    `${base}/openapi/site/chat${path}`,
    {
      method: options.method || 'GET',
      headers: siteLangHeaders(),
      body: options.body,
      query: options.query,
      credentials: 'include',
      // ⚠ 后端用 body 里的 code 表达「发太快了」这类情况，而那些 msg 是要给买家看的。
      //   让 $fetch 自己抛的话，响应体会被丢掉，只剩一个 "FetchError: 500"
      ignoreResponseError: true
    })
  if (res?.code !== 200) {
    throw new Error(res?.msg || '')
  }
  return res.data as T
}

/**
 * 整个应用一份。
 *
 * ⚠ 用 `useState` 而不是模块级的 `ref`：模块级变量在 SSR 下是**所有请求共用**的，
 * 那意味着一个访客的对话会出现在另一个访客的页面上。这个组件虽然是 `.client`（不 SSR），
 * 但 `useState` 是这个项目里正确的那条路，别改成模块级 ref。
 */
export function useChat() {
  const open = useState('chat-open', () => false)
  const messages = useState<ChatMessage[]>('chat-messages', () => [])
  const unread = useState('chat-unread', () => 0)
  const email = useState<string | null>('chat-email', () => null)
  const started = useState('chat-started', () => false)
  const sending = useState('chat-sending', () => false)
  const failed = useState('chat-failed', () => false)

  /**
   * 手里最大的消息 id。轮询靠它只取增量。
   *
   * 放在 `useState` 外面是可以的：它只在浏览器里用（这个 composable 只被
   * `.client` 组件调），而且丢了最坏是多拉一次历史。
   */
  const sinceId = useState('chat-since', () => 0)

  let timer: ReturnType<typeof setInterval> | null = null

  function absorb(batch: ChatMessage[]) {
    if (!batch.length) return
    messages.value = messages.value.concat(batch)
    sinceId.value = batch[batch.length - 1]!.id
  }

  /** 打开窗口时拉一次历史。⚠ 后端在这一步**不建库行**，只种 Cookie */
  async function start() {
    if (started.value) return
    try {
      const data = await callChat<OpenResult>('/open', { method: 'POST' })
      messages.value = data.messages || []
      unread.value = data.unread || 0
      email.value = data.email
      if (messages.value.length) {
        sinceId.value = messages.value[messages.value.length - 1]!.id
      }
      started.value = true
    } catch {
      // 打不开就是打不开，不弹错——一个还没说话的人看到「聊天初始化失败」
      // 只会觉得这个站坏了。下一次心跳会再试
    }
  }

  async function poll() {
    // ⚠ 见文件头：不可见就什么都不做
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
    try {
      const data = await callChat<PollResult>('/messages', { query: { sinceId: sinceId.value } })
      absorb(data.messages || [])
      unread.value = data.unread || 0
      // 窗口开着看到的就算读过了
      if (open.value && unread.value > 0) await markRead()
    } catch {
      // 网络抖一下不该让界面报错。下一次心跳会补上
    }
  }

  async function send(content: string, extra: { email?: string; name?: string; lang?: string }) {
    const text = content.trim()
    if (!text || sending.value) return
    sending.value = true
    failed.value = false
    try {
      await callChat('/send', {
        method: 'POST',
        body: {
          content: text,
          email: extra.email || undefined,
          name: extra.name || undefined,
          lang: extra.lang || undefined,
          // 运营一眼看出他在看哪个商品。这是这个窗口比询盘表单多出来的那点信息
          pageUrl: typeof location !== 'undefined' ? location.href : undefined
        }
      })
      if (extra.email) email.value = extra.email
      // 立刻拉一次，不等下一个 3 秒——自己发的话要马上出现，否则会以为没发出去
      await poll()
    } catch {
      failed.value = true
    } finally {
      sending.value = false
    }
  }

  async function markRead() {
    try {
      await callChat('/read', { method: 'POST' })
      unread.value = 0
    } catch {
      // 标不上就下次再标，不影响看消息
    }
  }

  function reschedule() {
    if (timer) clearInterval(timer)
    timer = setInterval(poll, open.value ? OPEN_MS : IDLE_MS)
  }

  async function toggle() {
    open.value = !open.value
    reschedule()
    if (open.value) {
      await start()
      await poll()
    }
  }

  function onVisibility() {
    if (document.visibilityState === 'visible') {
      // 切回这个标签页时立刻补一次，不用等下一个周期
      poll()
      reschedule()
    }
  }

  function mount() {
    // ⚠ 这里**不调 start()**：那会给每一个只是路过的访客种一个 Cookie。
    //   先只用一次轻量的轮询看看「有没有人回我」——没有历史的人拿回的是空。
    poll()
    reschedule()
    document.addEventListener('visibilitychange', onVisibility)
  }

  function unmount() {
    if (timer) clearInterval(timer)
    timer = null
    document.removeEventListener('visibilitychange', onVisibility)
  }

  return { open, messages, unread, email, sending, failed, send, toggle, mount, unmount }
}
