<script setup lang="ts">
import { Bell, UserRoundPlus } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'

import { getMyFollowNotify, getMySystemNotify } from '@/api/notify'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const loading = ref(false)
const systemMessages = ref<string[]>([])
const followMessages = ref<string[]>([])

onMounted(loadMessages)

async function loadMessages() {
  loading.value = true
  try {
    const [system, follow] = await Promise.all([getMySystemNotify(), getMyFollowNotify()])
    systemMessages.value = system ?? []
    followMessages.value = follow ?? []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知加载失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <LoadingState v-if="loading" />
  <section v-else class="notify-grid">
    <div class="notify-panel surface">
      <div class="toolbar">
        <h1 class="page-title">系统通知</h1>
        <Bell :size="22" />
      </div>
      <EmptyState v-if="!systemMessages.length" title="暂无系统通知" />
      <div v-else class="message-list">
        <p v-for="(item, index) in systemMessages" :key="index">{{ item }}</p>
      </div>
    </div>

    <div class="notify-panel surface">
      <div class="toolbar">
        <h1 class="page-title">关注通知</h1>
        <UserRoundPlus :size="22" />
      </div>
      <EmptyState v-if="!followMessages.length" title="暂无关注通知" />
      <div v-else class="message-list">
        <p v-for="(item, index) in followMessages" :key="index">{{ item }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.notify-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.notify-panel {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.message-list {
  display: grid;
  gap: 10px;
}

.message-list p {
  margin: 0;
  padding: 12px;
  border: 1px solid var(--tc-border);
  border-radius: 4px;
  background: #f7f8fa;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .notify-grid {
    grid-template-columns: 1fr;
  }
}
</style>
