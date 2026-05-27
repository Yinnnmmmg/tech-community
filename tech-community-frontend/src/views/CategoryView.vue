<script setup lang="ts">
import { Flame, FolderOpen } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listArticles, listCategories } from '@/api/article'
import type { ArticleCategory, ArticleListItem } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const route = useRoute()
const router = useRouter()

const categories = ref<ArticleCategory[]>([])
const articles = ref<ArticleListItem[]>([])
const nextCursor = ref<number | null>(1)
const loading = ref(false)
const loadingMore = ref(false)
const categoryId = computed(() => Number(route.params.id || 0))
const currentCategory = computed(() => categories.value.find((item) => item.id === categoryId.value) ?? null)
const hotArticles = computed(() =>
  [...articles.value]
    .sort((left, right) => hotScore(right) - hotScore(left))
    .slice(0, 6)
)

onMounted(async () => {
  await loadCategoryList()
  await loadFirstPage()
})

watch(
  () => route.params.id,
  async () => {
    nextCursor.value = 1
    await loadFirstPage()
  }
)

async function loadCategoryList() {
  try {
    categories.value = await listCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类加载失败')
  }
}

async function loadFirstPage() {
  if (!categoryId.value) {
    articles.value = []
    nextCursor.value = null
    return
  }
  loading.value = true
  try {
    const result = await listArticles(0, 10, categoryId.value)
    articles.value = result.list
    nextCursor.value = result.nextCursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类文章加载失败')
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextCursor.value) {
    return
  }
  loadingMore.value = true
  try {
    const result = await listArticles(nextCursor.value, 10, categoryId.value)
    articles.value.push(...result.list)
    nextCursor.value = result.nextCursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类文章加载失败')
  } finally {
    loadingMore.value = false
  }
}

function openCategory(target: ArticleCategory | null) {
  if (!target) {
    router.push({ name: 'home' })
    return
  }
  router.push({ name: 'category', params: { id: target.id } })
}

function hotScore(article: ArticleListItem) {
  return article.likeCount * 3 + article.collectionCount * 2 + article.commentCount
}
</script>

<template>
  <div class="category-view">
    <section class="category-nav surface">
      <div class="category-nav__tabs">
        <button
          v-for="item in categories"
          :key="item.id"
          class="category-pill"
          :class="{ active: item.id === categoryId }"
          @click="openCategory(item)"
        >
          {{ item.name }}
        </button>
      </div>
    </section>

    <div class="category-layout">
      <section class="category-feed surface">
        <div class="category-hero">
          <div class="category-hero__icon">
            <FolderOpen :size="24" />
          </div>
          <div>
            <h1 class="page-title">{{ currentCategory?.name || '分类' }}</h1>
            <p>{{ currentCategory ? `浏览 ${currentCategory.name} 分类下的最新技术文章。` : '正在加载分类信息。' }}</p>
          </div>
        </div>

        <LoadingState v-if="loading" />
        <EmptyState v-else-if="!currentCategory" title="分类不存在" description="这个分类可能已下线或地址有误。" />
        <EmptyState v-else-if="!articles.length" :title="`${currentCategory.name} 暂无文章`" description="这个分类还在等第一篇内容。" />
        <template v-else>
          <ArticleCard v-for="item in articles" :key="item.articleId" :article="item" />
          <div class="load-more">
            <el-button :loading="loadingMore" :disabled="!nextCursor" @click="loadMore">
              {{ nextCursor ? '加载更多' : '已经到底了' }}
            </el-button>
          </div>
        </template>
      </section>

      <aside class="category-side">
        <div class="category-side__inner">
          <section class="sidebar-card">
            <div class="sidebar-card__title">
              <Flame :size="18" />
              <h2>本分类热门</h2>
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
.category-view {
  display: grid;
  gap: 20px;
}

.category-nav {
  display: flex;
  align-items: center;
  padding: 14px 20px;
}

.category-nav__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-pill {
  flex: 0 0 auto;
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #3d4654;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  transition: background var(--tc-duration) var(--tc-ease), color var(--tc-duration) var(--tc-ease);
}

.category-pill:hover,
.category-pill.active {
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

.category-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}

.category-feed {
  padding: 22px;
}

.category-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--tc-border);
}

.category-hero__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
}

.category-hero p {
  margin: 8px 0 0;
  color: var(--tc-text-muted);
  line-height: 1.6;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 20px 0 2px;
}

.category-side {
  position: sticky;
  top: 80px;
}

.category-side__inner {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(100vh - 80px - 24px);
  overflow-y: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.18) transparent;
}

.category-side__inner::-webkit-scrollbar {
  width: 5px;
}

.category-side__inner::-webkit-scrollbar-track {
  background: transparent;
}

.category-side__inner::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.18);
  border-radius: 3px;
}

.category-side__inner::-webkit-scrollbar-thumb:hover {
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

.sidebar-card p,
.sidebar-muted {
  margin: 0;
  color: #5f6877;
  line-height: 1.7;
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
}

.hot-list__item:hover strong {
  color: var(--tc-brand);
}

@media (max-width: 980px) {
  .category-layout {
    grid-template-columns: 1fr;
  }

  .category-side {
    display: none;
  }
}

@media (max-width: 620px) {
  .category-nav {
    padding: 10px 14px;
  }

  .category-nav__tabs {
    gap: 6px;
  }

  .category-pill {
    padding: 0 12px;
    min-height: 32px;
    font-size: 14px;
  }

  .category-hero {
    grid-template-columns: 1fr;
  }

  .category-feed {
    padding: 16px;
  }
}
</style>
