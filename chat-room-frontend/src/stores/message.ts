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

  /** Subscribe to all member channels — ensures mentions/unread work everywhere. */
  function subscribeToAllChannels() {
    if (!errorsSubscription) {
      errorsSubscription = subscribe('/user/queue/errors', (payload: any) => {
        console.warn(payload.message || '操作失败')
      })
    }
    for (const cm of (channelStore.myChannels || [])) {
      if (!cm.channel?.id) continue
      const dest = `/topic/channel.${cm.channel.id}`
      if (!activeSubscriptions.has(dest)) {
        subscribe(dest, (payload: any) => handleIncomingMessage(payload))
        activeSubscriptions.add(dest)
      }
    }
  }

  /** Enter a channel — switch view + load history + set up typing. */
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

  /** Load initial history when entering a channel. */
  async function loadHistory(channelId: number) {
    loading.value = true
    try {
      const res: any = await request.get(`/channels/${channelId}/messages`, {
        params: { page: currentPage, size: PAGE_SIZE }
      })
      const data: Message[] = res.data || []
      if (data.length < PAGE_SIZE) hasMore.value = false
      if (currentPage === 0) {
        messages.value = data
      } else {
        messages.value = [...data, ...messages.value]
      }
    } finally {
      loading.value = false
    }
  }

  async function loadMore() {
    if (!hasMore.value || loading.value || !currentChannelId) return
    currentPage++
    await loadHistory(currentChannelId)
  }

  /** Handle incoming message from any subscribed channel. */
  function handleIncomingMessage(payload: any) {
    // Track unread for sidebar
    if (payload.channelId && currentChannelId !== payload.channelId) {
      const prev = unreadCount.value.get(payload.channelId) || 0
      unreadCount.value = new Map(unreadCount.value.set(payload.channelId, prev + 1))
    }

    // Check if current user is @mentioned
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

    // Channel state updates
    if (payload.type === 'CHANNEL_UPDATE') {
      if (channelStore.currentChannel && payload.channelId === channelStore.currentChannel.id) {
        channelStore.currentChannel.isMuted = payload.isMuted
      }
      return
    }

    // Handle RECALL before channel guard (RECALL payload may not include channelId)
    if (payload.type === 'RECALL') {
      const msg = messages.value.find(m => m.id === payload.messageId)
      if (msg) { msg.isRecalled = true; msg.content = '消息已撤回'; msg.type = 'SYSTEM' }
      return
    }

    // Only show messages for current channel
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
    messages.value.push(msg)
  }

  /** Handle typing indicator event. */
  function handleTypingEvent(payload: TypingEvent) {
    const { userId, nickname, typing } = payload
    const map = typingUsers.value
    const existing = map.get(userId)
    if (existing) clearTimeout(existing.timer)
    if (typing) {
      const timer = setTimeout(() => map.delete(userId), 3000)
      map.set(userId, { nickname, timer })
    } else {
      map.delete(userId)
    }
  }

  function sendMessage(channelId: number, content: string) {
    stompSend('/app/chat.send', { channelId, content, type: 'TEXT' })
  }

  function sendFileMessage(channelId: number, fileName: string, filePath: string, fileType: 'IMAGE' | 'FILE') {
    stompSend('/app/chat.send', { channelId, content: '', type: fileType, fileName, filePath })
  }

  function sendTyping(channelId: number, typing: boolean) {
    stompSend('/app/chat.typing', { channelId, typing })
  }

  function recallMessage(_channelId: number, messageId: number) {
    stompSend('/app/chat.recall', { messageId })
  }

  async function recallViaHttp(channelId: number, messageId: number) {
    await request.put(`/channels/${channelId}/messages/${messageId}/recall`)
  }

  function markRead(channelId: number) {
    if (messages.value.length > 0) {
      const last = messages.value[messages.value.length - 1]
      if (last.id) stompSend('/app/chat.read', { messageId: last.id, channelId })
    }
  }

  function clearMention(channelId: number) {
    const s = new Set(mentionedIn.value)
    s.delete(channelId)
    mentionedIn.value = s
  }

  function clearUnread(channelId: number) {
    const m = new Map(unreadCount.value)
    m.delete(channelId)
    unreadCount.value = m
  }

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

  function typingText(): string {
    const entries = Array.from(typingUsers.value.values())
    if (entries.length === 0) return ''
    if (entries.length === 1) return `${entries[0].nickname} 正在输入...`
    if (entries.length <= 3) return entries.map(e => e.nickname).join(', ') + ' 正在输入...'
    return `${entries[0].nickname} 等${entries.length}人正在输入...`
  }

  /** Clean up all subscriptions. */
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

