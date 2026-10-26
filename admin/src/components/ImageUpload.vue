<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { uploadFile } from '@/api'

/** 单图上传，v-model 为图片 URL */
const model = defineModel({ type: String, default: '' })
defineProps({ width: { type: Number, default: 120 }, height: { type: Number, default: 120 } })

const uploading = ref(false)
const ACCEPT = ['image/jpeg', 'image/png', 'image/webp']

function beforeUpload(file) {
  if (!ACCEPT.includes(file.type)) {
    ElMessage.error('只支持 jpg / png / webp 图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return false
  }
  return true
}

async function doUpload({ file }) {
  uploading.value = true
  try {
    const data = await uploadFile(file)
    model.value = data?.url || ''
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="image-upload" :style="{ width: `${width}px`, height: `${height}px` }">
    <el-upload
      v-loading="uploading"
      class="uploader"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="doUpload"
      accept="image/jpeg,image/png,image/webp"
    >
      <img v-if="model" :src="model" class="preview" alt="" />
      <div v-else class="placeholder">
        <el-icon :size="22"><Plus /></el-icon>
        <span>上传图片</span>
      </div>
    </el-upload>
    <el-button v-if="model" class="remove" circle size="small" type="danger" :icon="Delete" @click="model = ''" />
  </div>
</template>

<style scoped>
.image-upload {
  position: relative;
}
.uploader,
.uploader :deep(.el-upload) {
  width: 100%;
  height: 100%;
}
.uploader :deep(.el-upload) {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s;
}
.uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
}
.preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.remove {
  position: absolute;
  top: -8px;
  right: -8px;
}
</style>
