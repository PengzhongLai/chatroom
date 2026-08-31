<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useChannelStore } from '@/stores/channel'
import { usePresenceStore } from '@/stores/presence'
import * as channelApi from '@/api/channels'
import request from '@/api/request'
import { MacButton, MacInput, MacBadge, MacSheet, MacAvatar } from '@/components/ui'

const props = withDefaults(defineProps<{
  openSettings?: boolean
  openMembers?: boolean
}>(), {
  openSettings: false,
  openMembers: false
})

const channelStore = useChannelStore()
const presenceStore = usePresenceStore()

const emit = defineEmits<{
  startPrivateChat: [userId: number]
  'update:openSettings': [val: boolean]
  'update:openMembers': [val: boolean]
}>()

watch(() => props.openSettings, (val) => {
  if (val && channelStore.currentChannel) {
    editName.value = channelStore.currentChannel.name
    editDesc.value = channelStore.currentChannel.description || ''
    showSettings.value = true
    emit('update:openSettings', false)
  }
})
watch(() => props.openMembers, (val) => {
  if (val) { showMembers.value = true; emit('update:openMembers', false) }
})

const showSettings = ref(false)
const showMembers = ref(false)
const showInvite = ref(false)
const showDeleteConfirm = ref(false)
const editName = ref('')
const editDesc = ref('')
const inviteUsername = ref('')
const searchResults = ref<any[]>([])
const searching = ref(false)
const historyLevel = ref<'ALL' | 'LIMITED' | 'NONE'>('ALL')
const historyLimit = ref(50)

const isAdmin = computed(() => {
  const role = channelStore.getMyRole()
  return role === 'CREATOR' || role === 'ADMIN'
})

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

function copyInviteCode(code: string) {
  navigator.clipboard.writeText(code)
  showToast('邀请码已复制', 'success')
}

async function saveSettings() {
  if (!channelStore.currentChannel) return
  await channelApi.updateChannel(channelStore.currentChannel.id, editName.value, editDesc.value)
  await channelStore.selectChannel(channelStore.currentChannel.id)
  showSettings.value = false
  showToast('频道信息已更新', 'success')
}


async function handleKick(userId: number, nickname: string) {
  if (!confirm(`确定要踢出 ${nickname} 吗？`)) return
  await channelStore.kickMember(channelStore.currentChannel!.id, userId)
  showToast('已踢出', 'success')
}

async function handlePromote(userId: number, nickname: string) {
  if (!confirm(`确定要将 ${nickname} 设为管理员吗？`)) return
  await channelApi.promoteToAdmin(channelStore.currentChannel!.id, userId)
  await channelStore.selectChannel(channelStore.currentChannel!.id)
  showToast('已提升为管理员', 'success')
}

async function handleDemote(userId: number, nickname: string) {
  if (!confirm(`确定要将 ${nickname} 降级为普通成员吗？`)) return
  await channelApi.demoteToMember(channelStore.currentChannel!.id, userId)
  await channelStore.selectChannel(channelStore.currentChannel!.id)
  showToast('已降级为成员', 'success')
}

async function handleTransfer(userId: number, nickname: string) {
  if (!confirm(`确定要将频道转让给 ${nickname} 吗？转让后你将失去创建者权限。`)) return
  await channelApi.transferChannel(channelStore.currentChannel!.id, userId)
  await channelStore.selectChannel(channelStore.currentChannel!.id)
  channelStore.fetchMyChannels()
  showToast('频道已转让', 'success')
}

async function handleDeleteChannel() {
  if (!channelStore.currentChannel) return
  showSettings.value = false
  await nextTick()
  showDeleteConfirm.value = true
}

async function confirmDeleteChannel() {
  if (!channelStore.currentChannel) return
  showDeleteConfirm.value = false
  await channelApi.deleteChannel(channelStore.currentChannel.id)
  channelStore.currentChannel = null
  channelStore.members = []
  await channelStore.fetchMyChannels()
  await channelStore.fetchChannels()
  showToast('频道已解散', 'success')
}

async function searchUser() {
  if (!inviteUsername.value.trim()) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    const res: any = await request.get('/users/search', { params: { q: inviteUsername.value } })
    searchResults.value = res.data || []
  } finally {
    searching.value = false
  }
}

async function handleInvite(userId: number) {
  await channelApi.inviteMember(
    channelStore.currentChannel!.id,
    userId,
    historyLevel.value,
    historyLevel.value === 'LIMITED' ? historyLimit.value : undefined
  )
  showInvite.value = false
  inviteUsername.value = ''
  searchResults.value = []
  showToast('已邀请', 'success')
}

function handlePrivateChat(userId: number) {
  emit('startPrivateChat', userId)
}
</script>

<template>
  <div v-if="channelStore.currentChannel">
    <!-- Members sheet (visible to all) -->
    <MacSheet :visible="showMembers" :title="`频道成员（${channelStore.members.length}人）`" width="400px" @close="showMembers = false">
      <div class="member-list">
        <div v-for="m in channelStore.members" :key="m.id" class="member-row">
          <div class="member-info">
            <MacAvatar
              :name="m.user.nickname || m.user.username"
              :gradient="(m.user.id % 6) + 1"
              :online="presenceStore.isOnline(m.user.id)"
              :show-status="true"
              :size="28"
            />
            <div>
              <span class="member-name">{{ m.user.nickname || m.user.username }}</span>
              <MacBadge :variant="m.role === 'CREATOR' ? 'danger' : m.role === 'ADMIN' ? 'warning' : 'default'">
                {{ m.role === 'CREATOR' ? '创建者' : m.role === 'ADMIN' ? '管理员' : '成员' }}
              </MacBadge>
            </div>
          </div>
          <div class="member-actions">
            <MacButton v-if="m.user.id !== channelStore.getUserId()" variant="plain" size="sm"
              @click="handlePrivateChat(m.user.id); showMembers = false">私聊</MacButton>
          </div>
        </div>
      </div>
    </MacSheet>

    <!-- Settings sheet -->
    <MacSheet :visible="showSettings" title="频道设置" width="500px" @close="showSettings = false">
      <div class="form-group">
        <label class="form-label">频道名</label>
        <MacInput v-model="editName" />
      </div>
      <div class="form-group">
        <label class="form-label">描述</label>
        <MacInput v-model="editDesc" type="textarea" :rows="2" />
      </div>
      <div v-if="!channelStore.currentChannel.isPublic && channelStore.currentChannel.inviteCode" class="form-group">
        <label class="form-label">邀请码</label>
        <div class="invite-code-row">
          <MacInput :model-value="channelStore.currentChannel.inviteCode" readonly />
          <MacButton variant="primary" size="sm" @click="copyInviteCode(channelStore.currentChannel.inviteCode || '')">复制</MacButton>
        </div>
      </div>

      <div v-if="channelStore.getMyRole() === 'CREATOR'" class="danger-zone">
        <div class="danger-card">
          <div class="danger-icon">⚠️</div>
          <div class="danger-info">
            <div class="danger-label">解散频道</div>
            <div class="danger-desc">此操作不可撤销，频道和所有消息将被永久删除</div>
          </div>
          <MacButton variant="danger" @click="handleDeleteChannel">解散</MacButton>
        </div>
      </div>

      <h4 style="margin-top:20px;margin-bottom:0">
        成员管理（{{ channelStore.members.length }}人）
        <MacButton v-if="isAdmin" variant="plain" size="sm" style="float:right" @click="showInvite = true; inviteUsername = ''; searchResults = []">+ 邀请</MacButton>
      </h4>
      <div class="member-list">
        <div v-for="m in channelStore.members" :key="m.id" class="member-row">
          <div class="member-info">
            <MacAvatar
              :name="m.user.nickname || m.user.username"
              :gradient="(m.user.id % 6) + 1"
              :online="presenceStore.isOnline(m.user.id)"
              :show-status="true"
              :size="28"
            />
            <div>
              <span class="member-name">{{ m.user.nickname || m.user.username }}</span>
              <MacBadge :variant="m.role === 'CREATOR' ? 'danger' : m.role === 'ADMIN' ? 'warning' : 'default'">
                {{ m.role === 'CREATOR' ? '创建者' : m.role === 'ADMIN' ? '管理员' : '成员' }}
              </MacBadge>
            </div>
          </div>
          <div class="member-actions">
            <MacButton v-if="m.user.id !== channelStore.getUserId()" variant="plain" size="sm" @click="handlePrivateChat(m.user.id)">私聊</MacButton>
            <template v-if="isAdmin && m.role !== 'CREATOR' && m.user.id !== channelStore.getUserId()">
              <MacButton v-if="channelStore.getMyRole() === 'CREATOR' && m.role === 'MEMBER'" variant="plain" size="sm" @click="handlePromote(m.user.id, m.user.nickname || m.user.username)">提升</MacButton>
              <MacButton v-if="channelStore.getMyRole() === 'CREATOR' && m.role === 'ADMIN'" variant="plain" size="sm" @click="handleDemote(m.user.id, m.user.nickname || m.user.username)">降级</MacButton>
              <MacButton v-if="channelStore.getMyRole() === 'CREATOR' && m.role === 'ADMIN'" variant="plain" size="sm" @click="handleTransfer(m.user.id, m.user.nickname || m.user.username)">转让</MacButton>
              <MacButton variant="danger" size="sm" @click="handleKick(m.user.id, m.user.nickname || m.user.username)">踢出</MacButton>
            </template>
          </div>
        </div>
      </div>

      <div class="sheet-footer">
        <MacButton @click="showSettings = false">取消</MacButton>
        <MacButton variant="primary" @click="saveSettings">保存</MacButton>
      </div>
    </MacSheet>

    <!-- Invite sheet -->
    <MacSheet :visible="showInvite" title="邀请成员" width="420px" @close="showInvite = false; inviteUsername = ''; searchResults = []">
      <div class="form-group">
        <label class="form-label">用户名</label>
        <MacInput v-model="inviteUsername" placeholder="输入用户名搜索" @input="searchUser" />
        <div v-if="searchResults.length" class="search-dropdown">
          <div v-for="u in searchResults" :key="u.id" class="search-item" @click="handleInvite(u.id)">
            <span>{{ u.nickname || u.username }}</span>
            <span style="color:var(--text-tertiary);font-size:12px">@{{ u.username }}</span>
          </div>
        </div>
        <p v-if="inviteUsername && !searching && searchResults.length === 0" style="color:var(--text-tertiary);font-size:12px">
          未找到用户
        </p>
      </div>
      <div class="form-group">
        <label class="form-label">历史消息</label>
        <div class="radio-group">
          <label class="mac-radio"><input type="radio" v-model="historyLevel" value="ALL" /> 全部</label>
          <label class="mac-radio"><input type="radio" v-model="historyLevel" value="LIMITED" /> 最近</label>
          <label class="mac-radio"><input type="radio" v-model="historyLevel" value="NONE" /> 不可见</label>
        </div>
      </div>
      <div v-if="historyLevel === 'LIMITED'" class="form-group">
        <label class="form-label">条数</label>
        <input type="number" v-model.number="historyLimit" min="1" max="500" class="mac-input-number" />
      </div>
    </MacSheet>

    <!-- Delete confirm -->
    <MacSheet :visible="showDeleteConfirm" title="" width="380px" @close="showDeleteConfirm = false">
      <div class="delete-confirm">
        <div class="delete-icon-wrap">⚠️</div>
        <div class="delete-title">解散频道</div>
        <div class="delete-desc">解散后频道和所有消息将被永久删除，此操作不可撤销。确定要解散吗？</div>
        <div class="delete-actions">
          <MacButton @click="showDeleteConfirm = false">取消</MacButton>
          <MacButton variant="danger" @click="confirmDeleteChannel">确认解散</MacButton>
        </div>
      </div>
    </MacSheet>
  </div>
</template>

<style scoped>
.member-list {
  max-height: 300px;
  overflow-y: auto;
}
.member-row {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.member-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.member-info > div {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.member-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}
.member-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}
.form-group {
  margin-bottom: 14px;
}
.form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.invite-code-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.invite-code-row .mac-input {
  flex: 1;
}
.radio-group {
  display: flex;
  gap: 16px;
}
.mac-radio {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}
.mac-radio input[type="radio"] {
  accent-color: var(--blue);
}
.search-dropdown {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  margin-top: 4px;
  max-height: 150px;
  overflow-y: auto;
}
.search-item {
  padding: 8px 12px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
}
.search-item:hover {
  background: var(--bg-hover);
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}
.status-dot.online { background: var(--green); }
.status-dot.offline { background: var(--text-quaternary); }
.danger-zone {
  margin-top: 24px;
  border-top: 1px solid rgba(255, 69, 58, 0.15);
  padding-top: 20px;
}
.danger-card {
  background: rgba(255, 69, 58, 0.08);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 69, 58, 0.2);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.danger-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: rgba(255, 69, 58, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.danger-info { flex: 1; min-width: 0; }
.danger-label { color: var(--red); font-size: 13px; font-weight: 600; }
.danger-desc { color: var(--text-tertiary); font-size: 11px; margin-top: 2px; }
.sheet-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}
.mac-input-number {
  width: 100%;
  padding: 8px 12px;
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
}
.mac-input-number:focus {
  border-color: var(--blue);
}

/* Delete confirm dialog */
.delete-confirm {
  text-align: center;
  padding: 12px 0 4px;
}
.delete-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: rgba(255, 69, 58, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin: 0 auto 14px;
}
.delete-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.delete-desc {
  font-size: 13px;
  color: var(--text-tertiary);
  line-height: 1.5;
  margin-bottom: 20px;
}
.delete-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}
</style>
