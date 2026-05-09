<script setup lang="ts">
import { BadgeCheck, RefreshCcw, Search, ShieldX, Trash2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import {
  approveAdminComment,
  deleteAdminComment,
  getAdminComments,
  rejectAdminComment
} from '@/api/admin'
import type { AdminCommentListItem } from '@/api/types'

const loading = ref(false)
const rows = ref<AdminCommentListItem[]>([])
const page = reactive({
  current: 1,
  size: 10,
  total: 0
})
const filters = reactive<{
  keyword: string
  articleId?: number
  status?: number
}>({
  keyword: '',
  articleId: undefined,
  status: undefined
})

async function loadComments() {
  loading.value = true
  try {
    const result = await getAdminComments({
      keyword: filters.keyword || undefined,
      articleId: filters.articleId,
      status: filters.status,
      page: page.current,
      size: page.size
    })
    rows.value = result.records
    page.total = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载评论失败')
  } finally {
    loading.value = false
  }
}

async function approve(commentId: number) {
  try {
    await approveAdminComment(commentId)
    ElMessage.success('评论已通过')
    await loadComments()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function reject(commentId: number) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回评论', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '驳回原因不能为空'
    })
    await rejectAdminComment(commentId, value)
    ElMessage.success('评论已驳回')
    await loadComments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '操作失败')
    }
  }
}

async function removeComment(commentId: number) {
  try {
    await ElMessageBox.confirm('删除后会同步回滚评论计数和相关缓存，确认继续吗？', '删除评论', {
      type: 'warning'
    })
    await deleteAdminComment(commentId)
    ElMessage.success('评论已删除')
    await loadComments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.articleId = undefined
  filters.status = undefined
  page.current = 1
  loadComments()
}

function statusText(status: number) {
  return status === 1 ? '已通过' : status === 2 ? '已驳回' : '待审核'
}

function statusType(status: number) {
  return status === 1 ? 'success' : status === 2 ? 'danger' : 'warning'
}

onMounted(loadComments)
</script>

<template>
  <div class="stack">
    <section class="surface admin-page-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">评论管理</h2>
          <p class="muted">人工审核、驳回说明和全站评论删除都在这里进行。</p>
        </div>
        <div class="split-actions">
          <el-button class="icon-button" @click="resetFilters">
            <RefreshCcw :size="16" />
            <span>重置</span>
          </el-button>
          <el-button type="primary" class="icon-button" @click="page.current = 1; loadComments()">
            <Search :size="16" />
            <span>查询</span>
          </el-button>
        </div>
      </div>

      <div class="admin-filters admin-filters--comments">
        <el-input v-model="filters.keyword" placeholder="评论关键词" clearable />
        <el-input-number v-model="filters.articleId" :min="1" controls-position="right" placeholder="文章 ID" />
        <el-select v-model="filters.status" placeholder="状态" clearable>
          <el-option :value="0" label="待审核" />
          <el-option :value="1" label="已通过" />
          <el-option :value="2" label="已驳回" />
        </el-select>
      </div>
    </section>

    <section class="surface admin-page-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="articleTitle" label="文章" min-width="180" show-overflow-tooltip />
        <el-table-column prop="username" label="评论用户" width="120" />
        <el-table-column prop="content" label="评论内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="replyToUsername" label="回复对象" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="176" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <div class="split-actions">
              <el-button
                v-if="row.status !== 1"
                link
                type="success"
                class="icon-button"
                @click="approve(row.commentId)"
              >
                <BadgeCheck :size="16" />
                <span>通过</span>
              </el-button>
              <el-button
                v-if="row.status !== 2"
                link
                type="warning"
                class="icon-button"
                @click="reject(row.commentId)"
              >
                <ShieldX :size="16" />
                <span>驳回</span>
              </el-button>
              <el-button link type="danger" class="icon-button" @click="removeComment(row.commentId)">
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
          @current-change="loadComments"
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

.admin-filters--comments {
  grid-template-columns: minmax(0, 1.3fr) 180px 180px;
}

.admin-pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 800px) {
  .admin-filters--comments {
    grid-template-columns: 1fr;
  }
}
</style>
