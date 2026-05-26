<script setup lang="ts">
import { Flame, FolderOpen, Search } from 'lucide-vue-next'
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
const keyword = ref('')

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

function submitSearch() {
  const q = keyword.value.trim()
  if (q) {
    router.push({ name: 'search', query: { q } })
  }
}

function hotScore(article: ArticleListItem) {
  return article.likeCount * 3 + article.collectionCount * 2 + article.commentCount
}
</script>

<template>
  <div class="category-view">
    <section class="category-nav surface">
      <div class="category-nav__tabs">
        <button class="category-pill" @click="openCategory(null)">推荐</button>
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
      <div class="category-nav__search">
        <el-input v-model="keyword" placeholder="搜索文章" clearable @keyup.enter="submitSearch">
          <template #prefix><Search :size="17" /></template>
        </el-input>
        <el-button type="primary" class="icon-button" @click="submitSearch">
          <Search :size="16" />
          <span>搜索</span>
        </el-button>
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
        <section class="side-card">
          <div class="side-card__title">
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
          <p v-else class="muted">暂无热门内容</p>
        </section>
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
}

.category-nav__tabs {
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
}

.category-pill:hover,
.category-pill.active {
  color: var(--tc-brand);
  background: var(--tc-brand-soft);
}

.category-nav__search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
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
  display: grid;
}

.side-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--tc-border);
  border-radius: 6px;
  background: #ffffff;
}

.side-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tc-brand);
}

.side-card__title h2 {
  margin: 0;
  color: var(--tc-text-strong);
  font-size: 17px;
  letter-spacing: 0;
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
}

.hot-list__item:hover strong {
  color: var(--tc-brand-hover);
}

@media (max-width: 980px) {
  .category-nav,
  .category-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .category-nav__search,
  .category-hero {
    grid-template-columns: 1fr;
  }

  .category-feed {
    padding: 16px;
  }
}
</style>
