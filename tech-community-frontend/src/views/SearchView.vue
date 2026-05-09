<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { searchArticles } from '@/api/article'
import type { ArticleSearchHighlight } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const route = useRoute()
const router = useRouter()

const keyword = ref(String(route.query.q ?? ''))
const results = ref<ArticleSearchHighlight[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

onMounted(() => {
  if (keyword.value.trim()) {
    runSearch()
  }
})

watch(
  () => route.query.q,
  (value) => {
    keyword.value = String(value ?? '')
    page.value = 1
    if (keyword.value.trim()) {
      runSearch()
    } else {
      results.value = []
      total.value = 0
    }
  }
)

async function runSearch(targetPage = page.value) {
  const q = keyword.value.trim()
  if (!q) {
    return
  }
  loading.value = true
  try {
    const result = await searchArticles(q, targetPage, 10)
    results.value = result.records
    total.value = result.total
    page.value = targetPage
    if (route.query.q !== q) {
      router.replace({ name: 'search', query: { q } })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="search-page">
    <div class="search-box surface">
      <div>
        <h1 class="page-title">搜索文章</h1>
        <p>输入关键词，查找标题和正文中相关的技术内容。</p>
      </div>
      <div class="search-box__form">
        <el-input
          v-model="keyword"
          size="large"
          placeholder="输入关键词"
          clearable
          @keyup.enter="runSearch(1)"
        >
          <template #prefix><Search :size="18" /></template>
        </el-input>
        <el-button type="primary" size="large" class="icon-button" @click="runSearch(1)">
          <Search :size="17" />
          <span>搜索</span>
        </el-button>
      </div>
    </div>

    <LoadingState v-if="loading" />
    <EmptyState v-else-if="keyword && !results.length" title="没有搜索结果" description="换个关键词再试试。" />
    <EmptyState v-else-if="!keyword" title="等待关键词" description="可以搜索后端、前端、AI、数据库等内容。" />
    <section v-else class="search-results surface">
      <div class="toolbar search-results__head">
        <h2 class="section-title">共找到 {{ total }} 条结果</h2>
      </div>
      <ArticleCard v-for="item in results" :key="item.id" :article="item" />
      <div class="pagination">
        <el-pagination
          layout="prev, pager, next"
          :page-size="10"
          :total="total"
          :current-page="page"
          @current-change="runSearch"
        />
      </div>
    </section>
  </section>
</template>

<style scoped>
.search-page {
  display: grid;
  gap: 16px;
}

.search-box {
  display: grid;
  gap: 16px;
  padding: 22px;
}

.search-box p {
  margin: 8px 0 0;
  color: var(--tc-text-muted);
  line-height: 1.6;
}

.search-box__form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.search-results {
  padding: 20px 22px;
}

.search-results__head {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--tc-border);
}

.pagination {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}

@media (max-width: 620px) {
  .search-box__form {
    grid-template-columns: 1fr;
  }
}
</style>
