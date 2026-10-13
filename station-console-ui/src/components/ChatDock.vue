<!--
  在线咨询：右下角的悬浮球 + 双栏面板。

  运营在商品页、订单页都能随手回一句，不用先切到某个页面——这是做成悬浮球
  而不是导航页的全部理由。会话真多起来之后要的搜索、筛未读、看归档，
  等到了再加一页（YAGNI，见设计文档 §2.3）。

  ⚠ 挂在 AppShell 里，**不是 App.vue**：/login 那个空页面在 AppShell 之外，
    一个还没登录的人不该看到一个聊天球。

  ⚠ 轮询节奏（设计文档 §2.2）：
      面板开着   3 秒一次增量（带 sinceId）
      面板收起  15 秒一次，只拉未读数
      页面不可见 一律暂停 —— 不加这条，切到别的标签页之后它会整天打服务器，
                            而那种负载在日志里看起来像正常流量，很难查

  设计文档：docs/设计稿/2026-09-04-在线咨询.md
-->
<template>
  <div class="chat-dock">
    <!-- 悬浮球。红点是未读**条数**，不是会话数：写 3 的时候运营期待看到 3 句新话 -->
    <button class="chat-ball" :class="{ on: open }" @click="toggle"
      :title="open ? $t('chat.collapse') : $t('chat.title')">
      <el-icon :size="24"><ChatDotRound /></el-icon>
      <span v-if="unread > 0" class="chat-badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <div v-show="open" class="chat-panel">
      <header class="chat-panel-top">
        <span class="chat-title">{{ $t('chat.title') }}</span>
        <el-input v-model="keyword" size="small" clearable :placeholder="$t('chat.search')"
          class="chat-search" @change="reloadList" @clear="reloadList" />
        <el-icon class="chat-close" @click="toggle"><Close /></el-icon>
      </header>

      <div class="chat-body">
        <!-- 左：会话列表 -->
        <aside class="chat-list" v-loading="listLoading">
          <p v-if="!chats.length" class="chat-empty">{{ $t('chat.noChats') }}</p>
          <button v-for="c in chats" :key="c.chatCode" class="chat-item"
            :class="{ on: c.chatCode === activeCode }" @click="openChat(c)">
            <span class="chat-item-top">
              <span class="chat-who">{{ c.displayName }}</span>
              <span v-if="c.unread > 0" class="chat-dot">{{ c.unread }}</span>
            </span>
            <!-- ⚠ 文本插值，不是 v-html。消息内容是买家写的，见设计文档 §4.5 -->
            <span class="chat-last">{{ c.lastMessage || $t('chat.noMessage') }}</span>
            <span class="chat-time">{{ shortTime(c.lastMessageTime) }}</span>
          </button>
        </aside>

        <!-- 右：对话 -->
        <section class="chat-talk">
          <template v-if="activeCode">
            <div class="chat-meta">
              <span class="chat-meta-who">{{ active.displayName }}</span>
              <el-tag v-if="active.buyerCode" size="small" effect="plain">{{ active.buyerCode }}</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">{{ $t('chat.noEmail') }}</el-tag>
              <!-- 访客用哪种语言开的口。运营得知道用哪国话回 -->
              <el-tag v-if="active.lang" size="small" type="warning" effect="plain">{{ active.lang }}</el-tag>
              <el-link v-if="active.pageUrl" type="primary" :href="active.pageUrl" target="_blank"
                rel="noopener" :underline="false" class="chat-from">{{ $t('chat.onPage') }}</el-link>
            </div>

            <div ref="scroller" class="chat-msgs">
              <div v-for="m in messages" :key="m.id" class="chat-msg"
                :class="m.sender === '运营' ? 'mine' : 'theirs'">
                <span class="chat-bubble">{{ m.content }}</span>
                <span class="chat-stamp">
                  {{ m.sender === '运营' ? (m.senderUser || $t('role.operator')) : active.displayName }}
                  · {{ shortTime(m.createTime) }}
                </span>
              </div>
            </div>

            <div class="chat-input">
              <el-input v-model="draft" v-hasPermi="['site:chat:reply']" type="textarea" :rows="2"
                resize="none" maxlength="2000" :placeholder="$t('chat.inputPlaceholder')"
                @keydown.enter.exact.prevent="send" />
              <el-button v-hasPermi="['site:chat:reply']" type="primary" :loading="sending"
                :disabled="!draft.trim()" @click="send">{{ $t('chat.send') }}</el-button>
            </div>
          </template>
          <p v-else class="chat-empty chat-empty-big">{{ $t('chat.pickOne') }}</p>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup name="ChatDock">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import {
  listSiteChat, getSiteChatUnread, listSiteChatMessages, sendSiteChatMessage, readSiteChat
} from '@/api/console'
import { hasPermi } from '@/store/identity'
import { t } from '@/i18n'

const OPEN_MS = 3000
const IDLE_MS = 15000

const open = ref(false)
const unread = ref(0)
const chats = ref([])
const listLoading = ref(false)
const keyword = ref('')

const activeCode = ref('')
const active = reactive({})
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scroller = ref(null)

/**
 * 手里最大的消息 id。轮询靠它只取增量。
 *
 * ⚠ 换会话时必须清零，否则新会话会从上一条会话的 id 之后开始拉——
 *   表现是「点开另一个人，消息是空的」，而且只在 id 大的那条会话上出现。
 */
let sinceId = 0
let timer = null

const canReply = computed(() => hasPermi(['site:chat:reply']))

// ==================== 轮询 ====================

/**
 * 一次心跳。做什么取决于面板开着没有：
 *   收起 → 只要未读数（一条带索引的 count）
 *   开着 → 列表 + 当前会话的增量
 */
function tick() {
  // ⚠ 页面不可见就什么都不做。见文件头
  if (document.visibilityState === 'hidden') return

  if (!open.value) {
    getSiteChatUnread().then(r => { unread.value = r.data?.unread || 0 }).catch(() => {})
    return
  }
  reloadList(true)
  if (activeCode.value) pullMessages()
}

/** 重排定时器。开合面板、页面重新可见时都要调一次 */
function reschedule() {
  if (timer) clearInterval(timer)
  timer = setInterval(tick, open.value ? OPEN_MS : IDLE_MS)
}

function onVisibility() {
  if (document.visibilityState === 'visible') {
    // 回到这个标签页时立刻补一次，不用等下一个周期——
    // 等的话，切回来的头几秒界面上是过时的
    tick()
    reschedule()
  }
}

// ==================== 列表 ====================

function reloadList(quiet) {
  if (!quiet) listLoading.value = true
  listSiteChat({ name: keyword.value || undefined, pageNum: 1, pageSize: 50 })
    .then(r => {
      chats.value = r.rows || []
      // 面板开着时红点跟着列表走，省一次 /unread
      unread.value = chats.value.reduce((sum, c) => sum + (c.unread || 0), 0)
      if (activeCode.value) {
        const still = chats.value.find(c => c.chatCode === activeCode.value)
        if (still) Object.assign(active, still)
      }
    })
    .catch(() => {})
    .finally(() => { listLoading.value = false })
}

// ==================== 单条会话 ====================

function openChat(c) {
  if (activeCode.value === c.chatCode) return
  activeCode.value = c.chatCode
  Object.assign(active, c)
  messages.value = []
  sinceId = 0            // 见上面那条 ⚠
  draft.value = ''
  pullMessages(true)
}

function pullMessages(first) {
  const code = activeCode.value
  listSiteChatMessages(code, sinceId)
    .then(r => {
      // 拉回来的时候人可能已经点到别的会话了，那这批就不是给这个视图的
      if (activeCode.value !== code) return
      const data = r.data || {}
      const batch = data.messages || []
      if (data.chat) Object.assign(active, data.chat)
      if (batch.length) {
        messages.value = first ? batch : messages.value.concat(batch)
        sinceId = batch[batch.length - 1].id
        scrollDown()
        markRead(code)
      } else if (first) {
        markRead(code)
      }
    })
    .catch(() => {})
}

/** 标已读。⚠ 只在面板开着时调——收起状态下调它，红点就永远不会亮 */
function markRead(code) {
  if (!open.value) return
  readSiteChat(code)
    .then(() => {
      const row = chats.value.find(c => c.chatCode === code)
      if (row) {
        unread.value = Math.max(0, unread.value - (row.unread || 0))
        row.unread = 0
      }
    })
    .catch(() => {})
}

function send() {
  const text = draft.value.trim()
  if (!text || sending.value || !canReply.value) return
  sending.value = true
  sendSiteChatMessage(activeCode.value, text)
    .then(() => {
      draft.value = ''
      // 立刻拉一次，不等下一个 3 秒——自己发的话要马上出现，否则会以为没发出去
      pullMessages()
      reloadList(true)
    })
    .finally(() => { sending.value = false })
}

function scrollDown() {
  nextTick(() => {
    const el = scroller.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function toggle() {
  open.value = !open.value
  reschedule()
  if (open.value) {
    reloadList()
    if (activeCode.value) pullMessages()
  }
}

/** 只显示到分钟。运营看的是「刚才还是昨天」，秒没有意义 */
function shortTime(value) {
  if (!value) return ''
  return String(value).slice(5, 16)
}

onMounted(() => {
  tick()
  reschedule()
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<style scoped>
.chat-ball {
  position: fixed; right: 24px; bottom: 24px; z-index: 2000;
  width: 52px; height: 52px; border-radius: 50%; border: none; cursor: pointer;
  background: var(--el-color-primary); color: #fff;
  box-shadow: 0 6px 18px rgba(0, 0, 0, .18);
  display: flex; align-items: center; justify-content: center;
  transition: transform .15s ease;
}
.chat-ball:hover { transform: translateY(-2px); }
.chat-ball.on { background: var(--el-color-info); }

.chat-badge {
  position: absolute; top: -2px; right: -2px; min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: 9px; background: var(--el-color-danger); color: #fff;
  font-size: 11px; line-height: 18px; text-align: center;
}

.chat-panel {
  position: fixed; right: 24px; bottom: 88px; z-index: 2000;
  width: 720px; height: 480px; display: flex; flex-direction: column;
  background: var(--el-bg-color); border: 1px solid var(--el-border-color-light);
  border-radius: 10px; box-shadow: 0 12px 32px rgba(0, 0, 0, .16); overflow: hidden;
}

.chat-panel-top {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.chat-title { font-weight: 600; font-size: 14px; }
.chat-search { width: 220px; margin-left: auto; }
.chat-close { cursor: pointer; color: var(--el-text-color-secondary); }

.chat-body { flex: 1; display: flex; min-height: 0; }

.chat-list {
  width: 240px; border-right: 1px solid var(--el-border-color-lighter);
  overflow-y: auto; padding: 6px;
}
.chat-item {
  display: block; width: 100%; text-align: left; border: none; background: none;
  padding: 8px; border-radius: 6px; cursor: pointer;
}
.chat-item:hover { background: var(--el-fill-color-light); }
.chat-item.on { background: var(--el-color-primary-light-9); }
.chat-item-top { display: flex; align-items: center; gap: 6px; }
.chat-who { font-size: 13px; font-weight: 600; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-dot {
  min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px;
  background: var(--el-color-danger); color: #fff; font-size: 11px; line-height: 16px; text-align: center;
}
.chat-last {
  display: block; font-size: 12px; color: var(--el-text-color-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px;
}
.chat-time { display: block; font-size: 11px; color: var(--el-text-color-placeholder); margin-top: 2px; }

.chat-talk { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-meta {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter); font-size: 13px;
}
.chat-meta-who { font-weight: 600; }
.chat-from { font-size: 12px; margin-left: auto; }

.chat-msgs { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.chat-msg { display: flex; flex-direction: column; max-width: 78%; }
.chat-msg.theirs { align-self: flex-start; align-items: flex-start; }
.chat-msg.mine { align-self: flex-end; align-items: flex-end; }
.chat-bubble {
  padding: 7px 11px; border-radius: 10px; font-size: 13px; line-height: 1.5;
  white-space: pre-wrap; word-break: break-word;
  background: var(--el-fill-color-light);
}
.chat-msg.mine .chat-bubble { background: var(--el-color-primary); color: #fff; }
.chat-stamp { font-size: 11px; color: var(--el-text-color-placeholder); margin-top: 3px; }

.chat-input {
  display: flex; gap: 8px; align-items: flex-end; padding: 10px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.chat-input :deep(.el-textarea) { flex: 1; }

.chat-empty { color: var(--el-text-color-placeholder); font-size: 13px; text-align: center; padding: 20px 8px; }
.chat-empty-big { margin: auto; }

/* 窄屏（笔记本外接小屏、缩小的窗口）时面板占满可用宽度，否则会被推出屏幕 */
@media (max-width: 820px) {
  .chat-panel { right: 12px; left: 12px; width: auto; }
  .chat-list { width: 160px; }
}
</style>
