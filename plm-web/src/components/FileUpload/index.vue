<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

import { uploadAttachment } from '@/api/modules/attachment'

const emit = defineEmits<{
  (event: 'uploaded', payload: { attachmentId: number; fileName: string }): void
}>()

const uploading = ref(false)

async function beforeUpload(file: File) {
  uploading.value = true
  try {
    const result = await uploadAttachment(file.name)
    emit('uploaded', result)
    ElMessage.success(`已上传 ${file.name}`)
  } finally {
    uploading.value = false
  }
  return false
}
</script>

<template>
  <el-upload drag :show-file-list="false" :before-upload="beforeUpload">
    <div class="upload-box">
      <el-icon class="upload-box__icon"><UploadFilled /></el-icon>
      <div class="upload-box__title">{{ uploading ? '上传中...' : '拖拽文件到此处，或点击上传' }}</div>
      <div class="upload-box__tip">用于图纸、工艺文件、客户确认资料等附件演示。</div>
    </div>
  </el-upload>
</template>

<style scoped>
.upload-box {
  padding: 16px;
  text-align: center;
  color: var(--plm-color-text-secondary);
}

.upload-box__icon {
  font-size: 24px;
  color: var(--plm-color-primary);
}

.upload-box__title {
  margin-top: 10px;
  color: var(--plm-color-text-primary);
}

.upload-box__tip {
  margin-top: 6px;
  font-size: 12px;
}
</style>
