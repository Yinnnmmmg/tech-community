<script setup lang="ts">
import { ArrowUp } from 'lucide-vue-next'
import { onMounted, onUnmounted, ref } from 'vue'

const visible = ref(false)

function onScroll() {
  visible.value = window.scrollY > 400
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <Transition name="btop">
    <button
      v-show="visible"
      class="back-to-top"
      aria-label="回到顶部"
      title="回到顶部"
      @click="scrollToTop"
    >
      <ArrowUp :size="20" />
    </button>
  </Transition>
</template>

<style scoped>
.back-to-top {
  position: fixed;
  right: 28px;
  bottom: 32px;
  z-index: 40;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 0;
  border-radius: 50%;
  background: var(--tc-panel);
  color: var(--tc-text);
  box-shadow: var(--tc-shadow-md);
  cursor: pointer;
  transition:
    transform var(--tc-duration) var(--tc-ease),
    box-shadow var(--tc-duration) var(--tc-ease),
    color var(--tc-duration) var(--tc-ease);
}

.back-to-top:hover {
  color: var(--tc-brand);
  box-shadow: var(--tc-shadow-lg);
  transform: translateY(-2px);
}

.back-to-top:active {
  transform: scale(0.95);
}

.btop-enter-active {
  transition:
    opacity 0.25s var(--tc-ease),
    transform 0.25s var(--tc-ease);
}

.btop-leave-active {
  transition:
    opacity 0.18s var(--tc-ease),
    transform 0.18s var(--tc-ease);
}

.btop-enter-from,
.btop-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
