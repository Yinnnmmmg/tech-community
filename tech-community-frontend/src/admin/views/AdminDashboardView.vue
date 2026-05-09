<script setup lang="ts">
import { FileClock, FileText, MessageSquareText, Shield, Users, UserSquare2 } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'

import { getAdminDashboardSummary } from '@/api/admin'
import type { AdminDashboardSummary } from '@/api/types'

const loading = ref(false)
const summary = ref<AdminDashboardSummary | null>(null)

const cards = [
  { key: 'articleCount', label: '文章总数', icon: FileText, tone: 'amber' },
  { key: 'pendingArticleCount', label: '待审文章', icon: FileClock, tone: 'gold' },
  { key: 'commentCount', label: '评论总数', icon: MessageSquareText, tone: 'sky' },
  { key: 'pendingCommentCount', label: '待审评论', icon: Shield, tone: 'rose' },
  { key: 'userCount', label: '用户总数', icon: Users, tone: 'slate' },
  { key: 'adminCount', label: '管理员数', icon: UserSquare2, tone: 'teal' }
] as const

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await getAdminDashboardSummary()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载仪表盘失败')
  } finally {
    loading.value = false
  }
}

function statusText(status: number) {
  return status === 1 ? '已通过' : status === 2 ? '已驳回' : '待审核'
}

onMounted(loadSummary)
</script>

<template>
  <div class="stack">
    <section class="admin-hero surface">
      <div>
        <span class="admin-hero__eyebrow">Overview</span>
        <h2 class="page-title">运营工作台</h2>
        <p>先看风险和待处理事项，再进入文章、评论、分类和用户模块执行具体操作。</p>
      </div>
      <el-button :loading="loading" @click="loadSummary">刷新数据</el-button>
    </section>

    <section class="admin-stats">
      <article
        v-for="card in cards"
        :key="card.key"
        class="admin-stat surface"
        :class="`admin-stat--${card.tone}`"
      >
        <div class="admin-stat__icon">
          <component :is="card.icon" :size="18" />
        </div>
        <span>{{ card.label }}</span>
        <strong>{{ summary?.[card.key] ?? 0 }}</strong>
      </article>
    </section>

    <section class="page-grid">
      <div class="surface admin-panel">
        <div class="toolbar">
          <h3 class="section-title">最近文章</h3>
          <span class="muted">按更新时间倒序</span>
        </div>
        <el-table :data="summary?.recentArticles || []" v-loading="loading" stripe>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="authorName" label="作者" width="120" />
          <el-table-column prop="categoryName" label="分类" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" />
        </el-table>
      </div>

      <div class="surface admin-panel">
        <div class="toolbar">
          <h3 class="section-title">最近评论</h3>
          <span class="muted">优先处理待审核</span>
        </div>
        <el-table :data="summary?.recentComments || []" v-loading="loading" stripe>
          <el-table-column prop="username" label="用户" width="120" />
          <el-table-column prop="articleTitle" label="文章" min-width="180" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-hero {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
  padding: 24px;
}

.admin-hero__eyebrow {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.admin-hero p {
  margin: 10px 0 0;
  color: var(--tc-text-muted);
}

.admin-stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
}

.admin-stat {
  display: grid;
  gap: 12px;
  padding: 18px;
}

.admin-stat span {
  color: var(--tc-text-muted);
  font-size: 13px;
}

.admin-stat strong {
  font-size: 30px;
  color: #1c2436;
}

.admin-stat__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: #fff;
}

.admin-stat--amber .admin-stat__icon { background: #ff7b29; }
.admin-stat--gold .admin-stat__icon { background: #d9a21b; }
.admin-stat--sky .admin-stat__icon { background: #2b8cff; }
.admin-stat--rose .admin-stat__icon { background: #e45a6a; }
.admin-stat--slate .admin-stat__icon { background: #57617a; }
.admin-stat--teal .admin-stat__icon { background: #139b8a; }

.admin-panel {
  padding: 20px;
}

@media (max-width: 1280px) {
  .admin-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .admin-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .admin-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
