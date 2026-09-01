import { ref } from 'vue'
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '@/stores/auth'

const client = ref<Client | null>(null)
const connected = ref(false)
const subscriptions = new Map<string, StompSubscription>()
const pendingSubs: Array<{ destination: string; callback: (body: any) => void }> = []
let reconnectAttempts = 0
const MAX_RECONNECT = 10
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
/** Redis 在线状态心跳间隔（与后端 TTL 300s 保持 5 倍余量） */
const HEARTBEAT_INTERVAL_MS = 60000
let heartbeatTimer: ReturnType<typeof setInterval> | null = null

/** STOMP over SockJS 连接管理。单例模式，全局共享一个连接。
 *  支持断线重连（指数退避，最多 10 次）、连接前订阅排队、心跳保活。
 *  所有 subscribe/send 操作在连接未就绪时自动排队，onConnect 时回放。 */
export function useStomp() {
  const authStore = useAuthStore()

  function connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (client.value?.active) {
        resolve()
        return
      }

      const stompClient = new Client({
        webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
        connectHeaders: {
          Authorization: `Bearer ${authStore.token}`
        },
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        reconnectDelay: 0, // 自己控制重连逻辑
        onConnect: () => {
          connected.value = true
          reconnectAttempts = 0
          startHeartbeat()
          // Replay pending subscriptions
          for (const ps of pendingSubs) {
            subscribe(ps.destination, ps.callback)
          }
          pendingSubs.length = 0
          resolve()
        },
        onDisconnect: () => {
          connected.value = false
          stopHeartbeat()
        },
        onStompError: (frame) => {
          console.error('STOMP error:', frame.headers['message'])
          reject(new Error(frame.headers['message'] || 'STOMP connection error'))
        },
        onWebSocketClose: () => {
          connected.value = false
          stopHeartbeat()
          attemptReconnect()
        }
      })

      stompClient.activate()
      client.value = stompClient
    })
  }

  /** 应用层心跳：每 60s 发一次 /app/presence.heartbeat，续期后端 Redis 在线状态 TTL */
  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (client.value?.active) {
        client.value.publish({ destination: '/app/presence.heartbeat' })
      }
    }, HEARTBEAT_INTERVAL_MS)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function attemptReconnect() {
    if (reconnectAttempts >= MAX_RECONNECT) {
      console.warn('Max reconnect attempts reached')
      return
    }
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
    reconnectAttempts++
    console.log(`Reconnecting in ${delay}ms (attempt ${reconnectAttempts}/${MAX_RECONNECT})`)

    reconnectTimer = setTimeout(async () => {
      try {
        await connect()
      } catch {
        // attemptReconnect will be called again by onWebSocketClose
      }
    }, delay)
  }

  function subscribe(destination: string, callback: (body: any) => void): StompSubscription | null {
    if (!client.value?.active) {
      // Queue for when STOMP connects
      pendingSubs.push({ destination, callback })
      return null
    }
    // Unsubscribe if already subscribed
    if (subscriptions.has(destination)) {
      subscriptions.get(destination)!.unsubscribe()
    }
    const sub = client.value.subscribe(destination, (message) => {
      try {
        const body = JSON.parse(message.body)
        callback(body)
      } catch {
        callback(message.body)
      }
    })
    subscriptions.set(destination, sub)
    return sub
  }

  function unsubscribe(destination: string) {
    const sub = subscriptions.get(destination)
    if (sub) {
      sub.unsubscribe()
      subscriptions.delete(destination)
    }
  }

  function send(destination: string, body: any) {
    if (!client.value?.active) {
      console.warn('STOMP not connected, cannot send to', destination)
      return
    }
    client.value.publish({ destination, body: JSON.stringify(body) })
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopHeartbeat()
    subscriptions.forEach((sub) => sub.unsubscribe())
    subscriptions.clear()
    if (client.value?.active) {
      client.value.deactivate()
    }
    client.value = null
    connected.value = false
    reconnectAttempts = MAX_RECONNECT // Prevent auto-reconnect on manual disconnect
  }

  return { connected, connect, disconnect, subscribe, unsubscribe, send }
}
