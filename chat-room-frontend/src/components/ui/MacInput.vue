<script setup lang="ts">
withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  type?: string
  disabled?: boolean
  readonly?: boolean
  rows?: number
}>(), {
  placeholder: '',
  type: 'text',
  disabled: false,
  readonly: false
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

function onInput(e: Event) {
  const target = e.target as HTMLInputElement | HTMLTextAreaElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div class="mac-input-wrapper">
    <input
      v-if="type !== 'textarea'"
      class="mac-input"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      @input="onInput"
    />
    <textarea
      v-else
      class="mac-input mac-textarea"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :rows="rows || 2"
      @input="onInput"
    ></textarea>
  </div>
</template>

<style scoped>
.mac-input-wrapper { width: 100%; }
.mac-input {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  font-size: 13px;
  color: var(--text-secondary);
  font-family: inherit;
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.mac-input::placeholder { color: var(--text-quaternary); }
.mac-input:focus {
  border-color: var(--blue);
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.15);
}
.mac-input:disabled { opacity: 0.5; cursor: not-allowed; }
.mac-textarea { resize: vertical; min-height: 60px; line-height: 1.5; }
</style>
