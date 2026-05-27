import { defineStore } from 'pinia'

import * as authApi from '@/api/auth'
import { clearStoredToken, getStoredToken, saveStoredToken } from '@/api/http'
import type { User } from '@/api/types'

interface AuthState {
  token: string | null
  user: User | null
  initialized: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: getStoredToken(),
    user: null,
    initialized: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.userRole === 1
  },
  actions: {
    async restore() {
      if (!this.token) {
        this.initialized = true
        return
      }
      try {
        this.user = await authApi.getCurrentUser()
      } catch {
        this.clearAuth()
      } finally {
        this.initialized = true
      }
    },
    async login(username: string, password: string) {
      const token = await authApi.login(username, password)
      this.token = token
      saveStoredToken(token)
      this.user = await authApi.getCurrentUser()
    },
    async loginByPhone(phone: string, password?: string, smsCode?: string) {
      const token = await authApi.loginByPhone({ phone, password, smsCode })
      this.token = token
      saveStoredToken(token)
      this.user = await authApi.getCurrentUser()
    },
    async register(username: string, password: string) {
      return authApi.register(username, password)
    },
    async registerByPhone(phone: string, password: string, smsCode: string) {
      return authApi.registerByPhone(phone, password, smsCode)
    },
    async logout() {
      try {
        if (this.token) {
          await authApi.logout()
        }
      } finally {
        this.clearAuth()
      }
    },
    clearAuth() {
      this.token = null
      this.user = null
      clearStoredToken()
    }
  }
})
