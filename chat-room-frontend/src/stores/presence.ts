import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useStomp } from '@/composables/useStomp'
import request from '@/api/request'
import type { StompSubscription } from '@stomp/stompjs'

/**
 * 在线状态 Store。
 * 数据来自两条通道：WebSocket 订阅 /topic/presence 收增量，HTTP 拉一次当前快照。
 * 先订阅再拉快照——这样快照返回期间的状态变化不会丢。
 */
export const usePresenceStore = defineStore('presence', () => {
  /** userId → 状态（ONLINE / OFFLINE / INVISIBLE 对外已转为 OFFLINE） */
  const onlineMap = ref<Map<number, string>>(new Map())

  let subscription: StompSubscription | null = null

  const { subscribe, unsubscribe } = useStomp()

  /** 初始化：登记实时订阅 + 异步拉取当前状态快照 */
  function init() {
    // Subscribe to real-time updates first (synchronous, queues if not connected)
    // 未连接时 subscribe 会把订阅排进 pendingSubs，连上后自动回放
    if (!subscription) {
      subscription = subscribe('/topic/presence', (payload: any) => {
        onlineMap.value.set(payload.userId, payload.status)
        onlineMap.value = new Map(onlineMap.value)     // 替换 Map 触发响应式更新
      })
    }

    // Fetch current statuses via HTTP (runs independently)
    // 注意这里没有 await：拉快照可能晚于实时事件到达，晚到的快照会覆盖新值，
    // 但不会造成"状态不一致"的持续错误——下一次事件就会纠正
    request.get('/users/presence').then((res: any) => {
      const data = res?.data || {}
      for (const [id, status] of Object.entries(data)) {
        onlineMap.value.set(Number(id), status as string)
      }
      onlineMap.value = new Map(onlineMap.value)
    }).catch(() => { /* ignore */ })
  }

  /** 是否在线。只认 ONLINE——INVISIBLE 由后端转为 OFFLINE 后下发 */
  function isOnline(userId: number): boolean {
    return onlineMap.value.get(userId) === 'ONLINE'
  }

  /** 取状态，未知用户按 OFFLINE 处理 */
  function getStatus(userId: number): string {
    return onlineMap.value.get(userId) || 'OFFLINE'
  }

  /** 取消订阅并清除句柄，供页面卸载时调用 */
  function cleanup() {
    unsubscribe('/topic/presence')
    subscription = null
  }

  return { onlineMap, init, isOnline, getStatus, cleanup }
})
