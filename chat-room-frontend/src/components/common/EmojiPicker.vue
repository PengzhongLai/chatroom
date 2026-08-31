<script setup lang="ts">
const emit = defineEmits<{ select: [emoji: string] }>()

const emojis = [
  '😀','😂','🤣','😊','😍','🤩','😎','🥳',
  '😢','😡','👍','👎','👏','🙏','💪','🔥',
  '❤️','💔','🎉','⭐','✅','❌','⚠️','💡',
  '🐱','🐶','🌞','🌈','🍕','☕','🎵','💻'
]

function select(e: string) {
  emit('select', e)
}
</script>

<template>
  <div class="emoji-picker">
    <span v-for="e in emojis" :key="e" class="emoji-item" @click="select(e)">{{ e }}</span>
  </div>
</template>

<style scoped>
.emoji-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 280px;
  padding: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow);
}
.emoji-item {
  font-size: 22px;
  cursor: pointer;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}
.emoji-item:hover { background: var(--bg-hover); }
</style>
