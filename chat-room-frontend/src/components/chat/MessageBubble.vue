<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { Message } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { useMessageStore } from '@/stores/message'
import { downloadProtectedFile, fetchProtectedFile } from '@/api/files'
import { MacAvatar } from '@/components/ui'

const props = defineProps<{ message: Message; keyword?: string }>()
const authStore = useAuthStore()
const messageStore = useMessageStore()

const isMine = computed(() => props.message.sender?.id === authStore.user?.id)
const isSystem = computed(() => props.message.type === 'SYSTEM')
const isRecalled = computed(() => props.message.isRecalled)
const isImage = computed(() => props.message.type === 'IMAGE')
const isFile = computed(() => props.message.type === 'FILE')

const previewVisible = ref(false)
const hoverBubble = ref(false)
const imageObjectUrl = ref('')
const fileLoadFailed = ref(false)
let hoverTimer: ReturnType<typeof setTimeout> | null = null
let imageLoadGeneration = 0

function onBubbleEnter() {
  if (hoverTimer) clearTimeout(hoverTimer)
  hoverBubble.value = true
}

function onBubbleLeave() {
  hoverTimer = setTimeout(() => { hoverBubble.value = false }, 400)
}

function formatTime(dateStr: string) {
  const d = new Date(dateStr)
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}

function revokeImageObjectUrl() {
  if (imageObjectUrl.value) {
    URL.revokeObjectURL(imageObjectUrl.value)
    imageObjectUrl.value = ''
  }
}

async function loadProtectedImage() {
  const generation = ++imageLoadGeneration
  revokeImageObjectUrl()
  fileLoadFailed.value = false

  if (!isImage.value || !props.message.filePath) return

  try {
    const blob = await fetchProtectedFile(props.message.filePath)
    if (generation !== imageLoadGeneration) return
    imageObjectUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (generation === imageLoadGeneration) {
      fileLoadFailed.value = true
      console.error('图片加载失败', error)
    }
  }
}

async function handleFileDownload() {
  if (!props.message.filePath) return
  try {
    await downloadProtectedFile(props.message.filePath, props.message.fileName || 'download')
  } catch (error) {
    console.error('文件下载失败', error)
  }
}

watch(
  [() => props.message.filePath, () => props.message.type],
  loadProtectedImage,
  { immediate: true }
)

onBeforeUnmount(() => {
  imageLoadGeneration++
  revokeImageObjectUrl()
  if (hoverTimer) clearTimeout(hoverTimer)
})

interface HighlightSegment {
  text: string
  className?: 'mention-highlight' | 'search-highlight'
}

function highlightSegments(content: string): HighlightSegment[] {
  const keyword = props.keyword?.trim()
  const escapedKeyword = keyword?.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const matcher = escapedKeyword
    ? new RegExp(`(@\\w+)|(${escapedKeyword})`, 'gi')
    : /(@\w+)/g
  const segments: HighlightSegment[] = []
  let cursor = 0

  for (const match of content.matchAll(matcher)) {
    const index = match.index ?? cursor
    if (index > cursor) {
      segments.push({ text: content.slice(cursor, index) })
    }
    segments.push({
      text: match[0],
      className: match[1] ? 'mention-highlight' : 'search-highlight'
    })
    cursor = index + match[0].length
  }

  if (cursor < content.length) {
    segments.push({ text: content.slice(cursor) })
  }
  return segments
}

async function handleRecall() {
  if (!props.message.id || !props.message.channelId) return
  try {
    await messageStore.recallViaHttp(props.message.channelId, props.message.id)
  } catch { /* handled by interceptor */ }
}
</script>

<template>
  <div class="msg-root">
    <!-- System / Recalled -->
    <div v-if="isSystem || isRecalled" class="system-msg">
      <span>{{ isRecalled ? '消息已撤回' : message.content }}</span>
    </div>

    <!-- Image message -->
    <div v-else-if="isImage" class="msg-row" :class="{ mine: isMine }">
      <template v-if="!isMine">
        <MacAvatar :name="message.sender?.nickname || message.sender?.username || '?'" :gradient="(message.sender?.id || 0) % 6 + 1" :size="30" />
      </template>
      <div v-if="isMine" class="mine-group" @mouseenter="onBubbleEnter" @mouseleave="onBubbleLeave">
        <button v-if="!isRecalled && message.id" class="recall-btn" :class="{ show: hoverBubble }" @click="handleRecall">撤回</button>
        <div class="msg-content-wrapper">
          <img v-if="imageObjectUrl" :src="imageObjectUrl" class="msg-image" :class="{ mine: isMine }" @click="previewVisible = true" />
          <span v-else class="file-placeholder">{{ fileLoadFailed ? '图片加载失败' : '图片加载中…' }}</span>
          <div class="msg-time" :class="{ mine: isMine }">{{ formatTime(message.createdAt) }}</div>
        </div>
      </div>
      <div v-else class="msg-content-wrapper">
        <img v-if="imageObjectUrl" :src="imageObjectUrl" class="msg-image" @click="previewVisible = true" />
        <span v-else class="file-placeholder">{{ fileLoadFailed ? '图片加载失败' : '图片加载中…' }}</span>
        <div class="msg-time">{{ formatTime(message.createdAt) }}</div>
      </div>
    </div>

    <!-- File message -->
    <div v-else-if="isFile" class="msg-row" :class="{ mine: isMine }">
      <template v-if="!isMine">
        <MacAvatar :name="message.sender?.nickname || message.sender?.username || '?'" :gradient="(message.sender?.id || 0) % 6 + 1" :size="30" />
      </template>
      <div v-if="isMine" class="mine-group" @mouseenter="onBubbleEnter" @mouseleave="onBubbleLeave">
        <button v-if="!isRecalled && message.id" class="recall-btn" :class="{ show: hoverBubble }" @click="handleRecall">撤回</button>
        <div class="msg-content-wrapper">
          <div class="msg-bubble file" :class="{ mine: isMine }">
            <button type="button" class="file-link" :class="{ mine: isMine }" @click="handleFileDownload">📎 {{ message.fileName || '下载文件' }}</button>
          </div>
          <div class="msg-time" :class="{ mine: isMine }">{{ formatTime(message.createdAt) }}</div>
        </div>
      </div>
      <div v-else class="msg-content-wrapper">
        <div v-if="!isMine" class="msg-sender">{{ message.sender?.nickname || message.sender?.username }}</div>
        <div class="msg-bubble file">
          <button type="button" class="file-link" @click="handleFileDownload">📎 {{ message.fileName || '下载文件' }}</button>
        </div>
        <div class="msg-time">{{ formatTime(message.createdAt) }}</div>
      </div>
    </div>

    <!-- Text message -->
    <div v-else class="msg-row" :class="{ mine: isMine }">
      <template v-if="!isMine">
        <MacAvatar :name="message.sender?.nickname || message.sender?.username || '?'" :gradient="(message.sender?.id || 0) % 6 + 1" :size="30" />
      </template>
      <div v-if="isMine" class="mine-group" @mouseenter="onBubbleEnter" @mouseleave="onBubbleLeave">
        <button v-if="!isRecalled && message.id" class="recall-btn" :class="{ show: hoverBubble }" @click="handleRecall">撤回</button>
        <div class="msg-content-wrapper">
          <div class="msg-bubble" :class="{ mine: isMine }">
            <span
              v-for="(segment, index) in highlightSegments(message.content)"
              :key="index"
              :class="segment.className"
            >{{ segment.text }}</span>
          </div>
          <div class="msg-time" :class="{ mine: isMine }">{{ formatTime(message.createdAt) }}</div>
        </div>
      </div>
      <div v-else class="msg-content-wrapper">
        <div v-if="!isMine" class="msg-sender">{{ message.sender?.nickname || message.sender?.username }}</div>
        <div class="msg-bubble">
          <span
            v-for="(segment, index) in highlightSegments(message.content)"
            :key="index"
            :class="segment.className"
          >{{ segment.text }}</span>
        </div>
        <div class="msg-time">{{ formatTime(message.createdAt) }}</div>
      </div>
    </div>

    <!-- Image preview -->
    <Teleport to="body">
      <div v-if="previewVisible" class="image-preview-overlay" @click="previewVisible = false">
        <img v-if="imageObjectUrl" :src="imageObjectUrl" class="image-preview" />
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.msg-root { }
.system-msg { text-align: center; padding: 8px 0; }
.system-msg span {
  font-size: 12px; color: var(--text-tertiary);
  background: var(--bg-hover); padding: 2px 12px; border-radius: 10px;
  animation: recall-in 0.3s ease;
}
.msg-row { display: flex; margin: 4px 0; gap: 8px; align-items: flex-end; }
.msg-row.mine {
  justify-content: flex-end;
}
.mine-group {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.mine-group .msg-content-wrapper {
  max-width: none;
  min-width: 0;
}
.mine-group .recall-btn {
  flex-shrink: 0;
}
.mine-group .msg-bubble {
  word-break: normal;
  overflow-wrap: normal;
  white-space: pre-wrap;
  overflow-x: auto;
}
.msg-row.mine .recall-btn {
  margin-right: 0;
}
.msg-content-wrapper { max-width: 60%; }
.msg-bubble {
  padding: 8px 14px;
  border-radius: 16px 16px 16px 4px;
  background: var(--bubble-other);
  color: var(--bubble-other-text);
  line-height: 1.45;
  font-size: 14px;
  word-break: break-word;
}
.msg-bubble.mine {
  background: var(--bubble-mine);
  color: var(--bubble-mine-text);
  border-radius: 16px 16px 4px 16px;
}
.msg-bubble.file { padding: 10px 14px; }
.msg-sender { font-size: 12px; color: var(--blue); margin-bottom: 2px; font-weight: 500; }
.msg-time { font-size: 10px; color: var(--text-quaternary); margin-top: 2px; }
.msg-time.mine { text-align: right; }
.msg-image {
  max-width: 260px; max-height: 200px;
  border-radius: 12px; cursor: pointer; object-fit: cover;
}
.msg-image.mine { border: 2px solid var(--bubble-mine); }
.file-link {
  display: flex; align-items: center; gap: 6px; padding: 0;
  border: 0; background: transparent; font: inherit; cursor: pointer;
  text-decoration: none; color: var(--bubble-other-text); font-size: 14px;
}
.file-link.mine { color: var(--bubble-mine-text); }
.file-placeholder { display: inline-block; padding: 16px; color: var(--text-tertiary); font-size: 12px; }
.image-preview-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.85);
  display: flex; align-items: center; justify-content: center; z-index: 9999; cursor: pointer;
}
.image-preview { max-width: 90vw; max-height: 90vh; border-radius: 8px; }

/* Recall button — hidden by default, shown on hover */
.recall-btn {
  visibility: hidden;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(255, 69, 58, 0.1);
  border: 1px solid rgba(255, 69, 58, 0.2);
  font-size: 11px;
  color: var(--red);
  cursor: pointer;
  font-family: inherit;
  font-weight: 500;
  white-space: nowrap;
  transition: background var(--transition-fast);
  flex-shrink: 0;
  align-self: center;
}
.recall-btn:hover { background: rgba(255, 69, 58, 0.2); }
.recall-btn.show { visibility: visible; }

:deep(.mention-highlight) { color: #1d1d1f; font-weight: 600; background: #ffd60a; border-radius: 3px; padding: 0 3px; }
:deep(.search-highlight) { background: #ffd54f; border-radius: 2px; padding: 0 2px; }

/* Recall animation — plays when system-msg appears on recall */
@keyframes recall-in {
  from { opacity: 0; transform: translateY(-4px) scale(0.95); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
