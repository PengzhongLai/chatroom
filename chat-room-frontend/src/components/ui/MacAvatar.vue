<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  size?: number
  gradient?: number
  online?: boolean
  status?: 'ONLINE' | 'INVISIBLE' | 'OFFLINE'
  showStatus?: boolean
}>(), {
  size: 32,
  gradient: 1,
  showStatus: false
})

const gradients = [
  'var(--avatar-gradient-1)',
  'var(--avatar-gradient-2)',
  'var(--avatar-gradient-3)',
  'var(--avatar-gradient-4)',
  'var(--avatar-gradient-5)',
  'var(--avatar-gradient-6)',
]

const bgGradient = computed(() => gradients[(props.gradient - 1) % gradients.length])
const initial = computed(() => props.name.charAt(0).toUpperCase())
const fontSize = computed(() => Math.max(10, props.size * 0.38))
const dotClass = computed(() => {
  if (props.status === 'INVISIBLE') return 'invisible'
  if (props.status === 'OFFLINE') return 'offline'
  if (props.status === 'ONLINE') return 'online'
  return props.online ? 'online' : 'offline'
})
</script>

<template>
  <div class="mac-avatar" :style="{ width: size + 'px', height: size + 'px', background: bgGradient, fontSize: fontSize + 'px' }">
    {{ initial }}
    <span v-if="showStatus" class="status-dot" :class="dotClass"></span>
  </div>
</template>

<style scoped>
.mac-avatar {
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
  position: relative;
}
.status-dot {
  position: absolute;
  bottom: -1px;
  right: -1px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #000;
  background: var(--text-quaternary);
}
.status-dot.online { background: var(--green); }
.status-dot.offline { background: var(--text-quaternary); }
.status-dot.invisible { background: var(--orange); }
</style>
