import { request } from './http'

export function getMySystemNotify() {
  return request<string[]>({
    url: '/Notify/MySystemNotify',
    method: 'get'
  })
}

export function getMyFollowNotify() {
  return request<string[]>({
    url: '/Notify/MyFollowNotify',
    method: 'get'
  })
}
