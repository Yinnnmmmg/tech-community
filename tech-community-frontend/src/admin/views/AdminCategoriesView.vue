<script setup lang="ts">
import { Plus, RefreshCcw, Save } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import {
  createAdminCategory,
  getAdminCategories,
  updateAdminCategory,
  updateAdminCategoryStatus
} from '@/api/admin'
import type { AdminCategoryItem, AdminCategorySaveReq } from '@/api/types'

const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const rows = ref<AdminCategoryItem[]>([])
const form = reactive<AdminCategorySaveReq>({
  name: '',
  sort: 10,
  status: 1
})

async function loadCategories() {
  loading.value = true
  try {
    rows.value = await getAdminCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载分类失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.sort = 10
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: AdminCategoryItem) {
  editingId.value = row.id
  form.name = row.name
  form.sort = row.sort
  form.status = row.status
  dialogVisible.value = true
}

async function submit() {
  submitting.value = true
  try {
    if (editingId.value) {
      await updateAdminCategory(editingId.value, { ...form })
      ElMessage.success('分类已更新')
    } else {
      await createAdminCategory({ ...form })
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    await loadCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: AdminCategoryItem, status: number) {
  try {
    await updateAdminCategoryStatus(row.id, status)
    ElMessage.success(status === 1 ? '分类已启用' : '分类已停用')
    await loadCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新状态失败')
  }
}

onMounted(loadCategories)
</script>

<template>
  <div class="stack">
    <section class="surface admin-page-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">分类管理</h2>
          <p class="muted">维护分类排序和启停状态，前台只会显示启用中的分类。</p>
        </div>
        <div class="split-actions">
          <el-button class="icon-button" @click="loadCategories">
            <RefreshCcw :size="16" />
            <span>刷新</span>
          </el-button>
          <el-button type="primary" class="icon-button" @click="openCreate">
            <Plus :size="16" />
            <span>新增分类</span>
          </el-button>
        </div>
      </div>
    </section>

    <section class="surface admin-page-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="name" label="分类名" min-width="180" />
        <el-table-column prop="sort" label="排序" width="100" />
        <el-table-column prop="articleCount" label="关联文章数" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="176" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="split-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="row.status === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row, row.status === 1 ? 0 : 1)"
              >
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新增分类'" width="420px">
      <el-form label-position="top">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" maxlength="30" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="icon-button" :loading="submitting" @click="submit">
          <Save :size="16" />
          <span>保存</span>
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page-card {
  display: grid;
  gap: 18px;
  padding: 20px;
}

.admin-page-card p {
  margin: 8px 0 0;
}
</style>
