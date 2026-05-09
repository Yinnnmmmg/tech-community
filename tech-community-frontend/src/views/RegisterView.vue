<script setup lang="ts">
import { UserPlus } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const router = useRouter()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})
const loading = ref(false)

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await authStore.register(form.username, form.password)
    ElMessage.success('注册成功')
    router.push({ name: 'login' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-card surface">
      <div>
        <h1 class="page-title">注册</h1>
        <p>创建账号，开始沉淀你的技术笔记。</p>
      </div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="auth-button icon-button" :loading="loading" @click="submit">
          <UserPlus :size="16" />
          <span>注册</span>
        </el-button>
      </el-form>
      <RouterLink class="muted auth-link" :to="{ name: 'login' }">已有账号</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.auth-page {
  display: grid;
  place-items: start center;
  min-height: calc(100vh - 160px);
  padding-top: 28px;
}

.auth-card {
  display: grid;
  gap: 18px;
  width: min(440px, 100%);
  padding: 26px;
}

.auth-card p {
  margin: 8px 0 0;
  color: var(--tc-text-muted);
  line-height: 1.6;
}

.auth-button {
  width: 100%;
}

.auth-link {
  justify-self: center;
}
</style>
