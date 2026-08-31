<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { usePrivateChatStore } from '@/stores/privateChat'
import { usePresenceStore } from '@/stores/presence'
import { useAuthStore } from '@/stores/auth'
import type { PrivateChatInfo } from '@/types'
import request from '@/api/request'
import { MacAvatar, MacButton, MacBadge, MacInput, MacSheet } from '@/components/ui'

const privateChatStore = usePrivateChatStore()
const presenceStore = usePresenceStore()
const authStore = useAuthStore()

const showNewChat = ref(false)
const searchQuery = ref('')
const searchResults = ref<any[]>([])
const searching = ref(false)

const emit = defineEmits<{ select: [chat: PrivateChatInfo] }>()

// Sort: incoming PENDING first, then outgoing PENDING, then ACTIVE
// Force reactive dependency on presence updates
const sortedChats = computed(() => {
  void presenceStore.onlineMap
  return [...privateChatStore.chats].sort((a, b) => {
    const order = (c: PrivateChatInfo) => {
      if (c.status === 'PENDING' && !c.iAmInitiator) return 0
      if (c.status === 'PENDING' && c.iAmInitiator) return 1
      return 2
    }
    return order(a) - order(b)
  })
})

onMounted(() => {
  privateChatStore.fetchChats()
})

function handleSelect(chat: PrivateChatInfo) {
  if (chat.status !== 'ACTIVE') return // can't enter non-active chats
  emit('select', chat)
}

async function searchUser() {
  if (!searchQuery.value.trim()) { searchResults.value = []; return }
  searching.value = true
  try {
    const res: any = await request.get('/users/search', { params: { q: searchQuery.value } })
    searchResults.value = (res.data || []).filter(
      (u: any) => u.id !== authStore.user?.id
    )
  } finally {
    searching.value = false
  }
}

async function handleStartChat(userId: number) {
  try {
    const chat = await privateChatStore.startChat(userId)
    showNewChat.value = false
    searchQuery.value = ''
    searchResults.value = []
    emit('select', chat)
  } catch { /* handled by interceptor */ }
}

async function handleAccept(chatId: number, e: Event) {
  e.stopPropagation()
  await privateChatStore.acceptChat(chatId)
}

async function handleReject(chatId: number, e: Event) {
  e.stopPropagation()
  await privateChatStore.rejectChat(chatId)
}

async function handleDelete(chatId: number) {
  if (!window.confirm('确定要删除吗？聊天记录将被删除。')) return
  try {
    await privateChatStore.deleteChat(chatId)
  } catch { /* canceled */ }
}
</script>

<template>
  <div class="private-chat-list">
    <div class="section-header">
      <span class="title">私聊</span>
      <MacButton variant="default" @click="showNewChat = true; searchQuery = ''; searchResults = []">+ 新建</MacButton>
    </div>

    <div class="chat-items">
      <div
        v-for="chat in sortedChats"
        :key="chat.id"
        class="chat-item"
        :class="{ active: privateChatStore.currentChat?.id === chat.id }"
        @click="handleSelect(chat)"
      >
        <MacAvatar
          :name="chat.otherUser.nickname || chat.otherUser.username"
          :gradient="(chat.id % 6) + 1"
          :online="presenceStore.isOnline(chat.otherUser.id)"
          :show-status="true"
          :size="28"
        />
        <div class="chat-info">
          <div class="chat-top">
            <span class="chat-name">{{ chat.otherUser.nickname || chat.otherUser.username }}</span>
            <span class="chat-time">{{ chat.lastMessageTime?.substring(11, 16) || '' }}</span>
          </div>
          <div class="chat-preview">
            <template v-if="chat.status === 'ACTIVE'">{{ chat.lastMessage || '' }}</template>
            <MacBadge v-else-if="chat.status === 'PENDING' && !chat.iAmInitiator" variant="danger">待同意</MacBadge>
            <MacBadge v-else-if="chat.status === 'PENDING' && chat.iAmInitiator" variant="default">等待同意</MacBadge>
          </div>
        </div>
        <div class="chat-meta">
          <MacBadge v-if="privateChatStore.unreadCount.has(chat.id)" variant="info" size="sm">{{ privateChatStore.unreadCount.get(chat.id) }}</MacBadge>
        </div>
        <div v-if="chat.status === 'PENDING' && !chat.iAmInitiator" class="chat-actions">
          <MacButton variant="primary" size="sm" @click="handleAccept(chat.id, $event)">同意</MacButton>
          <MacButton variant="danger" size="sm" @click="handleReject(chat.id, $event)">拒绝</MacButton>
        </div>
        <button v-if="chat.status === 'ACTIVE'" class="chat-delete" @click.stop="handleDelete(chat.id)">✕</button>
      </div>
      <p v-if="sortedChats.length === 0" class="empty">暂无私聊</p>
    </div>

    <!-- New chat sheet -->
    <MacSheet :visible="showNewChat" title="新建私聊" @close="showNewChat = false">
      <MacInput v-model="searchQuery" placeholder="搜索用户..." @input="searchUser" />
      <div v-if="searchResults.length" class="search-dropdown">
        <div v-for="u in searchResults" :key="u.id" class="search-item" @click="handleStartChat(u.id)">
          <span>{{ u.nickname || u.username }}</span>
          <span class="search-username">@{{ u.username }}</span>
        </div>
      </div>
      <p v-if="searchQuery && !searching && searchResults.length === 0" class="no-results">
        未找到用户
      </p>
    </MacSheet>
  </div>
</template>

<style scoped>
.private-chat-list { padding: 12px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.title { font-size: 12px; color: var(--text-quaternary); }
.chat-items { margin-top: 4px; }
.chat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.chat-item:hover { background: var(--bg-hover); }
.chat-item.active { background: var(--bg-active); }
.chat-info { flex: 1; min-width: 0; }
.chat-top { display: flex; justify-content: space-between; align-items: center; }
.chat-name { font-size: 13px; font-weight: 500; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-time { font-size: 11px; color: var(--text-quaternary); flex-shrink: 0; }
.chat-preview { font-size: 12px; color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 1px; }
.chat-meta { flex-shrink: 0; }
.chat-meta :deep(.mac-badge.info) {
  animation: badge-pulse 2s ease infinite;
}
@keyframes badge-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.25); }
}
.chat-actions { display: flex; gap: 4px; flex-shrink: 0; }
.chat-delete {
  background: none;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  opacity: 0;
  flex-shrink: 0;
  transition: opacity var(--transition-fast);
}
.chat-item:hover .chat-delete { opacity: 1; }
.chat-delete:hover { background: var(--bg-hover); color: var(--red); }
.empty { color: var(--text-quaternary); font-size: 12px; padding: 8px 10px; }
.search-dropdown { border: 1px solid var(--border-subtle); border-radius: var(--radius-md); margin-top: 8px; max-height: 150px; overflow-y: auto; }
.search-item { padding: 8px 12px; cursor: pointer; display: flex; justify-content: space-between; }
.search-item:hover { background: var(--bg-hover); }
.search-username { color: var(--text-quaternary); font-size: 12px; }
.no-results { color: var(--text-quaternary); font-size: 12px; margin-top: 8px; }
</style>
