import { request } from './http'
import type { User } from './types'

export function login(username: string, password: string) {
  return request<string>({
    url: '/user/login',
    method: 'post',
    params: { username, password }
  })
}

export function register(username: string, password: string) {
  return request<number>({
    url: '/user/register',
    method: 'post',
    data: { username, password }
  })
}

export function logout() {
  return request<void>({
    url: '/user/logout',
    method: 'post'
  })
}

export function getCurrentUser() {
  return request<User>({
    url: '/user/current',
    method: 'get'
  })
}
