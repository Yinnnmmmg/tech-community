<script setup lang="ts">
import { Clock, Megaphone } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, onMounted } from 'vue'

import type { NotifyMessage } from '@/api/types'
import { useNotifyStore } from '@/stores/notifyStore'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const notifyStore = useNotifyStore()

// 合并系统通知和关注通知，按时间倒序
const allMessages = computed<NotifyMessage[]>(() => {
  return [...notifyStore.systemMessages, ...notifyStore.followMessages]
    .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
})

onMounted(async () => {
  // 先标记已读，让导航栏红点立即消失
  notifyStore.markSystemAsRead()
  try {
    await notifyStore.fetchAll()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知加载失败')
  }
})

function formatTime(iso: string) {
  const date = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
</script>

<template>
  <LoadingState v-if="notifyStore.loading" />
  <section v-else class="notify-grid">
    <div class="notify-panel surface">
      <div class="notify-panel__header">
        <div class="notify-panel__icon notify-panel__icon--system">
          <Megaphone :size="20" />
        </div>
        <div class="notify-panel__title-row">
          <h2 class="notify-panel__title">消息中心</h2>
        </div>
      </div>

      <EmptyState v-if="!allMessages.length" title="暂无消息" description="当有新的系统通知或关注动态时，会在这里显示" />

      <div v-else class="message-list">
        <div
          v-for="(item, index) in allMessages"
          :key="index"
          class="message-card"
        >
          <p class="message-card__text">{{ item.msg }}</p>
          <span class="message-card__time">
            <Clock :size="13" />
            {{ formatTime(item.createTime) }}
          </span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.notify-grid {
  max-width: 720px;
  margin: 0 auto;
  height: calc(100vh - 68px - 48px);
}

/* ── Panel ── */
.notify-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  height: 100%;
  overflow: hidden;
}

.notify-panel__header {
  display: flex;
  align-items: center;
  gap: 14px;
}

.notify-panel__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: #fff;
  flex-shrink: 0;
}

.notify-panel__icon--system {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
}

.notify-panel__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notify-panel__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--tc-text-strong);
}

.notify-panel__badge {
  display: grid;
  place-items: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
  font-size: 12px;
  font-weight: 600;
}

/* ── Message cards ── */
.message-list {
  display: grid;
  gap: 2px;
  overflow-y: auto;
  flex: 1;
  align-content: start;
}

.message-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
  transition: background 0.2s ease;
  position: relative;
}

.message-card:hover {
  background: #f8f9fb;
}

.message-card__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--tc-text);
  flex: 1;
}

.message-card__time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--tc-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

</style>
