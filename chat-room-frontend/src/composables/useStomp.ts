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
 *  subscribe 在连接未就绪时会排队并在 onConnect 时回放；
 *  但 send 不会排队——未连接时只打警告并丢弃，调用方无法感知。 */
export function useStomp() {
  const authStore = useAuthStore()

  /** 建立连接。注意 activate 只是发起连接，要等 onConnect 才代表协议就绪 */
  function connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (client.value?.active) {
        resolve()
        return
      }

      const stompClient = new Client({
        // SockJS 是对 WebSocket 的兼容层；后端 /ws 端点放行握手，认证在 CONNECT 帧里做
        webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
        // 这两个头属于 STOMP CONNECT 帧，不是 HTTP 头；
        // 后端 StompInterceptor 用 getFirstNativeHeader("Authorization") 读取
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

  /** 应用层心跳：每 60s 发一次 /app/presence.heartbeat，续期后端 Redis 在线状态 TTL。
   *  注意：后端的 StompInterceptor 尚未把这个目的地加入 SEND 白名单，
   *  因此当前心跳会被拦截器拒绝，在线状态仍会在 TTL 300 秒后失效。 */
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

  /** 断线后按指数退避重连，1s→2s→4s…最多 30s，共尝试 10 次 */
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

  /** 订阅地址。连接未就绪时先排队，onConnect 时回放；已订阅过则先取消旧的再订 */
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

  /** 取消订阅并移除记录 */
  function unsubscribe(destination: string) {
    const sub = subscriptions.get(destination)
    if (sub) {
      sub.unsubscribe()
      subscriptions.delete(destination)
    }
  }

  /** 发送消息。注意连接未就绪时只是打日志并丢弃，没有排队重放机制 */
  function send(destination: string, body: any) {
    if (!client.value?.active) {
      console.warn('STOMP not connected, cannot send to', destination)
      return
    }
    client.value.publish({ destination, body: JSON.stringify(body) })
  }

  /** 主动断开：清定时器、取消全部订阅、停心跳，并把重连计数置满以阻止自动重连 */
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
