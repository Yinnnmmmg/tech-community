<script setup lang="ts">
import { LockKeyhole } from 'lucide-vue-next'
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
    if (!authStore.isAdmin) {
      ElMessage.warning('当前账号没有后台访问权限')
      router.push({ name: 'admin-403' })
      return
    }
    ElMessage.success('后台登录成功')
    router.push(String(route.query.redirect || '/admin/dashboard'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="admin-login">
    <div class="admin-login__panel">
      <div class="admin-login__intro">
        <span class="admin-login__eyebrow">Tech Community Admin</span>
        <h1>管理端入口</h1>
        <p>统一查看内容状态、处理评论审核、维护分类与用户角色。</p>
      </div>

      <div class="admin-login__card surface">
        <div class="admin-login__icon">
          <LockKeyhole :size="20" />
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
          <el-button type="primary" class="admin-login__button" :loading="loading" @click="submit">
            登录后台
          </el-button>
        </el-form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.admin-login {
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at 15% 20%, rgba(255, 105, 0, 0.22), transparent 24%),
    radial-gradient(circle at 82% 18%, rgba(255, 160, 64, 0.18), transparent 18%),
    linear-gradient(135deg, #23160d 0%, #433528 35%, #f3ede7 100%);
}

.admin-login__panel {
  display: grid;
  grid-template-columns: minmax(280px, 1.1fr) minmax(320px, 420px);
  align-items: center;
  gap: 40px;
  width: min(1100px, 100%);
  min-height: calc(100vh - 48px);
  margin: 0 auto;
}

.admin-login__intro {
  color: #fff7ee;
}

.admin-login__eyebrow {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.admin-login__intro h1 {
  margin: 18px 0 10px;
  font-size: clamp(42px, 7vw, 68px);
  line-height: 0.95;
}

.admin-login__intro p {
  max-width: 520px;
  margin: 0;
  font-size: 18px;
  line-height: 1.7;
  color: rgba(255, 247, 238, 0.78);
}

.admin-login__card {
  display: grid;
  gap: 18px;
  padding: 30px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 28px 56px rgba(17, 24, 39, 0.22);
}

.admin-login__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, #ff6900, #ff9642);
}

.admin-login__button {
  width: 100%;
  margin-top: 8px;
}

@media (max-width: 840px) {
  .admin-login__panel {
    grid-template-columns: 1fr;
    align-items: start;
    padding-top: 48px;
  }
}
</style>
