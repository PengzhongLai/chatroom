import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useStomp } from '@/composables/useStomp'
import request from '@/api/request'
import type { StompSubscription } from '@stomp/stompjs'

export const usePresenceStore = defineStore('presence', () => {
  const onlineMap = ref<Map<number, string>>(new Map())

  let subscription: StompSubscription | null = null

  const { subscribe, unsubscribe } = useStomp()

  function init() {
    // Subscribe to real-time updates first (synchronous, queues if not connected)
    if (!subscription) {
      subscription = subscribe('/topic/presence', (payload: any) => {
        onlineMap.value.set(payload.userId, payload.status)
        onlineMap.value = new Map(onlineMap.value)
      })
    }

    // Fetch current statuses via HTTP (runs independently)
    request.get('/users/presence').then((res: any) => {
      const data = res?.data || {}
      for (const [id, status] of Object.entries(data)) {
        onlineMap.value.set(Number(id), status as string)
      }
      onlineMap.value = new Map(onlineMap.value)
    }).catch(() => { /* ignore */ })
  }

  function isOnline(userId: number): boolean {
    return onlineMap.value.get(userId) === 'ONLINE'
  }

  function getStatus(userId: number): string {
    return onlineMap.value.get(userId) || 'OFFLINE'
  }

  function cleanup() {
    unsubscribe('/topic/presence')
    subscription = null
  }

  return { onlineMap, init, isOnline, getStatus, cleanup }
})
