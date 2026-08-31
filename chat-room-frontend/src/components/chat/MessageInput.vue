<script setup lang="ts">
import { ref, watch, computed, nextTick } from 'vue'
import { useMessageStore } from '@/stores/message'
import { useChannelStore } from '@/stores/channel'
import FileUploader from '@/components/common/FileUploader.vue'
import EmojiPicker from '@/components/common/EmojiPicker.vue'
import { MacButton } from '@/components/ui'

const messageStore = useMessageStore()
const channelStore = useChannelStore()

const inputText = ref('')
const sending = ref(false)
const uploaderRef = ref<InstanceType<typeof FileUploader> | null>(null)
const showEmoji = ref(false)
const showMention = ref(false)
const mentionFilter = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
let typingTimer: ReturnType<typeof setTimeout> | null = null
let typingSent = false

watch(
  () => channelStore.currentChannel?.id,
  () => { inputText.value = ''; clearTyping() }
)

// Auto-resize textarea on content change
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
  // Scroll message list to bottom so new messages stay visible
  const msgList = document.querySelector('.message-list') as HTMLElement | null
  if (msgList) msgList.scrollTop = msgList.scrollHeight
}

function resetTextareaHeight() {
  const el = textareaRef.value
  if (el) {
    el.style.height = ''
  }
}

const mentionMembers = computed(() => {
  if (!mentionFilter.value) return channelStore.members || []
  const q = mentionFilter.value.toLowerCase()
  return (channelStore.members || []).filter(m => {
    const name = (m.user.nickname || m.user.username).toLowerCase()
    return name.includes(q)
  })
})

function handleKeydown(e: KeyboardEvent) {
  if (showMention.value) {
    if (e.key === 'Escape') { showMention.value = false; return }
    if (e.key === 'Enter') { e.preventDefault(); return }
    return
  }
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleInput() {
  autoResize()
  const textarea = textareaRef.value
  if (textarea) {
    const cursor = textarea.selectionStart
    const beforeCursor = inputText.value.substring(0, cursor)
    const atIdx = beforeCursor.lastIndexOf('@')
    if (atIdx !== -1 && (atIdx === 0 || beforeCursor[atIdx - 1] === ' ')) {
      const filter = beforeCursor.substring(atIdx + 1)
      if (!filter.includes(' ')) {
        mentionFilter.value = filter
        showMention.value = true
      } else {
        showMention.value = false
      }
    } else {
      showMention.value = false
    }
  }
  if (!typingSent) { sendTyping(true); typingSent = true }
  if (typingTimer) clearTimeout(typingTimer)
  typingTimer = setTimeout(() => { sendTyping(false); typingSent = false }, 2000)
}

function insertMention(username: string) {
  const textarea = textareaRef.value
  if (!textarea) return
  const cursor = textarea.selectionStart
  const beforeCursor = inputText.value.substring(0, cursor)
  const atIdx = beforeCursor.lastIndexOf('@')
  const before = inputText.value.substring(0, atIdx)
  const after = inputText.value.substring(cursor)
  inputText.value = before + '@' + username + ' ' + after
  showMention.value = false
  nextTick(() => textarea.focus())
}

function insertEmoji(emoji: string) {
  inputText.value += emoji
  showEmoji.value = false
  textareaRef.value?.focus()
}

function sendTyping(typing: boolean) {
  const chId = channelStore.currentChannel?.id
  if (chId) messageStore.sendTyping(chId, typing)
}

function clearTyping() {
  if (typingTimer) clearTimeout(typingTimer)
  if (typingSent) {
    const chId = channelStore.currentChannel?.id
    if (chId) messageStore.sendTyping(chId, false)
    typingSent = false
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  const chId = channelStore.currentChannel?.id
  if (!chId) return
  sending.value = true
  try {
    messageStore.sendMessage(chId, text)
    inputText.value = ''
    clearTyping()
    showMention.value = false
    nextTick(resetTextareaHeight)
  } finally { sending.value = false }
}

function handleFileUploaded(result: { fileName: string; filePath: string; fileType: 'IMAGE' | 'FILE' }) {
  const chId = channelStore.currentChannel?.id
  if (!chId) return
  messageStore.sendFileMessage(chId, result.fileName, result.filePath, result.fileType)
}

function handleDrop(e: DragEvent) {
  uploaderRef.value?.handleDrop(e)
}
</script>

<template>
  <div class="message-input" v-if="channelStore.currentChannel" @dragover.prevent @drop.prevent="handleDrop">
    <div v-if="channelStore.currentChannel.isMuted && channelStore.getMyRole() === 'MEMBER'" class="muted-hint">频道已被禁言</div>
    <template v-else>
      <div v-if="showMention && mentionMembers.length" class="mention-popup">
        <div v-for="m in mentionMembers" :key="m.id" class="mention-item" @click="insertMention(m.user.username)">
          {{ m.user.nickname || m.user.username }}
          <span style="color:var(--text-tertiary);font-size:12px">@{{ m.user.username }}</span>
        </div>
      </div>

      <div v-if="showEmoji" class="emoji-wrapper">
        <EmojiPicker @select="insertEmoji" />
      </div>

      <FileUploader ref="uploaderRef" :on-uploaded="handleFileUploaded" />
      <div class="input-row">
        <MacButton variant="plain" size="sm" @click="showEmoji = !showEmoji">😊</MacButton>
        <MacButton variant="plain" size="sm" @click="uploaderRef?.triggerFileSelect()">📎</MacButton>
        <textarea
          ref="textareaRef"
          v-model="inputText"
          class="input-area"
          placeholder=""
          :disabled="sending"
          @keydown="handleKeydown"
          @input="handleInput"
          rows="1"
        ></textarea>
        <button class="send-btn" :disabled="!inputText.trim() || sending" @click="handleSend">
          <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
            <path d="M10 3L4 11H8V17H12V11H16L10 3Z" fill="currentColor"/>
          </svg>
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.message-input {
  padding: 10px 14px 10px;
  position: relative;
  background: rgba(28, 28, 30, 0.65);
  backdrop-filter: blur(var(--blur-lg));
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.03);
}
.muted-hint { text-align: center; font-size: 13px; color: var(--red); padding: 8px; }
.input-row { display: flex; gap: 6px; align-items: center; }
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
.send-btn:active:not(:disabled) { transform: scale(0.9); }
.send-btn:hover:not(:disabled) { opacity: 0.9; }
.mention-popup {
  position: absolute; bottom: 100%; left: 16px; right: 16px;
  max-height: 160px; overflow-y: auto;
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-xl));
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  z-index: 100; margin-bottom: 4px;
}
.mention-item { padding: 8px 12px; cursor: pointer; display: flex; justify-content: space-between; color: var(--text-secondary); font-size: 13px; }
.mention-item:hover { background: var(--bg-hover); }
.emoji-wrapper { margin-bottom: 8px; }
</style>
