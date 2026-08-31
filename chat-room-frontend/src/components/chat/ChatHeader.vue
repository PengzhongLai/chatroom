<script setup lang="ts">
import { ref, computed } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { useMessageStore } from '@/stores/message'
import * as channelApi from '@/api/channels'
import { MacButton } from '@/components/ui'

const channelStore = useChannelStore()
const messageStore = useMessageStore()
const emit = defineEmits<{ toggleSettings: []; toggleMembers: [] }>()

const showMenu = ref(false)

const isAdmin = computed(() => {
  const role = channelStore.getMyRole()
  return role === 'CREATOR' || role === 'ADMIN'
})
const isCreator = computed(() => channelStore.getMyRole() === 'CREATOR')
const isMuted = computed(() => channelStore.currentChannel?.isMuted ?? false)

function toggleMenu() {
  showMenu.value = !showMenu.value
}

function closeMenu() {
  showMenu.value = false
}

function showToast(message: string, type: 'success' | 'error' | 'info' = 'info') {
  const toast = document.createElement('div')
  toast.className = `mac-toast ${type}`
  toast.textContent = message
  document.body.appendChild(toast)
  requestAnimationFrame(() => toast.classList.add('visible'))
  setTimeout(() => {
    toast.classList.remove('visible')
    setTimeout(() => toast.remove(), 300)
  }, 2500)
}

function handleAction(action: string) {
  closeMenu()
  if (action === 'members') { emit('toggleMembers'); return }
  if (action === 'settings') { emit('toggleSettings'); return }
  if (action === 'mute') { handleToggleMute(); return }
  if (action === 'leave') { handleLeave(); return }
  if (action === 'delete') { handleDelete(); return }
}

async function handleToggleMute() {
  if (!channelStore.currentChannel) return
  await channelStore.toggleMute(channelStore.currentChannel.id)
  showToast(channelStore.currentChannel.isMuted ? '已开启禁言' : '已关闭禁言', 'success')
}

async function handleLeave() {
  if (!channelStore.currentChannel) return
  if (channelStore.getMyRole() === 'CREATOR') {
    showToast('创建者不能退出，请先解散频道或转让', 'error')
    return
  }
  if (!confirm('确定要退出该频道吗？')) return
  const channelId = channelStore.currentChannel.id
  await channelStore.leaveChannel(channelId)
  messageStore.reset()
  showToast('已退出频道', 'success')
}

async function handleDelete() {
  if (!channelStore.currentChannel) return
  if (!confirm('解散后频道和所有消息将被永久删除，此操作不可撤销。确定要解散吗？')) return
  await channelApi.deleteChannel(channelStore.currentChannel.id)
  channelStore.currentChannel = null
  channelStore.members = []
  await channelStore.fetchMyChannels()
  await channelStore.fetchChannels()
  showToast('频道已解散', 'success')
}
</script>

<template>
  <div class="chat-header" v-if="channelStore.currentChannel">
    <div class="header-left">
      <span class="status-indicator online"></span>
      <h3>{{ channelStore.currentChannel.name }}</h3>
      <span class="separator">·</span>
      <span class="member-count" @click="emit('toggleMembers')">{{ channelStore.members.length }} 成员</span>
    </div>
    <div class="header-right">
      <span v-if="channelStore.currentChannel.description" class="header-desc">{{ channelStore.currentChannel.description }}</span>
      <MacButton v-if="isAdmin" variant="plain" size="sm" @click="emit('toggleSettings')">⚙</MacButton>
      <div class="menu-wrapper">
        <MacButton variant="plain" size="sm" @click="toggleMenu">⋯</MacButton>
        <Transition name="dropdown">
          <div v-if="showMenu" class="header-dropdown" @click.self="closeMenu">
            <div class="dropdown-item" @click="handleAction('members')">👥 查看成员</div>
            <div v-if="isAdmin" class="dropdown-item" @click="handleAction('settings')">⚙ 频道设置</div>
            <div v-if="isAdmin" class="dropdown-item" @click="handleAction('mute')">
              {{ isMuted ? '🔊 取消禁言' : '🔇 全频道禁言' }}
            </div>
            <div v-if="!isCreator" class="dropdown-divider"></div>
            <div v-if="!isCreator" class="dropdown-item" @click="handleAction('leave')">🚪 退出频道</div>
            <div v-if="isCreator" class="dropdown-divider"></div>
            <div v-if="isCreator" class="dropdown-item danger" @click="handleAction('delete')">🗑 解散频道</div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-header {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  background: var(--bg-secondary);
  backdrop-filter: blur(var(--blur-xl));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
  position: relative;
  z-index: 10;
}
.header-left { display: flex; align-items: center; gap: 8px; min-width: 0; }
.status-indicator { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.status-indicator.online { background: var(--green); }
.header-left h3 { margin: 0; font-size: 14px; color: var(--text-secondary); font-weight: 600; white-space: nowrap; }
.separator { color: var(--text-quaternary); }
.member-count { font-size: 12px; color: var(--text-tertiary); cursor: pointer; white-space: nowrap; }
.member-count:hover { color: var(--text-link); }
.header-right { display: flex; align-items: center; gap: 2px; min-width: 0; }
.header-desc { font-size: 12px; color: var(--text-tertiary); max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 8px; }

.menu-wrapper { position: relative; }

.header-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  min-width: 170px;
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-xl));
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 4px;
  z-index: 1000;
}
.dropdown-item {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  text-align: center;
  transition: background var(--transition-fast);
}
.dropdown-item:hover { background: var(--bg-hover); }
.dropdown-item.danger { color: var(--red); }
.dropdown-item.danger:hover { background: var(--red-dim); }
.dropdown-divider { height: 1px; background: var(--border-subtle); margin: 4px 0; }

.dropdown-enter-active, .dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.dropdown-enter-from, .dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
