import { request } from './http'
import type { PhoneLoginReq, User } from './types'
import { resolveAssetUrl } from '@/utils/asset'

export function login(username: string, password: string) {
  return request<string>({
    url: '/user/login',
    method: 'post',
    params: { username, password }
  })
}

export function loginByPhone(data: PhoneLoginReq) {
  return request<string>({
    url: '/user/login/phone',
    method: 'post',
    data
  })
}

export function sendSmsCode(phone: string) {
  return request<void>({
    url: '/user/sms/send',
    method: 'post',
    data: { phone }
  })
}

export function register(username: string, password: string) {
  return request<number>({
    url: '/user/register',
    method: 'post',
    data: { username, password }
  })
}

export function registerByPhone(phone: string, password: string, smsCode: string) {
  return request<number>({
    url: '/user/register',
    method: 'post',
    data: { phone, password, smsCode }
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
  }).then((user) => ({
    ...user,
    photo: resolveAssetUrl(user.photo)
  }))
}
