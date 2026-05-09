import axios, { type AxiosRequestConfig } from 'axios'

import type { ApiResult } from './types'

const TOKEN_KEY = 'tech-community-token'
const AUTH_REQUIRED_CODE = 4015

export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function saveStoredToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearStoredToken() {
  localStorage.removeItem(TOKEN_KEY)
}

const httpClient = axios.create({
  baseURL: '/api',
  timeout: 20000
})

httpClient.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.set('satoken', token)
  }
  return config
})

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await httpClient.request<ApiResult<T>>(config)
  const result = response.data
  if (result.code !== 200) {
    if (result.code === AUTH_REQUIRED_CODE) {
      clearStoredToken()
    }
    throw new ApiError(result.code, result.message || '请求失败')
  }
  return result.data
}

export { httpClient, AUTH_REQUIRED_CODE }
