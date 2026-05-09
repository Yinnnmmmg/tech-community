<script setup lang="ts">
import { Bell, Flame, MessageCircle, PenLine, Search, Sparkles } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listArticles, listCategories } from '@/api/article'
import type { ArticleCategory, ArticleListItem } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

const articles = ref<ArticleListItem[]>([])
const categories = ref<ArticleCategory[]>([])
const nextCursor = ref<number | null>(1)
const loading = ref(false)
const loadingMore = ref(false)
const keyword = ref('')

const featuredArticles = computed(() => articles.value.slice(0, 4))
const hotArticles = computed(() =>
  [...articles.value]
    .sort((left, right) => hotScore(right) - hotScore(left))
    .slice(0, 6)
)

onMounted(() => {
  loadFirstPage()
  loadCategoryList()
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

async function loadCategoryList() {
  try {
    categories.value = await listCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类加载失败')
  }
}

async function loadMore() {
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

function submitSearch() {
  const q = keyword.value.trim()
  if (q) {
    router.push({ name: 'search', query: { q } })
  }
}

function openCategory(category: ArticleCategory | null) {
  if (!category) {
    router.push({ name: 'home' })
    return
  }
  router.push({ name: 'category', params: { id: category.id } })
}

function hotScore(article: ArticleListItem) {
  return article.likeCount * 3 + article.collectionCount * 2 + article.commentCount
}

function compactNumber(value: number) {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}w`
  }
  return String(value)
}
</script>

<template>
  <div class="home-view">
    <section class="home-nav surface">
      <div class="home-nav__categories" aria-label="文章分类">
        <button class="category-pill active" @click="openCategory(null)">推荐</button>
        <button
          v-for="item in categories"
          :key="item.id"
          class="category-pill"
          @click="openCategory(item)"
        >
          {{ item.name }}
        </button>
      </div>
      <div class="home-nav__search">
        <el-input v-model="keyword" placeholder="搜索文章" clearable @keyup.enter="submitSearch">
          <template #prefix><Search :size="17" /></template>
        </el-input>
        <el-button type="primary" class="icon-button" @click="submitSearch">
          <Search :size="16" />
          <span>搜索</span>
        </el-button>
      </div>
    </section>

    <section v-if="featuredArticles.length" class="recommend">
      <RouterLink
        class="recommend__hero"
        :to="{ name: 'article-detail', params: { id: featuredArticles[0].articleId } }"
      >
        <img v-if="featuredArticles[0].coverUrl" :src="featuredArticles[0].coverUrl" alt="" />
        <div v-else class="recommend__fallback">
          <Sparkles :size="34" />
          <span>精选文章</span>
        </div>
        <div class="recommend__hero-body">
          <span class="recommend__tag">社区推荐</span>
          <h2>{{ featuredArticles[0].title }}</h2>
          <p>{{ featuredArticles[0].summary || '来自社区的最新技术分享' }}</p>
        </div>
      </RouterLink>

      <div class="recommend__list">
        <RouterLink
          v-for="item in featuredArticles.slice(1)"
          :key="item.articleId"
          class="recommend__item"
          :to="{ name: 'article-detail', params: { id: item.articleId } }"
        >
          <img v-if="item.coverUrl" :src="item.coverUrl" alt="" />
          <span v-else class="recommend__item-mark">{{ item.title.slice(0, 1) }}</span>
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.authorName || '社区作者' }} · {{ item.createTime || '刚刚' }}</p>
          </div>
        </RouterLink>
      </div>
    </section>

    <div class="home-layout">
      <section class="home-feed surface">
        <div class="home-feed__head">
          <div>
            <h1 class="page-title">最新文章</h1>
            <p>关注后端、前端、AI 与工程实践的新鲜讨论。</p>
          </div>
          <RouterLink :to="{ name: 'article-new' }">
            <el-button type="primary" class="icon-button">
              <PenLine :size="16" />
              <span>写文章</span>
            </el-button>
          </RouterLink>
        </div>

        <LoadingState v-if="loading" />
        <EmptyState v-else-if="!articles.length" title="暂无文章" description="发布第一篇文章，让社区热起来。" />
        <template v-else>
          <ArticleCard
            v-for="(item, index) in articles"
            :key="item.articleId"
            :article="item"
            :style="{ '--i': index }"
          />
          <div class="load-more">
            <el-button :loading="loadingMore" :disabled="!nextCursor" @click="loadMore">
              {{ nextCursor ? '加载更多' : '已经到底了' }}
            </el-button>
          </div>
        </template>
      </section>

      <aside class="home-sidebar">
        <section class="sidebar-card notice-card">
          <div class="sidebar-card__title">
            <Bell :size="18" />
            <h2>社区公告</h2>
          </div>
          <p>欢迎来到 Tech Community。分享问题、经验和方案，让好内容更容易被看见。</p>
        </section>

        <section class="sidebar-card">
          <div class="sidebar-card__title">
            <Flame :size="18" />
            <h2>热门文章</h2>
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

        <section class="sidebar-card profile-card">
          <div class="profile-card__avatar">{{ (authStore.user?.username || 'T').slice(0, 1) }}</div>
          <h2>{{ authStore.user?.username || '游客' }}</h2>
          <p>{{ authStore.isAuthenticated ? '继续记录你的技术思考。' : '登录后可发布、评论、点赞和收藏。' }}</p>
          <RouterLink :to="authStore.isAuthenticated ? { name: 'article-new' } : { name: 'login' }">
            <el-button type="primary" class="icon-button">
              <PenLine :size="16" />
              <span>{{ authStore.isAuthenticated ? '去创作' : '去登录' }}</span>
            </el-button>
          </RouterLink>
        </section>

        <section class="sidebar-card">
          <div class="sidebar-card__title">
            <MessageCircle :size="18" />
            <h2>社区数据</h2>
          </div>
          <div class="stats-grid">
            <div>
              <strong>{{ compactNumber(articles.length) }}</strong>
              <span>本页文章</span>
            </div>
            <div>
              <strong>{{ compactNumber(articles.reduce((sum, item) => sum + item.commentCount, 0)) }}</strong>
              <span>评论</span>
            </div>
            <div>
              <strong>{{ compactNumber(articles.reduce((sum, item) => sum + item.likeCount, 0)) }}</strong>
              <span>点赞</span>
            </div>
            <div>
              <strong>{{ compactNumber(articles.reduce((sum, item) => sum + item.collectionCount, 0)) }}</strong>
              <span>收藏</span>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.home-view {
  display: grid;
  gap: 20px;
}

.home-nav {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
}

.home-nav__categories {
  display: flex;
  gap: 6px;
  overflow-x: auto;
}

.category-pill {
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #3d4654;
  cursor: pointer;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
  transition:
    color var(--tc-duration) var(--tc-ease),
    background var(--tc-duration) var(--tc-ease);
}

.category-pill:hover {
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

.category-pill.active {
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

.category-pill:active {
  transform: scale(0.97);
}

.home-nav__search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.recommend {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 18px;
}

.recommend__hero,
.recommend__item {
  overflow: hidden;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: var(--tc-shadow-sm);
  transition:
    transform var(--tc-duration) var(--tc-ease),
    box-shadow var(--tc-duration) var(--tc-ease);
}

.recommend__hero:hover {
  transform: translateY(-2px);
  box-shadow: var(--tc-shadow-lg);
}

.recommend__item:hover {
  transform: translateY(-1px);
  box-shadow: var(--tc-shadow-md);
}

.recommend__hero {
  position: relative;
  display: block;
  min-height: 280px;
  color: #ffffff;
}

.recommend__hero > img,
.recommend__fallback {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.recommend__hero > img {
  object-fit: cover;
}

.recommend__fallback {
  display: grid;
  place-items: center;
  gap: 8px;
  background:
    radial-gradient(circle at 30% 20%, rgba(255, 135, 33, 0.36), transparent 32%),
    linear-gradient(135deg, #24292f, #384150 58%, #ff6900);
  font-size: 18px;
  font-weight: 700;
}

.recommend__hero::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 25%, rgba(0, 0, 0, 0.76));
  content: "";
}

.recommend__hero-body {
  position: absolute;
  right: 22px;
  bottom: 22px;
  left: 22px;
  z-index: 1;
  display: grid;
  gap: 8px;
}

.recommend__tag {
  width: fit-content;
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--tc-brand);
  font-size: 12px;
  font-weight: 700;
}

.recommend__hero h2,
.recommend__hero p,
.recommend__item h3,
.recommend__item p {
  margin: 0;
}

.recommend__hero h2 {
  display: -webkit-box;
  overflow: hidden;
  font-size: 26px;
  line-height: 1.25;
  letter-spacing: 0;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recommend__hero p {
  display: -webkit-box;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.84);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recommend__list {
  display: grid;
  gap: 12px;
}

.recommend__item {
  display: grid;
  grid-template-columns: 116px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 85px;
  padding: 10px;
}

.recommend__item img,
.recommend__item-mark {
  width: 116px;
  height: 65px;
  border-radius: 4px;
}

.recommend__item img {
  object-fit: cover;
}

.recommend__item-mark {
  display: grid;
  place-items: center;
  background: #eef1f5;
  color: var(--tc-brand);
  font-size: 26px;
  font-weight: 800;
}

.recommend__item h3 {
  display: -webkit-box;
  overflow: hidden;
  color: var(--tc-text-strong);
  font-size: 15px;
  line-height: 1.45;
  letter-spacing: 0;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recommend__item h3 {
  transition: color var(--tc-duration) var(--tc-ease);
}

.recommend__item:hover h3 {
  color: var(--tc-brand);
}

.recommend__item p {
  margin-top: 6px;
  overflow: hidden;
  color: var(--tc-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
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
  display: grid;
  gap: 16px;
}

.sidebar-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: var(--tc-shadow-xs);
}

.sidebar-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tc-brand);
}

.sidebar-card__title h2,
.profile-card h2 {
  margin: 0;
  color: var(--tc-text-strong);
  font-size: 17px;
  line-height: 1.35;
  letter-spacing: 0;
}

.notice-card {
  background:
    linear-gradient(135deg, rgba(255, 105, 0, 0.1), rgba(255, 255, 255, 0) 62%),
    #ffffff;
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
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: #ccd1da;
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.hot-list__item:nth-child(1) span {
  background: var(--tc-brand-red);
}

.hot-list__item:nth-child(2) span {
  background: var(--tc-brand);
}

.hot-list__item:nth-child(3) span {
  background: #f59e2f;
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

.profile-card {
  justify-items: center;
  text-align: center;
}

.profile-card__avatar {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #24292f, var(--tc-brand));
  color: #ffffff;
  font-size: 26px;
  font-weight: 800;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.stats-grid div {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 4px;
  background: #f5f7fa;
  transition: background var(--tc-duration) var(--tc-ease);
}

.stats-grid div:hover {
  background: var(--tc-brand-soft);
}

.stats-grid strong {
  color: var(--tc-text-strong);
  font-size: 19px;
}

.stats-grid span {
  color: var(--tc-text-muted);
  font-size: 12px;
}

@media (max-width: 980px) {
  .home-nav,
  .recommend,
  .home-layout {
    grid-template-columns: 1fr;
  }

  .home-sidebar {
    display: none;
  }
}

@media (max-width: 620px) {
  .home-nav__search,
  .recommend__item {
    grid-template-columns: 1fr;
  }

  .recommend__hero {
    min-height: 230px;
  }

  .recommend__item img,
  .recommend__item-mark {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
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
