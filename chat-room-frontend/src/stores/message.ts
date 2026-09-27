import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Message, TypingEvent } from '@/types'
import request from '@/api/request'
import { useStomp } from '@/composables/useStomp'
import { useChannelStore } from '@/stores/channel'
import { useAuthStore } from '@/stores/auth'
import type { StompSubscription } from '@stomp/stompjs'

/**
 * 频道消息状态管理 Store。
 * 管理当前频道消息列表、分页加载、所有频道的 WebSocket 订阅、未读计数和 @提醒。
 * subscribeToAllChannels() 订阅所有已加入频道，确保任何频道发来的 @提醒都能收到。
 */
export const useMessageStore = defineStore('message', () => {

  // 在 setup 闭包外获取 authStore 用户 ID，供回调中使用
  const authStore = useAuthStore()
  function myUserId(): number { return authStore.user?.id || 0 }
  const messages = ref<Message[]>([])
  const loading = ref(false)
  const hasMore = ref(true)
  const typingUsers = ref<Map<number, { nickname: string; timer: ReturnType<typeof setTimeout> }>>(new Map())
  const mentionedIn = ref<Set<number>>(new Set())
  const unreadCount = ref<Map<number, number>>(new Map())

  let currentPage = 0
  let currentChannelId: number | null = null
  let errorsSubscription: StompSubscription | null = null
  const PAGE_SIZE = 50

  const { send: stompSend, subscribe, unsubscribe } = useStomp()
  const channelStore = useChannelStore()

  // Track active channel subscriptions for cleanup
  const activeSubscriptions = new Set<string>()

  /**
   * 订阅所有已加入频道 + 业务错误队列。
   * 用 myChannels（已加入）而不是公开频道列表——服务端会校验成员身份，
   * 订阅未加入的频道会被拦截器拒绝，进而关掉整条连接。
   * 目的：任何频道来的 @提醒和未读都能收到，不必停留在那个频道。
   */
  function subscribeToAllChannels() {
    if (!errorsSubscription) {
      // 业务错误（禁言、参数非法等）由 WebSocketExceptionHandler 推到这里，连接不会断
      errorsSubscription = subscribe('/user/queue/errors', (payload: any) => {
        console.warn(payload.message || '操作失败')
      })
    }
    for (const cm of (channelStore.myChannels || [])) {
      if (!cm.channel?.id) continue
      const dest = `/topic/channel.${cm.channel.id}`
      if (!activeSubscriptions.has(dest)) {      // 幂等：已订阅过就跳过
        subscribe(dest, (payload: any) => handleIncomingMessage(payload))
        activeSubscriptions.add(dest)
      }
    }
  }

  /** 切换频道：清空当前列表 + 换 typing 订阅，然后用 HTTP 拉历史 */
  async function selectChannel(channelId: number) {
    if (currentChannelId !== channelId) {
      messages.value = []
      currentPage = 0
      hasMore.value = true
      // Unsub old typing, sub new typing
      if (currentChannelId) unsubscribe(`/topic/channel.${currentChannelId}.typing`)
      subscribe(`/topic/channel.${channelId}.typing`, (payload: TypingEvent) => handleTypingEvent(payload))
      currentChannelId = channelId
    }
    await loadHistory(channelId)
  }

  /**
   * 用 HTTP 拉历史消息（实时消息走 WebSocket，历史走 HTTP 是两条独立通道）。
   * page 为 0 时是覆盖式赋值。注意这里有一个时序风险：
   * 若赋值之前有实时消息先 push 进 messages，会被这次赋值覆盖掉。
   */
  async function loadHistory(channelId: number) {
    loading.value = true
    try {
      const res: any = await request.get(`/channels/${channelId}/messages`, {
        params: { page: currentPage, size: PAGE_SIZE }
      })
      const data: Message[] = res.data || []
      // 不足一页说明没有更多了，防止无限翻页
      if (data.length < PAGE_SIZE) hasMore.value = false
      if (currentPage === 0) {
        messages.value = data
      } else {
        messages.value = [...data, ...messages.value]      // 翻页：更早的消息插到前面
      }
    } finally {
      loading.value = false
    }
  }

  /** 加载下一页（更早的历史消息）。有 hasMore / loading / 当前频道三重保护 */
  async function loadMore() {
    if (!hasMore.value || loading.value || !currentChannelId) return
    currentPage++
    await loadHistory(currentChannelId)
  }

  /**
   * 处理任意已订阅频道推来的消息。
   * 这是 WebSocket 推送的统一入口，一个连接上所有频道共用它。
   * 处理顺序有讲究，见下方注释。
   */
  function handleIncomingMessage(payload: any) {
    // ① 先算未读：不是当前频道就 +1（侧边栏徽章）
    if (payload.channelId && currentChannelId !== payload.channelId) {
      const prev = unreadCount.value.get(payload.channelId) || 0
      unreadCount.value = new Map(unreadCount.value.set(payload.channelId, prev + 1))
    }

    // ② 再判断是否 @了我：payload.mentions 由后端解析正文中的 @用户名 生成
    const myId = myUserId()
    if (payload.mentions) {
      for (const m of payload.mentions) {
        if (m.userId === myId) {
          mentionedIn.value = new Set([...mentionedIn.value, payload.channelId])
          console.info(`${payload.sender?.nickname} @了你: ${payload.content}`)
          break
        }
      }
    }

    // ③ 频道状态事件（禁言开关变化）：只更新频道对象，不当作聊天消息
    if (payload.type === 'CHANNEL_UPDATE') {
      if (channelStore.currentChannel && payload.channelId === channelStore.currentChannel.id) {
        channelStore.currentChannel.isMuted = payload.isMuted
      }
      return
    }

    // ④ 撤回事件必须放在"当前频道"判断**之前**处理：
    //    RECALL 的载荷里只有 messageId 没有 channelId，否则会被下面的守卫直接丢掉
    if (payload.type === 'RECALL') {
      const msg = messages.value.find(m => m.id === payload.messageId)
      if (msg) { msg.isRecalled = true; msg.content = '消息已撤回'; msg.type = 'SYSTEM' }
      return
    }

    // ⑤ 走到这里才是普通聊天消息：只往当前频道的列表里追加，其他频道只计未读
    if (payload.channelId !== currentChannelId) return

    const msg: Message = {
      id: payload.id, channelId: payload.channelId,
      sender: payload.sender || null,
      type: payload.type || 'TEXT',
      content: payload.content || '',
      fileName: payload.fileName || null,
      filePath: payload.filePath || null,
      isRecalled: payload.isRecalled || false,
      createdAt: payload.createdAt || new Date().toISOString()
    }
    messages.value.push(msg)      // push 即可，Vue 响应式会重渲染列表
  }

  /**
   * 处理"正在输入"事件。
   * 这是会自然过期的临时状态：3 秒内没有新事件就自动从列表移除，
   * 所以对方停止输入（或掉线）不会留下永久残留。
   */
  function handleTypingEvent(payload: TypingEvent) {
    const { userId, nickname, typing } = payload
    const map = typingUsers.value
    const existing = map.get(userId)
    if (existing) clearTimeout(existing.timer)      // 重置已有计时器
    if (typing) {
      const timer = setTimeout(() => map.delete(userId), 3000)
      map.set(userId, { nickname, timer })
    } else {
      map.delete(userId)                            // 对方主动停止
    }
  }

  /** 发文本消息。走 /app/chat.send，没有返回值——发送结果无法从这里得知 */
  function sendMessage(channelId: number, content: string) {
    stompSend('/app/chat.send', { channelId, content, type: 'TEXT' })
  }

  /** 发图片/附件消息。content 留空，文件信息由 type + fileName + filePath 表达 */
  function sendFileMessage(channelId: number, fileName: string, filePath: string, fileType: 'IMAGE' | 'FILE') {
    stompSend('/app/chat.send', { channelId, content: '', type: fileType, fileName, filePath })
  }

  /** 通知"我正在输入"。仅广播不落库，属于临时状态 */
  function sendTyping(channelId: number, typing: boolean) {
    stompSend('/app/chat.typing', { channelId, typing })
  }

  /** 走 WebSocket 撤回。载荷只有 messageId——频道由服务端从消息反查 */
  function recallMessage(_channelId: number, messageId: number) {
    stompSend('/app/chat.recall', { messageId })
  }

  /** 走 HTTP 撤回（与上面的 WebSocket 版本并存），用于需要 HTTP 语义的场景 */
  async function recallViaHttp(channelId: number, messageId: number) {
    await request.put(`/channels/${channelId}/messages/${messageId}/recall`)
  }

  /** 上报已读：只发最后一条消息的 ID，不是把整个列表都标记为已读 */
  function markRead(channelId: number) {
    if (messages.value.length > 0) {
      const last = messages.value[messages.value.length - 1]
      if (last.id) stompSend('/app/chat.read', { messageId: last.id, channelId })
    }
  }

  /** 清除某频道的 @提醒标记，同时把该频道未读清零 */
  function clearMention(channelId: number) {
    const s = new Set(mentionedIn.value)
    s.delete(channelId)
    mentionedIn.value = s
  }

  /** 清除某频道的未读数 */
  function clearUnread(channelId: number) {
    const m = new Map(unreadCount.value)
    m.delete(channelId)
    unreadCount.value = m
  }

  /**
   * 切换频道前重置状态。
   * 注意只取消 .typing 订阅——频道消息订阅由 subscribeToAllChannels 统一管理，
   * 切频道不应该取消它（否则收不到其他频道的 @提醒和未读）
   */
  function reset() {
    if (currentChannelId) {
      unsubscribe(`/topic/channel.${currentChannelId}.typing`)
    }
    messages.value = []
    typingUsers.value = new Map()
    currentChannelId = null
    currentPage = 0
    hasMore.value = true
  }

  /** 生成"正在输入"的提示文案，人数多时折叠显示 */
  function typingText(): string {
    const entries = Array.from(typingUsers.value.values())
    if (entries.length === 0) return ''
    if (entries.length === 1) return `${entries[0].nickname} 正在输入...`
    if (entries.length <= 3) return entries.map(e => e.nickname).join(', ') + ' 正在输入...'
    return `${entries[0].nickname} 等${entries.length}人正在输入...`
  }

  /** 页面卸载时清理全部订阅。漏掉任何一项都会留下悬挂的回调 */
  function cleanup() {
    for (const dest of activeSubscriptions) unsubscribe(dest)
    activeSubscriptions.clear()
    unsubscribe('/user/queue/errors')
    errorsSubscription = null
    if (currentChannelId) unsubscribe(`/topic/channel.${currentChannelId}.typing`)
  }

  return {
    messages, loading, hasMore, typingUsers, mentionedIn, unreadCount,
    subscribeToAllChannels, selectChannel, loadMore,
    sendMessage, sendFileMessage, sendTyping, recallMessage, recallViaHttp, markRead,
    clearMention, clearUnread, reset, cleanup, typingText
  }
})

