<script setup lang="ts">
import { Delete, Download, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadUserFile } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import {
  deleteAttachment,
  downloadAttachment,
  getTimelineAttachments,
  uploadTimelineAttachment,
  type AttachmentVO
} from '@/api/modules/attachment'
import { formatFileSize, saveBlob } from '@/utils/file'

const props = defineProps<{ projectId: number; nodeKey: string | null }>()
const emit = defineEmits<{ (event: 'changed'): void }>()

const loading = ref(false)
const uploading = ref(false)
const loadError = ref('')
const attachments = ref<AttachmentVO[]>([])
const selectedFile = ref<File | null>(null)
const fileList = ref<UploadUserFile[]>([])
const uploadForm = reactive({
  fileCategory: 'testing',
  versionNo: 'V1',
  remark: ''
})

const categoryOptions = [
  { label: '图纸', value: 'drawing' },
  { label: 'SOP', value: 'sop' },
  { label: 'SIP', value: 'sip' },
  { label: '测试资料', value: 'testing' },
  { label: '客户确认件', value: 'customer_confirm' },
  { label: '其他', value: 'other' }
]

const canUpload = computed(() => Boolean(props.nodeKey && selectedFile.value && !uploading.value))

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

function categoryLabel(value: string) {
  return categoryOptions.find((item) => item.value === value)?.label || value
}

async function loadAttachments() {
  loadError.value = ''
  if (!props.nodeKey) {
    attachments.value = []
    return
  }
  loading.value = true
  try {
    attachments.value = await getTimelineAttachments(props.projectId, props.nodeKey)
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function handleFileChange(uploadFile: UploadFile) {
  const file = uploadFile.raw || null
  if (!file) return
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('单个文件不能超过 50 MB')
    selectedFile.value = null
    fileList.value = []
    return
  }
  selectedFile.value = file
  fileList.value = [uploadFile]
}

function handleFileRemove() {
  selectedFile.value = null
  fileList.value = []
}

async function uploadFile() {
  if (!props.nodeKey || !selectedFile.value) {
    ElMessage.warning(props.nodeKey ? '请先选择文件' : '当前项目没有可用的时间轴节点')
    return
  }
  uploading.value = true
  try {
    await uploadTimelineAttachment(props.projectId, props.nodeKey, selectedFile.value, {
      fileCategory: uploadForm.fileCategory,
      versionNo: uploadForm.versionNo.trim() || undefined,
      remark: uploadForm.remark.trim() || undefined
    })
    selectedFile.value = null
    fileList.value = []
    uploadForm.remark = ''
    await loadAttachments()
    emit('changed')
    ElMessage.success('附件已上传到当前时间轴节点')
  } finally {
    uploading.value = false
  }
}

async function downloadFile(attachment: AttachmentVO) {
  const blob = await downloadAttachment(attachment.attachmentId)
  saveBlob(blob, attachment.originalFileName || attachment.fileName)
  ElMessage.success('文件已开始下载')
}

async function removeFile(attachment: AttachmentVO) {
  await ElMessageBox.confirm(`确认删除“${attachment.originalFileName}”吗？`, '删除附件', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await deleteAttachment(attachment.attachmentId)
  await loadAttachments()
  emit('changed')
  ElMessage.success('附件已删除')
}

watch([() => props.projectId, () => props.nodeKey], loadAttachments, { immediate: true })
</script>

<template>
  <div class="attachment-panel" v-loading="loading">
    <div class="attachment-panel__toolbar">
      <div>
        <h4 class="section-title">当前节点附件</h4>
        <p class="page-panel-desc">附件归属当前 Product 和时间轴节点，上传后可在文件中心统一查询。</p>
      </div>
      <el-button :icon="Refresh" circle title="刷新附件" :disabled="!nodeKey" @click="loadAttachments" />
    </div>

    <el-alert
      v-if="!nodeKey"
      title="当前项目没有可用的时间轴节点"
      description="请先完成项目时间轴初始化，再上传节点成果文件。"
      type="warning"
      show-icon
      :closable="false"
    />
    <el-alert v-else-if="loadError" :title="loadError" type="error" show-icon :closable="false" />

    <div class="attachment-upload-bar">
      <el-select v-model="uploadForm.fileCategory" aria-label="文件分类" placeholder="文件分类">
        <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="uploadForm.versionNo" aria-label="文件版本" placeholder="版本，例如 V1" maxlength="50" />
      <el-input v-model="uploadForm.remark" aria-label="附件备注" placeholder="备注" maxlength="500" />
      <el-upload
        v-model:file-list="fileList"
        action="#"
        :auto-upload="false"
        :limit="1"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.jpg,.jpeg,.png,.zip"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <el-button :icon="Upload">选择文件</el-button>
      </el-upload>
      <el-button
        data-test="attachment-upload"
        type="primary"
        :icon="Upload"
        :disabled="!canUpload"
        :loading="uploading"
        @click="uploadFile"
      >
        上传
      </el-button>
    </div>

    <template v-if="nodeKey && !loadError">
      <el-table :data="attachments" border stripe size="small" class="attachment-panel__table">
        <el-table-column prop="originalFileName" label="文件名称" min-width="220" />
        <el-table-column label="分类" width="110"><template #default="{ row }">{{ categoryLabel(row.fileCategory) }}</template></el-table-column>
        <el-table-column prop="versionNo" label="版本" width="90" />
        <el-table-column label="大小" width="100"><template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template></el-table-column>
        <el-table-column prop="createdBy" label="上传人" width="120" />
        <el-table-column prop="createdAt" label="上传时间" min-width="170" />
        <el-table-column prop="remark" label="备注" min-width="150"><template #default="{ row }">{{ row.remark || '--' }}</template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Download" title="下载" @click="downloadFile(row)" />
            <el-button link type="danger" :icon="Delete" title="删除" @click="removeFile(row)" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!attachments.length" description="当前节点还没有附件" />
    </template>
  </div>
</template>

<style scoped>
.attachment-panel { min-width: 0; }
.attachment-panel__toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.attachment-panel__toolbar h4,
.attachment-panel__toolbar p { margin-top: 0; }
.attachment-upload-bar { display: grid; grid-template-columns: 150px 130px minmax(180px, 1fr) auto auto; gap: 10px; align-items: start; margin: 14px 0; }
.attachment-panel__table { width: 100%; }
@media (max-width: 900px) {
  .attachment-upload-bar { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 620px) {
  .attachment-panel__toolbar { align-items: flex-start; }
  .attachment-upload-bar { grid-template-columns: 1fr; }
}
</style>
