<script setup lang="ts">
defineProps<{
  modelValue: boolean
  fileName: string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    :title="`文件预览 - ${fileName}`"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="preview-box">
      <el-empty description="演示环境仅预留附件预览入口，可接 MinIO / S3 真实文件流。" />
    </div>
  </el-dialog>
</template>

<style scoped>
.preview-box {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
