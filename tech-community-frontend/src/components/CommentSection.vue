<script setup lang="ts">
import { MessageCircle, Send, ThumbsUp, Trash2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { deleteComment, likeComment, listArticleComments, listCommentReplies, publishComment } from '@/api/comment'
import type { CommentListItem } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
import { resolveAssetUrl } from '@/utils/asset'

const props = defineProps<{
  articleId: number
}>()

const authStore = useAuthStore()
const router = useRouter()

const loading = ref(false)
const topContent = ref('')
const page = ref(1)
const total = ref(0)
const publicComments = ref<CommentListItem[]>([])
const myComments = ref<CommentListItem[]>([])
const replies = reactive<Record<number, CommentListItem[]>>({})
const replyContent = reactive<Record<number, string>>({})
const expanded = reactive<Record<number, boolean>>({})

const comments = computed(() => {
  const ownIds = new Set(myComments.value.map((item) => item.commentId))
  return [...myComments.value, ...publicComments.value.filter((item) => !ownIds.has(item.commentId))]
})

onMounted(loadComments)

watch(
  () => props.articleId,
  () => loadComments()
)

async function loadComments(targetPage = 1) {
  loading.value = true
  try {
    const result = await listArticleComments(props.articleId, targetPage)
    page.value = targetPage
    total.value = result.publicPage.total
    publicComments.value = result.publicPage.records
    myComments.value = result.mine ?? []
  } finally {
    loading.value = false
  }
}

async function submitTopComment() {
  if (!ensureLogin()) {
    return
  }
  const content = topContent.value.trim()
  if (!content) {
    return
  }
  await publishComment({ articleId: props.articleId, content })
  topContent.value = ''
  ElMessage.success('评论已提交')
  await loadComments(1)
}

async function submitReply(comment: CommentListItem) {
  if (!ensureLogin()) {
    return
  }
  const content = (replyContent[comment.commentId] ?? '').trim()
  if (!content) {
    return
  }
  await publishComment({
    articleId: props.articleId,
    content,
    parentCommentId: comment.commentId,
    replyToCommentId: comment.commentId,
    replyToUserId: comment.userId
  })
  replyContent[comment.commentId] = ''
  ElMessage.success('回复已提交')
  await loadReplies(comment.commentId)
}

async function toggleReplies(comment: CommentListItem) {
  expanded[comment.commentId] = !expanded[comment.commentId]
  if (expanded[comment.commentId] && !replies[comment.commentId]) {
    await loadReplies(comment.commentId)
  }
}

async function loadReplies(commentId: number) {
  const result = await listCommentReplies(commentId)
  replies[commentId] = [...(result.mine ?? []), ...result.publicPage.records]
}

async function handleLike(comment: CommentListItem) {
  if (!ensureLogin()) {
    return
  }
  const result = await likeComment(comment.commentId)
  comment.likeCount = result.likeCount
  comment.likeStat = result.likeStat
}

async function handleDelete(commentId: number) {
  if (!ensureLogin()) {
    return
  }
  await ElMessageBox.confirm('删除后无法恢复，确定删除这条评论吗？', '删除评论', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteComment(commentId)
  ElMessage.success('评论已删除')
  await loadComments(page.value)
}

function ensureLogin() {
  if (authStore.isAuthenticated) {
    return true
  }
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  return false
}

function getAvatarUrl(photo: string | undefined) {
  return resolveAssetUrl(photo) || ''
}
</script>

<template>
  <section class="comments surface">
    <div class="toolbar">
      <h2 class="section-title">评论</h2>
      <span class="muted">{{ total }} 条</span>
    </div>

    <div class="comment-editor">
      <el-input
        v-model="topContent"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        maxlength="800"
        show-word-limit
        placeholder="写下你的评论"
        @keydown.enter.exact.prevent="submitTopComment"
      />
      <div class="split-actions">
        <el-button type="primary" class="icon-button" @click="submitTopComment">
          <Send :size="16" />
          <span>发布评论</span>
        </el-button>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="4" animated />
    <div v-else-if="!comments.length" class="comment-empty">
      <MessageCircle :size="28" />
      <span>还没有评论</span>
    </div>
    <div v-else class="comment-list">
      <article v-for="item in comments" :key="item.commentId" class="comment-item">
        <div class="comment-item__head">
          <img
            v-if="item.photo"
            class="comment-avatar"
            :src="getAvatarUrl(item.photo)"
            :alt="item.username"
          />
          <span v-else class="comment-avatar comment-avatar--default">
            {{ (item.username || '匿')[0] }}
          </span>
          <RouterLink class="comment-user-link" :to="{ name: 'user-profile', params: { userId: item.userId } }">
            {{ item.username || '匿名用户' }}
          </RouterLink>
          <span>{{ item.createTime }}</span>
        </div>
        <p class="comment-item__content">{{ item.content }}</p>
        <p v-if="item.rejectReason" class="comment-item__reject">{{ item.rejectReason }}</p>
        <div class="comment-item__actions">
          <el-button text class="icon-button" @click="handleLike(item)">
            <ThumbsUp :size="15" :fill="item.likeStat ? 'currentColor' : 'none'" />
            <span>{{ item.likeCount || 0 }}</span>
          </el-button>
          <el-button text class="icon-button" @click="toggleReplies(item)">
            <MessageCircle :size="15" />
            <span>{{ expanded[item.commentId] ? '收起回复' : `回复 ${item.replyCount || 0}` }}</span>
          </el-button>
          <el-button
            v-if="item.canDelete"
            text
            class="icon-button danger-link"
            @click="handleDelete(item.commentId)"
          >
            <Trash2 :size="15" />
            <span>删除</span>
          </el-button>
        </div>

        <div v-if="expanded[item.commentId]" class="reply-box">
          <div v-if="replies[item.commentId]?.length" class="reply-list">
            <div v-for="reply in replies[item.commentId]" :key="reply.commentId" class="reply-item">
              <div class="reply-meta">
                <img
                  v-if="reply.photo"
                  class="comment-avatar"
                  :src="getAvatarUrl(reply.photo)"
                  :alt="reply.username"
                />
                <span v-else class="comment-avatar comment-avatar--default">
                  {{ (reply.username || '匿')[0] }}
                </span>
                <RouterLink class="comment-user-link" :to="{ name: 'user-profile', params: { userId: reply.userId } }">
                  {{ reply.username || '匿名用户' }}
                </RouterLink>
                <span v-if="reply.replyToUsername">
                  回复
                  <RouterLink
                    v-if="reply.replyToUserId"
                    class="comment-user-link"
                    :to="{ name: 'user-profile', params: { userId: reply.replyToUserId } }"
                  >
                    {{ reply.replyToUsername }}
                  </RouterLink>
                  <template v-else>{{ reply.replyToUsername }}</template>
                </span>
              </div>
              <p>{{ reply.content }}</p>
              <div class="comment-item__actions">
                <el-button text class="icon-button" @click="handleLike(reply)">
                  <ThumbsUp :size="15" :fill="reply.likeStat ? 'currentColor' : 'none'" />
                  <span>{{ reply.likeCount || 0 }}</span>
                </el-button>
                <el-button
                  v-if="reply.canDelete"
                  text
                  class="icon-button danger-link"
                  @click="handleDelete(reply.commentId)"
                >
                  <Trash2 :size="15" />
                  <span>删除</span>
                </el-button>
              </div>
            </div>
          </div>
          <el-input
            v-model="replyContent[item.commentId]"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            maxlength="500"
            show-word-limit
            :placeholder="`回复 ${item.username || '这条评论'}`"
            @keydown.enter.exact.prevent="submitReply(item)"
          />
          <el-button type="primary" class="icon-button" @click="submitReply(item)">
            <Send :size="15" />
            <span>发送回复</span>
          </el-button>
        </div>
      </article>
    </div>

    <div v-if="total > 20" class="comment-pagination">
      <el-pagination
        layout="prev, pager, next"
        :page-size="20"
        :total="total"
        :current-page="page"
        @current-change="loadComments"
      />
    </div>
  </section>
</template>

<style scoped>
.comments {
  display: grid;
  gap: 18px;
  padding: 20px;
}

.comment-editor {
  display: grid;
  gap: 10px;
}

.comment-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 28px;
  color: var(--tc-text-muted);
}

.comment-list {
  display: grid;
  gap: 14px;
}

.comment-item {
  display: grid;
  gap: 8px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--tc-border);
}

.comment-item:last-child {
  border-bottom: 0;
}

.comment-item__head,
.reply-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  color: var(--tc-text-muted);
  font-size: 13px;
}

.comment-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.comment-avatar--default {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--tc-brand);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  object-fit: unset;
}

.comment-user-link {
  color: var(--tc-text-strong);
  font-weight: 700;
}

.comment-user-link:hover {
  color: var(--tc-brand);
}

.comment-item__content {
  margin: 0;
  color: #2c3340;
  line-height: 1.7;
  white-space: pre-wrap;
}

.comment-item__reject {
  margin: 0;
  color: #d93025;
  font-size: 13px;
}

.comment-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.reply-box {
  display: grid;
  gap: 10px;
  margin-top: 6px;
  padding: 12px;
  border-radius: 4px;
  background: #f5f7fa;
}

.reply-list {
  display: grid;
  gap: 10px;
}

.reply-item {
  display: grid;
  gap: 4px;
  color: var(--tc-text-muted);
  font-size: 13px;
}

.reply-item p {
  margin: 0;
  color: #2c3340;
  line-height: 1.6;
  white-space: pre-wrap;
}

.comment-pagination {
  display: flex;
  justify-content: center;
}
</style>
