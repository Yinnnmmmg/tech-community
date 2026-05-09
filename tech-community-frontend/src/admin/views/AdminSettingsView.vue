<script setup lang="ts">
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
</script>

<template>
  <section class="surface admin-settings">
    <div>
      <h2 class="section-title">系统设置</h2>
      <p class="muted">第一版先保留只读信息区，后续可以继续扩展站点配置和系统参数。</p>
    </div>

    <el-descriptions :column="2" border>
      <el-descriptions-item label="项目名称">Tech Community Admin</el-descriptions-item>
      <el-descriptions-item label="权限体系">Sa-Token / admin role</el-descriptions-item>
      <el-descriptions-item label="当前管理员">{{ authStore.user?.username || '-' }}</el-descriptions-item>
      <el-descriptions-item label="角色编码">{{ authStore.user?.userRole ?? '-' }}</el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<style scoped>
.admin-settings {
  display: grid;
  gap: 18px;
  padding: 22px;
}

.admin-settings p {
  margin: 8px 0 0;
}
</style>
