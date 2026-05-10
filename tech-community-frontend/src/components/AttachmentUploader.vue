<script setup lang="ts">
import { Upload, X } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { ref, watch } from 'vue'

import { uploadAttachment } from '@/api/article'
import type { ArticleAttachment } from '@/api/types'

const props = defineProps<{
  modelValue: number[]
  initialAttachments?: ArticleAttachment[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  change: [value: ArticleAttachment[]]
}>()

const uploading = ref(false)
const attachments = ref<ArticleAttachment[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

function triggerUpload() {
  fileInput.value?.click()
}

watch(
  () => props.initialAttachments,
  (value) => {
    if (value && value.length && attachments.value.length === 0) {
      attachments.value = [...value]
      sync()
    }
  },
  { immediate: true }
)

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (!files.length) {
    return
  }
  uploading.value = true
  try {
    for (const file of files) {
      const attachment = await uploadAttachment(file)
      attachments.value.push(attachment)
    }
    sync()
    ElMessage.success('附件已上传')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '附件上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function removeAttachment(id: number) {
  attachments.value = attachments.value.filter((item) => item.attachmentId !== id)
  sync()
}

function sync() {
  const ids = attachments.value.map((item) => item.attachmentId)
  emit('update:modelValue', ids)
  emit('change', attachments.value)
}

function formatSize(size: number) {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="uploader">
    <input ref="fileInput" type="file" multiple :disabled="uploading" @change="onFileChange" />
    <el-button class="icon-button" :loading="uploading" native-type="button" @click="triggerUpload">
      <Upload :size="16" />
      <span>上传附件</span>
    </el-button>

    <div v-if="attachments.length" class="attachment-list">
      <div v-for="item in attachments" :key="item.attachmentId" class="attachment-item">
        <a :href="item.url" target="_blank" rel="noreferrer">{{ item.fileName }}</a>
        <span>{{ formatSize(item.fileSize) }}</span>
        <el-button text class="danger-link" @click="removeAttachment(item.attachmentId)">
          <X :size="15" />
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.uploader {
  display: grid;
  gap: 10px;
}

.uploader input[type="file"] {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.attachment-list {
  display: grid;
  gap: 8px;
}

.attachment-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--tc-border);
  border-radius: 4px;
  background: #f7f8fa;
  color: var(--tc-text-muted);
  font-size: 13px;
}

.attachment-item a {
  overflow: hidden;
  color: var(--tc-brand);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
