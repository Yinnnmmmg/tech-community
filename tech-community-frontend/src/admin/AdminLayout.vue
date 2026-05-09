<script setup lang="ts">
import { LogOut, Shield } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { adminMenus } from '@/admin/menu'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => {
  const matched = adminMenus.find((item) => route.path.startsWith(item.to))
  return matched?.to || '/admin/dashboard'
})

async function logout() {
  await authStore.logout()
  ElMessage.success('已退出后台')
  router.push({ name: 'admin-login' })
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="admin-brand" to="/admin/dashboard">
        <div class="admin-brand__mark">
          <Shield :size="18" />
        </div>
        <div>
          <strong>Tech Community</strong>
          <p>管理控制台</p>
        </div>
      </RouterLink>
      <el-menu :default-active="activeMenu" class="admin-menu" router>
        <el-menu-item v-for="item in adminMenus" :key="item.to" :index="item.to">
          <component :is="item.icon" :size="16" />
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div>
          <h1>管理端</h1>
          <p>内容治理、分类维护和用户角色统一在这里完成。</p>
        </div>
        <div class="admin-topbar__actions">
          <div class="admin-user-card">
            <span class="admin-user-card__label">当前管理员</span>
            <strong>{{ authStore.user?.username || 'admin' }}</strong>
          </div>
          <el-button class="icon-button" @click="logout">
            <LogOut :size="16" />
            <span>退出</span>
          </el-button>
        </div>
      </header>

      <main class="admin-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  min-height: 100vh;
  background:
    radial-gradient(circle at top right, rgba(255, 105, 0, 0.16), transparent 26%),
    linear-gradient(180deg, #f6f2ee 0%, #f5f7fb 42%, #edf2f9 100%);
}

.admin-sidebar {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 22px;
  padding: 22px 18px;
  border-right: 1px solid rgba(36, 41, 47, 0.08);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.82)),
    rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(14px);
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 105, 0, 0.16);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 105, 0, 0.08), rgba(255, 255, 255, 0.92));
}

.admin-brand strong {
  display: block;
  font-size: 16px;
  color: var(--tc-text-strong);
}

.admin-brand p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--tc-text-muted);
}

.admin-brand__mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, #ff6900, #ff9a4d);
  box-shadow: 0 14px 26px rgba(255, 105, 0, 0.24);
}

.admin-menu {
  border: none;
  background: transparent;
}

.admin-menu :deep(.el-menu-item) {
  display: flex;
  gap: 10px;
  margin-bottom: 6px;
  border-radius: 12px;
}

.admin-main {
  display: grid;
  grid-template-rows: auto 1fr;
  min-width: 0;
}

.admin-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 28px 28px 18px;
}

.admin-topbar h1 {
  margin: 0;
  font-size: 30px;
  color: #172033;
}

.admin-topbar p {
  margin: 8px 0 0;
  color: #657189;
}

.admin-topbar__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-user-card {
  display: grid;
  gap: 4px;
  padding: 10px 14px;
  min-width: 150px;
  border: 1px solid rgba(36, 41, 47, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
}

.admin-user-card__label {
  font-size: 11px;
  color: var(--tc-text-muted);
}

.admin-content {
  padding: 0 28px 28px;
}

@media (max-width: 1080px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    grid-template-rows: auto auto;
    border-right: none;
    border-bottom: 1px solid rgba(36, 41, 47, 0.08);
  }

  .admin-topbar {
    padding-top: 18px;
  }
}

@media (max-width: 720px) {
  .admin-topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .admin-topbar__actions {
    justify-content: space-between;
  }

  .admin-content {
    padding: 0 16px 20px;
  }
}
</style>
