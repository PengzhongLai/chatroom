<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'primary' | 'danger' | 'plain'
  size?: 'sm' | 'md'
  disabled?: boolean
  loading?: boolean
}>(), {
  variant: 'default',
  size: 'sm',
  disabled: false,
  loading: false
})

const emit = defineEmits<{ click: [e: MouseEvent] }>()
</script>

<template>
  <button
    class="mac-btn"
    :class="[variant, size]"
    :disabled="disabled || loading"
    @click="emit('click', $event)"
  >
    <span v-if="loading" class="spinner"></span>
    <slot />
  </button>
</template>

<style scoped>
.mac-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  cursor: pointer;
  font-family: inherit;
  font-weight: 500;
  transition: all var(--transition-fast);
  white-space: nowrap;
  border-radius: var(--radius-md);
  font-size: 12px;
  padding: 6px 12px;
}
.mac-btn.sm { padding: 5px 10px; font-size: 11px; border-radius: var(--radius-sm); }
.mac-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.mac-btn:active:not(:disabled) { transform: scale(0.96); }

.mac-btn.default {
  background: var(--bg-hover);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
}
.mac-btn.default:hover:not(:disabled) { background: rgba(118, 118, 128, 0.18); }

.mac-btn.primary {
  background: var(--blue);
  color: #fff;
  box-shadow: 0 2px 8px rgba(10, 132, 255, 0.3);
}
.mac-btn.primary:hover:not(:disabled) { opacity: 0.9; }

.mac-btn.danger {
  background: var(--red-dim);
  color: var(--red);
  border: 1px solid rgba(255, 69, 58, 0.2);
}
.mac-btn.danger:hover:not(:disabled) { background: rgba(255, 69, 58, 0.25); }

.mac-btn.plain {
  background: transparent;
  color: var(--text-tertiary);
}
.mac-btn.plain:hover:not(:disabled) { color: var(--text-secondary); }

.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
