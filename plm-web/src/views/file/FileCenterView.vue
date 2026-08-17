<script setup lang="ts">
import { Delete, Download, FolderOpened, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  deleteAttachment,
  getFileCenterAttachments,
  type AttachmentVO,
  type FileCenterQuery
} from '@/api/modules/attachment'
import FixedTableViewport from '@/components/FixedTableViewport/index.vue'
import PageContainer from '@/components/PageContainer/index.vue'
import { useAttachmentViewer } from '@/composables/useAttachmentViewer'
import type { PageResponse } from '@/api/request'
import { formatDate } from '@/utils/format'
import { FILE_CATEGORY_OPTIONS, fileCategoryLabel, formatFileSize } from '@/utils/file'
import { toInProgressProjectRoute } from '@/utils/projectRoute'

const router = useRouter()
const { viewAttachment, downloadFile } = useAttachmentViewer()
const loading = ref(false)
const loadError = ref('')
const pageData = ref<PageResponse<AttachmentVO>>({
  content: [],
  page: 1,
  size: 20,
  totalElements: 0,
  totalPages: 0
})

const filters = reactive({
  keyword: '',
  projectId: undefined as number | undefined,
  nodeKey: '',
  fileCategory: '',
  page: 1,
  size: 20
})

const categoryOptions = FILE_CATEGORY_OPTIONS

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '文件中心查询失败，请稍后重试'
}

function buildQuery(): FileCenterQuery {
  return {
    ...(filters.keyword.trim() ? { keyword: filters.keyword.trim() } : {}),
    ...(filters.projectId ? { projectId: filters.projectId } : {}),
    ...(filters.nodeKey.trim() ? { nodeKey: filters.nodeKey.trim() } : {}),
    ...(filters.fileCategory ? { fileCategory: filters.fileCategory } : {}),
    page: filters.page,
    size: filters.size
  }
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    pageData.value = await getFileCenterAttachments(buildQuery())
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function searchFiles() {
  filters.page = 1
  loadData()
}

function resetFilters() {
  filters.keyword = ''
  filters.projectId = undefined
  filters.nodeKey = ''
  filters.fileCategory = ''
  filters.page = 1
  filters.size = 20
  loadData()
}

function changePage(page: number) {
  filters.page = page
  loadData()
}

function changePageSize(size: number) {
  filters.size = size
  filters.page = 1
  loadData()
}

function openProject(projectId: number | null | undefined) {
  if (!projectId) return
  router.push(toInProgressProjectRoute({ productId: String(projectId) }))
}

async function removeFile(attachment: AttachmentVO) {
  await ElMessageBox.confirm(`确认删除“${attachment.originalFileName}”吗？`, '删除附件', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await deleteAttachment(attachment.attachmentId)
  if (pageData.value.content.length === 1 && filters.page > 1) filters.page -= 1
  await loadData()
  ElMessage.success('附件已删除')
}

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="文件中心"
    description="按项目、时间轴节点和资料分类查询已沉淀文件，并执行下载与受控删除。"
  >
    <section class="page-panel file-center-toolbar">
      <div class="file-center-toolbar__filters">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索文件名或备注"
          @keyup.enter="searchFiles"
        />
        <el-input-number
          v-model="filters.projectId"
          :min="1"
          :controls="false"
          placeholder="项目 ID"
          aria-label="项目 ID"
        />
        <el-input v-model="filters.nodeKey" clearable placeholder="时间轴节点编码" />
        <el-select v-model="filters.fileCategory" clearable placeholder="文件分类">
          <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
      <div class="file-center-toolbar__actions">
        <el-button type="primary" :icon="Search" @click="searchFiles">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </section>

    <el-alert
      title="上传入口在项目详情的当前时间轴节点"
      description="文件中心只负责归档查询、下载和删除，避免文件脱离项目节点单独存在。"
      type="info"
      show-icon
      :closable="false"
      class="file-center-note"
    />

    <section class="page-panel" v-loading="loading">
      <div class="file-center-summary">
        <div>
          <h3 class="section-title">归档文件</h3>
          <p class="page-panel-desc">查询成功后显示后端分页结果；接口失败不会伪装成空列表。</p>
        </div>
        <strong>{{ pageData.totalElements }} 个文件</strong>
      </div>

      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />

      <template v-else>
        <FixedTableViewport v-slot="{ tableHeight }" :refresh-key="pageData.content">
        <el-table :data="pageData.content" :height="tableHeight" border stripe class="file-center-table">
          <el-table-column prop="originalFileName" label="文件名称" min-width="230" />
          <el-table-column label="项目编码" min-width="170">
            <template #default="{ row }">{{ row.projectCode || '--' }}</template>
          </el-table-column>
          <el-table-column label="项目名称" min-width="200">
            <template #default="{ row }">{{ row.projectName || '--' }}</template>
          </el-table-column>
          <el-table-column prop="timelineNodeKey" label="时间轴节点" min-width="150"><template #default="{ row }">{{ row.timelineNodeKey || '--' }}</template></el-table-column>
          <el-table-column label="分类" width="110"><template #default="{ row }">{{ fileCategoryLabel(row.fileCategory) }}</template></el-table-column>
          <el-table-column prop="versionNo" label="版本" width="90" />
          <el-table-column label="大小" width="100"><template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template></el-table-column>
          <el-table-column prop="createdBy" label="上传人" width="120" />
          <el-table-column label="上传时间" width="150"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150"><template #default="{ row }">{{ row.remark || '--' }}</template></el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="FolderOpened" title="进入项目" @click="openProject(row.projectId || row.ownerObjectId)" />
              <el-button link type="primary" :icon="View" title="查看" @click="viewAttachment(row)" />
              <el-button link type="primary" :icon="Download" title="下载" @click="downloadFile(row)" />
              <el-button link type="danger" :icon="Delete" title="删除" @click="removeFile(row)" />
            </template>
          </el-table-column>
        </el-table>
        </FixedTableViewport>

        <el-empty v-if="!pageData.content.length" description="当前筛选条件下没有文件" />

        <el-pagination
          v-if="pageData.totalElements > 0"
          class="file-center-pagination"
          background
          layout="total, sizes, prev, pager, next"
          :current-page="filters.page"
          :page-size="filters.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pageData.totalElements"
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </template>
    </section>
  </PageContainer>
</template>

<style scoped>
.file-center-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.file-center-toolbar__filters { display: grid; grid-template-columns: minmax(220px, 1.5fr) 130px minmax(180px, 1fr) 150px; gap: 10px; flex: 1; min-width: 0; }
.file-center-toolbar__actions { display: flex; gap: 8px; flex: 0 0 auto; }
.file-center-note { margin-bottom: 16px; }
.file-center-summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.file-center-summary h3,
.file-center-summary p { margin-top: 0; }
.file-center-table { width: 100%; }
.file-center-pagination { justify-content: flex-end; margin-top: 16px; }
@media (max-width: 1080px) {
  .file-center-toolbar { align-items: stretch; flex-direction: column; }
  .file-center-toolbar__filters { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 620px) {
  .file-center-toolbar__filters { grid-template-columns: 1fr; }
  .file-center-toolbar__actions > * { flex: 1; }
  .file-center-summary { align-items: flex-start; flex-direction: column; }
  .file-center-pagination { justify-content: flex-start; overflow-x: auto; }
}
</style>

