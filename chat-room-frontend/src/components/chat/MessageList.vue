<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted } from 'vue'
import { useMessageStore } from '@/stores/message'
import { useChannelStore } from '@/stores/channel'
import MessageBubble from './MessageBubble.vue'
import { MacInput } from '@/components/ui'

const messageStore = useMessageStore()
const channelStore = useChannelStore()
const scrollContainer = ref<HTMLElement | null>(null)
const isLoadingMore = ref(false)
const searchText = ref('')

const filteredMessages = computed(() => {
  if (!searchText.value.trim()) return messageStore.messages
  const kw = searchText.value.toLowerCase()
  return messageStore.messages.filter((m: any) =>
    (m.content && m.content.toLowerCase().includes(kw)) ||
    (m.fileName && m.fileName.toLowerCase().includes(kw))
  )
})

watch(
  () => messageStore.messages.length,
  () => {
    if (messageStore.messages.length > 0 && !isLoadingMore.value) {
      nextTick(() => requestAnimationFrame(() => scrollToBottom()))
    }
  }
)

onMounted(() => {
  nextTick(() => scrollToBottom())
})

function scrollToBottom() {
  const el = scrollContainer.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function onChannelEnter() {
  requestAnimationFrame(() => scrollToBottom())
}

defineExpose({ scrollToBottom })

async function handleScroll() {
  const el = scrollContainer.value
  if (!el) return
  if (el.scrollTop <= 50 && messageStore.hasMore && !isLoadingMore.value) {
    isLoadingMore.value = true
    const prevHeight = el.scrollHeight
    await messageStore.loadMore()
    await nextTick()
    if (el) {
      el.scrollTop = el.scrollHeight - prevHeight
    }
    isLoadingMore.value = false
  }
}

// Search not needed — always visible
</script>

<template>
  <div class="msg-glass-card">
    <div class="local-search">
      <MacInput v-model="searchText" placeholder="在频道内搜索..." />
    </div>
    <Transition name="channel" mode="out-in" @after-enter="onChannelEnter">
      <div class="message-list" :key="channelStore?.currentChannel?.id || 'empty'" ref="scrollContainer" @scroll="handleScroll">
        <div v-if="messageStore.loading && messageStore.messages.length === 0" class="hint">加载消息中...</div>
        <div v-else-if="messageStore.hasMore" class="hint" v-show="!isLoadingMore">向上滚动加载更多</div>
        <div v-else class="hint">已加载全部消息</div>

        <TransitionGroup name="msg" tag="div" class="msg-group">
          <MessageBubble
            v-for="msg in filteredMessages"
            :key="msg.id || msg.content + (msg.createdAt || '')"
            :message="msg"
            :keyword="searchText"
          />
        </TransitionGroup>

        <div ref="bottomAnchor"></div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.msg-glass-card {
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
/* Search */
.local-search {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

/* Messages */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
}
.hint {
  text-align: center;
  padding: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
}

/* --- Message bubble spring in --- */
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

/* --- Channel switch --- */
.channel-enter-active, .channel-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.channel-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.channel-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
