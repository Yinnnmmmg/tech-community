<script setup lang="ts">
import { Bot, Copy, Plus, Send, Sparkles, StopCircle, UserRound } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

import { streamChat } from '@/api/ai'
import type { Reference } from '@/api/types'
import { copyToClipboard } from '@/utils/clipboard'
import { renderMarkdown } from '@/utils/markdown'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  displayedContent?: string
  references?: Reference[]
  timestamp: number
}

const suggestedQuestions = [
  '最近有什么热门文章？',
  'Java 如何入门学习？',
  '请推荐几篇前端技术文章',
  'AI 领域的核心技术有哪些？',
]

const STORAGE_KEY = 'tech-community-ai-messages'

function loadMessages(): ChatMessage[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as ChatMessage[]
      return parsed.map((m) => ({ ...m, displayedContent: m.content }))
    }
  } catch { /* corrupted data, ignore */ }
  return []
}

const question = ref('')
const sending = ref(false)
const messages = ref<ChatMessage[]>(loadMessages())
const controller = ref<AbortController | null>(null)
const typewriterTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const TYPING_NORMAL_MS = 30
const TYPING_FAST_MS = 8
const sessionId = computed(() => getSessionId())
const messagesContainer = ref<HTMLElement | null>(null)
const showScrollButton = ref(false)

function getLastAiMessage(): ChatMessage | undefined {
  const last = messages.value[messages.value.length - 1]
  return last?.role === 'assistant' ? last : undefined
}

async function sendQuestion() {
  const text = question.value.trim()
  if (!text || sending.value) {
    return
  }

  question.value = ''
  messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
  messages.value.push({ role: 'assistant', content: '', displayedContent: undefined, timestamp: Date.now() })

  controller.value = new AbortController()
  sending.value = true
  try {
    await streamChat(
      { sessionId: sessionId.value, question: text },
      (chunk) => {
        if (chunk.errorCode) {
          throw new Error(chunk.errorMessage || 'AI 返回异常')
        }
        const last = getLastAiMessage()
        if (!last) return
        if (chunk.content) {
          last.content += chunk.content
          last.timestamp = Date.now()
          startTypewriter()
        }
        if (chunk.references?.length) {
          last.references = chunk.references
        }
      },
      controller.value.signal
    )
  } catch (error) {
    stopTypewriter(true)
    if ((error as Error).name !== 'AbortError') {
      ElMessage.error(error instanceof Error ? error.message : 'AI 对话失败')
    }
  } finally {
    sending.value = false
    controller.value = null
  }
}

function stopStream() {
  const last = getLastAiMessage()
  if (last) {
    last.displayedContent = last.content
  }
  if (typewriterTimer.value) {
    clearTimeout(typewriterTimer.value)
    typewriterTimer.value = null
  }
  controller.value?.abort()
}

function startTypewriter() {
  if (typewriterTimer.value) return
  typewriterTick()
}

function typewriterTick() {
  const last = getLastAiMessage()
  if (!last) {
    typewriterTimer.value = null
    return
  }
  const displayed = last.displayedContent ?? ''
  if (displayed.length >= last.content.length) {
    typewriterTimer.value = null
    return
  }
  const speed = sending.value ? TYPING_NORMAL_MS : TYPING_FAST_MS
  last.displayedContent = last.content.slice(0, displayed.length + 1)
  typewriterTimer.value = setTimeout(() => typewriterTick(), speed)
}

function stopTypewriter(flush = false) {
  if (typewriterTimer.value) {
    clearTimeout(typewriterTimer.value)
    typewriterTimer.value = null
  }
  if (flush) {
    const last = getLastAiMessage()
    if (last) {
      last.displayedContent = last.content
    }
  }
}

function isTypewriterActiveFor(index: number): boolean {
  if (index !== messages.value.length - 1) return false
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'assistant') return false
  const displayed = last.displayedContent ?? ''
  return displayed.length < last.content.length
}

function clearMessages() {
  messages.value = []
  localStorage.removeItem(STORAGE_KEY)
}

async function scrollToBottom(smooth = true) {
  await nextTick()
  const el = messagesContainer.value
  if (el) {
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'instant' })
  }
}

function onScroll() {
  const el = messagesContainer.value
  if (!el) return
  showScrollButton.value = el.scrollHeight - el.scrollTop - el.clientHeight > 120
}

function clickSuggestedQuestion(q: string) {
  question.value = q
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

watch(
  () => messages.value.length,
  () => scrollToBottom()
)

watch(
  () => {
    const last = messages.value[messages.value.length - 1]
    return last ? (last.displayedContent ?? last.content) : ''
  },
  () => scrollToBottom(false)
)

watch(
  messages,
  (val) => {
    const slim = val.map(({ displayedContent: _, ...rest }) => rest)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(slim))
  },
  { deep: true }
)

onUnmounted(() => {
  stopTypewriter()
})

function getSessionId() {
  const key = 'tech-community-ai-session'
  const existing = sessionStorage.getItem(key)
  if (existing) {
    return existing
  }
  const id = globalThis.crypto?.randomUUID?.() ?? `session-${Date.now()}`
  sessionStorage.setItem(key, id)
  return id
}
</script>

<template>
  <section class="chat surface">
    <div class="chat__head">
      <div>
        <h1 class="page-title">AI 问答</h1>
      </div>
      <div class="chat__head-actions">
        <el-button
          v-if="messages.length"
          class="icon-button"
          @click="clearMessages"
        >
          <Plus :size="16" />
          <span>新对话</span>
        </el-button>
        <div class="chat__head-icon">
          <Bot :size="26" />
        </div>
      </div>
    </div>

    <div
      ref="messagesContainer"
      class="chat__messages"
      @scroll="onScroll"
    >
      <!-- Welcome / Empty state -->
      <div v-if="!messages.length" class="chat__welcome">
        <div class="chat__welcome-icon">
          <Sparkles :size="36" />
        </div>
        <h2 class="chat__welcome-title">AI 问答助手</h2>
        <p class="chat__welcome-sub">
          我可以根据社区文章内容回答你的问题，试试下面的问题吧
        </p>
        <div class="chat__suggestions">
          <button
            v-for="q in suggestedQuestions"
            :key="q"
            class="chat__suggestion-chip"
            @click="clickSuggestedQuestion(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <!-- Messages -->
      <article
        v-for="(message, index) in messages"
        :key="index"
        class="chat-message"
        :class="`chat-message--${message.role}`"
      >
        <!-- User message -->
        <template v-if="message.role === 'user'">
          <div class="chat-message__bubble chat-message__bubble--user">
            <span class="chat-message__text">{{ message.content }}</span>
            <span class="chat-message__time">{{ formatTime(message.timestamp) }}</span>
          </div>
        </template>

        <!-- AI message -->
        <template v-else>
          <div class="chat-message__avatar">
            <Bot :size="20" />
          </div>
          <div class="chat-message__main">
            <div class="chat-message__bubble chat-message__bubble--ai">
              <button
                v-if="message.content"
                class="chat-message__copy"
                title="复制"
                @click="copyToClipboard(message.content)"
              >
                <Copy :size="14" />
              </button>
              <!-- Stream loading dots -->
              <div
                v-if="(!message.displayedContent || message.displayedContent.length === 0) && sending && index === messages.length - 1"
                class="chat-message__typing"
              >
                <span class="typing-dot" />
                <span class="typing-dot" />
                <span class="typing-dot" />
              </div>
              <!-- Markdown content -->
              <div
                v-else
                class="chat-message__markdown"
                v-html="renderMarkdown(message.displayedContent ?? message.content)"
              />
              <!-- Typewriter cursor -->
              <span
                v-if="isTypewriterActiveFor(index)"
                class="typewriter-cursor"
              />
              <span class="chat-message__time">{{ formatTime(message.timestamp) }}</span>
            </div>
            <!-- References -->
            <div v-if="message.references?.length" class="chat-message__refs">
              <span class="chat-message__refs-label">参考文章</span>
              <div class="chat-message__ref-cards">
                <RouterLink
                  v-for="ref in message.references"
                  :key="ref.articleId"
                  :to="{ name: 'article-detail', params: { id: ref.articleId } }"
                  class="chat-message__ref-card"
                >
                  {{ ref.title }}
                </RouterLink>
              </div>
            </div>
          </div>
        </template>
      </article>
    </div>

    <!-- Scroll to bottom -->
    <transition name="fade">
      <button
        v-if="showScrollButton"
        class="chat__scroll-btn"
        @click="scrollToBottom(true)"
      >
        &#8595;
      </button>
    </transition>

    <!-- Input area -->
    <div class="chat__input">
      <el-input
        v-model="question"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        maxlength="1000"
        show-word-limit
        placeholder="向 AI 提问，Shift+Enter 换行"
        class="chat__textarea"
        @keydown.enter.exact.prevent="sendQuestion"
      />
      <div class="chat__input-actions">
        <div class="chat__input-btns">
          <el-button
            v-if="sending"
            plain
            size="small"
            @click="stopStream"
          >
            <StopCircle :size="14" />
            <span>停止</span>
          </el-button>
          <el-button
            type="primary"
            size="large"
            :loading="sending"
            :disabled="!question.trim()"
            @click="sendQuestion"
          >
            <Send :size="18" />
            <span>发送</span>
          </el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* ── Layout ── */
.chat {
  display: grid;
  grid-template-rows: auto minmax(360px, 1fr) auto;
  gap: 20px;
  min-height: calc(100vh - 150px);
  padding: 24px;
  position: relative;
}

.chat__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.chat__head p {
  margin: 6px 0 0;
  color: var(--tc-text-muted);
  font-size: 14px;
}

.chat__head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.chat__head-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--tc-brand), #f0873a);
  color: #fff;
}

/* ── Messages container ── */
.chat__messages {
  display: grid;
  align-content: start;
  gap: 20px;
  overflow: auto;
  padding: 20px;
  border: 1px solid var(--tc-border);
  border-radius: 12px;
  background: #f5f7fa;
  scroll-behavior: smooth;
}

/* ── Welcome ── */
.chat__welcome {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  min-height: 320px;
  padding: 40px 20px;
  text-align: center;
}

.chat__welcome-icon {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--tc-brand), #f0873a);
  color: #fff;
  margin-bottom: 8px;
}

.chat__welcome-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--tc-text-strong);
}

.chat__welcome-sub {
  margin: 0;
  color: var(--tc-text-muted);
  font-size: 14px;
  max-width: 360px;
  line-height: 1.6;
}

.chat__suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 8px;
}

.chat__suggestion-chip {
  padding: 8px 16px;
  border: 1px solid var(--tc-border);
  border-radius: 20px;
  background: #fff;
  color: var(--tc-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.chat__suggestion-chip:hover {
  border-color: var(--tc-brand);
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

/* ── Message bubbles ── */
.chat-message {
  display: flex;
  gap: 10px;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message__avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--tc-brand), #f0873a);
  color: #fff;
}

.chat-message__main {
  display: grid;
  gap: 10px;
  min-width: 0;
  max-width: 720px;
}

.chat-message__bubble {
  position: relative;
  display: grid;
  gap: 6px;
  padding: 14px 18px;
  border-radius: 16px;
  line-height: 1.7;
  font-size: 14px;
}

.chat-message__bubble--user {
  max-width: 560px;
  border-bottom-right-radius: 6px;
  background: linear-gradient(135deg, var(--tc-brand), #f07b2e);
  color: #fff;
}

.chat-message__bubble--ai {
  border-bottom-left-radius: 6px;
  background: #fff;
  box-shadow: var(--tc-shadow-xs);
  border: 1px solid var(--tc-border);
}

.chat-message__text {
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-message__time {
  font-size: 11px;
  opacity: 0.55;
  justify-self: end;
}

.chat-message--user .chat-message__time {
  justify-self: start;
}

/* ── Copy button ── */
.chat-message__copy {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--tc-border);
  border-radius: 6px;
  background: #fff;
  color: var(--tc-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, color 0.2s ease;
  z-index: 2;
}

.chat-message__bubble--ai:hover .chat-message__copy {
  opacity: 1;
}

.chat-message__copy:hover {
  color: var(--tc-brand);
  border-color: var(--tc-brand);
}

/* ── Typing dots ── */
.chat-message__typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 0;
}

.typing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tc-text-muted);
  animation: typing-bounce 1.4s infinite ease-in-out both;
}

.typing-dot:nth-child(1) { animation-delay: -0.32s; }
.typing-dot:nth-child(2) { animation-delay: -0.16s; }
.typing-dot:nth-child(3) { animation-delay: 0s; }

@keyframes typing-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ── Typewriter cursor ── */
.typewriter-cursor {
  display: inline-block;
  width: 2px;
  height: 1.1em;
  margin-left: 2px;
  background: var(--tc-brand);
  animation: cursor-blink 1s step-end infinite;
  vertical-align: text-bottom;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ── Markdown ── */
.chat-message__markdown :deep(p) {
  margin: 0 0 8px;
}

.chat-message__markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.chat-message__markdown :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f3f4f6;
  font-size: 13px;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
}

.chat-message__markdown :deep(pre) {
  margin: 10px 0;
  padding: 14px 16px;
  border-radius: 8px;
  background: #1e1e2e;
  overflow-x: auto;
}

.chat-message__markdown :deep(pre code) {
  padding: 0;
  background: none;
  color: #cdd6f4;
  font-size: 13px;
}

.chat-message__markdown :deep(ul),
.chat-message__markdown :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}

.chat-message__markdown :deep(li) {
  margin: 3px 0;
}

.chat-message__markdown :deep(strong) {
  font-weight: 600;
  color: var(--tc-text-strong);
}

.chat-message__markdown :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 14px;
  border-left: 3px solid var(--tc-brand);
  background: var(--tc-brand-soft);
  border-radius: 0 6px 6px 0;
  color: var(--tc-text-muted);
}

.chat-message__markdown :deep(h1),
.chat-message__markdown :deep(h2),
.chat-message__markdown :deep(h3) {
  margin: 12px 0 6px;
  color: var(--tc-text-strong);
}

.chat-message__markdown :deep(h1) { font-size: 18px; }
.chat-message__markdown :deep(h2) { font-size: 16px; }
.chat-message__markdown :deep(h3) { font-size: 15px; }

.chat-message__markdown :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.chat-message__markdown :deep(th),
.chat-message__markdown :deep(td) {
  border: 1px solid var(--tc-border);
  padding: 6px 10px;
  text-align: left;
  font-size: 13px;
}

.chat-message__markdown :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}

.chat-message__markdown :deep(a) {
  color: var(--tc-brand);
}

/* ── References ── */
.chat-message__refs {
  display: grid;
  gap: 8px;
}

.chat-message__refs-label {
  font-size: 12px;
  color: var(--tc-text-muted);
  font-weight: 500;
}

.chat-message__ref-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chat-message__ref-card {
  padding: 8px 14px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--tc-border);
  color: var(--tc-text);
  font-size: 13px;
  text-decoration: none;
  transition: all 0.2s ease;
  box-shadow: var(--tc-shadow-xs);
}

.chat-message__ref-card:hover {
  border-color: var(--tc-brand);
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

/* ── Scroll to bottom ── */
.chat__scroll-btn {
  position: absolute;
  bottom: 140px;
  right: 48px;
  width: 36px;
  height: 36px;
  border: 1px solid var(--tc-border);
  border-radius: 50%;
  background: #fff;
  box-shadow: var(--tc-shadow-sm);
  font-size: 18px;
  cursor: pointer;
  z-index: 4;
  display: grid;
  place-items: center;
  color: var(--tc-text-muted);
  transition: all 0.2s ease;
}

.chat__scroll-btn:hover {
  color: var(--tc-brand);
  border-color: var(--tc-brand);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ── Input ── */
.chat__input {
  display: grid;
  gap: 8px;
}

.chat__textarea :deep(.el-textarea__inner) {
  border-radius: 12px;
  border-color: var(--tc-border);
  font-size: 14px;
  line-height: 1.6;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  resize: none;
}

.chat__textarea :deep(.el-textarea__inner:focus) {
  border-color: var(--tc-brand);
  box-shadow: var(--tc-focus-ring);
}

.chat__input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chat__input-hint {
  font-size: 12px;
  color: var(--tc-text-muted);
}

.chat__input-btns {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
