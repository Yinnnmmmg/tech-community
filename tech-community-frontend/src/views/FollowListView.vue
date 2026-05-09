<script setup lang="ts">
import { UserCheck, UserMinus, UserPlus, Users } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { followUser, getFanList, getFollowList, getFollowStats, unfollowUser } from '@/api/user'
import type { FollowStats, UserFollowListItem } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const authStore = useAuthStore()

const userId = computed(() => Number(route.params.userId))
const mode = computed(() => (route.name === 'fans' ? 'fans' : 'follows'))
const title = computed(() => (mode.value === 'fans' ? '粉丝' : '关注'))
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const stats = ref<FollowStats | null>(null)
const users = ref<UserFollowListItem[]>([])

onMounted(loadData)

watch([userId, mode], () => {
  page.value = 1
  loadData()
})

async function loadData(targetPage = page.value) {
  loading.value = true
  try {
    const [nextStats, result] = await Promise.all([
      getFollowStats(userId.value),
      mode.value === 'fans' ? getFanList(userId.value, targetPage) : getFollowList(userId.value, targetPage)
    ])
    stats.value = nextStats
    users.value = result.records
    total.value = result.total
    page.value = targetPage
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '列表加载失败')
  } finally {
    loading.value = false
  }
}

async function toggleFollow(user: UserFollowListItem) {
  try {
    const result = user.followed ? await unfollowUser(user.userId) : await followUser(user.userId)
    user.followed = result.followed
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关注操作失败')
  }
}
</script>

<template>
  <section class="follow-page">
    <div class="follow-head surface">
      <div>
        <h1 class="page-title">{{ title }}</h1>
        <div class="meta-row">
          <span>关注 {{ stats?.followCount ?? 0 }}</span>
          <span>粉丝 {{ stats?.fanCount ?? 0 }}</span>
        </div>
      </div>
      <Users :size="28" />
    </div>

    <div class="follow-tabs">
      <RouterLink :to="{ name: 'follows', params: { userId } }">
        <el-button :type="mode === 'follows' ? 'primary' : 'default'">关注</el-button>
      </RouterLink>
      <RouterLink :to="{ name: 'fans', params: { userId } }">
        <el-button :type="mode === 'fans' ? 'primary' : 'default'">粉丝</el-button>
      </RouterLink>
    </div>

    <LoadingState v-if="loading" />
    <EmptyState v-else-if="!users.length" :title="`还没有${title}`" />
    <div v-else class="user-list">
      <article v-for="user in users" :key="user.userId" class="user-card surface">
        <RouterLink class="avatar" :to="{ name: 'user-profile', params: { userId: user.userId } }">
          <img v-if="user.photo" :src="user.photo" alt="" />
          <span v-else>{{ (user.username || 'U').slice(0, 1) }}</span>
        </RouterLink>
        <RouterLink class="user-card__body" :to="{ name: 'user-profile', params: { userId: user.userId } }">
          <strong>{{ user.username || '匿名用户' }}</strong>
          <span v-if="user.position || user.company">{{ [user.position, user.company].filter(Boolean).join(' · ') }}</span>
          <p v-if="user.profile">{{ user.profile }}</p>
        </RouterLink>
        <el-button
          v-if="user.userId !== authStore.user?.id"
          class="icon-button"
          :type="user.followed ? 'default' : 'primary'"
          @click="toggleFollow(user)"
        >
          <UserMinus v-if="user.followed" :size="16" />
          <UserPlus v-else :size="16" />
          <span>{{ user.followed ? '已关注' : '关注' }}</span>
        </el-button>
        <el-button v-else class="icon-button" disabled>
          <UserCheck :size="16" />
          <span>这是你</span>
        </el-button>
      </article>
      <div class="pagination">
        <el-pagination
          layout="prev, pager, next"
          :page-size="10"
          :total="total"
          :current-page="page"
          @current-change="loadData"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.follow-page {
  display: grid;
  gap: 16px;
}

.follow-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
}

.follow-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.user-list {
  display: grid;
  gap: 12px;
}

.user-card {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  overflow: hidden;
  border-radius: 50%;
  background: var(--tc-brand-soft);
  color: var(--tc-brand);
  font-weight: 800;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-card__body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.user-card__body strong {
  color: var(--tc-text-strong);
}

.user-card__body:hover strong {
  color: var(--tc-brand);
}

.user-card__body span,
.user-card__body p {
  margin: 0;
  color: var(--tc-text-muted);
  line-height: 1.5;
}

.pagination {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

@media (max-width: 640px) {
  .user-card {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .user-card .el-button {
    grid-column: 1 / -1;
    width: fit-content;
  }
}
</style>
