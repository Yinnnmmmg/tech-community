<script setup lang="ts">
import { Bookmark, Edit3, Heart, Paperclip, Trash2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { collectArticle, deleteArticle, getArticleDetail, likeArticle } from '@/api/article'
import type { ArticleDetail } from '@/api/types'
import CommentSection from '@/components/CommentSection.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const articleId = computed(() => Number(route.params.id))
const article = ref<ArticleDetail | null>(null)
const loading = ref(false)
const actionLoading = ref(false)

const canManageArticle = computed(() => {
  if (!authStore.user || !article.value) {
    return false
  }
  return authStore.isAdmin || authStore.user.id === article.value.authorId
})

onMounted(loadArticle)

watch(articleId, loadArticle)

async function loadArticle() {
  if (!articleId.value) {
    return
  }
  loading.value = true
  try {
    article.value = await getArticleDetail(articleId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文章加载失败')
  } finally {
    loading.value = false
  }
}

async function handleLike() {
  if (!ensureLogin()) {
    return
  }
  actionLoading.value = true
  try {
    const result = await likeArticle(articleId.value)
    if (article.value) {
      article.value.likeCount = result.likeCount
      article.value.likeStat = result.likeStat
    }
  } finally {
    actionLoading.value = false
  }
}

async function handleCollect() {
  if (!ensureLogin()) {
    return
  }
  actionLoading.value = true
  try {
    const result = await collectArticle(articleId.value)
    if (article.value) {
      article.value.collectionCount = result.collectionCount
      article.value.collectionStat = result.collectionStat
    }
  } finally {
    actionLoading.value = false
  }
}

async function handleDelete() {
  if (!ensureLogin()) {
    return
  }
  await ElMessageBox.confirm('删除后无法恢复，确定删除这篇文章吗？', '删除文章', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteArticle(articleId.value)
  ElMessage.success('文章已删除')
  router.push({ name: 'home' })
}

function ensureLogin() {
  if (authStore.isAuthenticated) {
    return true
  }
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}
</script>

<template>
  <LoadingState v-if="loading" />
  <EmptyState v-else-if="!article" title="文章不存在或暂时不可见" />
  <div v-else class="detail-layout">
    <main class="detail-main">
      <article class="article-detail surface">
        <img v-if="article.coverUrl" class="article-cover" :src="article.coverUrl" alt="" />
        <div class="article-main">
          <div class="detail-meta">
            <RouterLink
              v-if="article.categoryId && article.categoryName"
              class="detail-category"
              :to="{ name: 'category', params: { id: article.categoryId } }"
            >
              {{ article.categoryName }}
            </RouterLink>
            <span v-else>{{ article.categoryName || '未分类' }}</span>
            <RouterLink
              v-if="article.authorId"
              class="detail-author-link"
              :to="{ name: 'user-profile', params: { userId: article.authorId } }"
            >
              {{ article.authorName || '匿名作者' }}
            </RouterLink>
            <span v-else>{{ article.authorName || '匿名作者' }}</span>
            <span>{{ article.createTime }}</span>
          </div>
          <h1 class="detail-title">{{ article.title }}</h1>
          <div class="detail-actions">
            <el-button class="icon-button" :loading="actionLoading" @click="handleLike">
              <Heart :size="16" :fill="article.likeStat ? 'currentColor' : 'none'" />
              <span>{{ article.likeCount || 0 }}</span>
            </el-button>
            <el-button class="icon-button" :loading="actionLoading" @click="handleCollect">
              <Bookmark :size="16" :fill="article.collectionStat ? 'currentColor' : 'none'" />
              <span>{{ article.collectionCount || 0 }}</span>
            </el-button>
            <span v-if="canManageArticle" class="detail-actions-sep"></span>
            <RouterLink v-if="canManageArticle" :to="{ name: 'article-edit', params: { id: article.articleId } }">
              <el-button class="icon-button">
                <Edit3 :size="16" />
                <span>编辑</span>
              </el-button>
            </RouterLink>
            <el-button v-if="canManageArticle" class="icon-button danger-link" plain @click="handleDelete">
              <Trash2 :size="16" />
              <span>删除</span>
            </el-button>
          </div>
          <div class="article-content">{{ article.content }}</div>
        </div>
      </article>

      <section v-if="article.attachments?.length" class="attachments surface">
        <h2 class="section-title">附件</h2>
        <a
          v-for="item in article.attachments"
          :key="item.attachmentId"
          :href="item.url"
          target="_blank"
          rel="noreferrer"
          class="attachment-link"
        >
          <Paperclip :size="16" />
          <span>{{ item.fileName }}</span>
        </a>
      </section>

      <CommentSection :article-id="article.articleId" />
    </main>

    <aside class="detail-side">
      <section class="side-card">
        <h2>文章数据</h2>
        <div class="detail-stats">
          <div>
            <strong>{{ article.likeCount || 0 }}</strong>
            <span>点赞</span>
          </div>
          <div>
            <strong>{{ article.collectionCount || 0 }}</strong>
            <span>收藏</span>
          </div>
          <div>
            <strong>{{ article.commentCount || 0 }}</strong>
            <span>评论</span>
          </div>
        </div>
      </section>
      <section class="side-card">
        <h2>作者</h2>
        <RouterLink
          v-if="article.authorId"
          class="author-card"
          :to="{ name: 'user-profile', params: { userId: article.authorId } }"
        >
          <span>{{ (article.authorName || 'U').slice(0, 1) }}</span>
          <strong>{{ article.authorName || '匿名作者' }}</strong>
        </RouterLink>
        <div v-else class="author-card">
          <span>{{ (article.authorName || 'U').slice(0, 1) }}</span>
          <strong>{{ article.authorName || '匿名作者' }}</strong>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.detail-main {
  display: grid;
  gap: 18px;
}

.article-detail {
  overflow: hidden;
}

.article-cover {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: cover;
  transition: opacity 0.3s var(--tc-ease);
}

.article-main {
  display: grid;
  gap: 18px;
  padding: 28px 28px 36px;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--tc-text-muted);
  font-size: 13px;
}

.detail-category,
.detail-author-link {
  color: var(--tc-brand);
  font-weight: 700;
  transition: color var(--tc-duration) var(--tc-ease);
}

.detail-category:hover,
.detail-author-link:hover {
  color: var(--tc-brand-hover);
}

.detail-title {
  margin: 0;
  color: var(--tc-text-strong);
  font-size: clamp(28px, 5vw, 42px);
  line-height: 1.25;
  letter-spacing: 0;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.detail-actions-sep {
  width: 1px;
  height: 22px;
  background: var(--tc-border);
  margin: 0 4px;
}

.article-content {
  max-width: 65ch;
  color: #2c3340;
  font-size: 16px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.attachments {
  display: grid;
  gap: 10px;
  padding: 18px;
}

.attachment-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--tc-brand);
}

.attachment-link span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-side {
  position: sticky;
  top: 80px;
  display: grid;
  gap: 16px;
}

.side-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: var(--tc-shadow-xs);
}

.side-card h2 {
  margin: 0;
  color: var(--tc-text-strong);
  font-size: 17px;
  letter-spacing: 0;
}

.detail-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.detail-stats div {
  display: grid;
  gap: 4px;
  justify-items: center;
  padding: 10px;
  border-radius: 4px;
  background: #f5f7fa;
  transition: background var(--tc-duration) var(--tc-ease);
}

.detail-stats div:hover {
  background: var(--tc-brand-soft);
}

.detail-stats strong {
  color: var(--tc-text-strong);
  font-size: 18px;
}

.detail-stats span {
  color: var(--tc-text-muted);
  font-size: 12px;
}

.author-card {
  display: flex;
  align-items: center;
  gap: 10px;
}

.author-card span {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, #24292f, var(--tc-brand));
  color: #ffffff;
  font-weight: 800;
}

.author-card strong {
  color: var(--tc-text-strong);
  transition: color var(--tc-duration) var(--tc-ease);
}

.author-card:hover strong {
  color: var(--tc-brand);
}

@media (max-width: 980px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .detail-side {
    position: static;
  }
}

@media (max-width: 620px) {
  .article-main {
    padding: 20px;
  }
}
</style>
