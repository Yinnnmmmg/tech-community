<script setup lang="ts">
import { Bell, Bot, CircleUserRound, Home, LogIn, LogOut, PenLine } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const router = useRouter()

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
          <span>AI</span>
        </RouterLink>
        <RouterLink :to="profileRoute" class="nav__item">
          <CircleUserRound :size="17" />
          <span>个人主页</span>
        </RouterLink>
        <RouterLink :to="{ name: 'notifications' }" class="nav__item">
          <Bell :size="17" />
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
          <RouterLink :to="profileRoute" class="account__name">
            {{ authStore.user?.username || '当前用户' }}
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
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px) saturate(1.4);
  -webkit-backdrop-filter: blur(20px) saturate(1.4);
  border-bottom: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
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

.account {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 10px;
}

.account__name {
  max-width: 116px;
  overflow: hidden;
  color: var(--tc-text-muted);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--tc-duration) var(--tc-ease);
}

.account__name:hover {
  color: var(--tc-brand);
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
  .brand__name,
  .account__name {
    display: none;
  }

  .write-button span,
  .ghost-button span {
    display: none;
  }
}
</style>
