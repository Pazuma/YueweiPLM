<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

import { getFileSections } from '@/api/modules/foundation'
import FilePreview from '@/components/FilePreview/index.vue'
import PageContainer from '@/components/PageContainer/index.vue'
import type {
  FileDateRange,
  FileProductTypeFilter,
  FileProjectGroup,
  FileRecord,
  FileSection
} from '@/types/foundation'
import { formatDate } from '@/utils/format'
import { toArchivedProductRoute } from '@/utils/projectRoute'

const router = useRouter()

const loading = ref(false)
const sections = ref<FileSection[]>([])
const activeView = ref<'in_progress' | 'all'>('in_progress')
const activeDateRange = ref<FileDateRange>('30d')
const activeProductType = ref<FileProductTypeFilter>('all')
const expandedGroups = ref<string[]>([])
const keyword = ref('')

const uploadDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const pendingDeleteFile = ref<FileRecord | null>(null)
const previewVisible = ref(false)
const previewFileRecord = ref<FileRecord | null>(null)

const uploadForm = reactive({
  productId: undefined as number | undefined,
  projectName: '',
  stageKey: '',
  category: '',
  fileName: '',
  versionNo: ''
})

const inProgressCodes = ['PRD-CD30-001', 'PRD-CD30-IP18-BLK']
const stageOptions = [
  { label: '立项资料', value: 'project_setup' },
  { label: '工程图纸', value: 'engineering' },
  { label: '测试资料', value: 'testing' },
  { label: '生产资料', value: 'production' },
  { label: '客户确认件', value: 'customer_confirm' },
  { label: '差异资料', value: 'variant' }
]

const normalizedGroups = computed(() =>
  sections.value.flatMap((section) =>
    section.groups.map((group) => ({
      ...group,
      productType: group.productType ?? (section.key === 'product_files' ? 'product' : 'variant'),
      files: group.files.map((file) => ({
        ...file,
        stageLabel: file.stageLabel ?? file.category
      }))
    }))
  )
)

const totalFileCount = computed(() => normalizedGroups.value.reduce((total, group) => total + group.files.length, 0))

const visibleGroups = computed(() => {
  const source =
    activeView.value === 'in_progress'
      ? normalizedGroups.value.filter((group) => inProgressCodes.includes(group.productCode))
      : normalizedGroups.value

  const keywordValue = keyword.value.trim().toLowerCase()

  return source
    .filter((group) => activeProductType.value === 'all' || group.productType === activeProductType.value)
    .map((group) => {
      const filteredFiles = group.files.filter((file) => {
        const dateMatched = isWithinRange(file.uploadedAt, activeDateRange.value)

        if (!dateMatched) return false
        if (!keywordValue) return true

        return (
          group.projectName.toLowerCase().includes(keywordValue) ||
          group.productCode.toLowerCase().includes(keywordValue) ||
          file.fileName.toLowerCase().includes(keywordValue) ||
          file.category.toLowerCase().includes(keywordValue) ||
          file.owner.toLowerCase().includes(keywordValue)
        )
      })

      return {
        ...group,
        files: filteredFiles
      }
    })
    .filter((group) => group.files.length > 0)
})

watch(
  visibleGroups,
  (groups) => {
    if (!groups.length) {
      expandedGroups.value = []
      return
    }

    const active = expandedGroups.value.filter((groupId) => groups.some((group) => group.groupId === groupId))
    expandedGroups.value = active.length ? active : [groups[0].groupId]
  },
  { immediate: true }
)

function isWithinRange(dateText: string, range: FileDateRange) {
  const uploadedAt = new Date(dateText)
  if (Number.isNaN(uploadedAt.getTime())) return true

  const now = new Date()
  const diffDays = (now.getTime() - uploadedAt.getTime()) / (1000 * 60 * 60 * 24)

  if (range === '7d') return diffDays <= 7
  if (range === '30d') return diffDays <= 30
  return diffDays <= 180
}

function isExpanded(groupId: string) {
  return expandedGroups.value.includes(groupId)
}

function toggleGroup(groupId: string) {
  expandedGroups.value = isExpanded(groupId) ? [] : [groupId]
}

function openProduct(productId: number) {
  router.push(toArchivedProductRoute(productId))
}

function previewFile(file: FileRecord) {
  previewFileRecord.value = file
  previewVisible.value = true
}

function openUploadDialog(group: FileProjectGroup) {
  uploadForm.productId = group.productId
  uploadForm.projectName = group.projectName
  uploadForm.stageKey = ''
  uploadForm.category = ''
  uploadForm.fileName = ''
  uploadForm.versionNo = ''
  uploadDialogVisible.value = true
}

function confirmUpload() {
  if (!uploadForm.productId || !uploadForm.stageKey || !uploadForm.category || !uploadForm.fileName || !uploadForm.versionNo) {
    ElMessage.warning('请先补齐所属环节、分类、文件名称和版本号。')
    return
  }

  const targetSectionKey = activeProductType.value === 'variant' ? 'variant_files' : guessSectionKeyByProductId(uploadForm.productId)
  const targetSection = sections.value.find((section) => section.key === targetSectionKey)
  const targetGroup = targetSection?.groups.find((group) => group.productId === uploadForm.productId)
  const stageLabel = stageOptions.find((item) => item.value === uploadForm.stageKey)?.label ?? uploadForm.category

  if (!targetSection || !targetGroup) {
    ElMessage.error('未找到对应项目组，暂时无法添加文件。')
    return
  }

  targetGroup.files.unshift({
    fileId: `temp-${Date.now()}`,
    fileName: uploadForm.fileName,
    category: uploadForm.category,
    owner: '当前用户',
    uploadedAt: new Date().toISOString().slice(0, 10),
    versionNo: uploadForm.versionNo,
    productId: uploadForm.productId,
    stageKey: uploadForm.stageKey,
    stageLabel
  })
  targetGroup.updatedAt = new Date().toISOString().slice(0, 10)

  uploadDialogVisible.value = false
  expandedGroups.value = [targetGroup.groupId]
  ElMessage.success('文件已添加到当前项目组，当前为前端演示数据。')
}

function guessSectionKeyByProductId(productId: number) {
  const variantSection = sections.value.find((section) => section.key === 'variant_files')
  if (variantSection?.groups.some((group) => group.productId === productId)) return 'variant_files'
  return 'product_files'
}

function openDeleteDialog(file: FileRecord) {
  pendingDeleteFile.value = file
  deleteDialogVisible.value = true
}

function confirmDelete() {
  const file = pendingDeleteFile.value
  if (!file) return

  sections.value.forEach((section) => {
    section.groups.forEach((group) => {
      if (group.productId !== file.productId) return
      group.files = group.files.filter((item) => item.fileId !== file.fileId)
    })
  })

  deleteDialogVisible.value = false
  pendingDeleteFile.value = null
  ElMessage.success('文件已从当前列表移除，当前为前端演示数据。')
}

async function loadData() {
  loading.value = true
  try {
    sections.value = await getFileSections()
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="文件中心"
    description="先筛选项目范围，再按项目组查看和维护图纸、测试资料、生产资料与差异文件。"
  >
    <section class="page-panel file-toolbar">
      <div class="file-toolbar__left">
        <el-segmented
          v-model="activeView"
          :options="[
            { label: '进行中项目', value: 'in_progress' },
            { label: '全部项目', value: 'all' }
          ]"
        />
      </div>

      <div class="file-toolbar__filters">
        <el-select v-model="activeDateRange" class="file-toolbar__filter" placeholder="时间范围">
          <el-option label="最近一星期" value="7d" />
          <el-option label="最近一个月" value="30d" />
          <el-option label="最近半年" value="180d" />
        </el-select>

        <el-segmented
          v-model="activeProductType"
          :options="[
            { label: '全部', value: 'all' },
            { label: '产品', value: 'product' },
            { label: '新型号', value: 'variant' }
          ]"
        />

        <div class="file-total-chip">
          <span>文件总数</span>
          <strong>{{ totalFileCount }}</strong>
        </div>
      </div>

      <div class="file-toolbar__right">
        <el-input
          v-model="keyword"
          clearable
          class="file-toolbar__search"
          placeholder="搜索项目、编码、文件名、分类、负责人"
        />
      </div>
    </section>

    <section class="page-panel" v-loading="loading">
      <div class="toolbar-row">
        <div>
          <h3 class="section-title">{{ activeView === 'in_progress' ? '进行中的项目文件' : '全部项目文件' }}</h3>
          <p class="page-panel-desc">
            {{ activeView === 'in_progress' ? '默认聚焦当前开发与评审阶段的项目资料。' : '这里同时包含当前项目与历史归档项目资料。' }}
          </p>
        </div>
        <el-tag effect="light">{{ visibleGroups.length }} 个项目组</el-tag>
      </div>

      <div v-if="visibleGroups.length" class="page-stack">
        <section v-for="group in visibleGroups" :key="group.groupId" class="group-card">
          <div class="group-card__header">
            <button class="group-card__summary" type="button" @click="toggleGroup(group.groupId)">
              <div class="group-card__meta">
                <strong>{{ group.projectName }}</strong>
                <span class="subtle-text">
                  {{ group.productCode }} / {{ group.owner }} / 更新于 {{ formatDate(group.updatedAt) }}
                </span>
              </div>

              <el-icon class="group-card__arrow" :class="{ 'is-expanded': isExpanded(group.groupId) }">
                <ArrowDown />
              </el-icon>
            </button>

            <div class="group-card__actions">
              <span class="subtle-text">{{ group.files.length }} 个文件</span>

              <div class="group-card__action-buttons">
                <el-button size="small" type="primary" plain @click="openUploadDialog(group)">添加文件</el-button>
                <el-button link type="primary" @click="openProduct(group.productId)">进入产品</el-button>
              </div>
            </div>
          </div>

          <div v-if="isExpanded(group.groupId)" class="group-card__body">
            <el-table :data="group.files" border stripe>
              <el-table-column prop="fileName" label="文件名称" min-width="260" />
              <el-table-column prop="stageLabel" label="所属环节" min-width="140" />
              <el-table-column prop="category" label="分类" min-width="140" />
              <el-table-column prop="owner" label="上传人 / 部门" width="140" />
              <el-table-column prop="versionNo" label="版本" width="100" />
              <el-table-column label="更新时间" width="140">
                <template #default="{ row }">{{ formatDate(row.uploadedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <div class="file-row-actions">
                    <el-button link type="primary" @click="previewFile(row)">查看</el-button>
                    <el-button link type="danger" @click="openDeleteDialog(row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </div>

      <el-empty v-else description="当前筛选条件下没有匹配到文件项目组" />
    </section>

    <el-dialog v-model="uploadDialogVisible" title="添加文件" width="560px">
      <el-form label-width="90px">
        <el-form-item label="所属项目">
          <el-input :model-value="uploadForm.projectName" disabled />
        </el-form-item>

        <el-form-item label="所属环节">
          <el-select v-model="uploadForm.stageKey" placeholder="请选择环节">
            <el-option v-for="item in stageOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="文件分类">
          <el-input v-model="uploadForm.category" placeholder="例如：工程图纸 / 测试资料 / 差异资料" />
        </el-form-item>

        <el-form-item label="文件名称">
          <el-input v-model="uploadForm.fileName" placeholder="请输入文件名称" />
        </el-form-item>

        <el-form-item label="版本号">
          <el-input v-model="uploadForm.versionNo" placeholder="例如：A.1 / V2" />
        </el-form-item>

        <el-form-item label="文件上传">
          <el-upload drag :auto-upload="false">
            <div class="upload-placeholder">
              <strong>拖拽文件到这里</strong>
              <span>当前为前端演示，暂不接真实上传接口</span>
            </div>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmUpload">确认添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deleteDialogVisible" title="删除文件" width="420px">
      <p class="dialog-text">
        删除后，<strong>{{ pendingDeleteFile?.fileName }}</strong> 将从当前项目资料列表中移除。
      </p>
      <p class="dialog-text subtle-text">当前为前端演示删除，不会影响真实后端文件。</p>

      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>

    <FilePreview v-model="previewVisible" :file-name="previewFileRecord?.fileName || ''" />
  </PageContainer>
</template>

<style scoped>
.file-toolbar {
  display: grid;
  grid-template-columns: auto minmax(280px, 1fr) minmax(280px, 360px);
  gap: 16px;
  align-items: center;
}

.file-toolbar__left,
.file-toolbar__right {
  min-width: 0;
}

.file-toolbar__filters {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.file-toolbar__filter {
  width: 180px;
  flex: 0 0 180px;
}

.file-total-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: rgba(248, 250, 252, 0.92);
  color: var(--plm-color-text-secondary);
  font-size: var(--plm-font-size-sm);
  white-space: nowrap;
}

.file-total-chip strong {
  color: var(--plm-color-text-primary);
  font-size: var(--plm-font-size-base);
}

.file-toolbar__search {
  width: 100%;
}

.group-card {
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  overflow: hidden;
  background: #fff;
}

.group-card__header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--plm-color-border-light);
}

.group-card__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.group-card__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.group-card__arrow {
  flex: 0 0 auto;
  color: var(--plm-color-text-secondary);
  transition: transform 0.2s ease;
}

.group-card__arrow.is-expanded {
  transform: rotate(180deg);
}

.group-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.group-card__action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-card__body {
  padding: 16px;
}

.file-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  color: var(--plm-color-text-secondary);
}

.dialog-text {
  margin: 0 0 8px;
  line-height: 1.6;
}

@media (max-width: 1080px) {
  .file-toolbar {
    grid-template-columns: 1fr;
  }

  .file-toolbar__filters {
    flex-direction: column;
    align-items: stretch;
  }

  .file-toolbar__filter {
    width: 100%;
    flex: 1 1 auto;
  }

  .file-total-chip {
    justify-content: space-between;
    width: 100%;
  }
}

@media (max-width: 900px) {
  .group-card__header {
    grid-template-columns: 1fr;
  }

  .group-card__summary,
  .group-card__actions {
    width: 100%;
  }

  .group-card__actions {
    justify-content: space-between;
    flex-wrap: wrap;
  }
}
</style>
