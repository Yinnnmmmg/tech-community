<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="empty">
    <div class="empty__icon">
      <div class="empty__ring"></div>
      <Inbox :size="28" />
    </div>
    <strong>{{ title }}</strong>
    <span v-if="description" class="empty__desc">{{ description }}</span>
    <slot name="action" />
  </div>
</template>

<style scoped>
.empty {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 48px 20px;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: var(--tc-shadow-xs);
  color: var(--tc-text-muted);
  text-align: center;
}

.empty__icon {
  position: relative;
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  margin-bottom: 4px;
  color: var(--tc-brand);
}

.empty__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px dashed var(--tc-border);
  background: var(--tc-brand-soft);
  opacity: 0.6;
}

.empty strong {
  color: var(--tc-text-strong);
  font-size: 16px;
}

.empty__desc {
  max-width: 280px;
  line-height: 1.7;
}
</style>
