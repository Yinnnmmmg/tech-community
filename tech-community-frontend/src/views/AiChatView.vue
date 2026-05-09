<script setup lang="ts">
import { Bot, Send, StopCircle, UserRound } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'

import { streamChat } from '@/api/ai'
import type { Reference } from '@/api/types'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  references?: Reference[]
}

const question = ref('')
const sending = ref(false)
const messages = ref<ChatMessage[]>([])
const controller = ref<AbortController | null>(null)
const sessionId = computed(() => getSessionId())

async function sendQuestion() {
  const text = question.value.trim()
  if (!text || sending.value) {
    return
  }

  question.value = ''
  messages.value.push({ role: 'user', content: text })
  const assistantMessage: ChatMessage = { role: 'assistant', content: '' }
  messages.value.push(assistantMessage)

  controller.value = new AbortController()
  sending.value = true
  try {
    await streamChat(
      { sessionId: sessionId.value, question: text },
      (chunk) => {
        if (chunk.errorCode) {
          throw new Error(chunk.errorMessage || 'AI 返回异常')
        }
        if (chunk.content) {
          assistantMessage.content += chunk.content
        }
        if (chunk.references?.length) {
          assistantMessage.references = chunk.references
        }
      },
      controller.value.signal
    )
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      ElMessage.error(error instanceof Error ? error.message : 'AI 对话失败')
    }
  } finally {
    sending.value = false
    controller.value = null
  }
}

function stopStream() {
  controller.value?.abort()
}

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
        <p>围绕社区文章提问，实时获取流式回答。</p>
      </div>
      <Bot :size="26" />
    </div>

    <div class="chat__messages">
      <div v-if="!messages.length" class="chat__empty">
        <Bot :size="38" />
        <span>等待提问</span>
      </div>
      <article
        v-for="(message, index) in messages"
        :key="index"
        class="chat-message"
        :class="`chat-message--${message.role}`"
      >
        <div class="chat-message__icon">
          <UserRound v-if="message.role === 'user'" :size="18" />
          <Bot v-else :size="18" />
        </div>
        <div class="chat-message__body">
          <p>{{ message.content || (sending && index === messages.length - 1 ? '...' : '') }}</p>
          <div v-if="message.references?.length" class="reference-list">
            <RouterLink
              v-for="reference in message.references"
              :key="reference.articleId"
              :to="{ name: 'article-detail', params: { id: reference.articleId } }"
            >
              {{ reference.title }}
            </RouterLink>
          </div>
        </div>
      </article>
    </div>

    <div class="chat__input">
      <el-input
        v-model="question"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        maxlength="1000"
        show-word-limit
        placeholder="向 AI 提问"
        @keyup.ctrl.enter="sendQuestion"
      />
      <div class="split-actions">
        <el-button v-if="sending" class="icon-button" @click="stopStream">
          <StopCircle :size="16" />
          <span>停止</span>
        </el-button>
        <el-button type="primary" class="icon-button" :loading="sending" @click="sendQuestion">
          <Send :size="16" />
          <span>发送</span>
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat {
  display: grid;
  grid-template-rows: auto minmax(360px, 1fr) auto;
  gap: 16px;
  min-height: calc(100vh - 150px);
  padding: 20px;
}

.chat__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.chat__head p {
  margin: 8px 0 0;
  color: var(--tc-text-muted);
  line-height: 1.6;
}

.chat__messages {
  display: grid;
  align-content: start;
  gap: 14px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--tc-border);
  border-radius: 4px;
  background: #f5f7fa;
}

.chat__empty {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 280px;
  color: var(--tc-text-muted);
}

.chat-message {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  max-width: 820px;
}

.chat-message--user {
  justify-self: end;
}

.chat-message__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--tc-border);
  border-radius: 4px;
  background: #ffffff;
  color: var(--tc-brand);
}

.chat-message__body {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--tc-border);
  border-radius: 4px;
  background: #ffffff;
}

.chat-message--user .chat-message__body {
  border-color: #ffd2ad;
  background: #fff7ed;
}

.chat-message__body p {
  margin: 0;
  line-height: 1.75;
  white-space: pre-wrap;
}

.reference-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.reference-list a {
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
  font-size: 13px;
}

.chat__input {
  display: grid;
  gap: 10px;
}
</style>
