import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Message, PrivateChatInfo } from '@/types'
import { useStomp } from '@/composables/useStomp'
import * as privateChatApi from '@/api/privateChats'
import type { StompSubscription } from '@stomp/stompjs'

/**
 * 私聊状态管理 Store。
 * 管理私聊会话列表、消息收发、申请-同意-拒绝-删除流程、未读计数。
 * 初始化时订阅 /user/queue/private 接收所有私聊事件（INVITATION/STATUS_CHANGE/消息）。
 */
export const usePrivateChatStore = defineStore('privateChat', () => {
  const chats = ref<PrivateChatInfo[]>([])
  const messages = ref<Message[]>([])
  const currentChat = ref<PrivateChatInfo | null>(null)
  const loading = ref(false)
  const hasMore = ref(true)
  const unreadCount = ref<Map<number, number>>(new Map())

  let currentPage = 0
  let chatSubscription: StompSubscription | null = null
  const PAGE_SIZE = 50

  const { send: stompSend, subscribe } = useStomp()

  /** Initialize — subscribe to private message queue. */
  function init() {
    if (chatSubscription) return
    chatSubscription = subscribe('/user/queue/private', (payload: any) => {
      handleIncomingMessage(payload)
    })
  }

  /** Fetch all private chats (excludes REJECTED). */
  async function fetchChats() {
    try {
      const res: any = await privateChatApi.listChats()
      chats.value = res.data || []
    } catch { /* handled by interceptor */ }
  }

  /** Initiate (or re-request) a private chat. */
  async function startChat(targetUserId: number): Promise<PrivateChatInfo> {
    const res: any = await privateChatApi.initiateChat(targetUserId)
    await fetchChats()
    const found = chats.value.find(c => c.id === res.data.id)
    if (found) return found
    return {
      id: res.data.id,
      initiatorId: res.data.initiator?.id || 0,
      status: res.data.status || 'PENDING',
      otherUser: { id: targetUserId, username: '', nickname: '' }
    }
  }

  /** Accept a pending chat request. */
  async function acceptChat(chatId: number) {
    await privateChatApi.acceptChat(chatId)
    await fetchChats()
  }

  /** Reject a pending chat request. */
  async function rejectChat(chatId: number) {
    await privateChatApi.rejectChat(chatId)
    await fetchChats()
  }

  /** Delete a private chat (both sides). */
  async function deleteChat(chatId: number) {
    await privateChatApi.deleteChat(chatId)
    if (currentChat.value?.id === chatId) {
      currentChat.value = null
    }
    await fetchChats()
  }

  /** Select and enter a private chat. */
  async function selectChat(chat: PrivateChatInfo) {
    if (currentChat.value?.id === chat.id) return
    messages.value = []
    currentPage = 0
    hasMore.value = true
    currentChat.value = chat
    if (chat.status === 'ACTIVE') {
      await loadHistory(chat.id)
    }
  }

  /** Handle incoming messages, invitations, and status changes. */
  function handleIncomingMessage(payload: any) {
    // Error
    if (payload.type === 'ERROR') {
      console.warn(payload.message || '操作失败')
      return
    }

    // New invitation
    if (payload.type === 'INVITATION') {
      fetchChats()
      console.info('收到新的私聊申请')
      return
    }

    // Status change (accept / auto-activate)
    if (payload.type === 'STATUS_CHANGE') {
      fetchChats()
      if (currentChat.value?.id === payload.id) {
        // Force reactivity by creating new object
        currentChat.value = { ...currentChat.value!, status: payload.status }
      }
      return
    }

    // Rejected or Deleted
    if (payload.type === 'REJECTED' || payload.type === 'DELETED') {
      fetchChats()
      if (payload.type === 'REJECTED') console.info('对方拒绝了你的私聊申请')
      if (currentChat.value?.id === payload.id) {
        currentChat.value = null
      }
      return
    }

    // Regular message
    const chatId = payload.chatId
    const chat = chats.value.find(c => c.id === chatId)
    if (chat) {
      chat.lastMessage = payload.content || ''
      chat.lastMessageTime = payload.createdAt || ''
      chat.lastSenderId = payload.sender?.id || 0
    }

    if (chatId !== currentChat.value?.id && chatId) {
      const prev = unreadCount.value.get(chatId) || 0
      unreadCount.value = new Map(unreadCount.value.set(chatId, prev + 1))
      return
    }

    if (chatId !== currentChat.value?.id) return

    const msg: Message = {
      id: payload.id,
      channelId: 0,
      sender: payload.sender || null,
      type: payload.type || 'TEXT',
      content: payload.content || '',
      fileName: payload.fileName || null,
      filePath: payload.filePath || null,
      isRecalled: false,
      createdAt: payload.createdAt || new Date().toISOString()
    }
    messages.value.push(msg)
  }

  /** Load history for a chat. */
  async function loadHistory(chatId: number) {
    loading.value = true
    try {
      const res: any = await privateChatApi.getMessages(chatId, currentPage, PAGE_SIZE)
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
    if (loading.value || !hasMore.value || !currentChat.value) return
    currentPage++
    await loadHistory(currentChat.value.id)
  }

  function sendMessage(chatId: number, content: string) {
    stompSend('/app/private.send', { chatId, content, type: 'TEXT' })
  }

  function sendFileMessage(chatId: number, fileName: string, filePath: string, fileType: 'IMAGE' | 'FILE') {
    stompSend('/app/private.send', { chatId, content: '', type: fileType, fileName, filePath })
  }

  function reset() {
    messages.value = []
    currentChat.value = null
    currentPage = 0
  }

  function clearUnread(chatId: number) {
    const m = new Map(unreadCount.value)
    m.delete(chatId)
    unreadCount.value = m
  }

  return {
    chats, messages, currentChat, loading, hasMore, unreadCount,
    init, fetchChats, startChat, acceptChat, rejectChat, deleteChat, selectChat,
    sendMessage, sendFileMessage, loadMore, reset, clearUnread
  }
})
