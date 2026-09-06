<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Delete, Promotion, RefreshRight, VideoPause, User } from '@element-plus/icons-vue'
import { useSSE } from '@/composables/useSSE'
import { useUserStore } from '@/stores/user'
import {
  createSession,
  deleteSession,
  hotQuestions,
  sessionDetail,
  sessionHistory,
  stopChat,
} from '@/api/ai'
import ChatMessageItem from '@/components/ai/ChatMessageItem.vue'
import type { ChatMessage, ChatSession } from '@/types/api'

const userStore = useUserStore()
const { streaming, start, stop } = useSSE()

/* ---------- 会话管理 ---------- */
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<string>('')
const hotList = ref<string[]>([])
const loadingSessions = ref(true)

async function fetchSessions() {
  loadingSessions.value = true
  try {
    sessions.value = await sessionHistory()
  } catch {
    /* ignore */
  } finally {
    loadingSessions.value = false
  }
}

async function newSession() {
  try {
    const s = await createSession()
    sessions.value.unshift(s)
    currentSessionId.value = s.id
    messages.value = []
    input.value = ''
  } catch {
    /* ignore */
  }
}

async function switchSession(id: string) {
  if (streaming.value) return
  currentSessionId.value = id
  messages.value = []
  try {
    const history = await sessionDetail(id)
    messages.value = history.map((m) => ({ ...m }))
    await scrollToBottom(true)
  } catch {
    /* ignore */
  }
}

async function onRefreshSessions() {
  await fetchSessions()
  if (currentSessionId.value) await switchSession(currentSessionId.value)
}

async function removeSession(id: string) {
  await ElMessageBox.confirm('删除后不可恢复，确定删除该会话吗？', '删除会话', { type: 'warning' }).catch(() => null)
  try {
    await deleteSession(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = ''
      messages.value = []
    }
    ElMessage.success('会话已删除')
  } catch {
    /* ignore */
  }
}

/* ---------- 消息流 ---------- */
interface LocalMessage extends ChatMessage {
  pending?: boolean
}
const messages = ref<LocalMessage[]>([])
const input = ref('')
const textareaRef = ref<HTMLTextAreaElement>()
const scrollRef = ref<HTMLElement>()

async function scrollToBottom(instant = false) {
  await nextTick()
  scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight, behavior: instant ? 'auto' : 'smooth' })
}

/** 发送：Enter 发送 / Shift+Enter 换行 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

async function send() {
  const question = input.value.trim()
  if (!question || streaming.value) return
  if (!currentSessionId.value) {
    await newSession()
  }
  input.value = ''
  const sessionId = currentSessionId.value
  messages.value.push({ type: 'USER', content: question })
  const aiMsg = reactive<LocalMessage>({ type: 'AI', content: '', pending: true })
  messages.value.push(aiMsg)
  await scrollToBottom()

  const sessionForApi = sessionId || 'default'

  await start({
    sessionId: sessionForApi,
    question,
    onEvent: (ev) => {
      if (ev.type === 'START') {
        aiMsg.agent = ev.agent || aiMsg.agent
      } else if (ev.type === 'DELTA') {
        aiMsg.content += ev.content
        scrollToBottom()
      } else if (ev.type === 'END') {
        aiMsg.pending = false
        // 里程碑：有回复后用问题摘要命名新会话
        const s = sessions.value.find((x) => x.id === sessionId)
        if (s && (!s.title || s.title === '新会话')) {
          s.title = question.slice(0, 16)
        }
        scrollToBottom()
      }
    },
    onDone: () => {
      aiMsg.pending = false
      scrollToBottom()
    },
    onError: () => {
      aiMsg.pending = false
      if (!aiMsg.content) {
        aiMsg.content = '_连接中断，请重试。_'
      }
      scrollToBottom()
    },
  })
}

/** 中断生成（本地 Abort + 通知后端停止） */
function onAbort() {
  stop()
  stopChat(currentSessionId.value || 'default').catch(() => undefined)
  const last = messages.value[messages.value.length - 1]
  if (last && last.type === 'AI') last.pending = false
  ElMessage.info('已停止生成')
}

/* ---------- 输入框自适应高度 ---------- */
function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

onMounted(async () => {
  await fetchSessions()
  try {
    hotList.value = (await hotQuestions()) ?? []
  } catch {
    /* ignore */
  }
  if (sessions.value.length) {
    await switchSession(sessions.value[0].id)
  } else {
    await newSession()
  }
})

onBeforeUnmount(() => {
  if (streaming.value) stop()
})
</script>

<template>
  <div class="zx-page">
    <div class="zx-card zx-assistant flex overflow-hidden" style="height: calc(100vh - 220px); min-height: 520px">
      <!-- 左侧：历史会话 -->
      <aside class="zx-sider hidden w-64 shrink-0 flex-col md:flex">
        <div class="p-3">
          <el-button type="primary" round class="w-full" :icon="ChatDotRound" @click="newSession">
            新建会话
          </el-button>
        </div>
        <div class="zx-sider__label px-4 pb-1 text-xs">历史会话（Redis 会话记忆）</div>
        <div class="zx-sider__list flex-1 space-y-1 overflow-y-auto px-2 pb-3">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="zx-session-item group"
            :class="{ 'zx-session-item--active': s.id === currentSessionId }"
            @click="switchSession(s.id)"
          >
            <el-icon class="shrink-0"><ChatDotRound /></el-icon>
            <span class="flex-1 truncate">{{ s.title || '新会话' }}</span>
            <el-icon
              class="zx-session-delete opacity-0 transition-opacity group-hover:opacity-100"
              @click.stop="removeSession(s.id)"
            >
              <Delete />
            </el-icon>
          </div>
          <div v-if="!loadingSessions && !sessions.length" class="zx-text-secondary px-3 py-6 text-center text-xs">
            暂无历史会话
          </div>
        </div>

        <!-- 热门问题 -->
        <div v-if="hotList.length" class="zx-sider__label border-t px-4 py-3 text-xs" style="border-color: var(--zx-border)">
          试试问我
        </div>
        <div class="space-y-1 overflow-y-auto px-2 pb-3" style="max-height: 160px">
          <div
            v-for="q in hotList"
            :key="q"
            class="zx-hot-item"
            @click="input = q; textareaRef?.focus()"
          >
            {{ q }}
          </div>
        </div>
      </aside>

      <!-- 右侧：消息流 + 输入区 -->
      <section class="flex min-w-0 flex-1 flex-col">
        <!-- 顶栏 -->
        <div class="zx-chat-header flex items-center gap-3 border-b px-5 py-3" style="border-color: var(--zx-border)">
          <div class="zx-ai-avatar flex h-9 w-9 items-center justify-center rounded-xl font-bold">AI</div>
          <div>
            <div class="font-semibold">知行 AI 智能助教</div>
            <div class="zx-text-secondary text-xs">
              {{ streaming ? '正在输入…' : '课程推荐 · 技术答疑 · 学习规划' }}
            </div>
          </div>
          <el-button
            class="ml-auto"
            text
            :icon="RefreshRight"
            :disabled="streaming"
            @click="onRefreshSessions"
          >
            刷新
          </el-button>
        </div>

        <!-- 消息区 -->
        <div ref="scrollRef" class="zx-msg-list flex-1 space-y-5 overflow-y-auto px-5 py-6">
          <!-- 欢迎语 -->
          <div v-if="!messages.length" class="flex h-full flex-col items-center justify-center text-center">
            <div class="zx-ai-avatar zx-welcome-avatar flex items-center justify-center rounded-3xl text-3xl font-bold">
              AI
            </div>
            <h2 class="mt-5 text-xl font-bold">你好，{{ userStore.username || '同学' }}！我是知行 AI 助教</h2>
            <p class="zx-text-secondary mt-2 max-w-md text-sm leading-6">
              我可以为你推荐课程、解答技术问题、制定学习计划。下方是热门问题，点击即可开始对话。
            </p>
            <div class="mt-6 flex flex-wrap justify-center gap-2">
              <el-tag
                v-for="q in hotList"
                :key="q"
                effect="plain"
                round
                class="cursor-pointer"
                @click="input = q; send()"
              >
                {{ q }}
              </el-tag>
            </div>
          </div>

          <ChatMessageItem
            v-for="(m, i) in messages"
            :key="i"
            :message="m"
            :streaming="m.pending && m.type === 'AI'"
          />
        </div>

        <!-- 输入区 -->
        <div class="zx-input-area border-t p-4" style="border-color: var(--zx-border)">
          <div class="zx-input-box flex items-end gap-2 rounded-2xl p-2" :class="{ 'is-streaming': streaming }">
            <el-icon class="mb-2 ml-2 text-primary"><User /></el-icon>
            <textarea
              ref="textareaRef"
              v-model="input"
              rows="1"
              class="zx-textarea"
              placeholder="输入你的问题…（Enter 发送，Shift+Enter 换行）"
              :disabled="streaming"
              @keydown="onKeydown"
              @input="autoResize"
            />
            <el-button
              v-if="!streaming"
              type="primary"
              :icon="Promotion"
              class="mb-0.5"
              :disabled="!input.trim()"
              @click="send"
            >
              发送
            </el-button>
            <el-button v-else type="danger" plain :icon="VideoPause" class="mb-0.5" @click="onAbort">
              停止
            </el-button>
          </div>
          <div class="zx-text-secondary mt-2 flex justify-between px-1 text-xs">
            <span>回答由 AI 生成，课程推荐卡片可点击直达详情</span>
            <span>SSE 流式输出 · 断线自动重连</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.zx-assistant {
  background: var(--zx-bg);
}
.zx-sider {
  background: var(--zx-bg-card);
  border-right: 1px solid var(--zx-border);
}
.zx-sider__label {
  color: var(--zx-text-secondary);
  font-weight: 600;
}
.zx-session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--zx-text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}
.zx-session-item:hover {
  background: var(--zx-primary-bg);
  color: var(--zx-primary);
}
.zx-session-item--active {
  background: var(--zx-primary-bg);
  color: var(--zx-primary);
  font-weight: 600;
}
.zx-session-delete:hover {
  color: #ef4444;
}
.zx-hot-item {
  padding: 7px 10px;
  border-radius: 8px;
  font-size: 12px;
  color: var(--zx-text-secondary);
  cursor: pointer;
  border: 1px dashed var(--zx-border);
  transition: all 0.15s;
}
.zx-hot-item:hover {
  color: var(--zx-primary);
  border-color: var(--zx-primary);
}
.zx-msg-list {
  background:
    radial-gradient(600px 200px at 80% 0%, rgba(99, 102, 241, 0.05), transparent);
}
.zx-welcome-avatar {
  width: 72px;
  height: 72px;
  animation: zx-float 3s ease-in-out infinite;
}
@keyframes zx-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}
.zx-input-box {
  background: var(--zx-bg-card);
  border: 1px solid var(--zx-border);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.zx-input-box:focus-within {
  border-color: var(--zx-primary);
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);
}
.zx-textarea {
  flex: 1;
  resize: none;
  border: none;
  outline: none;
  background: transparent;
  color: var(--zx-text);
  font-size: 14px;
  line-height: 1.6;
  padding: 8px 4px;
  max-height: 160px;
}
.zx-textarea::placeholder {
  color: var(--zx-text-secondary);
}
.zx-textarea:disabled {
  opacity: 0.6;
}
</style>
