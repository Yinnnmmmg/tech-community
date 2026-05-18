<script setup lang="ts">
import { Bell, Bot, CircleUserRound, Home, LogIn, LogOut, PenLine } from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'
import { useNotifyStore } from '@/stores/notifyStore'

const authStore = useAuthStore()
const notifyStore = useNotifyStore()
const router = useRouter()
const route = useRoute()

onMounted(() => {
  if (authStore.isAuthenticated) {
    notifyStore.fetchUnreadCount()
    notifyStore.startPolling()
  }
})

onUnmounted(() => {
  notifyStore.stopPolling()
})

// 仅在进出通知页面时刷新未读数，避免每次路由切换都发请求
watch(() => route.name, (to, from) => {
  if (from === 'notifications' || to === 'notifications') {
    notifyStore.fetchUnreadCount()
  }
})

const profileRoute = computed(() => {
  if (!authStore.user) {
    return { name: 'login' }
  }
  return { name: 'user-profile', params: { userId: authStore.user.id } }
})

async function handleLogout() {
  await authStore.logout()
  router.push({ name: 'home' })
}
</script>

<template>
  <header class="topbar">
    <div class="topbar__inner">
      <RouterLink class="brand" :to="{ name: 'home' }" aria-label="Tech Community 首页">
        <span class="brand__mark">TC</span>
        <span class="brand__name">Tech Community</span>
      </RouterLink>

      <nav class="nav" aria-label="主导航">
        <RouterLink :to="{ name: 'home' }" class="nav__item">
          <Home :size="17" />
          <span>首页</span>
        </RouterLink>
        <RouterLink :to="{ name: 'ai-chat' }" class="nav__item">
          <Bot :size="17" />
          <span>AI 助手</span>
        </RouterLink>
        <RouterLink :to="profileRoute" class="nav__item">
          <CircleUserRound :size="17" />
          <span>个人主页</span>
        </RouterLink>
        <RouterLink :to="{ name: 'notifications' }" class="nav__item" @click="notifyStore.markSystemAsRead()">
          <span class="nav__icon-wrap">
            <Bell :size="17" />
            <span v-if="notifyStore.unreadCount > 0" class="nav__badge">{{ notifyStore.unreadCount > 99 ? '99+' : notifyStore.unreadCount }}</span>
          </span>
          <span>通知</span>
        </RouterLink>
      </nav>

      <div class="account">
        <RouterLink :to="{ name: 'article-new' }">
          <el-button type="primary" class="icon-button write-button">
            <PenLine :size="16" />
            <span>发文章</span>
          </el-button>
        </RouterLink>
        <template v-if="authStore.isAuthenticated">
          <RouterLink :to="profileRoute" class="account__avatar" :title="authStore.user?.username || '当前用户'">
            <img v-if="authStore.user?.photo" :src="authStore.user.photo" alt="" />
            <span v-else>{{ (authStore.user?.username || 'U').slice(0, 1) }}</span>
          </RouterLink>
          <el-button class="icon-button ghost-button" @click="handleLogout">
            <LogOut :size="16" />
            <span>退出</span>
          </el-button>
        </template>
        <RouterLink v-else :to="{ name: 'login' }">
          <el-button class="icon-button ghost-button">
            <LogIn :size="16" />
            <span>登录</span>
          </el-button>
        </RouterLink>
      </div>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(255, 248, 245, 0.72);
  backdrop-filter: blur(16px) saturate(1.5);
  -webkit-backdrop-filter: blur(16px) saturate(1.5);
  border-bottom: 1px solid rgba(232, 101, 15, 0.08);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.topbar__inner {
  display: flex;
  align-items: center;
  gap: 20px;
  width: min(1200px, calc(100% - 32px));
  min-height: 60px;
  margin: 0 auto;
}

.brand {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 10px;
  color: var(--tc-text-strong);
  font-weight: 800;
  white-space: nowrap;
}

.brand__mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  background: var(--tc-brand);
  color: #ffffff;
  font-size: 13px;
}

.brand__name {
  letter-spacing: 0;
}

.nav {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.nav__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 12px;
  color: var(--tc-text-muted);
  font-size: 14px;
  white-space: nowrap;
  position: relative;
  transition: color var(--tc-duration) var(--tc-ease);
}

.nav__item::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 0;
  height: 2px;
  border-radius: 1px;
  background: var(--tc-brand);
  transform: translateX(-50%);
  transition: width var(--tc-duration) var(--tc-ease);
}

.nav__item:hover {
  color: var(--tc-brand);
}

.nav__item.router-link-active {
  color: var(--tc-text-strong);
}

.nav__item.router-link-active::after {
  width: calc(100% - 24px);
}

.nav__badge {
  position: absolute;
  top: -4px;
  right: -4px;
  display: grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  animation: badge-pop 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes badge-pop {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  80% {
    transform: scale(1.15);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.nav__icon-wrap {
  position: relative;
  display: inline-flex;
}

.account {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 10px;
}

.account__avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  overflow: hidden;
  border-radius: 50%;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  transition: box-shadow var(--tc-duration) var(--tc-ease);
}

.account__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account__avatar:hover {
  box-shadow: 0 0 0 2px var(--tc-brand);
}

.write-button {
  border-color: var(--tc-brand);
  background: var(--tc-brand);
  color: #ffffff;
  transition: all var(--tc-duration) var(--tc-ease);
}

.write-button:active {
  transform: scale(0.98);
}

.ghost-button {
  border-color: var(--tc-border);
  background: transparent;
  color: var(--tc-text-muted);
  transition: all var(--tc-duration) var(--tc-ease);
}

.ghost-button:hover {
  border-color: var(--tc-brand);
  color: var(--tc-brand);
  background: rgba(232, 101, 15, 0.1);
}

.ghost-button:active {
  transform: scale(0.98);
}

@media (max-width: 980px) {
  .topbar__inner {
    width: min(100% - 24px, 760px);
    flex-wrap: wrap;
    gap: 10px 14px;
    padding: 10px 0;
  }

  .nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .account {
    margin-left: auto;
  }
}

@media (max-width: 560px) {
  .brand__name {
    display: none;
  }

  .write-button span,
  .ghost-button span {
    display: none;
  }
}
</style>
