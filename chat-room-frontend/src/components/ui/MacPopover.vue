<script setup lang="ts">
withDefaults(defineProps<{
  visible: boolean
}>(), {
  visible: false
})

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <Transition name="popover">
      <div v-if="visible" class="popover-overlay" @click="emit('close')">
        <div class="popover-content" @click.stop>
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.popover-overlay {
  position: fixed; inset: 0; z-index: 2001;
}
.popover-content {
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-xl));
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
.popover-enter-active, .popover-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.popover-enter-from, .popover-leave-to { opacity: 0; transform: scale(0.95); }
</style>
