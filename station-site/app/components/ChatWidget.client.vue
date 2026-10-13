<!--
  在线咨询：右下角的悬浮球 + 对话窗口。

  ## ⚠ 文件名后缀 .client 是必须的

  这个组件**不参与 SSR**。理由和 `useBuyer.ts` 顶部那条一样：它显示的是每个访客
  各自的东西，收录没有意义；而 SSR 要把访客的 Cookie 从 Nuxt 服务端转发给后端，
  那是一整套额外的东西，为一个不需要收录的窗口做不值得。

  去掉 `.client` 的表现是：服务端渲染出一个空窗口，客户端再渲染一次真实内容，
  中间闪一下，控制台里是 hydration mismatch。

  ## ⚠ 消息用 {{ }} 渲染，不要改成 v-html

  内容是买家自己写的。文本插值不解释 HTML，所以存原文是安全的（设计文档 §4.5）。
  改成 v-html 的那一刻，任何一个访客就能在运营的管理台里执行脚本。

  ## 邮箱是选填的，别改成必填

  这个站的询盘和下单都不挡登录（cart/index.vue 第 1 条）。这里同理：
  问一句「多少钱」之前要先填表的人，多数会直接走掉。

  设计文档：docs/设计稿/2026-09-04-在线咨询.md
-->
<template>
  <div class="cw">
    <button class="cw-ball" :class="{ on: open }" :aria-label="t.chatOpen" :title="t.chatOpen"
      @click="toggle">
      <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
        <path v-if="!open" fill="currentColor"
          d="M12 3C6.99 3 3 6.36 3 10.5c0 2.3 1.24 4.35 3.2 5.72-.13.9-.5 2.1-1.4 3.28 1.9-.3 3.3-1.05 4.2-1.66.93.24 1.93.36 3 .36 5.01 0 9-3.36 9-7.5S17.01 3 12 3Z" />
        <path v-else fill="currentColor"
          d="M18.3 5.7a1 1 0 0 0-1.4 0L12 10.6 7.1 5.7a1 1 0 1 0-1.4 1.4l4.9 4.9-4.9 4.9a1 1 0 1 0 1.4 1.4l4.9-4.9 4.9 4.9a1 1 0 0 0 1.4-1.4L13.4 12l4.9-4.9a1 1 0 0 0 0-1.4Z" />
      </svg>
      <span v-if="unread > 0" class="cw-badge">{{ unread > 9 ? '9+' : unread }}</span>
    </button>

    <section v-show="open" class="cw-panel" role="dialog" :aria-label="t.chatTitle">
      <header class="cw-top">
        <div>
          <p class="cw-title">{{ t.chatTitle }}</p>
          <p class="cw-lead">{{ t.chatLead }}</p>
        </div>
        <button class="cw-x" :aria-label="t.chatClose" :title="t.chatClose" @click="toggle">×</button>
      </header>

      <div ref="scroller" class="cw-msgs">
        <p v-if="!messages.length" class="cw-hint">{{ t.chatEmpty }}</p>
        <div v-for="m in messages" :key="m.id" class="cw-msg"
          :class="m.sender === '买家' ? 'mine' : 'theirs'">
          <!-- ⚠ 文本插值。见文件头 -->
          <span class="cw-bubble">{{ m.content }}</span>
          <span class="cw-stamp">
            {{ m.sender === '买家' ? t.chatYou : t.chatUs }} · {{ shortTime(m.createTime) }}
          </span>
        </div>
      </div>

      <!--
        没留过邮箱时才问，而且**不拦发送**。留过一次之后这一格就消失了——
        每次都问会让回头客觉得我们没记住他。
      -->
      <div v-if="!email" class="cw-id">
        <input v-model="draftName" class="cw-input" type="text" autocomplete="name"
          :placeholder="t.chatNameLabel" />
        <input v-model="draftEmail" class="cw-input" type="email" autocomplete="email"
          :placeholder="t.chatEmailLabel" />
        <p class="cw-note">{{ t.chatEmailNote }}</p>
      </div>

      <p v-if="failed" class="cw-fail">{{ t.chatFailed }}</p>

      <form class="cw-compose" @submit.prevent="submit">
        <textarea v-model="draft" class="cw-text" rows="2" maxlength="2000"
          :placeholder="t.chatPlaceholder" @keydown.enter.exact.prevent="submit" />
        <button class="cw-send" type="submit" :disabled="sending || !draft.trim()">
          {{ t.chatSend }}
        </button>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import type { SiteInfo } from '~/composables/useSite'

const { open, messages, unread, email, sending, failed, send, toggle, mount, unmount } = useChat()

const draft = ref('')
const draftName = ref('')
const draftEmail = ref('')
const scroller = ref<HTMLElement | null>(null)

/**
 * 访客的语言。
 *
 * ⚠ 这里**不调 `useSiteInfo()`**：那个 composable 在取不到站点信息时抛的是
 * `fatal: true` 的 `createError`，会把整页换成错误页。一个聊天窗口没有资格
 * 因为自己拿不到语言就把页面干掉。
 *
 * 所以读的是它已经缓存好的那份（每个页面都调过 `useSiteInfo`，客户端挂载时
 * 缓存一定在），拿不到就退到 URL 上的语言，再退到英文——三层都不会抛。
 */
const urlLang = useUrlLang()
const cached = useNuxtData<SiteInfo>(`site-info-${urlLang.value || 'default'}`)
const lang = computed(() => cached.data.value?.lang || urlLang.value || 'en')
const t = useShopCopy(lang)

function shortTime(value: string) {
  // 只到分钟。买家关心的是「刚才还是昨天」，秒没有意义
  return value ? String(value).slice(5, 16) : ''
}

function submit() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  send(text, {
    email: draftEmail.value.trim() || undefined,
    name: draftName.value.trim() || undefined,
    lang: lang.value
  }).then(() => {
    if (!failed.value) draft.value = ''
  })
}

// 新消息进来就滚到底。⚠ 要等 DOM 更新完，否则 scrollHeight 还是旧的
watch(() => messages.value.length, () => {
  nextTick(() => {
    const el = scroller.value
    if (el) el.scrollTop = el.scrollHeight
  })
})

onMounted(mount)
onBeforeUnmount(unmount)
</script>

<style scoped>
.cw-ball {
  position: fixed; right: 20px; bottom: 20px; z-index: 900;
  width: 54px; height: 54px; border-radius: 50%; border: none; cursor: pointer;
  background: var(--primary); color: #fff;
  box-shadow: 0 8px 20px rgba(1, 150, 250, .32);
  display: flex; align-items: center; justify-content: center;
  transition: transform .15s ease, background .15s ease;
}
.cw-ball:hover { transform: translateY(-2px); }
.cw-ball.on { background: #334155; }

.cw-badge {
  position: absolute; top: -2px; right: -2px; min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: 9px; background: #dc2626; color: #fff;
  font-size: 11px; line-height: 18px; text-align: center; font-weight: 600;
}

.cw-panel {
  position: fixed; right: 20px; bottom: 86px; z-index: 900;
  width: 340px; max-height: 70vh; display: flex; flex-direction: column;
  background: #fff; border: 1px solid #e2e8f0; border-radius: 12px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, .18); overflow: hidden;
}

.cw-top {
  display: flex; gap: 8px; align-items: flex-start;
  padding: 14px 14px 10px; border-bottom: 1px solid #f1f5f9;
}
.cw-title { margin: 0; font-size: 15px; font-weight: 700; color: #0f172a; }
.cw-lead { margin: 4px 0 0; font-size: 12px; line-height: 1.5; color: #64748b; }
.cw-x {
  margin-left: auto; border: none; background: none; cursor: pointer;
  font-size: 22px; line-height: 1; color: #94a3b8; padding: 0 2px;
}

.cw-msgs {
  flex: 1; min-height: 140px; overflow-y: auto; padding: 12px 14px;
  display: flex; flex-direction: column; gap: 10px;
}
.cw-hint { margin: auto 0; font-size: 13px; color: #94a3b8; text-align: center; }

.cw-msg { display: flex; flex-direction: column; max-width: 85%; }
.cw-msg.mine { align-self: flex-end; align-items: flex-end; }
.cw-msg.theirs { align-self: flex-start; align-items: flex-start; }
.cw-bubble {
  padding: 8px 11px; border-radius: 12px; font-size: 13px; line-height: 1.55;
  white-space: pre-wrap; word-break: break-word;
  background: #f1f5f9; color: #0f172a;
}
.cw-msg.mine .cw-bubble { background: var(--primary); color: #fff; }
.cw-stamp { font-size: 11px; color: #94a3b8; margin-top: 3px; }

.cw-id { padding: 8px 14px 0; display: flex; flex-wrap: wrap; gap: 6px; }
.cw-input {
  flex: 1 1 120px; min-width: 0; padding: 7px 9px; font-size: 12px;
  border: 1px solid #e2e8f0; border-radius: 8px; color: #0f172a;
}
.cw-input:focus { outline: none; border-color: var(--primary); }
.cw-note { flex: 1 1 100%; margin: 0; font-size: 11px; color: #94a3b8; line-height: 1.4; }

.cw-fail { margin: 8px 14px 0; font-size: 12px; color: #dc2626; }

.cw-compose { display: flex; gap: 8px; align-items: flex-end; padding: 10px 14px 14px; }
.cw-text {
  flex: 1; padding: 8px 10px; font-size: 13px; line-height: 1.5; resize: none;
  border: 1px solid #e2e8f0; border-radius: 8px; font-family: inherit; color: #0f172a;
}
.cw-text:focus { outline: none; border-color: var(--primary); }
.cw-send {
  padding: 9px 14px; border: none; border-radius: 8px; cursor: pointer;
  background: var(--primary); color: #fff; font-size: 13px; font-weight: 600; white-space: nowrap;
}
.cw-send:disabled { background: #cbd5e1; cursor: not-allowed; }

/* 手机上占满宽度。外贸站的移动端流量不低，340px 固定宽在小屏上会顶出边界 */
@media (max-width: 420px) {
  .cw-panel { right: 12px; left: 12px; width: auto; bottom: 80px; max-height: 76vh; }
  .cw-ball { right: 14px; bottom: 14px; }
}
</style>
