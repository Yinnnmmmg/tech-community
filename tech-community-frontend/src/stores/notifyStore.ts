import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { NotifyMessage } from '@/api/types'
import { getMyFollowNotify, getMySystemNotify, getUnreadCount, markNotifyRead } from '@/api/notify'

export const useNotifyStore = defineStore('notify', () => {
  const systemMessages = ref<NotifyMessage[]>([])
  const followMessages = ref<NotifyMessage[]>([])
  const loading = ref(false)

  /** 导航栏红点显示的未读数量（由后端统计，基于 state=UNREAD） */
  const unreadCount = ref(0)

  let pollingTimer: ReturnType<typeof setInterval> | null = null

  /** 拉取全部通知（系统 + 关注），用于通知页面 */
  async function fetchAll() {
    loading.value = true
    try {
      const [system, follow] = await Promise.all([
        getMySystemNotify(),
        getMyFollowNotify()
      ])
      systemMessages.value = system ?? []
      followMessages.value = follow ?? []
    } catch (err) {
      console.warn('[notifyStore] fetchAll failed:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  /** 仅拉取未读数量（轻量），用于导航栏轮询 */
  async function fetchUnreadCount() {
    try {
      unreadCount.value = await getUnreadCount()
    } catch (err) {
      console.warn('[notifyStore] fetchUnreadCount failed:', err)
    }
  }

  /** 通知页面进入时标记全部已读 */
  async function markSystemAsRead() {
    try {
      await markNotifyRead()
      unreadCount.value = 0
    } catch (err) {
      console.warn('[notifyStore] markSystemAsRead failed:', err)
    }
  }

  /** 启动定时轮询（默认 60 秒） */
  function startPolling(intervalMs = 60000) {
    stopPolling()
    pollingTimer = setInterval(fetchUnreadCount, intervalMs)
  }

  /** 停止轮询 */
  function stopPolling() {
    if (pollingTimer !== null) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
  }

  return {
    systemMessages,
    followMessages,
    loading,
    unreadCount,
    fetchAll,
    fetchUnreadCount,
    markSystemAsRead,
    startPolling,
    stopPolling,
  }
})
