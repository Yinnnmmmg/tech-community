<script setup lang="ts">
import { Eye, RefreshCcw, Search, Trash2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import { deleteAdminArticle, getAdminArticleDetail, getAdminArticles, getAdminCategories } from '@/api/admin'
import type { AdminArticleDetail, AdminArticleListItem, AdminCategoryItem } from '@/api/types'

const loading = ref(false)
const detailLoading = ref(false)
const rows = ref<AdminArticleListItem[]>([])
const categories = ref<AdminCategoryItem[]>([])
const detail = ref<AdminArticleDetail | null>(null)
const detailVisible = ref(false)
const page = reactive({
  current: 1,
  size: 10,
  total: 0
})
const filters = reactive<{
  keyword: string
  authorName: string
  status?: number
  categoryId?: number
}>({
  keyword: '',
  authorName: '',
  status: undefined,
  categoryId: undefined
})

async function loadCategories() {
  categories.value = await getAdminCategories()
}

async function loadArticles() {
  loading.value = true
  try {
    const result = await getAdminArticles({
      keyword: filters.keyword || undefined,
      authorName: filters.authorName || undefined,
      status: filters.status,
      categoryId: filters.categoryId,
      page: page.current,
      size: page.size
    })
    rows.value = result.records
    page.total = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载文章失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(articleId: number) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAdminArticleDetail(articleId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function removeArticle(articleId: number) {
  try {
    await ElMessageBox.confirm('删除后文章正文、附件绑定和读侧数据会一并清理，确认继续吗？', '删除文章', {
      type: 'warning'
    })
    await deleteAdminArticle(articleId)
    ElMessage.success('文章已删除')
    await loadArticles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '删除文章失败')
    }
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.authorName = ''
  filters.status = undefined
  filters.categoryId = undefined
  page.current = 1
  loadArticles()
}

function statusText(status: number) {
  return status === 1 ? '已通过' : status === 2 ? '已驳回' : '待审核'
}

function statusType(status: number) {
  return status === 1 ? 'success' : status === 2 ? 'danger' : 'warning'
}

onMounted(async () => {
  try {
    await loadCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载分类失败')
  }
  await loadArticles()
})
</script>

<template>
  <div class="stack">
    <section class="surface admin-page-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">文章管理</h2>
          <p class="muted">查看全站文章状态、内容详情和删除操作。</p>
        </div>
        <div class="split-actions">
          <el-button class="icon-button" @click="resetFilters">
            <RefreshCcw :size="16" />
            <span>重置</span>
          </el-button>
          <el-button type="primary" class="icon-button" @click="page.current = 1; loadArticles()">
            <Search :size="16" />
            <span>查询</span>
          </el-button>
        </div>
      </div>

      <div class="admin-filters">
        <el-input v-model="filters.keyword" placeholder="标题关键词" clearable />
        <el-input v-model="filters.authorName" placeholder="作者名" clearable />
        <el-select v-model="filters.status" placeholder="审核状态" clearable>
          <el-option :value="0" label="待审核" />
          <el-option :value="1" label="已通过" />
          <el-option :value="2" label="已驳回" />
        </el-select>
        <el-select v-model="filters.categoryId" placeholder="分类" clearable>
          <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </div>
    </section>

    <section class="surface admin-page-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="likeCount" label="点赞" width="88" />
        <el-table-column prop="collectionCount" label="收藏" width="88" />
        <el-table-column prop="commentCount" label="评论" width="88" />
        <el-table-column prop="updateTime" label="更新时间" width="176" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="split-actions">
              <el-button link type="primary" class="icon-button" @click="openDetail(row.articleId)">
                <Eye :size="16" />
                <span>详情</span>
              </el-button>
              <el-button link type="danger" class="icon-button" @click="removeArticle(row.articleId)">
                <Trash2 :size="16" />
                <span>删除</span>
              </el-button>
            </div>
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
          @current-change="loadArticles"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="文章详情" size="56%">
      <div v-loading="detailLoading" class="stack">
        <template v-if="detail">
          <div class="stack">
            <h3 class="section-title">{{ detail.title }}</h3>
            <div class="meta-row">
              <span>作者：{{ detail.authorName || '-' }}</span>
              <span>分类：{{ detail.categoryName || '-' }}</span>
              <el-tag :type="statusType(detail.status)" size="small">{{ statusText(detail.status) }}</el-tag>
            </div>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ detail.updateTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="点赞">{{ detail.likeCount }}</el-descriptions-item>
            <el-descriptions-item label="收藏">{{ detail.collectionCount }}</el-descriptions-item>
            <el-descriptions-item label="评论">{{ detail.commentCount }}</el-descriptions-item>
            <el-descriptions-item label="附件">{{ detail.attachments.length }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="detail.summary" class="surface admin-detail-block">
            <h4>摘要</h4>
            <p>{{ detail.summary }}</p>
          </div>
          <div class="surface admin-detail-block">
            <h4>正文</h4>
            <pre>{{ detail.content }}</pre>
          </div>
        </template>
      </div>
    </el-drawer>
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.admin-pagination {
  display: flex;
  justify-content: flex-end;
}

.admin-detail-block {
  padding: 18px;
}

.admin-detail-block h4 {
  margin: 0 0 12px;
  font-size: 14px;
}

.admin-detail-block p,
.admin-detail-block pre {
  margin: 0;
  line-height: 1.7;
  color: var(--tc-text);
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 880px) {
  .admin-filters {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .admin-filters {
    grid-template-columns: 1fr;
  }
}
</style>
