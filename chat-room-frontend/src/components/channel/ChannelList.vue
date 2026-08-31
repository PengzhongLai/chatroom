<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { useMessageStore } from '@/stores/message'
import { type ChannelInfo } from '@/api/channels'
import { MacButton, MacInput, MacSheet, MacBadge } from '@/components/ui'

const channelStore = useChannelStore()
const messageStore = useMessageStore()

const showCreate = ref(false)
const showInvite = ref(false)
const inviteCode = ref('')
const form = ref({ name: '', description: '', isPublic: true })
const searchKeyword = ref('')

const emit = defineEmits<{ select: [id: number] }>()

onMounted(() => {
  channelStore.fetchChannels()
  channelStore.fetchMyChannels()
})

async function handleJoinByInvite() {
  if (!inviteCode.value.trim()) return
  try {
    await channelStore.joinByCode(inviteCode.value)
    inviteCode.value = ''
    showInvite.value = false
    console.log('加入成功')
  } catch (e: any) {
    // error handled by interceptor
  }
}

async function handleCreate() {
  if (!form.value.name.trim()) return
  await channelStore.createChannel(form.value.name, form.value.description, form.value.isPublic)
  showCreate.value = false
  form.value = { name: '', description: '', isPublic: true }
  console.log('频道创建成功')
}

function handleSearch() {
  channelStore.fetchChannels(searchKeyword.value || undefined)
}

function handleMyChannelSelect(channel: ChannelInfo) {
  // User is already a member — enter directly
  emit('select', channel.id)
}

async function handleDiscoverSelect(channel: ChannelInfo) {
  // Auto-join public channel (backend is idempotent — no-op if already a member)
  try {
    await channelStore.joinChannel(channel.id)
  } catch (e: any) {
    console.error(e?.response?.data?.message || '加入失败')
    return
  }
  emit('select', channel.id)
}
</script>

<template>
  <div class="channel-list">
    <div class="sidebar-header">
      <h3>频道</h3>
      <MacButton variant="primary" @click="showCreate = true">+ 创建</MacButton>
    </div>

    <div class="section-actions">
      <MacButton variant="default" style="width:100%" @click="showInvite = true">输入邀请码加入</MacButton>
    </div>

    <MacInput v-model="searchKeyword" placeholder="搜索频道..." @input="handleSearch" />

    <div class="section-header">
      <span class="section-label">我的频道</span>
    </div>
    <div class="channel-items">
      <div
        v-for="cm in channelStore.myChannels"
        :key="cm.id"
        class="channel-item"
        :class="{ active: channelStore.currentChannel?.id === cm.channel.id }"
        @click="handleMyChannelSelect(cm.channel as any)"
      >
        <span class="dot" :class="{ active: channelStore.currentChannel?.id === cm.channel.id }"></span>
        <span class="name">{{ cm.channel?.name || '#' }}</span>
        <span v-if="messageStore.mentionedIn.has(cm.channel.id)" class="badge mention">@</span>
        <span v-if="messageStore.unreadCount.has(cm.channel.id)" class="badge unread">{{ messageStore.unreadCount.get(cm.channel.id) }}</span>
      </div>
      <p v-if="channelStore.myChannels.length === 0" class="empty">暂未加入频道</p>
    </div>

    <div class="section-header">
      <span class="section-label">发现频道</span>
    </div>
    <div class="channel-items">
      <div
        v-for="ch in channelStore.channels"
        :key="ch.id"
        class="channel-item"
        @click="handleDiscoverSelect(ch)"
      >
        <span class="dot"></span>
        <span class="name">{{ ch.name }}</span>
        <MacBadge v-if="!ch.isPublic" variant="warning">私有</MacBadge>
      </div>
      <p v-if="channelStore.channels.length === 0" class="empty">暂无公开频道</p>
    </div>

    <!-- Create sheet -->
    <MacSheet :visible="showCreate" title="创建频道" width="400px" @close="showCreate = false">
      <form @submit.prevent="handleCreate">
        <div class="form-group">
          <label class="form-label">频道名</label>
          <MacInput v-model="form.name" placeholder="输入频道名称" />
        </div>
        <div class="form-group">
          <label class="form-label">描述</label>
          <MacInput v-model="form.description" placeholder="频道简介（选填）" type="textarea" :rows="2" />
        </div>
        <div class="form-group">
          <label class="form-label">类型</label>
          <div>
            <label class="mac-radio">
              <input type="radio" v-model="form.isPublic" :value="true" /> 公开
            </label>
            <label class="mac-radio">
              <input type="radio" v-model="form.isPublic" :value="false" /> 私有
            </label>
          </div>
        </div>
        <MacButton variant="primary" type="submit" :disabled="!form.name.trim()">创建</MacButton>
      </form>
    </MacSheet>

    <!-- Invite code sheet -->
    <MacSheet :visible="showInvite" title="通过邀请码加入" width="350px" @close="showInvite = false">
      <MacInput v-model="inviteCode" placeholder="请输入邀请码" />
      <div class="sheet-footer">
        <MacButton variant="default" @click="showInvite = false">取消</MacButton>
        <MacButton variant="primary" @click="handleJoinByInvite" :disabled="!inviteCode.trim()">加入</MacButton>
      </div>
    </MacSheet>
  </div>
</template>

<style scoped>
.channel-list {
  padding: 12px;
}
.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.sidebar-header h3 { margin: 0; font-size: 16px; }
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 0 4px;
}
.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-quaternary);
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.section-actions { margin-bottom: 4px; }
.channel-items { margin-top: 4px; }
.channel-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.channel-item:hover { background: var(--bg-hover); }
.channel-item.active { background: var(--bg-active); }
.dot {
  width: 4px; height: 4px;
  border-radius: 50%;
  background: var(--text-quaternary);
  flex-shrink: 0;
}
.dot.active { background: var(--blue); }
.name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
  font-size: 13px;
}
.badge { font-size: 10px; padding: 1px 6px; border-radius: var(--radius-full); font-weight: 700; margin-left: auto; flex-shrink: 0; }
.badge.mention { background: var(--red); color: #fff; }
.badge.unread { background: var(--blue); color: #fff; animation: badge-pulse 2s ease infinite; }
@keyframes badge-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.25); }
}
.empty { color: var(--text-quaternary); font-size: 12px; padding: 8px 10px; }
.mac-radio { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); cursor: pointer; margin-right: 16px; }
.mac-radio input[type="radio"] { accent-color: var(--blue); }
.form-group { margin-bottom: 14px; }
.form-label { display: block; font-size: 12px; font-weight: 500; color: var(--text-secondary); margin-bottom: 6px; }
.sheet-footer { display: flex; gap: 8px; justify-content: flex-end; margin-top: 12px; }
</style>
