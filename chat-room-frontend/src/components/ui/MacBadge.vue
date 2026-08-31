<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'info' | 'success' | 'danger' | 'warning'
  size?: 'sm' | 'md'
}>(), {
  variant: 'default',
  size: 'sm'
})
</script>

<template>
  <span class="mac-badge" :class="[variant, size]">
    <slot />
  </span>
</template>

<style scoped>
.mac-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-size: 10px;
  font-weight: 600;
  line-height: 1.4;
}
.mac-badge.md { padding: 2px 8px; font-size: 11px; }
.mac-badge.default { background: var(--bg-hover); color: var(--text-tertiary); }
.mac-badge.info { background: var(--blue-dim); color: var(--blue); }
.mac-badge.success { background: var(--green-dim); color: var(--green); }
.mac-badge.danger { background: var(--red-dim); color: var(--red); }
.mac-badge.warning { background: var(--orange-dim); color: var(--orange); }
</style>
