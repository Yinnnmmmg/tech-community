import type { NotifyMessage } from './types'
import { request } from './http'

export function getMySystemNotify() {
  return request<NotifyMessage[]>({
    url: '/Notify/MySystemNotify',
    method: 'get'
  })
}

export function getMyFollowNotify() {
  return request<NotifyMessage[]>({
    url: '/Notify/MyFollowNotify',
    method: 'get'
  })
}

/** 获取未读系统通知数量 */
export function getUnreadCount() {
  return request<number>({
    url: '/Notify/UnreadCount',
    method: 'get'
  })
}

/** 将所有未读系统通知标记为已读 */
export function markNotifyRead() {
  return request<void>({
    url: '/Notify/MarkRead',
    method: 'put'
  })
}
