<script setup lang="ts">
import { ref, computed } from 'vue'
import { uploadFile } from '@/api/files'

const emit = defineEmits<{
  uploaded: [result: { fileName: string; filePath: string; fileType: 'IMAGE' | 'FILE' }]
}>()

const props = defineProps<{
  onUploaded?: (result: { fileName: string; filePath: string; fileType: 'IMAGE' | 'FILE' }) => void
}>()

const dragging = ref(false)
const uploading = ref(false)
const progress = ref(0)
const currentFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const bottleFillHeight = computed(() => `${progress.value}%`)

function triggerFileSelect() {
  fileInput.value?.click()
}
defineExpose({ triggerFileSelect, handleDrop })

function handleDrop(e: DragEvent) {
  dragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) startUpload(file)
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) startUpload(file)
  input.value = ''
}

async function startUpload(file: File) {
  currentFile.value = file
  uploading.value = true
  progress.value = 0
  try {
    const result = await uploadFile(file, (pct) => {
      progress.value = pct
    })
    // Prefer callback prop over emit for reliability
    if (props.onUploaded) {
      props.onUploaded(result)
    } else {
      emit('uploaded', result)
    }
  } catch {
    // handled by interceptor
  } finally {
    uploading.value = false
    currentFile.value = null
    progress.value = 0
  }
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}
</script>

<template>
  <div
    class="file-uploader"
    :class="{ dragging }"
    @dragover.prevent="dragging = true"
    @dragleave="dragging = false"
    @drop.prevent="handleDrop"
  >
    <div v-if="uploading && currentFile" class="upload-card">
      <div class="file-info">
        <span class="file-name">{{ currentFile.name }}</span>
        <span class="file-size">{{ formatSize(currentFile.size) }}</span>
      </div>
      <div class="bottle">
        <div class="bottle-fill" :style="{ height: bottleFillHeight }"></div>
        <span class="bottle-text">{{ progress }}%</span>
      </div>
    </div>

    <input
      ref="fileInput"
      type="file"
      class="hidden-input"
      accept="image/*,.pdf,.doc,.docx,.txt,.zip,.rar"
      @change="handleFileSelect"
    />

    <div v-if="dragging" class="drag-hint">释放文件以上传</div>
  </div>
</template>

<style scoped>
.file-uploader { position: relative; }
.hidden-input { display: none; }
.dragging {
  outline: 2px dashed var(--primary);
  outline-offset: -4px;
  border-radius: 6px;
}
.drag-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-dim);
  color: var(--primary);
  font-size: 14px;
  border-radius: 6px;
  z-index: 10;
}
.upload-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-sidebar);
  border-radius: 8px;
  margin-bottom: 8px;
}
.file-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.file-name {
  font-size: 13px;
  font-weight: 500;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size { font-size: 11px; color: var(--text-dim); }
.bottle {
  width: 36px;
  height: 48px;
  border: 2px solid var(--primary);
  border-radius: 4px 4px 12px 12px;
  position: relative;
  overflow: hidden;
  background: var(--bg-card);
}
.bottle-fill {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, var(--primary), var(--primary));
  transition: height 0.3s ease;
}
.bottle-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #303133;
  z-index: 1;
}
</style>
