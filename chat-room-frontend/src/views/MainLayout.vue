<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { useMessageStore } from '@/stores/message'
import { usePrivateChatStore } from '@/stores/privateChat'
import { usePresenceStore } from '@/stores/presence'
import { useAuthStore } from '@/stores/auth'
import { useStomp } from '@/composables/useStomp'
import request from '@/api/request'
import type { PrivateChatInfo } from '@/types'
import ChannelList from '@/components/channel/ChannelList.vue'
import ChannelSettings from '@/components/channel/ChannelSettings.vue'
import ChatHeader from '@/components/chat/ChatHeader.vue'
import MessageList from '@/components/chat/MessageList.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import TypingIndicator from '@/components/chat/TypingIndicator.vue'
import PrivateChatList from '@/components/chat/PrivateChatList.vue'
import PrivateChatView from '@/components/chat/PrivateChatView.vue'
import { searchMessages } from '@/api/search'
import { MacAvatar } from '@/components/ui'

const channelStore = useChannelStore()
const messageStore = useMessageStore()
const privateChatStore = usePrivateChatStore()
const presenceStore = usePresenceStore()
const authStore = useAuthStore()
const { connect, disconnect } = useStomp()

type ViewMode = 'none' | 'channel' | 'private'
const viewMode = ref<ViewMode>('none')
const showSettingsPanel = ref(false)
const showMembersPanel = ref(false)

onMounted(async () => {
  try {
    presenceStore.init()
    privateChatStore.init()
    await connect()
    await channelStore.fetchMyChannels()
    messageStore.subscribeToAllChannels()
    privateChatStore.fetchChats()
    if (channelStore.currentChannel?.id) {
      await enterChannel(channelStore.currentChannel.id)
    }
  } catch (e) {
    console.error('WebSocket connection failed:', e)
  }
})

onUnmounted(() => {
  disconnect()
  messageStore.cleanup()
})

function handleLogout() {
  authStore.clearAuth()
  window.location.href = '/login'
}

const panelMode = ref('channels')

const viewModeKey = computed(() => {
  if (viewMode.value === 'channel' && channelStore.currentChannel?.id) return 'ch-' + channelStore.currentChannel.id
  if (viewMode.value === 'private' && privateChatStore.currentChat?.id) return 'pv-' + privateChatStore.currentChat.id
  return 'empty'
})

async function handleSelectChannel(id: number) {
  privateChatStore.reset()
  messageStore.reset()
  messageStore.clearMention(id)
  messageStore.clearUnread(id)
  viewMode.value = 'channel'
  await channelStore.selectChannel(id)
  messageStore.subscribeToAllChannels()
  await messageStore.selectChannel(id)
  messageStore.markRead(id)
}

async function enterChannel(channelId: number) {
  await messageStore.selectChannel(channelId)
  messageStore.markRead(channelId)
}

// --- Search ---
const searchKeyword = ref('')
const searchResults = ref<any[]>([])
const searching = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | null = null
function handleSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(doSearch, 300)
}

async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) { searchResults.value = []; return }
  searching.value = true
  try {
    const res: any = await searchMessages(kw)
    searchResults.value = res.data || []
  } finally { searching.value = false }
}

function jumpToResult(item: any) {
  searchKeyword.value = ''
  searchResults.value = []
  panelMode.value = item.context === 'channel' ? 'channels' : 'private'
  if (item.context === 'channel') {
    handleSelectChannel(item.contextId)
  } else if (item.context === 'private') {
    const chat = privateChatStore.chats.find(c => c.id === item.contextId)
    if (chat) handleSelectPrivateChat(chat)
  }
}

async function handleSelectPrivateChat(chat: PrivateChatInfo) {
  messageStore.reset()
  channelStore.currentChannel = null
  viewMode.value = 'private'
  privateChatStore.clearUnread(chat.id)
  await privateChatStore.selectChat(chat)
}

async function handleStartPrivateChatFromMember(userId: number) {
  const chat = await privateChatStore.startChat(userId)
  messageStore.reset()
  channelStore.currentChannel = null
  viewMode.value = 'private'
  await privateChatStore.selectChat(chat)
}

function openSettings() {
  showSettingsPanel.value = true
}

function toggleMembersPanel() {
  showMembersPanel.value = !showMembersPanel.value
}

// Status menu
const showStatusMenu = ref(false)
const statusOptions = [
  { label: '在线', value: 'ONLINE', icon: '🟢' },
  { label: '隐身', value: 'INVISIBLE', icon: '🔘' },
  { label: '离线', value: 'OFFLINE', icon: '⚫' }
]

async function setStatus(status: string) {
  await request.put('/users/status', { status })
  authStore.user = { ...authStore.user!, status } as any
  showStatusMenu.value = false
}

function toggleStatusMenu() {
  showStatusMenu.value = !showStatusMenu.value
}
</script>

<template>
  <div class="main-layout">
    <!-- macOS Sidebar -->
    <aside class="sidebar">
      <div class="sidebar-top">
        <!-- Search -->
        <div class="sidebar-search">
          <input v-model="searchKeyword" class="search-input" placeholder="搜索消息或频道..." @input="handleSearchInput" />
          <div v-if="searchResults.length" class="search-drop">
            <div v-for="item in searchResults" :key="item.id" class="search-row" @click="jumpToResult(item)">
              <div class="search-row-hd">
                <span class="sr-context">{{ item.context === 'channel' ? '#' : '@' }}{{ item.contextName }}</span>
                <span class="sr-time">{{ item.createdAt?.substring(11, 16) }}</span>
              </div>
              <div class="sr-preview">{{ item.content || item.fileName }}</div>
            </div>
          </div>
        </div>

        <!-- Sidebar Content (ChannelList + PrivateChatList) -->
        <div class="sidebar-scroll">
          <ChannelList @select="handleSelectChannel" />
          <PrivateChatList @select="handleSelectPrivateChat" />
        </div>
      </div>

      <!-- User Bar -->
      <div class="sidebar-user">
        <div class="avatar-wrapper" @click="toggleStatusMenu">
          <MacAvatar
            :name="authStore.user?.nickname || authStore.user?.username || '?'"
            :gradient="1"
            :status="(authStore.user as any)?.status || 'ONLINE'"
            :show-status="true"
            :size="28"
          />
        </div>
        <span class="user-name">{{ authStore.user?.nickname || authStore.user?.username }}</span>
        <!-- Status dropdown -->
        <Transition name="status">
          <div v-if="showStatusMenu" class="status-overlay" @click="showStatusMenu = false"></div>
        </Transition>
        <Transition name="status">
          <div v-if="showStatusMenu" class="status-dropdown">
            <div class="dropdown-header">状态</div>
            <div v-for="opt in statusOptions" :key="opt.value" class="status-item" :class="{ active: ((authStore.user as any)?.status || 'ONLINE') === opt.value }" @click.stop="setStatus(opt.value)">
              <span class="sd-dot" :class="opt.value === 'ONLINE' ? 'online' : (opt.value === 'OFFLINE' ? 'offline' : 'invisible')"></span>
              <span class="sd-label">{{ opt.label }}</span>
              <span v-if="((authStore.user as any)?.status || 'ONLINE') === opt.value" class="sd-check">✓</span>
            </div>
            <div class="dropdown-divider"></div>
            <div class="status-item" @click="handleLogout">
              <span class="sd-icon">🚪</span>
              <span class="sd-label">退出登录</span>
            </div>
          </div>
        </Transition>
      </div>
    </aside>

    <!-- Chat Area -->
    <main class="chat-area">
      <Transition name="chat-switch" mode="out-in">
        <div :key="viewModeKey" class="chat-fill">
          <template v-if="viewMode === 'channel' && channelStore.currentChannel">
            <ChatHeader @toggle-settings="openSettings" @toggle-members="toggleMembersPanel" />
        <ChannelSettings
          :open-settings="showSettingsPanel"
          :open-members="showMembersPanel"
          @update:open-settings="showSettingsPanel = $event"
          @update:open-members="showMembersPanel = $event"
          @start-private-chat="handleStartPrivateChatFromMember"
        />
        <MessageList />
        <TypingIndicator />
        <MessageInput />
      </template>
      <template v-else-if="viewMode === 'private' && privateChatStore.currentChat">
        <PrivateChatView />
      </template>
      <div v-else class="placeholder">
        <span class="placeholder-icon">💬</span>
        <p class="placeholder-text">选择频道或私聊开始聊天</p>
      </div>
        </div>
      </Transition>
    </main>
  </div>
</template>

<style scoped>
.main-layout { display: flex; height: 100vh; width: 100%; }

/* macOS Sidebar */
.sidebar {
  width: 240px;
  min-width: 240px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px;
  background: transparent;
}

/* Sidebar top glass card */
.sidebar-top {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-secondary);
  backdrop-filter: blur(var(--blur-xl));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
  overflow: hidden;
}

/* Search */
.sidebar-search { padding: 12px 14px 8px; position: relative; }
.search-input {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: 7px 10px;
  font-size: 12px;
  color: var(--text-secondary);
  outline: none;
  font-family: inherit;
  transition: border-color var(--transition-fast);
}
.search-input:focus { border-color: var(--blue); }
.search-input::placeholder { color: var(--text-quaternary); }

.search-drop {
  position: absolute;
  top: calc(100% - 4px);
  left: 14px;
  right: 14px;
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-xl));
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  z-index: 100;
  max-height: 280px;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}
.search-row { padding: 8px 12px; cursor: pointer; border-bottom: 1px solid var(--border-subtle); }
.search-row:hover { background: var(--bg-hover); }
.search-row-hd { display: flex; justify-content: space-between; margin-bottom: 2px; }
.sr-context { font-size: 11px; color: var(--blue); font-weight: 600; }
.sr-time { font-size: 10px; color: var(--text-quaternary); }
.sr-preview { font-size: 12px; color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.sidebar-scroll { flex: 1; overflow-y: auto; padding: 0; }

/* User Bar — glass card */
.sidebar-user {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  background: rgba(28, 28, 30, 0.6);
  backdrop-filter: blur(var(--blur-lg));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
}
.user-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu {
  color: var(--text-quaternary);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: var(--radius-sm);
}
.user-menu:hover { background: var(--bg-hover); }

/* Status Dropdown */
.status-dropdown {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 8px;
  min-width: 170px;
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-xl));
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 24px rgba(0,0,0,0.5);
  padding: 6px;
  z-index: 1000;
}
.dropdown-header {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-quaternary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 4px 10px 6px;
}
.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: background var(--transition-fast);
}
.status-item:hover { background: var(--bg-hover); }
.status-item.active { background: rgba(10, 132, 255, 0.1); }
.sd-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.sd-dot.online { background: var(--green); box-shadow: 0 0 6px rgba(48, 209, 88, 0.5); }
.sd-dot.offline { background: var(--text-quaternary); }
.sd-dot.invisible { background: var(--orange); box-shadow: 0 0 6px rgba(255, 159, 10, 0.5); }
.sd-label { flex: 1; }
.sd-check {
  color: var(--blue);
  font-weight: 700;
  font-size: 12px;
}
.sd-icon { font-size: 12px; }
.dropdown-divider { height: 1px; background: var(--border-subtle); margin: 6px 0; }
.avatar-wrapper {
  cursor: pointer;
  border-radius: 50%;
  line-height: 0;
  transition: opacity var(--transition-fast);
}
.avatar-wrapper:hover { opacity: 0.8; }

.status-enter-active, .status-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.status-enter-from, .status-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
.status-overlay {
  position: fixed;
  inset: 0;
  z-index: 999;
}

/* Chat Area */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-primary);
}
.placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.placeholder-icon { font-size: 48px; }
.placeholder-text { color: var(--text-tertiary); font-size: 14px; }
.chat-fill { display: flex; flex-direction: column; flex: 1; min-height: 0; gap: 2px; padding: 6px 6px 6px 0; }

/* Chat switch transition */
.chat-switch-enter-active, .chat-switch-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.chat-switch-enter-from { opacity: 0; transform: translateY(8px); }
.chat-switch-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
