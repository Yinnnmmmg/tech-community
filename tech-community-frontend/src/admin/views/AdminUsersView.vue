<script setup lang="ts">
import { RefreshCcw, ShieldCheck, ShieldOff } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import { getAdminUsers, updateAdminUserRole } from '@/api/admin'
import type { AdminUserListItem } from '@/api/types'

const loading = ref(false)
const rows = ref<AdminUserListItem[]>([])
const actingUserId = ref<number | null>(null)
const page = reactive({
  current: 1,
  size: 10,
  total: 0
})
const filters = reactive<{
  username: string
  userRole?: number
}>({
  username: '',
  userRole: undefined
})

async function loadUsers() {
  loading.value = true
  try {
    const result = await getAdminUsers({
      username: filters.username || undefined,
      userRole: filters.userRole,
      page: page.current,
      size: page.size
    })
    rows.value = result.records
    page.total = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户失败')
  } finally {
    loading.value = false
  }
}

async function toggleRole(row: AdminUserListItem) {
  actingUserId.value = row.userId
  try {
    const nextRole = row.userRole === 1 ? 0 : 1
    await updateAdminUserRole(row.userId, nextRole)
    ElMessage.success(nextRole === 1 ? '已提升为管理员' : '已降为普通用户')
    await loadUsers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色更新失败')
  } finally {
    actingUserId.value = null
  }
}

function resetFilters() {
  filters.username = ''
  filters.userRole = undefined
  page.current = 1
  loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <div class="stack">
    <section class="surface admin-page-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">用户管理</h2>
          <p class="muted">按需授予后台权限，系统会阻止最后一个管理员被降级。</p>
        </div>
        <div class="split-actions">
          <el-button class="icon-button" @click="resetFilters">
            <RefreshCcw :size="16" />
            <span>重置</span>
          </el-button>
        </div>
      </div>
      <div class="admin-filters admin-filters--users">
        <el-input v-model="filters.username" placeholder="用户名" clearable />
        <el-select v-model="filters.userRole" placeholder="角色" clearable>
          <el-option :value="1" label="管理员" />
          <el-option :value="0" label="普通用户" />
        </el-select>
        <el-button type="primary" @click="page.current = 1; loadUsers()">查询</el-button>
      </div>
    </section>

    <section class="surface admin-page-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" min-width="160" />
        <el-table-column prop="company" label="公司" min-width="140" show-overflow-tooltip />
        <el-table-column prop="position" label="职位" min-width="140" show-overflow-tooltip />
        <el-table-column prop="profile" label="简介" min-width="220" show-overflow-tooltip />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.userRole === 1 ? 'success' : 'info'" size="small">
              {{ row.userRole === 1 ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="176" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              :loading="actingUserId === row.userId"
              :type="row.userRole === 1 ? 'warning' : 'success'"
              class="icon-button"
              @click="toggleRole(row)"
            >
              <ShieldOff v-if="row.userRole === 1" :size="16" />
              <ShieldCheck v-else :size="16" />
              <span>{{ row.userRole === 1 ? '降级' : '设为管理员' }}</span>
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="admin-pagination">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          background
          layout="total, prev, pager, next"
          :total="page.total"
          @current-change="loadUsers"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-page-card {
  display: grid;
  gap: 18px;
  padding: 20px;
}

.admin-page-card p {
  margin: 8px 0 0;
}

.admin-filters {
  display: grid;
  gap: 12px;
}

.admin-filters--users {
  grid-template-columns: minmax(0, 1fr) 180px 120px;
}

.admin-pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 800px) {
  .admin-filters--users {
    grid-template-columns: 1fr;
  }
}
</style>
