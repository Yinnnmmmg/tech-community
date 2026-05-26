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
import { renderMarkdown } from '@/utils/markdown'

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
        <div v-if="article.coverUrl" class="cover-wrapper">
          <img class="article-cover" :src="article.coverUrl" alt="" />
          <div class="cover-gradient"></div>
        </div>
        <div class="article-main" :class="{ 'has-cover': article.coverUrl }">
          <div class="detail-meta">
            <RouterLink
              v-if="article.categoryId && article.categoryName"
              class="detail-category"
              :to="{ name: 'category', params: { id: article.categoryId } }"
            >
              {{ article.categoryName }}
            </RouterLink>
            <span v-else class="detail-category">{{ article.categoryName || '未分类' }}</span>
            <span class="meta-divider">·</span>
            <span class="meta-time">{{ article.createTime }}</span>
          </div>
          <h1 class="detail-title">{{ article.title }}</h1>
          <div class="detail-author-row">
            <RouterLink
              v-if="article.authorId"
              class="author-inline"
              :to="{ name: 'user-profile', params: { userId: article.authorId } }"
            >
              <span class="author-avatar-sm">{{ (article.authorName || 'U').slice(0, 1) }}</span>
              <span class="author-name-sm">{{ article.authorName || '匿名作者' }}</span>
            </RouterLink>
            <span v-else class="author-inline">
              <span class="author-avatar-sm">{{ (article.authorName || 'U').slice(0, 1) }}</span>
              <span class="author-name-sm">{{ article.authorName || '匿名作者' }}</span>
            </span>
          </div>
          <div class="detail-actions">
            <el-button
              class="action-btn"
              :class="{ active: article.likeStat }"
              :loading="actionLoading"
              @click="handleLike"
            >
              <Heart :size="18" :fill="article.likeStat ? 'currentColor' : 'none'" />
              <span>{{ article.likeCount || 0 }}</span>
            </el-button>
            <el-button
              class="action-btn"
              :class="{ active: article.collectionStat }"
              :loading="actionLoading"
              @click="handleCollect"
            >
              <Bookmark :size="18" :fill="article.collectionStat ? 'currentColor' : 'none'" />
              <span>{{ article.collectionCount || 0 }}</span>
            </el-button>
            <span v-if="canManageArticle" class="detail-actions-sep"></span>
            <RouterLink v-if="canManageArticle" :to="{ name: 'article-edit', params: { id: article.articleId } }">
              <el-button class="action-btn manage-btn">
                <Edit3 :size="16" />
                <span>编辑</span>
              </el-button>
            </RouterLink>
            <el-button v-if="canManageArticle" class="action-btn manage-btn danger-link" plain @click="handleDelete">
              <Trash2 :size="16" />
              <span>删除</span>
            </el-button>
          </div>
          <div class="article-content" v-html="renderMarkdown(article.content)"></div>
        </div>
      </article>

      <section v-if="article.attachments?.length" class="attachments surface">
        <h2 class="section-title">
          <Paperclip :size="18" />
          <span>附件 ({{ article.attachments.length }})</span>
        </h2>
        <div class="attachment-list">
          <a
            v-for="item in article.attachments"
            :key="item.attachmentId"
            :href="item.url"
            target="_blank"
            rel="noreferrer"
            class="attachment-link"
          >
            <div class="attachment-icon">
              <Paperclip :size="14" />
            </div>
            <span class="attachment-name">{{ item.fileName }}</span>
          </a>
        </div>
      </section>

      <CommentSection :article-id="article.articleId" />
    </main>

    <aside class="detail-side">
      <div class="detail-side__inner">
        <section class="side-card stats-card">
          <h2 class="side-card-title">文章数据</h2>
          <div class="detail-stats">
            <div class="stat-item">
              <div class="stat-icon stat-like">
                <Heart :size="18" />
              </div>
              <strong>{{ article.likeCount || 0 }}</strong>
              <span>点赞</span>
            </div>
            <div class="stat-item">
              <div class="stat-icon stat-collect">
                <Bookmark :size="18" />
              </div>
              <strong>{{ article.collectionCount || 0 }}</strong>
              <span>收藏</span>
            </div>
            <div class="stat-item">
              <div class="stat-icon stat-comment">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              </div>
              <strong>{{ article.commentCount || 0 }}</strong>
              <span>评论</span>
            </div>
          </div>
        </section>

        <section class="side-card author-side-card">
          <h2 class="side-card-title">关于作者</h2>
          <RouterLink
            v-if="article.authorId"
            class="author-card"
            :to="{ name: 'user-profile', params: { userId: article.authorId } }"
          >
            <span class="author-avatar">{{ (article.authorName || 'U').slice(0, 1) }}</span>
            <div class="author-info">
              <strong>{{ article.authorName || '匿名作者' }}</strong>
              <span class="author-hint">查看作者主页 →</span>
            </div>
          </RouterLink>
          <div v-else class="author-card">
            <span class="author-avatar">{{ (article.authorName || 'U').slice(0, 1) }}</span>
            <div class="author-info">
              <strong>{{ article.authorName || '匿名作者' }}</strong>
            </div>
          </div>
        </section>

        <section class="side-card ad-card">
          <div class="ad-placeholder">
            <div class="ad-label">广告</div>
            <div class="ad-content">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.3"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>
              <p>广告位招租</p>
              <span>如需投放广告请联系管理员</span>
              <span>QQ：3221388136</span>
            </div>
          </div>
        </section>
      </div>
    </aside>
  </div>
</template>

<style scoped>
/* ========== Layout ========== */
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 24px;
  align-items: start;
}

.detail-main {
  display: grid;
  gap: 20px;
}

/* ========== Article Card ========== */
.article-detail {
  overflow: hidden;
}

/* Cover */
.cover-wrapper {
  position: relative;
  overflow: hidden;
}

.article-cover {
  display: block;
  width: 100%;
  max-height: 400px;
  object-fit: cover;
  transition: transform 0.4s var(--tc-ease);
}

.article-detail:hover .article-cover {
  transform: scale(1.02);
}

.cover-gradient {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 80px;
  background: linear-gradient(transparent, rgba(24, 24, 27, 0.03));
  pointer-events: none;
}

/* Article main area */
.article-main {
  display: grid;
  gap: 20px;
  padding: 32px 36px 40px;
}

.article-main.has-cover {
  padding-top: 24px;
}

/* Meta row */
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--tc-text-muted);
  font-size: 13px;
}

.meta-divider {
  opacity: 0.4;
}

.meta-time {
  letter-spacing: 0.02em;
}

.detail-category {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
  font-weight: 600;
  font-size: 12px;
  transition: background var(--tc-duration) var(--tc-ease);
}

.detail-category:hover {
  background: rgba(232, 101, 15, 0.18);
}

/* Title */
.detail-title {
  margin: 0;
  color: var(--tc-text-strong);
  font-size: clamp(26px, 4vw, 38px);
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.01em;
}

/* Author inline row */
.detail-author-row {
  display: flex;
  align-items: center;
}

.author-inline {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  transition: opacity var(--tc-duration) var(--tc-ease);
}

.author-inline:hover {
  opacity: 0.8;
}

.author-avatar-sm {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #24292f, var(--tc-brand));
  color: #ffffff;
  font-weight: 700;
  font-size: 13px;
}

.author-name-sm {
  color: var(--tc-text-strong);
  font-weight: 600;
  font-size: 14px;
}

/* Actions */
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-top: 4px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 20px;
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 500;
  color: var(--tc-text-muted);
  background: #f5f7fa;
  border: none;
  transition: all var(--tc-duration) var(--tc-ease);
}

.action-btn:hover {
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

.action-btn.active {
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

.manage-btn {
  color: var(--tc-text);
  background: transparent;
  padding: 8px 14px;
}

.manage-btn:hover {
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

.detail-actions-sep {
  width: 1px;
  height: 20px;
  background: var(--tc-border);
  margin: 0 2px;
}

/* Article content */
.article-content {
  max-width: 72ch;
  color: #2c3340;
  font-size: 16px;
  line-height: 1.85;
  padding-top: 8px;
  border-top: 1px solid var(--tc-border);
}

/* ========== Attachments ========== */
.attachments {
  padding: 22px 24px;
}

.attachments .section-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.attachment-list {
  display: grid;
  gap: 6px;
  margin-top: 14px;
}

.attachment-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f8f9fb;
  color: var(--tc-text);
  transition: all var(--tc-duration) var(--tc-ease);
}

.attachment-link:hover {
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
}

.attachment-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: #e8eaed;
  color: var(--tc-text-muted);
  flex-shrink: 0;
  transition: all var(--tc-duration) var(--tc-ease);
}

.attachment-link:hover .attachment-icon {
  background: rgba(232, 101, 15, 0.15);
  color: var(--tc-brand);
}

.attachment-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

/* ========== Sidebar ========== */
.detail-side {
  position: sticky;
  top: 84px;
}

.detail-side__inner {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(100vh - 84px - 24px);
  overflow-y: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.18) transparent;
}

.detail-side__inner::-webkit-scrollbar {
  width: 5px;
}

.detail-side__inner::-webkit-scrollbar-track {
  background: transparent;
}

.detail-side__inner::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.18);
  border-radius: 3px;
}

.detail-side__inner::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.30);
}

.side-card {
  display: grid;
  flex-shrink: 0;
  gap: 16px;
  padding: 20px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: var(--tc-shadow-xs);
  border: 1px solid var(--tc-border);
}

.side-card-title {
  margin: 0;
  color: var(--tc-text-strong);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0;
}

/* Stats card */
.detail-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.stat-item {
  display: grid;
  gap: 4px;
  justify-items: center;
  padding: 12px 6px;
  border-radius: 10px;
  background: #f8f9fb;
  transition: all var(--tc-duration) var(--tc-ease);
}

.stat-item:hover {
  background: var(--tc-brand-soft);
  transform: translateY(-2px);
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  margin-bottom: 2px;
}

.stat-like {
  color: #e65151;
  background: rgba(230, 81, 81, 0.1);
}

.stat-collect {
  color: #e8a010;
  background: rgba(232, 160, 16, 0.1);
}

.stat-comment {
  color: #4a90d9;
  background: rgba(74, 144, 217, 0.1);
}

.stat-item strong {
  color: var(--tc-text-strong);
  font-size: 17px;
  font-weight: 700;
}

.stat-item span {
  color: var(--tc-text-muted);
  font-size: 11px;
}

/* Author card */
.author-side-card .author-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 0;
}

.author-avatar {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #24292f, var(--tc-brand));
  color: #ffffff;
  font-weight: 800;
  font-size: 18px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(232, 101, 15, 0.25);
}

.author-info {
  display: grid;
  gap: 3px;
}

.author-info strong {
  color: var(--tc-text-strong);
  font-size: 15px;
  font-weight: 700;
  transition: color var(--tc-duration) var(--tc-ease);
}

.author-card:hover .author-info strong {
  color: var(--tc-brand);
}

.author-hint {
  color: var(--tc-text-muted);
  font-size: 12px;
  transition: color var(--tc-duration) var(--tc-ease);
}

.author-card:hover .author-hint {
  color: var(--tc-brand);
}

/* ========== Ad Card ========== */
.ad-card {
  border: 1px dashed rgba(232, 101, 15, 0.25);
  background: linear-gradient(135deg, #fef9f5, #fafbff);
}

.ad-placeholder {
  display: grid;
  gap: 12px;
  justify-items: center;
  text-align: center;
}

.ad-label {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(232, 101, 15, 0.12);
  color: var(--tc-brand);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.ad-content {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 18px 0 8px;
  color: var(--tc-text-muted);
}

.ad-content p {
  margin: 0;
  color: var(--tc-text);
  font-weight: 600;
  font-size: 14px;
}

.ad-content span {
  font-size: 12px;
}

/* ========== Responsive ========== */
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
    padding: 20px 20px 28px;
  }

  .article-main.has-cover {
    padding-top: 16px;
  }

  .detail-actions {
    gap: 6px;
  }

  .action-btn {
    padding: 6px 14px;
    font-size: 13px;
  }
}
</style>
