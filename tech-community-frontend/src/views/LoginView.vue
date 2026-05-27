<script setup lang="ts">
import { LogIn } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'
import { sendSmsCode } from '@/api/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const activeTab = ref('password')
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)

const form = reactive({
  phone: '',
  password: '',
  smsCode: ''
})

let timer: ReturnType<typeof setInterval> | null = null

function startCountdown() {
  countdown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (timer) clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function handleSendSms() {
  if (!form.phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  sending.value = true
  try {
    await sendSmsCode(form.phone)
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (!form.phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  if (activeTab.value === 'password' && !form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (activeTab.value === 'sms' && !form.smsCode) {
    ElMessage.warning('请输入验证码')
    return
  }

  loading.value = true
  try {
    if (activeTab.value === 'password') {
      await authStore.loginByPhone(form.phone, form.password)
    } else {
      await authStore.loginByPhone(form.phone, undefined, form.smsCode)
    }
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

      <el-tabs v-model="activeTab" class="auth-tabs">
        <el-tab-pane label="密码登录" name="password" />
        <el-tab-pane label="短信登录" name="sms" />
      </el-tabs>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" autocomplete="tel" placeholder="请输入手机号" />
        </el-form-item>

        <el-form-item v-if="activeTab === 'password'" label="密码">
          <el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            show-password
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item v-if="activeTab === 'sms'" label="验证码">
          <div class="sms-row">
            <el-input v-model="form.smsCode" placeholder="请输入验证码" class="sms-input" @keyup.enter="submit" />
            <el-button
              class="sms-button"
              :loading="sending"
              :disabled="countdown > 0"
              @click="handleSendSms"
            >
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </el-button>
          </div>
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

.auth-tabs {
  margin-top: -8px;
}

.auth-button {
  width: 100%;
}

.auth-link {
  justify-self: center;
}

.sms-row {
  display: flex;
  gap: 8px;
}

.sms-input {
  flex: 1;
}

.sms-button {
  flex-shrink: 0;
  min-width: 110px;
}
</style>
