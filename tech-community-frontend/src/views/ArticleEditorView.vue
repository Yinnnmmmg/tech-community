<script setup lang="ts">
import { Save } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getArticleDetail, listCategories, publishArticle, updateArticle } from '@/api/article'
import type { ArticleAttachment, ArticleCategory } from '@/api/types'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import CategorySelect from '@/components/CategorySelect.vue'

const route = useRoute()
const router = useRouter()

const articleId = computed(() => Number(route.params.id || 0))
const isEdit = computed(() => Boolean(articleId.value))
const loading = ref(false)
const submitting = ref(false)
const categories = ref<ArticleCategory[]>([])
const initialAttachments = ref<ArticleAttachment[]>([])
const form = reactive({
  title: '',
  categoryId: null as number | null,
  content: '',
  attachmentIds: [] as number[]
})

onMounted(async () => {
  loading.value = true
  try {
    categories.value = await listCategories()
    if (isEdit.value) {
      const detail = await getArticleDetail(articleId.value)
      form.title = detail.title
      form.content = detail.content
      form.categoryId = categories.value.find((item) => item.name === detail.categoryName)?.id ?? null
      initialAttachments.value = detail.attachments ?? []
      form.attachmentIds = initialAttachments.value.map((item) => item.attachmentId)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文章加载失败')
  } finally {
    loading.value = false
  }
})

async function submit() {
  const title = form.title.trim()
  const content = form.content.trim()
  if (!title || !content || !form.categoryId) {
    ElMessage.warning('请补全文章内容')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title,
      content,
      categoryId: form.categoryId,
      attachmentIds: form.attachmentIds
    }
    const id = isEdit.value ? await updateArticle(articleId.value, payload) : await publishArticle(payload)
    ElMessage.success(isEdit.value ? '文章已更新' : '文章已发布，正在审核中')
    if (isEdit.value) {
      router.push({ name: 'article-detail', params: { id } })
    } else {
      router.push({ name: 'home' })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="editor surface" v-loading="loading">
    <div class="toolbar editor-head">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑文章' : '写文章' }}</h1>
        <p>选择分类、整理正文，也可以添加附件。</p>
      </div>
      <el-button type="primary" class="icon-button" :loading="submitting" @click="submit">
        <Save :size="16" />
        <span>保存</span>
      </el-button>
    </div>

    <el-form label-position="top" class="editor-form" @submit.prevent>
      <el-form-item label="标题">
        <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="输入文章标题" />
      </el-form-item>
      <el-form-item label="分类">
        <CategorySelect v-model="form.categoryId" />
      </el-form-item>
      <el-form-item label="内容">
        <el-input
          v-model="form.content"
          type="textarea"
          :autosize="{ minRows: 14, maxRows: 28 }"
          maxlength="20000"
          show-word-limit
          placeholder="写下正文内容"
        />
      </el-form-item>
      <el-form-item label="附件">
        <AttachmentUploader v-model="form.attachmentIds" :initial-attachments="initialAttachments" />
      </el-form-item>
    </el-form>
  </section>
</template>

<style scoped>
.editor {
  display: grid;
  gap: 22px;
  padding: 24px;
}

.editor-head p {
  margin: 8px 0 0;
  color: var(--tc-text-muted);
  line-height: 1.6;
}

.editor-form {
  display: grid;
  gap: 4px;
}
</style>
