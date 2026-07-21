<script setup lang="ts">
import { Download, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'

import { commitBomImport, downloadBomImportErrors, downloadBomImportTemplate, previewBomImport } from '@/api/modules/bom'
import type { BomImportPreview } from '@/types/bom'

const props = defineProps<{ modelValue: boolean; productId: number; bomId: number | null }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'committed'): void }>()
const loading = ref(false)
const preview = ref<BomImportPreview | null>(null)
const previewStats = computed(() => {
  const rows = preview.value?.rows || []
  return {
    autoMatched: rows.filter(row => row.materialSource !== 'manual' && row.unmatchedFlag !== 1).length,
    manualRows: rows.filter(row => row.materialSource === 'manual' || row.unmatchedFlag === 1).length,
    supplierMissing: rows.filter(row => !row.supplierName).length,
    costMissing: rows.filter(row => row.unitCost == null || row.lineCost == null).length
  }
})

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
    <div v-if="preview" class="preview-quality">
      <el-tag type="success">自动匹配 {{ previewStats.autoMatched }}</el-tag>
      <el-tag type="warning">人工录入 {{ previewStats.manualRows }}</el-tag>
      <el-tag :type="previewStats.supplierMissing ? 'danger' : 'info'">供应商缺失 {{ previewStats.supplierMissing }}</el-tag>
      <el-tag :type="previewStats.costMissing ? 'danger' : 'info'">成本缺失 {{ previewStats.costMissing }}</el-tag>
    </div>
    <el-table v-if="preview?.rows.length" :data="preview.rows" size="small" max-height="260" class="preview-table">
      <el-table-column prop="lineNo" label="NO" width="64" />
      <el-table-column prop="routeName" label="路线" min-width="120" />
      <el-table-column prop="itemCode" label="物料编码" min-width="120" />
      <el-table-column prop="itemName" label="物料名称" min-width="140" />
      <el-table-column prop="supplierName" label="供应商" min-width="130">
        <template #default="{ row }">{{ row.supplierName || '未选择' }}</template>
      </el-table-column>
      <el-table-column prop="unitCost" label="单价" width="100" />
      <el-table-column prop="lineCost" label="单个成本" width="110" />
      <el-table-column label="来源" width="90">
        <template #default="{ row }">
          <el-tag :type="row.materialSource === 'manual' || row.unmatchedFlag === 1 ? 'warning' : 'success'" size="small">
            {{ row.materialSource === 'manual' || row.unmatchedFlag === 1 ? '人工' : '库存' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="preview?.status !== 'ready'" @click="commit">确认入库</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-actions, .preview-quality { display: flex; flex-wrap: wrap; gap: 8px; }
.preview-quality { margin: 10px 0; }
.preview-table { margin-top: 8px; }
</style>
