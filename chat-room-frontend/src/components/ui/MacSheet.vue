<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  visible: boolean
  title?: string
  width?: string
}>(), {
  title: '',
  width: '420px'
})

const emit = defineEmits<{ close: [] }>()

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet">
      <div v-if="visible" class="sheet-overlay" @click.self="emit('close')">
        <div class="sheet-panel" :style="{ maxWidth: width }">
          <div v-if="title" class="sheet-header">
            <span class="sheet-title">{{ title }}</span>
          </div>
          <div class="sheet-body">
            <slot />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sheet-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.sheet-panel {
  width: 90%;
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-xl));
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-subtle);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
}
.sheet-header {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
}
.sheet-title {
  color: var(--text-secondary);
  font-weight: 600;
  font-size: 14px;
}
.sheet-body { padding: 16px; max-height: 70vh; overflow-y: auto; }

.sheet-enter-active {
  transition: opacity 0.2s ease;
}
.sheet-enter-active .sheet-panel {
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.sheet-leave-active {
  transition: opacity 0.15s ease;
}
.sheet-leave-active .sheet-panel {
  transition: transform 0.15s ease;
}
.sheet-enter-from, .sheet-leave-to { opacity: 0; }
.sheet-enter-from .sheet-panel { transform: scale(0.95); }
.sheet-leave-to .sheet-panel { transform: scale(0.95); }
</style>
