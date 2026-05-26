<script setup lang="ts">
import { LogIn } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({
  username: '',
  password: ''
})
const loading = ref(false)

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push(String(route.query.redirect || '/'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-card surface">
      <div>
        <h1 class="page-title">登录</h1>
        <p>回到社区，继续参与讨论和创作。</p>
      </div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="auth-button icon-button" :loading="loading" @click="submit">
          <LogIn :size="16" />
          <span>登录</span>
        </el-button>
      </el-form>
      <RouterLink class="muted auth-link" :to="{ name: 'register' }">注册账号</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.auth-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  min-width: 100vw;
  padding-top: 28px;
  background: url('@/png/login-Background.png') center / cover no-repeat;
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
