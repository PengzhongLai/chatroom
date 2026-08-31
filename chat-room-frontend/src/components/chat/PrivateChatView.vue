<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted } from 'vue'
import { usePrivateChatStore } from '@/stores/privateChat'
import MessageBubble from './MessageBubble.vue'
import FileUploader from '@/components/common/FileUploader.vue'
import EmojiPicker from '@/components/common/EmojiPicker.vue'
import { MacInput, MacButton, MacAvatar, MacBadge } from '@/components/ui'

const store = usePrivateChatStore()
const scrollContainer = ref<HTMLElement | null>(null)
const inputText = ref('')
const showEmoji = ref(false)
const searchText = ref('')
const uploaderRef = ref<InstanceType<typeof FileUploader> | null>(null)
const isLoadingMore = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

watch(inputText, () => {
  nextTick(autoResize)
})

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  const newHeight = Math.min(el.scrollHeight, 220)
  el.style.height = newHeight + 'px'
  el.style.overflowY = el.scrollHeight > 220 ? 'auto' : 'hidden'
  const msgList = document.querySelector('.message-list') as HTMLElement | null
  if (msgList) msgList.scrollTop = msgList.scrollHeight
}

const filteredMessages = computed(() => {
  if (!searchText.value.trim()) return store.messages
  const kw = searchText.value.toLowerCase()
  return store.messages.filter((m: any) =>
    (m.content && m.content.toLowerCase().includes(kw)) ||
    (m.fileName && m.fileName.toLowerCase().includes(kw))
  )
})

watch(
  () => store.messages.length,
  () => {
    if (store.messages.length > 0 && !isLoadingMore.value) {
      nextTick(() => requestAnimationFrame(() => scrollToBottom()))
    }
  }
)

onMounted(() => {
  nextTick(() => scrollToBottom())
})

function scrollToBottom() {
  if (scrollContainer.value) {
    scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
  }
}

async function handleScroll() {
  const el = scrollContainer.value
  if (!el || el.scrollTop > 50 || !store.hasMore || isLoadingMore.value) return
  isLoadingMore.value = true
  const prevHeight = el.scrollHeight
  await store.loadMore()
  await nextTick()
  if (el) {
    el.scrollTop = el.scrollHeight - prevHeight
  }
  isLoadingMore.value = false
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text || !store.currentChat) return
  store.sendMessage(store.currentChat.id, text)
  inputText.value = ''
  nextTick(autoResize)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleFileUploaded(result: { fileName: string; filePath: string; fileType: 'IMAGE' | 'FILE' }) {
  if (!store.currentChat) return
  store.sendFileMessage(store.currentChat.id, result.fileName, result.filePath, result.fileType)
}

async function handleDeleteChat() {
  if (!store.currentChat) return
  if (!confirm('确定要删除吗？聊天记录将被删除。')) return
  await store.deleteChat(store.currentChat.id)
}

function handleDrop(e: DragEvent) {
  uploaderRef.value?.handleDrop(e)
}

function insertEmoji(emoji: string) {
  inputText.value += emoji
  showEmoji.value = false
}
</script>

<template>
  <div class="private-chat-view" v-if="store.currentChat" @dragover.prevent @drop.prevent="handleDrop">
    <div class="pv-header">
      <MacAvatar :name="store.currentChat.otherUser.nickname || store.currentChat.otherUser.username || '?'" :gradient="(store.currentChat.otherUser.id || 0) % 6 + 1" :size="28" />
      <h3>{{ store.currentChat.otherUser.nickname || store.currentChat.otherUser.username }}</h3>
      <MacBadge v-if="store.currentChat.status === 'PENDING' && store.currentChat.iAmInitiator" variant="info">等待对方同意</MacBadge>
      <MacButton variant="danger" size="sm" class="delete-btn" @click="handleDeleteChat">删除</MacButton>
    </div>

    <!-- PENDING: show placeholder -->
    <div v-if="store.currentChat.status !== 'ACTIVE'" class="pv-status">
      <template v-if="store.currentChat.status === 'PENDING' && store.currentChat.iAmInitiator">
        <p>等待 {{ store.currentChat.otherUser.nickname || store.currentChat.otherUser.username }} 同意私聊申请</p>
      </template>
      <template v-else>
        <p>私聊尚未激活</p>
      </template>
    </div>

    <!-- ACTIVE: normal chat -->
    <template v-else>
    <div class="pv-messages">
      <div v-if="store.messages.length > 0 || searchText" class="local-search">
        <MacInput v-model="searchText" placeholder="在私聊内搜索..." />
      </div>
      <div class="message-list" ref="scrollContainer" @scroll="handleScroll">
        <div v-if="store.loading && store.messages.length === 0" class="hint">加载中...</div>
        <TransitionGroup name="msg" tag="div" class="msg-group">
          <MessageBubble
            v-for="msg in filteredMessages"
            :key="msg.id || msg.content + (msg.createdAt || '')"
            :message="msg"
            :keyword="searchText"
          />
        </TransitionGroup>
      </div>
    </div>
    <FileUploader ref="uploaderRef" :on-uploaded="handleFileUploaded" />
    <div v-if="showEmoji" class="emoji-wrapper">
      <EmojiPicker @select="insertEmoji" />
    </div>
    <div class="pv-input">
      <div class="input-row">
      <MacButton variant="plain" size="sm" @click="showEmoji = !showEmoji">😊</MacButton>
      <MacButton variant="plain" size="sm" @click="uploaderRef?.triggerFileSelect()">📎</MacButton>
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="input-area"
        placeholder=""
        rows="1"
        @keydown="handleKeydown"
      ></textarea>
      <button class="send-btn" :disabled="!inputText.trim()" @click="handleSend">
        <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
          <path d="M10 3L4 11H8V17H12V11H16L10 3Z" fill="currentColor"/>
        </svg>
      </button>
    </div>
    </div>
    </template>
  </div>
  <div v-else class="placeholder">
    <p>选择一个私聊开始对话</p>
  </div>
</template>

<style scoped>
.private-chat-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 2px;
}
.pv-header {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  background: var(--bg-secondary);
  backdrop-filter: blur(var(--blur-xl));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
}
.pv-header h3 { margin: 0; font-size: 14px; color: var(--text-secondary); font-weight: 600; }
.pv-messages {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: rgba(28, 28, 30, 0.55);
  backdrop-filter: blur(var(--blur-lg));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
  overflow: hidden;
}
.pv-input {
  background: rgba(28, 28, 30, 0.65);
  backdrop-filter: blur(var(--blur-lg));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
}
.pv-status {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: 14px;
  background: rgba(28, 28, 30, 0.55);
  backdrop-filter: blur(var(--blur-lg));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
}
.local-search { padding: 8px 16px; border-bottom: 1px solid var(--border-subtle); }
.emoji-wrapper { margin-bottom: 8px; }
.delete-btn { margin-left: auto; }
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  min-height: 0;
}
.hint { text-align: center; padding: 8px; font-size: 12px; color: var(--text-tertiary); }
.input-row {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 10px 14px 10px;
}
.input-row .mac-btn { height: 28px; }
.input-area {
  flex: 1;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 4px 10px;
  height: 28px;
  font-size: 14px;
  resize: none;
  outline: none;
  font-family: inherit;
  background: var(--bg-input);
  color: var(--text-secondary);
  transition: border-color var(--transition-fast), height 0.2s ease;
  box-sizing: border-box;
}
.input-area:focus { border-color: var(--blue); }
.input-area::placeholder { color: var(--text-quaternary); }
.send-btn {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--blue);
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: opacity var(--transition-fast);
  flex-shrink: 0;
}
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.send-btn:hover:not(:disabled) { opacity: 0.9; }
.placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

/* Bubble spring animation */
.msg-group { display: contents; }
.msg-enter-active {
  animation: msg-in 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.msg-move {
  transition: transform 0.3s ease;
}
@keyframes msg-in {
  from { opacity: 0; transform: translateY(16px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
