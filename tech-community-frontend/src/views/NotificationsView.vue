<script setup lang="ts">
import { Bell, Megaphone } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { onMounted } from 'vue'

import { useNotifyStore } from '@/stores/notifyStore'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const notifyStore = useNotifyStore()

onMounted(async () => {
  // 先标记已读，让导航栏红点立即消失
  notifyStore.markSystemAsRead()
  try {
    await notifyStore.fetchAll()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知加载失败')
  }
})
</script>

<template>
  <LoadingState v-if="notifyStore.loading" />
  <section v-else class="notify-grid">
    <!-- 系统通知 -->
    <div class="notify-panel surface">
      <div class="notify-panel__header">
        <div class="notify-panel__icon notify-panel__icon--system">
          <Megaphone :size="20" />
        </div>
        <div class="notify-panel__title-row">
          <h2 class="notify-panel__title">系统通知</h2>
          <span v-if="notifyStore.systemMessages.length" class="notify-panel__badge">
            {{ notifyStore.systemMessages.length }}
          </span>
        </div>
      </div>

      <EmptyState v-if="!notifyStore.systemMessages.length" title="暂无系统通知" description="当有新的系统消息时，会在这里显示" />

      <div v-else class="message-list">
        <div
          v-for="(item, index) in notifyStore.systemMessages"
          :key="index"
          class="message-card message-card--system"
        >
          <p class="message-card__text">{{ item }}</p>
        </div>
      </div>
    </div>

    <!-- 关注通知 -->
    <div class="notify-panel surface">
      <div class="notify-panel__header">
        <div class="notify-panel__icon notify-panel__icon--follow">
          <Bell :size="20" />
        </div>
        <div class="notify-panel__title-row">
          <h2 class="notify-panel__title">关注通知</h2>
          <span v-if="notifyStore.followMessages.length" class="notify-panel__badge">
            {{ notifyStore.followMessages.length }}
          </span>
        </div>
      </div>

      <EmptyState v-if="!notifyStore.followMessages.length" title="暂无关注通知" description="当你关注的作者发布新文章时，会在这里通知你" />

      <div v-else class="message-list">
        <div
          v-for="(item, index) in notifyStore.followMessages"
          :key="index"
          class="message-card message-card--follow"
        >
          <p class="message-card__text">{{ item }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.notify-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

/* ── Panel ── */
.notify-panel {
  display: grid;
  gap: 20px;
  padding: 24px;
  align-content: start;
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

.notify-panel__icon--follow {
  background: linear-gradient(135deg, #f59e0b, #ef4444);
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
}

/* ── Responsive ── */
@media (max-width: 760px) {
  .notify-grid {
    grid-template-columns: 1fr;
  }
}
</style>
