<script setup lang="ts">
import { Bell, Flame, Sparkles, Star, Trophy } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { listArticles } from '@/api/article'
import type { ArticleListItem } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const route = useRoute()

const articles = ref<ArticleListItem[]>([])
const nextCursor = ref<number | null>(1)
const loading = ref(false)
const loadingMore = ref(false)

const followingMode = computed(() => route.query.tab === 'following')
const followedArticles = ref<ArticleListItem[]>([])
const followedNextCursor = ref<number | null>(1)
const followingLoading = ref(false)
const followingLoadingMore = ref(false)

const featuredArticles = computed(() => articles.value.slice(0, 4))
const hotArticles = computed(() =>
  [...articles.value]
    .sort((left, right) => hotScore(right) - hotScore(left))
    .slice(0, 6)
)

const topAuthors = computed(() => {
  const authorMap = new Map<string, { authorId: string; authorName: string; totalScore: number }>()
  for (const article of articles.value) {
    const key = String(article.authorId || article.authorName || 'unknown')
    const existing = authorMap.get(key)
    if (existing) {
      existing.totalScore += article.likeCount + article.commentCount + article.collectionCount
    } else {
      authorMap.set(key, {
        authorId: String(article.authorId || ''),
        authorName: article.authorName || '社区作者',
        totalScore: article.likeCount + article.commentCount + article.collectionCount
      })
    }
  }
  return [...authorMap.values()]
    .sort((a, b) => b.totalScore - a.totalScore)
    .slice(0, 6)
})

const displayArticles = computed(() => followingMode.value ? followedArticles.value : articles.value)
const displayNextCursor = computed(() => followingMode.value ? followedNextCursor.value : nextCursor.value)
const displayLoading = computed(() => followingMode.value ? followingLoading.value : loading.value)

onMounted(() => {
  if (followingMode.value) {
    loadFollowedFirstPage()
  } else {
    loadFirstPage()
  }
})

watch(() => route.query.tab, (newTab) => {
  if (newTab === 'following') {
    loadFollowedFirstPage()
  } else {
    loadFirstPage()
  }
})

async function loadFirstPage() {
  loading.value = true
  try {
    const result = await listArticles(0, 10)
    articles.value = result.list
    nextCursor.value = result.nextCursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文章加载失败')
  } finally {
    loading.value = false
  }
}

async function loadFollowedFirstPage() {
  followingLoading.value = true
  try {
    const result = await listArticles(0, 10, undefined, true)
    followedArticles.value = result.list
    followedNextCursor.value = result.nextCursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关注文章加载失败')
  } finally {
    followingLoading.value = false
  }
}

async function loadMore() {
  if (followingMode.value) {
    if (!followedNextCursor.value) return
    followingLoadingMore.value = true
    try {
      const result = await listArticles(followedNextCursor.value, 10, undefined, true)
      followedArticles.value.push(...result.list)
      followedNextCursor.value = result.nextCursor
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '文章加载失败')
    } finally {
      followingLoadingMore.value = false
    }
    return
  }
  if (!nextCursor.value) {
    return
  }
  loadingMore.value = true
  try {
    const result = await listArticles(nextCursor.value, 10)
    articles.value.push(...result.list)
    nextCursor.value = result.nextCursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文章加载失败')
  } finally {
    loadingMore.value = false
  }
}

function hotScore(article: ArticleListItem) {
  return article.likeCount * 3 + article.collectionCount * 2 + article.commentCount
}

</script>

<template>
  <div class="home-view">
    <section v-if="!followingMode && featuredArticles.length" class="home-hero">
      <section class="recommend">
        <div class="recommend__header">
          <Star :size="18" />
          <h2>社区推荐</h2>
        </div>

        <div class="recommend__body">
          <RouterLink
            v-for="(item, index) in featuredArticles"
            :key="item.articleId"
            class="recommend__card"
            :to="{ name: 'article-detail', params: { id: item.articleId } }"
          >
            <div class="recommend__card-media">
              <img v-if="item.coverUrl" :src="item.coverUrl" alt="" />
              <div v-else class="recommend__card-fallback">
                <Sparkles :size="24" />
                <span>精选文章</span>
              </div>
              <span v-if="index === 0" class="recommend__tag">编辑推荐</span>
            </div>
            <div class="recommend__card-body">
              <h2>{{ item.title }}</h2>
              <p>{{ item.summary || '来自社区的最新技术分享' }}</p>
              <span class="recommend__card-meta">{{ item.authorName || '社区作者' }} &middot; {{ item.createTime || '刚刚' }}</span>
            </div>
          </RouterLink>
        </div>
      </section>
    </section>

    <div class="home-layout">
      <section class="home-feed surface">
        <div class="home-feed__head">
          <div>
            <h1 class="page-title">最新文章</h1>
            <p>关注后端、前端、AI 与工程实践的新鲜讨论。</p>
          </div>
        </div>

        <LoadingState v-if="displayLoading" />
        <EmptyState v-else-if="!displayArticles.length" title="暂无文章" :description="followingMode ? '关注的人还没有发布文章。' : '发布第一篇文章，让社区热起来。'" />
        <template v-else>
          <ArticleCard
            v-for="(item, index) in displayArticles"
            :key="item.articleId"
            :article="item"
            :style="{ '--i': index }"
          />
          <div class="load-more">
            <el-button :loading="followingMode ? followingLoadingMore : loadingMore" :disabled="!displayNextCursor" @click="loadMore">
              {{ displayNextCursor ? '加载更多' : '已经到底了' }}
            </el-button>
          </div>
        </template>
      </section>

      <aside class="home-sidebar">
        <div class="home-sidebar__inner">
          <section class="sidebar-card notice-card">
            <div class="sidebar-card__title">
              <Bell :size="18" />
              <h2>社区公告</h2>
            </div>
            <p>欢迎来到 Tech Community。分享问题、经验和方案，让好内容更容易被看见。</p>
          </section>

          <template v-if="!followingMode">
            <section class="sidebar-card">
              <div class="sidebar-card__title">
                <Flame :size="18" />
                <h2>文章榜</h2>
              </div>
              <div v-if="hotArticles.length" class="hot-list">
                <RouterLink
                  v-for="(item, index) in hotArticles"
                  :key="item.articleId"
                  :to="{ name: 'article-detail', params: { id: item.articleId } }"
                  class="hot-list__item"
                >
                  <span>{{ index + 1 }}</span>
                  <strong>{{ item.title }}</strong>
                </RouterLink>
              </div>
              <p v-else class="sidebar-muted">暂无热门内容</p>
            </section>

            <section class="sidebar-card">
              <div class="sidebar-card__title">
                <Trophy :size="18" />
                <h2>作者榜</h2>
              </div>
              <div v-if="topAuthors.length" class="hot-list">
                <div
                  v-for="(author, index) in topAuthors"
                  :key="author.authorId || index"
                  class="hot-list__item"
                >
                  <span>{{ index + 1 }}</span>
                  <RouterLink :to="`/users/${author.authorId}`" class="author-link">{{ author.authorName }}</RouterLink>
                </div>
              </div>
              <p v-else class="sidebar-muted">暂无作者数据</p>
            </section>
          </template>
          <template v-else>
            <section class="sidebar-card ad-card">
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
            <section class="sidebar-card ad-card">
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
          </template>

          <section class="sidebar-card ad-card">
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
  </div>
</template>

<style scoped>
.home-view {
  display: grid;
  gap: 20px;
}

.home-hero {
  margin-top: -20px;
  margin-left: calc(-50vw + 50%);
  margin-right: calc(-50vw + 50%);
  padding-left: calc(50vw - 50%);
  padding-right: calc(50vw - 50%);
  overflow: hidden;
  border-radius: 0 0 16px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.88);
  display: grid;
  gap: 0;
}

.recommend {
  display: grid;
  gap: 16px;
  margin-left: calc(-50vw + 50%);
  margin-right: calc(-50vw + 50%);
  padding: 24px;
  padding-left: calc(50vw - 50% + 24px);
  padding-right: calc(50vw - 50% + 24px);
  border-radius: 0;
  background:
    radial-gradient(ellipse 600px 240px at 0% 30%, rgba(232, 101, 15, 0.18), transparent),
    radial-gradient(ellipse 360px 200px at 80% 80%, rgba(255, 182, 193, 0.18), transparent),
    linear-gradient(180deg, rgba(255, 200, 150, 0.75), rgba(255, 220, 210, 0.65), rgba(255, 240, 245, 0.7));
}

.recommend__header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tc-brand);
}

.recommend__header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0;
  color: var(--tc-text-strong);
}

.recommend__body {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}

.recommend__card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.02);
  transition:
    transform var(--tc-duration) var(--tc-ease),
    box-shadow var(--tc-duration) var(--tc-ease);
}

.recommend__card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12);
}

.recommend__card-media {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: linear-gradient(135deg, #1a1d23, #2d3548);
}

.recommend__card-media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.45s var(--tc-ease);
}

.recommend__card:hover .recommend__card-media img {
  transform: scale(1.06);
}

.recommend__card-fallback {
  display: grid;
  place-items: center;
  gap: 8px;
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at 30% 20%, rgba(255, 135, 33, 0.30), transparent 32%),
    linear-gradient(135deg, #1a1d23, #2d3548 58%, #e8650f);
  color: rgba(255, 255, 255, 0.88);
  font-size: 15px;
  font-weight: 700;
}

.recommend__tag {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1;
  padding: 4px 10px;
  border-radius: 5px;
  background: var(--tc-brand);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.recommend__card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 6px;
  padding: 16px;
}

.recommend__card-body h2 {
  margin: 0;
  display: -webkit-box;
  overflow: hidden;
  color: var(--tc-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  transition: color var(--tc-duration) var(--tc-ease);
}

.recommend__card:hover .recommend__card-body h2 {
  color: var(--tc-brand);
}

.recommend__card-body > p {
  margin: 0;
  display: -webkit-box;
  overflow: hidden;
  flex: 1;
  color: var(--tc-text-muted);
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recommend__card-meta {
  margin-top: auto;
  color: var(--tc-text-muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
  max-width: 1320px;
  margin: 0 auto;
  width: 100%;
}

.home-feed {
  display: grid;
  gap: 12px;
  padding: 22px;
  background: transparent;
  box-shadow: none;
}

.home-feed__head {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 4px;
}

.home-feed__head p {
  margin: 6px 0 0;
  color: var(--tc-text-muted);
  line-height: 1.6;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 20px 0 2px;
}

.home-sidebar {
  position: sticky;
  top: 80px;
}

.home-sidebar__inner {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(100vh - 80px - 24px);
  overflow-y: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.18) transparent;
}

.home-sidebar__inner::-webkit-scrollbar {
  width: 5px;
}

.home-sidebar__inner::-webkit-scrollbar-track {
  background: transparent;
}

.home-sidebar__inner::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.18);
  border-radius: 3px;
}

.home-sidebar__inner::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.30);
}

.sidebar-card {
  display: grid;
  flex-shrink: 0;
  gap: 14px;
  padding: 18px;
  border-radius: 16px;
  background: var(--tc-panel);
  backdrop-filter: var(--tc-glass-blur);
  -webkit-backdrop-filter: var(--tc-glass-blur);
  box-shadow: var(--tc-shadow-xs), var(--tc-shadow-glow);
}

.sidebar-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tc-brand);
}

.sidebar-card__title h2 {
  margin: 0;
  color: var(--tc-text-strong);
  font-size: 17px;
  line-height: 1.35;
  letter-spacing: 0;
}

.notice-card {
  background:
    linear-gradient(135deg, rgba(232, 101, 15, 0.10), rgba(255, 255, 255, 0) 62%),
    var(--tc-panel);
}

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

.sidebar-card p,
.sidebar-muted {
  margin: 0;
  color: #5f6877;
  line-height: 1.7;
}

.hot-list {
  display: grid;
  gap: 12px;
}

.hot-list__item {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  color: #424b59;
}

.hot-list__item span {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.08);
  color: var(--tc-text-muted);
  font-size: 11px;
  font-weight: 700;
}

.hot-list__item:nth-child(1) span {
  background: rgba(230, 81, 81, 0.15);
  color: var(--tc-brand-red);
}

.hot-list__item:nth-child(2) span {
  background: rgba(232, 101, 15, 0.12);
  color: var(--tc-brand);
}

.hot-list__item:nth-child(3) span {
  background: rgba(245, 158, 47, 0.15);
  color: #c4881f;
}

.hot-list__item strong {
  display: -webkit-box;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  transition: color var(--tc-duration) var(--tc-ease);
}

.hot-list__item:hover strong {
  color: var(--tc-brand);
}

.author-link {
  display: -webkit-box;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.55;
  color: #424b59;
  text-decoration: none;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  transition: color var(--tc-duration) var(--tc-ease);
}

.author-link:hover {
  color: var(--tc-brand);
}

@media (max-width: 980px) {
  .home-hero {
    margin-top: -16px;
  }

  .home-layout {
    grid-template-columns: 1fr;
  }

  .recommend__body {
    grid-template-columns: repeat(2, 1fr);
  }

  .recommend {
    padding: 20px 16px;
    padding-left: calc(50vw - 50% + 16px);
    padding-right: calc(50vw - 50% + 16px);
  }

  .home-sidebar {
    display: none;
  }
}

@media (max-width: 620px) {
  .recommend__body {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .home-feed {
    padding: 16px;
  }

  .home-feed__head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
