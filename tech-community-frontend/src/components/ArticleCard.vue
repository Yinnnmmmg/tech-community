<script setup lang="ts">
import { Bookmark, MessageCircle, Paperclip, ThumbsUp } from 'lucide-vue-next'
import { computed } from 'vue'

import type { ArticleListItem, ArticleSearchHighlight } from '@/api/types'

const props = defineProps<{
  article: ArticleListItem | ArticleSearchHighlight
  /** 搜索关键词，用于客户端高亮。仅搜索页面传入 */
  searchKeyword?: string
}>()

function isListArticle(article: ArticleListItem | ArticleSearchHighlight): article is ArticleListItem {
  return 'articleId' in article
}

/**
 * 客户端关键词高亮：将文本中匹配到的关键词用 {@code <em>} 标签包裹。
 */
function highlightText(text: string, keyword: string): string {
  if (!keyword || !text) return text
  const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${escaped})`, 'gi')
  return text.replace(regex, '<em>$1</em>')
}

const articleId = computed(() => (isListArticle(props.article) ? props.article.articleId : props.article.id))
const authorId = computed(() => (isListArticle(props.article) ? props.article.authorId : props.article.authorId))
const titleHtml = computed(() => {
  // 优先使用旧版 ES 高亮（兼容），其次使用客户端高亮
  if ('highlightedTitle' in props.article && props.article.highlightedTitle) {
    return props.article.highlightedTitle
  }
  if (props.searchKeyword) {
    return highlightText(props.article.title, props.searchKeyword)
  }
  return props.article.title
})
const summaryHtml = computed(() => {
  // 优先使用旧版 ES 高亮（兼容），其次 summary，再用客户端高亮
  if ('highlightedContent' in props.article && props.article.highlightedContent) {
    return props.article.highlightedContent
  }
  const raw = 'summary' in props.article ? props.article.summary : ''
  if (raw && props.searchKeyword) {
    return highlightText(raw, props.searchKeyword)
  }
  return raw
})
const authorName = computed(() => (isListArticle(props.article) ? props.article.authorName : props.article.author))
const categoryId = computed(() => (isListArticle(props.article) ? props.article.categoryId : undefined))
const categoryName = computed(() => (isListArticle(props.article) ? props.article.categoryName : undefined))
const createTime = computed(() => {
  if (isListArticle(props.article)) {
    return props.article.createTime
  }
  if ('publishTime' in props.article && props.article.publishTime) {
    return new Date(props.article.publishTime).toLocaleString()
  }
  return ''
})
const coverUrl = computed(() => ('coverUrl' in props.article ? props.article.coverUrl : ''))
const metrics = computed(() => {
  if (isListArticle(props.article)) {
    return {
      likeCount: props.article.likeCount,
      collectionCount: props.article.collectionCount,
      commentCount: props.article.commentCount,
      attachmentCount: props.article.attachmentCount
    }
  }
  return {
    likeCount: 0,
    collectionCount: 0,
    commentCount: 0,
    attachmentCount: props.article.tags?.length ?? 0
  }
})
const titleText = computed(() => String(props.article.title ?? '').replace(/<[^>]*>/g, ''))
</script>

<template>
  <article class="article-card">
    <div class="article-card__main">
      <div class="article-card__head">
        <RouterLink
          v-if="categoryId && categoryName"
          class="article-card__category"
          :to="{ name: 'category', params: { id: categoryId } }"
        >
          {{ categoryName }}
        </RouterLink>
        <RouterLink class="article-card__title-link" :to="{ name: 'article-detail', params: { id: articleId } }">
          <h2 class="article-card__title" v-html="titleHtml"></h2>
        </RouterLink>
      </div>
      <p v-if="summaryHtml" class="article-card__summary" v-html="summaryHtml"></p>
      <div class="article-card__footer">
        <div class="article-card__author">
          <span class="article-card__avatar">{{ (authorName || 'U').slice(0, 1) }}</span>
          <RouterLink
            v-if="authorId"
            class="article-card__author-link"
            :to="{ name: 'user-profile', params: { userId: authorId } }"
          >
            {{ authorName || '匿名作者' }}
          </RouterLink>
          <span v-else>{{ authorName || '匿名作者' }}</span>
          <span v-if="createTime" class="article-card__date">{{ createTime }}</span>
        </div>
        <div class="article-card__metrics">
          <span title="点赞"><ThumbsUp :size="15" />{{ metrics.likeCount }}</span>
          <span title="收藏"><Bookmark :size="15" />{{ metrics.collectionCount }}</span>
          <span title="评论"><MessageCircle :size="15" />{{ metrics.commentCount }}</span>
          <span title="附件"><Paperclip :size="15" />{{ metrics.attachmentCount }}</span>
        </div>
      </div>
    </div>
    <RouterLink
      v-if="coverUrl"
      class="article-card__thumb"
      :to="{ name: 'article-detail', params: { id: articleId } }"
      :aria-label="titleText"
    >
      <img :src="coverUrl" alt="" />
    </RouterLink>
  </article>
</template>

<style scoped>
.article-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  padding: 20px;
  border-radius: 16px;
  background: var(--tc-panel);
  backdrop-filter: var(--tc-glass-blur);
  -webkit-backdrop-filter: var(--tc-glass-blur);
  box-shadow: var(--tc-shadow-xs), var(--tc-shadow-glow);
  transition:
    transform var(--tc-duration) var(--tc-ease),
    box-shadow var(--tc-duration) var(--tc-ease);
  animation: cardReveal 0.35s var(--tc-ease) both;
  animation-delay: calc(var(--i, 0) * 60ms);
}

@keyframes cardReveal {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.article-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--tc-shadow-md);
}

.article-card:active {
  transform: scale(0.99);
}

.article-card__main {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.article-card__head {
  display: grid;
  gap: 8px;
}

.article-card__category {
  width: fit-content;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
  font-size: 12px;
  font-weight: 700;
  transition: color var(--tc-duration) var(--tc-ease);
}

.article-card__category:hover {
  color: var(--tc-brand-hover);
}

.article-card__title-link {
  min-width: 0;
}

.article-card__title {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--tc-text-strong);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.45;
  letter-spacing: 0;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  transition: color var(--tc-duration) var(--tc-ease);
}

.article-card:hover .article-card__title {
  color: var(--tc-brand-hover);
}

.article-card__summary {
  display: -webkit-box;
  max-height: 48px;
  margin: 0;
  overflow: hidden;
  color: #5f6877;
  font-size: 14px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.article-card__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  align-items: center;
  justify-content: space-between;
  color: var(--tc-text-muted);
  font-size: 13px;
}

.article-card__author,
.article-card__metrics,
.article-card__metrics span {
  display: inline-flex;
  align-items: center;
}

.article-card__author {
  gap: 8px;
  min-width: 0;
}

.article-card__avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  color: var(--tc-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.article-card__author-link {
  transition: color var(--tc-duration) var(--tc-ease);
}

.article-card__author-link:hover {
  color: var(--tc-brand);
}

.article-card__date {
  position: relative;
  padding-left: 10px;
}

.article-card__date::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--tc-text-muted);
  content: '';
  transform: translateY(-50%);
}

.article-card__metrics {
  gap: 16px;
}

.article-card__metrics span {
  gap: 5px;
}

.article-card__thumb {
  display: block;
  width: 180px;
  height: 104px;
  overflow: hidden;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.04);
}

.article-card__thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s var(--tc-ease);
}

.article-card:hover .article-card__thumb img {
  transform: scale(1.03);
}

:deep(em) {
  color: var(--tc-brand-red);
  font-style: normal;
  font-weight: 700;
}

@media (max-width: 640px) {
  .article-card {
    grid-template-columns: 1fr;
  }

  .article-card__thumb {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
  }
}
</style>
