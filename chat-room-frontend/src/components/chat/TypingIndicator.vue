<script setup lang="ts">
import { useMessageStore } from '@/stores/message'
const messageStore = useMessageStore()
</script>

<template>
  <div v-if="messageStore.typingText()" class="typing-indicator">
    <div class="typing-dots">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </div>
    <span class="typing-text">{{ messageStore.typingText() }}</span>
  </div>
</template>

<style scoped>
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 20px 2px;
}
.typing-dots { display: flex; gap: 3px; }
.typing-dots .dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
  animation: typingBounce 1.4s ease-in-out infinite;
}
.typing-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dots .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes typingBounce {
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-4px); }
}
.typing-text { font-size: 12px; color: var(--text-tertiary); }
</style>
