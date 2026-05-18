<script setup lang="ts">
import { CheckCircle2, Loader2, Upload, X, XCircle } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { reactive, ref, watch } from 'vue'

import { uploadAttachment } from '@/api/article'
import type { ArticleAttachment } from '@/api/types'

interface FileTask {
  name: string
  size: number
  progress: number
  status: 'uploading' | 'done' | 'error'
  error?: string
  attachment?: ArticleAttachment
}

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
const taskMap = reactive<Map<string, FileTask>>(new Map())
const taskList = ref<FileTask[]>([])

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
  if (!files.length) return

  // Deduplicate by name + size
  const newFiles = files.filter(
    (f) => !attachments.value.some((a) => a.fileName === f.name && a.fileSize === f.size)
  )
  if (!newFiles.length) {
    input.value = ''
    return
  }

  uploading.value = true
  const tasks: FileTask[] = newFiles.map((file) => {
    const task: FileTask = {
      name: file.name,
      size: file.size,
      progress: 0,
      status: 'uploading'
    }
    taskMap.set(file.name, task)
    return task
  })
  taskList.value = [...taskMap.values()]

  const results = await Promise.allSettled(
    newFiles.map((file) =>
      uploadAttachment(file, (progress) => {
        const task = taskMap.get(file.name)
        if (task) task.progress = progress
      }).then((attachment) => {
        const task = taskMap.get(file.name)
        if (task) {
          task.status = 'done'
          task.progress = 100
          task.attachment = attachment
          attachments.value.push(attachment)
        }
      })
    )
  )

  // Mark errors
  results.forEach((result, i) => {
    if (result.status === 'rejected') {
      const task = taskMap.get(newFiles[i].name)
      if (task) {
        task.status = 'error'
        task.error = result.reason instanceof Error ? result.reason.message : '上传失败'
      }
    }
  })

  sync()
  uploading.value = false
  input.value = ''

  const doneCount = results.filter((r) => r.status === 'fulfilled').length
  const failCount = results.length - doneCount
  if (failCount === 0) {
    ElMessage.success(`已上传 ${doneCount} 个附件`)
  } else {
    ElMessage.warning(`${doneCount} 个成功，${failCount} 个失败`)
  }
}

function removeAttachment(id: number) {
  const item = attachments.value.find((a) => a.attachmentId === id)
  attachments.value = attachments.value.filter((a) => a.attachmentId !== id)
  if (item) {
    taskMap.delete(item.fileName)
    taskList.value = [...taskMap.values()]
  }
  sync()
}

function sync() {
  const ids = attachments.value.map((item) => item.attachmentId)
  emit('update:modelValue', ids)
  emit('change', attachments.value)
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="uploader">
    <input ref="fileInput" type="file" multiple :disabled="uploading" @change="onFileChange" />

    <el-button class="icon-button" :loading="uploading" native-type="button" :disabled="uploading" @click="triggerUpload">
      <Upload :size="16" />
      <span>{{ uploading ? '上传中...' : '上传附件' }}</span>
    </el-button>

    <!-- Progress list -->
    <div v-if="taskList.length" class="progress-list">
      <div
        v-for="task in taskList"
        :key="task.name"
        class="progress-item"
        :class="`progress-item--${task.status}`"
      >
        <div class="progress-item__icon">
          <Loader2 v-if="task.status === 'uploading'" :size="15" class="spin" />
          <CheckCircle2 v-else-if="task.status === 'done'" :size="15" />
          <XCircle v-else :size="15" />
        </div>
        <div class="progress-item__info">
          <span class="progress-item__name">{{ task.name }}</span>
          <span class="progress-item__size">{{ formatSize(task.size) }}</span>
          <template v-if="task.status === 'uploading'">
            <div class="progress-bar">
              <div class="progress-bar__fill" :style="{ width: task.progress + '%' }" />
            </div>
            <span class="progress-item__percent">{{ task.progress }}%</span>
          </template>
          <span v-else-if="task.status === 'error'" class="progress-item__error">{{ task.error }}</span>
        </div>
      </div>
    </div>

    <!-- Uploaded list -->
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

/* ── Progress ── */
.progress-list {
  display: grid;
  gap: 8px;
}

.progress-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--tc-border);
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
}

.progress-item--error {
  border-color: #fca5a5;
  background: #fef2f2;
}

.progress-item__icon {
  display: grid;
  place-items: center;
  padding-top: 1px;
  color: var(--tc-brand);
  flex-shrink: 0;
}

.progress-item--done .progress-item__icon {
  color: #22c55e;
}

.progress-item--error .progress-item__icon {
  color: #ef4444;
}

.progress-item__info {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 10px;
  flex: 1;
  min-width: 0;
}

.progress-item__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--tc-text);
}

.progress-item__size {
  color: var(--tc-text-muted);
  font-size: 12px;
}

.progress-item__percent {
  color: var(--tc-text-muted);
  font-size: 12px;
}

.progress-item__error {
  grid-column: 1 / -1;
  color: #ef4444;
  font-size: 12px;
}

.progress-bar {
  grid-column: 1 / -1;
  height: 4px;
  border-radius: 2px;
  background: #f0f2f5;
  overflow: hidden;
}

.progress-bar__fill {
  height: 100%;
  border-radius: 2px;
  background: var(--tc-brand);
  transition: width 0.3s ease;
}

/* ── Spinner ── */
.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ── Attachment list ── */
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
