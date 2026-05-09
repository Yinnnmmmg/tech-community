<script setup lang="ts">
import {
  CalendarDays,
  Edit3,
  FileHeart,
  FileText,
  Heart,
  MapPin,
  UserMinus,
  UserPlus,
  Users
} from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCurrentUser } from '@/api/auth'
import {
  followUser,
  getFanList,
  getFollowList,
  getUserProfile,
  listUserArticles,
  listUserCollectionArticles,
  listUserLikeArticles,
  unfollowUser,
  updateCurrentUserProfile
} from '@/api/user'
import type { ArticleListItem, UserFollowListItem, UserProfile, UserProfileUpdateReq } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/authStore'

type TabKey = 'articles' | 'collections' | 'likes' | 'follows' | 'fans'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const userId = computed(() => Number(route.params.userId))
const profile = ref<UserProfile | null>(null)
const loadingProfile = ref(false)
const actionLoading = ref(false)
const editVisible = ref(false)
const activeTab = ref<TabKey>('articles')

const articleItems = ref<ArticleListItem[]>([])
const userItems = ref<UserFollowListItem[]>([])
const listLoading = ref(false)

const articlePage = ref(1)
const articleTotal = ref(0)
const userPage = ref(1)
const userTotal = ref(0)
const pageSize = 10

const editForm = reactive<UserProfileUpdateReq>({
  username: '',
  photo: '',
  position: '',
  company: '',
  profile: ''
})

const isSelf = computed(() => Boolean(profile.value?.self))
const canEdit = computed(() => Boolean(authStore.isAuthenticated && isSelf.value))

const visibleTabs = computed(() => {
  const tabs: Array<{ key: TabKey; label: string }> = [{ key: 'articles', label: isSelf.value ? '我的文章' : 'TA 的文章' }]
  if (isSelf.value) {
    tabs.push({ key: 'collections', label: '我的收藏' })
    tabs.push({ key: 'likes', label: '我的点赞' })
  }
  tabs.push({ key: 'follows', label: '关注' })
  tabs.push({ key: 'fans', label: '粉丝' })
  return tabs
})

const articleEmptyConfig = computed(() => {
  switch (activeTab.value) {
    case 'collections':
      return { title: '还没有收藏文章', description: '看到喜欢的内容后，这里会慢慢丰富起来。' }
    case 'likes':
      return { title: '还没有点赞文章', description: '给喜欢的文章点个赞，这里就会出现记录。' }
    default:
      return { title: isSelf.value ? '还没有公开文章' : 'TA 还没有公开文章', description: '等有新内容发布后，这里就会更新。' }
  }
})

const userEmptyTitle = computed(() => (activeTab.value === 'fans' ? '还没有粉丝' : '还没有关注任何人'))

onMounted(loadPageData)

watch(userId, async () => {
  activeTab.value = 'articles'
  articlePage.value = 1
  userPage.value = 1
  await loadPageData()
})

watch(activeTab, async () => {
  if (!visibleTabs.value.some((item) => item.key === activeTab.value)) {
    activeTab.value = 'articles'
    return
  }
  await loadCurrentTab()
})

async function loadPageData() {
  if (!userId.value) {
    profile.value = null
    articleItems.value = []
    userItems.value = []
    return
  }
  await loadProfile()
  if (!visibleTabs.value.some((item) => item.key === activeTab.value)) {
    activeTab.value = 'articles'
  }
  await loadCurrentTab()
}

async function loadProfile() {
  loadingProfile.value = true
  try {
    profile.value = await getUserProfile(userId.value)
  } catch (error) {
    profile.value = null
    ElMessage.error(error instanceof Error ? error.message : '个人主页加载失败')
  } finally {
    loadingProfile.value = false
  }
}

async function loadCurrentTab(targetPage?: number) {
  if (!profile.value) {
    return
  }
  listLoading.value = true
  try {
    if (activeTab.value === 'articles' || activeTab.value === 'collections' || activeTab.value === 'likes') {
      await loadArticleTab(targetPage ?? articlePage.value)
    } else {
      await loadUserTab(targetPage ?? userPage.value)
    }
  } finally {
    listLoading.value = false
  }
}

async function loadArticleTab(targetPage = articlePage.value) {
  let result
  if (activeTab.value === 'collections') {
    result = await listUserCollectionArticles(userId.value, targetPage, pageSize)
  } else if (activeTab.value === 'likes') {
    result = await listUserLikeArticles(userId.value, targetPage, pageSize)
  } else {
    result = await listUserArticles(userId.value, targetPage, pageSize)
  }
  articleItems.value = result.records
  articleTotal.value = result.total
  articlePage.value = targetPage
}

async function loadUserTab(targetPage = userPage.value) {
  const result =
    activeTab.value === 'fans'
      ? await getFanList(userId.value, targetPage, pageSize)
      : await getFollowList(userId.value, targetPage, pageSize)
  userItems.value = result.records
  userTotal.value = result.total
  userPage.value = targetPage
}

function openEditDialog() {
  if (!profile.value) {
    return
  }
  editForm.username = profile.value.username ?? ''
  editForm.photo = profile.value.photo ?? ''
  editForm.position = profile.value.position ?? ''
  editForm.company = profile.value.company ?? ''
  editForm.profile = profile.value.profile ?? ''
  editVisible.value = true
}

async function saveProfile() {
  actionLoading.value = true
  try {
    await updateCurrentUserProfile({
      username: editForm.username?.trim(),
      photo: editForm.photo?.trim(),
      position: editForm.position?.trim(),
      company: editForm.company?.trim(),
      profile: editForm.profile?.trim()
    })
    if (authStore.isAuthenticated && authStore.user?.id === userId.value) {
      authStore.user = await getCurrentUser()
    }
    editVisible.value = false
    ElMessage.success('个人资料已更新')
    await loadProfile()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    actionLoading.value = false
  }
}

async function toggleProfileFollow() {
  if (!profile.value || profile.value.self) {
    return
  }
  if (!authStore.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  actionLoading.value = true
  try {
    const result = profile.value.followed
      ? await unfollowUser(profile.value.userId)
      : await followUser(profile.value.userId)
    profile.value.followed = result.followed
    profile.value.fanCount += result.followed ? 1 : -1
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关注操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function toggleUserFollow(user: UserFollowListItem) {
  if (!authStore.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const result = user.followed ? await unfollowUser(user.userId) : await followUser(user.userId)
    user.followed = result.followed
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关注操作失败')
  }
}
</script>

<template>
  <LoadingState v-if="loadingProfile && !profile" />
  <EmptyState v-else-if="!profile" title="这个用户不存在" description="换个用户试试，或者返回首页继续逛逛。" />
  <div v-else class="profile-page">
    <section class="profile-hero surface">
      <div class="profile-hero__main">
        <div class="profile-avatar">
          <img v-if="profile.photo" :src="profile.photo" alt="" />
          <span v-else>{{ (profile.username || 'U').slice(0, 1) }}</span>
        </div>

        <div class="profile-meta">
          <div class="profile-title">
            <h1>{{ profile.username || `用户 ${profile.userId}` }}</h1>
            <el-tag v-if="profile.userRole === 1" type="warning" effect="plain">管理员</el-tag>
          </div>
          <p v-if="profile.position || profile.company" class="profile-subtitle">
            <MapPin :size="16" />
            <span>{{ [profile.position, profile.company].filter(Boolean).join(' · ') }}</span>
          </p>
          <p class="profile-bio">{{ profile.profile || '这个人很低调，还没写个人介绍。' }}</p>
          <div class="profile-extra">
            <span>
              <CalendarDays :size="15" />
              <span>{{ profile.createTime || '加入时间未知' }}</span>
            </span>
          </div>
        </div>
      </div>

      <div class="profile-actions">
        <el-button v-if="canEdit" type="primary" class="icon-button" @click="openEditDialog">
          <Edit3 :size="16" />
          <span>编辑资料</span>
        </el-button>
        <el-button
          v-else
          class="icon-button"
          :type="profile.followed ? 'default' : 'primary'"
          :loading="actionLoading"
          @click="toggleProfileFollow"
        >
          <UserMinus v-if="profile.followed" :size="16" />
          <UserPlus v-else :size="16" />
          <span>{{ profile.followed ? '已关注' : '关注' }}</span>
        </el-button>
      </div>
    </section>

    <section class="profile-stats">
      <article class="stat-card surface">
        <FileText :size="18" />
        <strong>{{ profile.articleCount }}</strong>
        <span>文章</span>
      </article>
      <article v-if="isSelf" class="stat-card surface">
        <FileHeart :size="18" />
        <strong>{{ profile.followCount }}</strong>
        <span>关注中</span>
      </article>
      <article v-if="isSelf" class="stat-card surface">
        <Heart :size="18" />
        <strong>{{ profile.fanCount }}</strong>
        <span>粉丝</span>
      </article>
      <article v-if="!isSelf" class="stat-card surface">
        <Users :size="18" />
        <strong>{{ profile.followCount }}</strong>
        <span>关注</span>
      </article>
      <article v-if="!isSelf" class="stat-card surface">
        <Users :size="18" />
        <strong>{{ profile.fanCount }}</strong>
        <span>粉丝</span>
      </article>
    </section>

    <section class="profile-content surface">
      <el-tabs v-model="activeTab" class="profile-tabs">
        <el-tab-pane v-for="tab in visibleTabs" :key="tab.key" :label="tab.label" :name="tab.key" />
      </el-tabs>

      <LoadingState v-if="listLoading" />

      <template v-else-if="activeTab === 'articles' || activeTab === 'collections' || activeTab === 'likes'">
        <EmptyState
          v-if="!articleItems.length"
          :title="articleEmptyConfig.title"
          :description="articleEmptyConfig.description"
        />
        <template v-else>
          <ArticleCard v-for="item in articleItems" :key="item.articleId" :article="item" />
          <div class="pagination">
            <el-pagination
              layout="prev, pager, next"
              :page-size="pageSize"
              :total="articleTotal"
              :current-page="articlePage"
              @current-change="loadCurrentTab"
            />
          </div>
        </template>
      </template>

      <template v-else>
        <EmptyState v-if="!userItems.length" :title="userEmptyTitle" description="等有新的关系建立后，这里会自动更新。" />
        <div v-else class="user-list">
          <article v-for="user in userItems" :key="user.userId" class="user-card">
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
              @click="toggleUserFollow(user)"
            >
              <UserMinus v-if="user.followed" :size="16" />
              <UserPlus v-else :size="16" />
              <span>{{ user.followed ? '已关注' : '关注' }}</span>
            </el-button>
            <el-button v-else class="icon-button" disabled>
              <Users :size="16" />
              <span>这是你</span>
            </el-button>
          </article>
          <div class="pagination">
            <el-pagination
              layout="prev, pager, next"
              :page-size="pageSize"
              :total="userTotal"
              :current-page="userPage"
              @current-change="loadCurrentTab"
            />
          </div>
        </div>
      </template>
    </section>

    <el-dialog v-model="editVisible" title="编辑个人资料" width="560px">
      <el-form label-position="top">
        <el-form-item label="显示名称">
          <el-input v-model="editForm.username" maxlength="32" placeholder="用于前台展示的昵称" />
        </el-form-item>
        <el-form-item label="头像链接">
          <el-input v-model="editForm.photo" placeholder="填写图片 URL" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="职位">
            <el-input v-model="editForm.position" maxlength="64" placeholder="例如：后端开发" />
          </el-form-item>
          <el-form-item label="公司">
            <el-input v-model="editForm.company" maxlength="64" placeholder="例如：Tech Community" />
          </el-form-item>
        </div>
        <el-form-item label="个人简介">
          <el-input
            v-model="editForm.profile"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 6 }"
            maxlength="300"
            show-word-limit
            placeholder="介绍一下你自己"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  display: grid;
  gap: 18px;
}

.profile-hero {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
}

.profile-hero__main {
  display: flex;
  gap: 18px;
  align-items: center;
  min-width: 0;
}

.profile-avatar {
  display: grid;
  place-items: center;
  width: 92px;
  height: 92px;
  overflow: hidden;
  border-radius: 24px;
  background: linear-gradient(135deg, #24292f, var(--tc-brand));
  color: #ffffff;
  flex: 0 0 auto;
  font-size: 34px;
  font-weight: 800;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-meta {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.profile-title {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.profile-title h1,
.profile-bio,
.profile-subtitle {
  margin: 0;
}

.profile-title h1 {
  color: var(--tc-text-strong);
  font-size: clamp(24px, 4vw, 34px);
}

.profile-subtitle,
.profile-extra {
  color: var(--tc-text-muted);
}

.profile-subtitle,
.profile-extra span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.profile-bio {
  max-width: 720px;
  color: #404857;
  line-height: 1.7;
}

.profile-actions {
  flex: 0 0 auto;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: grid;
  gap: 8px;
  justify-items: start;
  padding: 18px;
  color: var(--tc-text-muted);
}

.stat-card strong {
  color: var(--tc-text-strong);
  font-size: 24px;
}

.profile-content {
  padding: 22px;
}

.profile-tabs {
  margin-bottom: 8px;
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
  padding: 14px 0;
  border-bottom: 1px solid var(--tc-border);
}

.user-card:last-child {
  border-bottom: 0;
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
  padding-top: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 860px) {
  .profile-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-stats,
  .form-grid {
    grid-template-columns: 1fr;
  }
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

@media (max-width: 620px) {
  .profile-hero__main {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-avatar {
    width: 76px;
    height: 76px;
    border-radius: 20px;
    font-size: 28px;
  }
}
</style>
