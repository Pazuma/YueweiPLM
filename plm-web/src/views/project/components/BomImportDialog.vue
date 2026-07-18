<script setup lang="ts">
import { Download, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'

import { commitBomImport, downloadBomImportErrors, downloadBomImportTemplate, previewBomImport } from '@/api/modules/bom'
import type { BomImportPreview } from '@/types/bom'

const props = defineProps<{ modelValue: boolean; productId: number; bomId: number | null }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'committed'): void }>()
const loading = ref(false)
const preview = ref<BomImportPreview | null>(null)

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

async function selectFile(file: { raw?: File }) {
  if (!props.bomId || !file.raw) return false
  loading.value = true
  try { preview.value = await previewBomImport(props.productId, props.bomId, file.raw) } finally { loading.value = false }
  return false
}

async function commit() {
  if (!preview.value || preview.value.status !== 'ready') return
  await commitBomImport(preview.value.importToken)
  ElMessage.success('BOM 导入完成')
  emit('committed')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="导入 XLSX" width="720px" @close="emit('update:modelValue', false)">
    <div class="import-actions">
      <el-button :icon="Download" @click="downloadBomImportTemplate().then(blob => saveBlob(blob, 'BOM-import-template.xlsx'))">下载模板</el-button>
      <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="selectFile">
        <el-button type="primary" :icon="Upload" :loading="loading">选择 XLSX</el-button>
      </el-upload>
    </div>
    <el-result v-if="preview" :icon="preview.status === 'ready' ? 'success' : 'error'" :title="preview.status === 'ready' ? '预校验通过' : '预校验未通过'" :sub-title="`有效 ${preview.validRows} 行，错误 ${preview.errorRows} 行`">
      <template #extra>
        <el-button v-if="preview.errors.length" @click="downloadBomImportErrors(preview.importToken).then(blob => saveBlob(blob, 'BOM-import-errors.xlsx'))">下载错误报告</el-button>
      </template>
    </el-result>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="preview?.status !== 'ready'" @click="commit">确认入库</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>.import-actions { display: flex; gap: 8px; }</style>
