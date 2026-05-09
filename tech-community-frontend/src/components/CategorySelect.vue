<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { listCategories } from '@/api/article'
import type { ArticleCategory } from '@/api/types'

defineProps<{
  modelValue: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
}>()

const categories = ref<ArticleCategory[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    categories.value = await listCategories()
  } finally {
    loading.value = false
  }
})

function handleChange(value: number | null) {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-select
    :model-value="modelValue"
    :loading="loading"
    clearable
    filterable
    placeholder="选择分类"
    @update:model-value="handleChange"
  >
    <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
  </el-select>
</template>
