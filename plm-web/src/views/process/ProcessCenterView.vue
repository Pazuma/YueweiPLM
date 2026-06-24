<script setup lang="ts">
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProcessCenterSnapshot } from '@/api/modules/process'
import PageContainer from '@/components/PageContainer/index.vue'
import ProjectTimeRangeFilter, { type ProjectTimeRangeValue } from '@/components/ProjectTimeRangeFilter/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type {
  ProcessAttachmentSummary,
  ProcessCenterSnapshot,
  ProcessCenterViewMode,
  ProcessOperationRecord,
  ProcessRouteDetail,
  ProcessRouteListItem
} from '@/types/process'
import { formatAmount } from '@/utils/format'
import { normalizeLegacyProductTarget } from '@/utils/projectRoute'

type DetailSectionKey = 'overview' | 'operations' | 'attachments' | 'changes' | 'impacts'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const snapshot = ref<ProcessCenterSnapshot | null>(null)
const viewMode = ref<ProcessCenterViewMode>('route')
const activeSection = ref<DetailSectionKey>('overview')
const timeRange = ref<ProjectTimeRangeValue>('all')
const operationKeyword = ref('')
const activeImpactLabel = ref('')

function isWithinTimeRange(dateText: string | undefined, range: ProjectTimeRangeValue) {
  if (!dateText || range === 'all') return true
  const end = new Date('2026-06-11')
  const current = new Date(dateText)
  if (Number.isNaN(current.getTime())) return true
  const diffDays = Math.floor((end.getTime() - current.getTime()) / (1000 * 60 * 60 * 24))
  if (range === '3d') return diffDays <= 3
  if (range === '7d') return diffDays <= 7
  if (range === '30d') return diffDays <= 30
  return diffDays <= 180
}

const rows = computed(() => (snapshot.value?.routes || []).filter((item) => isWithinTimeRange(item.updatedAt, timeRange.value)))
const activeRouteId = computed(() => Number(route.query.routeId || 0) || null)
const isDetailMode = computed(() => Boolean(activeRouteId.value))

const sectionOptions = [
  { label: '概览', value: 'overview' },
  { label: '工序明细', value: 'operations' },
  { label: '资料挂接', value: 'attachments' },
  { label: '版本变更', value: 'changes' },
  { label: '联动影响', value: 'impacts' }
] as const

const searchFields: SearchField[] = [
  {
    prop: 'keyword',
    label: '关键词',
    type: 'input',
    placeholder: '路线编码 / 名称 / 产品编码 / 负责人'
  },
  {
    prop: 'routeType',
    label: '路线类型',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '新产品线', value: 'new_product_line' },
      { label: '新型号线', value: 'new_model_variant' }
    ]
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '草稿', value: 'draft' },
      { label: '已确认', value: 'confirmed' },
      { label: '已锁定', value: 'locked' },
      { label: '已发布', value: 'released' }
    ]
  },
  {
    prop: 'templateSource',
    label: '来源',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '标准模板', value: 'standard' },
      { label: '继承差异', value: 'inherited' },
      { label: '手工调整', value: 'manual' }
    ]
  }
]

const table = useTable(rows, ['routeCode', 'routeName', 'productCode', 'productName', 'owner'], (row, filters) => {
  const routeType = String(filters.routeType || '')
  const status = String(filters.status || '')
  const templateSource = String(filters.templateSource || '')

  if (routeType && row.routeType !== routeType) return false
  if (status && row.status !== status) return false
  if (templateSource && row.templateSource !== templateSource) return false
  return true
})

const activeDetail = computed<ProcessRouteDetail | null>(() => {
  if (!snapshot.value || !activeRouteId.value) return null
  return snapshot.value.routeDetails[activeRouteId.value] || null
})

const activeRouteRow = computed<ProcessRouteListItem | null>(() => {
  if (!activeRouteId.value) return null
  return snapshot.value?.routes.find((item) => item.routeId === activeRouteId.value) || null
})

const overviewItems = computed(() => {
  const detail = activeDetail.value
  if (!detail) return []

  return [
    { label: '工序数量', value: `${detail.operations.length} 道` },
    { label: '工艺成本', value: formatAmount(detail.totalCost) },
    { label: '差异工序', value: `${detail.differenceOperationCount} 道` },
    { label: '当前门禁', value: detail.currentGate, emphasis: true },
    { label: '路线类型', value: routeTypeLabel(detail.routeType) },
    { label: '模板来源', value: templateSourceLabel(detail.templateSource) },
    { label: '负责人', value: detail.owner },
    { label: '继承来源', value: detail.inheritedFrom || '无继承来源' },
    { label: '锁定状态', value: detail.isLocked ? '已锁定' : '未锁定' },
    { label: '门禁结果', value: detail.passedGate ? '已通过' : '待补齐', emphasis: true }
  ]
})

const activeImpact = computed(() => {
  const impacts = activeDetail.value?.impacts || []
  return impacts.find((item) => item.label === activeImpactLabel.value) || impacts[0] || null
})

const activeImpactSummaryItems = computed(() => {
  const detail = activeDetail.value
  const impact = activeImpact.value
  if (!detail || !impact) return []

  if (impact.label.includes('产品')) {
    return [
      { label: '产品编码', value: detail.productCode },
      { label: '产品名称', value: detail.productName },
      { label: '工艺版本', value: detail.versionNo },
      { label: '当前状态', value: detail.status },
      { label: '当前进程', value: detail.currentGate }
    ]
  }

  if (impact.label.includes('质量')) {
    const qualityConfirmation = detail.confirmations.find((item) => item.roleName.includes('质量'))
    const qualitySpecCount = detail.attachments.reduce((sum, item) => sum + item.qualitySpecCount, 0)
    return [
      { label: '门禁结果', value: detail.passedGate ? '已通过' : '待补齐' },
      { label: '质量确认', value: qualityConfirmation?.status === 'approved' ? '已确认' : qualityConfirmation?.status === 'blocked' ? '阻塞' : '待确认' },
      { label: '质量负责人', value: qualityConfirmation?.ownerName || '--' },
      { label: '检验标准', value: `${qualitySpecCount} 份` },
      { label: '差异工序', value: `${detail.differenceOperationCount} 道` }
    ]
  }

  if (impact.label.includes('文件')) {
    const attachmentTotal = detail.attachments.reduce(
      (sum, item) => sum + item.sopCount + item.sipCount + item.parameterSheetCount + item.qualitySpecCount,
      0
    )
    const missingCount = detail.attachments.filter((item) => item.status === 'missing').length
    const sortedUpdatedAt = detail.attachments.map((item) => item.updatedAt).sort()
    const latestUpdatedAt = sortedUpdatedAt[sortedUpdatedAt.length - 1] || '--'
    return [
      { label: '资料总数', value: `${attachmentTotal} 份` },
      { label: '缺失工序', value: `${missingCount} 个` },
      { label: '最近更新', value: latestUpdatedAt },
      { label: '路线版本', value: detail.versionNo },
      { label: '锁定状态', value: detail.isLocked ? '已锁定' : '未锁定' }
    ]
  }

  return [
    { label: '关联对象', value: impact.label },
    { label: '摘要', value: impact.summary },
    { label: '产品', value: detail.productName },
    { label: '版本', value: detail.versionNo },
    { label: '进程', value: detail.currentGate }
  ]
})

const filteredOperationRows = computed(() => {
  const detail = activeDetail.value
  if (!detail) return []

  let operations = detail.operations
  if (viewMode.value === 'operation') {
    operations = operations.filter((item) => item.isKeyProcess || item.isDifferenceOperation)
  } else if (viewMode.value === 'change') {
    operations = operations.filter((item) => item.changedInCurrentVersion || item.isDifferenceOperation)
  }

  const keyword = operationKeyword.value.trim().toLowerCase()
  if (!keyword) return operations

  return operations.filter((item) => {
    const fields = [
      getOperationCode(item),
      item.operationName,
      item.operationType,
      item.workstationName,
      item.supplierName || '',
      item.confirmerName || '',
      item.confirmerRole || ''
    ]
    return fields.some((field) => field.toLowerCase().includes(keyword))
  })
})

async function loadSnapshot() {
  loading.value = true
  try {
    snapshot.value = await getProcessCenterSnapshot()
    table.setQuery({
      keyword: String(route.query.keyword || ''),
      routeType: String(route.query.routeType || ''),
      status: String(route.query.status || ''),
      templateSource: String(route.query.templateSource || '')
    })
    const mode = String(route.query.mode || 'route') as ProcessCenterViewMode
    viewMode.value = ['route', 'operation', 'change'].includes(mode) ? mode : 'route'
  } finally {
    loading.value = false
  }
}

function openRouteDetail(row: ProcessRouteListItem) {
  activeSection.value = 'overview'
  operationKeyword.value = ''
  router.push({
    path: '/processes',
    query: {
      ...route.query,
      routeId: row.routeId
    }
  })
}

function backToList() {
  const nextQuery = { ...route.query }
  delete nextQuery.routeId
  activeSection.value = 'overview'
  operationKeyword.value = ''
  router.push({ path: '/processes', query: nextQuery })
}

function syncViewMode(mode: ProcessCenterViewMode) {
  viewMode.value = mode
}

function openTarget(path: string) {
  router.push(normalizeLegacyProductTarget(path))
}

function routeTypeLabel(type: ProcessRouteListItem['routeType']) {
  return type === 'new_product_line' ? '新产品线' : '新型号线'
}

function templateSourceLabel(source: ProcessRouteListItem['templateSource']) {
  if (source === 'standard') return '标准模板'
  if (source === 'inherited') return '继承差异'
  return '手工调整'
}

function attachmentStatusLabel(status: string) {
  if (status === 'complete') return '齐套'
  if (status === 'partial') return '待补'
  return '缺失'
}

function getOperationTypeLabel(type: string) {
  if (type === 'semi_finished') return '半成品'
  if (type === 'finished') return '成品'
  return type
}

function getOperationCode(operation: ProcessOperationRecord) {
  if (operation.operationCode) return operation.operationCode
  const prefix = activeDetail.value?.routeCode || 'PROC'
  return `${prefix}-${String(operation.sequenceNo).padStart(3, '0')}`
}

function previewAttachment(row: ProcessAttachmentSummary) {
  if (!row.previewPath) {
    ElMessage.info('当前工序暂无可预览资料')
    return
  }
  ElMessage.info(`预览 ${row.operationName} 的资料`)
}

function addAttachment(row: ProcessAttachmentSummary) {
  if (row.canAdd === false) {
    ElMessage.info('当前状态暂不能添加资料')
    return
  }
  ElMessage.info(`为 ${row.operationName} 添加资料`)
}

function applyChange() {
  ElMessage.info('变更申请入口已预留')
}

function finalizeChange() {
  ElMessage.info('敲定入口已预留')
}

watch(
  () => activeDetail.value?.routeId,
  () => {
    activeImpactLabel.value = activeDetail.value?.impacts[0]?.label || ''
  },
  { immediate: true }
)

onMounted(loadSnapshot)
</script>

<template>
  <PageContainer
    title="工艺路线中心"
    description="围绕 Process 对象集中查看工艺路线、工序结构、确认门禁、资料挂接、版本变更和业务联动。"
  >
    <SearchBar
      v-if="!isDetailMode"
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', routeType: '', status: '', templateSource: '' })"
    >
      <template #extra>
        <ProjectTimeRangeFilter v-model="timeRange" />
      </template>
    </SearchBar>

    <section v-if="!isDetailMode" class="page-panel route-table-panel" v-loading="loading">
      <div class="toolbar-row route-table-panel__header">
        <div>
          <h3 class="section-title">工艺产品列表</h3>
          <p class="page-panel-desc">先找到产品和路线，再进入工序、资料、门禁和版本变更详情。</p>
        </div>
        <el-tag effect="light">共 {{ table.filteredRows.value.length }} 条</el-tag>
      </div>

      <el-table :data="table.filteredRows.value" border stripe class="route-table">
        <el-table-column prop="routeCode" label="编号" min-width="160" />
        <el-table-column label="所属项目" min-width="230"><template #default="{ row }"><strong>{{ row.productName }}</strong></template></el-table-column>
        <el-table-column prop="routeName" label="名字" min-width="250" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" object-type="process" />
          </template>
        </el-table-column>
        <el-table-column prop="currentGate" label="进程" min-width="170" />
        <el-table-column prop="versionNo" label="版本" width="90" />
        <el-table-column label="详情" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRouteDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <article v-else-if="activeDetail" class="page-panel detail-panel">
      <div class="detail-topline">
        <el-button link type="primary" :icon="ArrowLeft" @click="backToList">返回列表</el-button>
      </div>

      <div class="toolbar-row detail-header">
        <div>
          <h3 class="section-title">{{ activeDetail.routeName }}</h3>
          <div class="detail-breadcrumb">
            <span>工艺路线中心</span>
            <span>{{ activeDetail.productName }}</span>
            <span>{{ activeDetail.routeCode }}</span>
            <span>版本 {{ activeDetail.versionNo }}</span>
          </div>
        </div>
        <div class="header-actions">
          <StatusTag :status="activeDetail.status" object-type="process" />
          <el-button link type="primary" @click="openTarget(`/products/${activeDetail.productId}`)">产品详情</el-button>
        </div>
      </div>

      <section class="section-nav">
        <el-segmented v-model="activeSection" :options="sectionOptions" class="section-switcher" />
      </section>

      <section v-if="activeSection === 'overview'" class="page-stack detail-section">
        <section class="split-grid overview-detail-grid">
          <article class="page-panel inner-panel">
            <div class="toolbar-row">
              <h3 class="section-title">工序视图</h3>
            </div>
            <p class="page-panel-desc">概览区收纳原有数据板信息，具体工序在“工序明细”里查看和搜索。</p>
          </article>

          <article class="page-panel inner-panel">
            <h3 class="section-title">当前对象</h3>
            <div class="object-lines">
              <span>{{ activeDetail.productCode }}</span>
              <strong>{{ activeDetail.productName }}</strong>
              <span>{{ activeRouteRow ? routeTypeLabel(activeRouteRow.routeType) : routeTypeLabel(activeDetail.routeType) }}</span>
            </div>
          </article>
        </section>

        <section class="overview-panel">
          <div class="summary-box summary-box--note">
            <span class="subtle-text">概览</span>
            <strong>{{ activeDetail.overviewNote }}</strong>
          </div>

          <div class="overview-grid">
            <div v-for="item in overviewItems" :key="item.label" class="overview-item">
              <span class="subtle-text">{{ item.label }}</span>
              <strong :class="{ 'is-emphasis': item.emphasis }">{{ item.value }}</strong>
            </div>
          </div>
        </section>
      </section>

      <section v-else-if="activeSection === 'operations'" class="page-stack detail-section">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">工序明细</h3>
            <p class="page-panel-desc">按工序查看参数、质量要求、责任确认人和关键标记。</p>
          </div>
          <div class="operation-toolbar">
            <el-segmented
              :model-value="viewMode"
              :options="[
                { label: '全部工序', value: 'route' },
                { label: '关键工序', value: 'operation' },
                { label: '变更工序', value: 'change' }
              ]"
              @change="(value: string | number) => syncViewMode(value as ProcessCenterViewMode)"
            />
            <el-input v-model="operationKeyword" clearable placeholder="搜索工序 / 位置 / 供应商" class="operation-search" />
          </div>
        </div>

        <el-table :data="filteredOperationRows" border stripe>
          <el-table-column prop="sequenceNo" label="顺序" width="80" />
          <el-table-column label="工序编码" min-width="150">
            <template #default="{ row }">
              {{ getOperationCode(row) }}
            </template>
          </el-table-column>
          <el-table-column prop="operationName" label="工序名称" min-width="150" />
          <el-table-column label="工序类型" min-width="120">
            <template #default="{ row }">
              {{ getOperationTypeLabel(row.operationType) }}
            </template>
          </el-table-column>
          <el-table-column prop="workstationName" label="执行位置" min-width="170" />
          <el-table-column prop="supplierName" label="供应商" min-width="140">
            <template #default="{ row }">{{ row.supplierName || '--' }}</template>
          </el-table-column>
          <el-table-column label="确认人" min-width="140">
            <template #default="{ row }">
              <div class="cell-stack">
                <strong>{{ row.confirmerName || '--' }}</strong>
                <span class="subtle-text">{{ row.confirmerRole || '未指定' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="parameterSummary" label="核心参数摘要" min-width="220" />
          <el-table-column prop="qualityRequirement" label="质量要求" min-width="180" />
          <el-table-column label="单工序成本" width="130">
            <template #default="{ row }">{{ formatAmount(row.unitCost) }}</template>
          </el-table-column>
          <el-table-column label="标记" min-width="170">
            <template #default="{ row }">
              <div class="tag-wrap">
                <el-tag v-if="row.isKeyProcess" type="warning" effect="light">关键工序</el-tag>
                <el-tag v-if="row.isExternalOperation" type="success" effect="light">外协</el-tag>
                <el-tag v-if="row.isDifferenceOperation" type="info" effect="light">差异</el-tag>
                <el-tag v-if="row.changedInCurrentVersion" type="danger" effect="light">本版变更</el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-else-if="activeSection === 'attachments'" class="page-stack detail-section">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">资料挂接</h3>
            <p class="page-panel-desc">按工序查看 SOP、SIP、参数表和检验标准是否齐套。</p>
          </div>
        </div>

        <el-table :data="activeDetail.attachments" border stripe>
          <el-table-column prop="operationName" label="工序" min-width="150" />
          <el-table-column prop="sopCount" label="SOP" width="70" />
          <el-table-column prop="sipCount" label="SIP" width="70" />
          <el-table-column prop="parameterSheetCount" label="参数表" width="90" />
          <el-table-column prop="qualitySpecCount" label="检验标准" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'complete' ? 'success' : row.status === 'partial' ? 'warning' : 'danger'" effect="light">
                {{ attachmentStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="最近更新" width="140" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="previewAttachment(row)">预览</el-button>
                <el-button link type="success" :disabled="row.canAdd === false" @click="addAttachment(row)">添加</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-else-if="activeSection === 'changes'" class="page-stack detail-section">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">版本变更</h3>
            <p class="page-panel-desc">聚焦这条路线的版本变化、原因和影响。</p>
          </div>
          <div class="header-actions">
            <el-button plain :disabled="activeDetail.canApplyChange === false" @click="applyChange">变更申请</el-button>
            <el-button type="primary" :disabled="activeDetail.canFinalize === false" @click="finalizeChange">敲定</el-button>
          </div>
        </div>

        <div class="page-stack">
          <div v-for="item in activeDetail.changes" :key="`${item.versionNo}-${item.changedAt}`" class="change-card">
            <div class="toolbar-row">
              <strong>版本 {{ item.versionNo }} / {{ item.changeType }}</strong>
              <span>{{ item.changedAt }}</span>
            </div>
            <p class="page-panel-desc">{{ item.changeReason }}</p>
            <p class="subtle-text">涉及工序：{{ item.affectedOperations.join('、') }}</p>
            <div class="route-meta route-meta--compact">
              <span>成本变化 {{ formatAmount(item.costDelta) }}</span>
              <span>交期变化 {{ item.leadDayDelta }} 天</span>
              <span>责任人 {{ item.ownerName }}</span>
            </div>
          </div>
        </div>
      </section>

      <section v-else class="page-stack detail-section">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">联动影响</h3>
            <p class="page-panel-desc">点击左侧联动项，在右侧查看当前路线相关摘要。</p>
          </div>
        </div>

        <div class="impact-preview-layout">
          <div class="page-stack impact-list">
            <button
              v-for="item in activeDetail.impacts"
              :key="item.label"
              class="impact-card"
              :class="{ 'is-active': activeImpact?.label === item.label }"
              type="button"
              @click="activeImpactLabel = item.label"
            >
              <div class="toolbar-row">
                <strong>{{ item.label }}</strong>
                <el-icon><ArrowRight /></el-icon>
              </div>
              <p class="page-panel-desc">{{ item.summary }}</p>
            </button>
          </div>

          <article v-if="activeImpact" class="impact-preview-panel">
            <div class="toolbar-row">
              <div>
                <h3 class="section-title">{{ activeImpact.label }}</h3>
                <p class="page-panel-desc">{{ activeImpact.summary }}</p>
              </div>
              <el-button link type="primary" @click="openTarget(activeImpact.targetPath)">查看完整页面</el-button>
            </div>

            <div class="impact-summary-grid">
              <div v-for="item in activeImpactSummaryItems" :key="item.label" class="overview-item">
                <span class="subtle-text">{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </article>
        </div>
      </section>
    </article>
  </PageContainer>
</template>

<style scoped>
.route-table-panel,
.detail-panel {
  min-height: 0;
}

.route-table-panel__header {
  margin-bottom: 14px;
}

.route-table {
  width: 100%;
}

.detail-topline {
  margin-bottom: 10px;
}

.detail-header {
  align-items: flex-start;
  gap: 16px;
}

.detail-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: var(--plm-color-text-secondary);
  font-size: var(--plm-font-size-sm);
}

.detail-breadcrumb span + span::before {
  content: '/';
  margin-right: 8px;
  color: var(--plm-color-text-placeholder);
}

.header-actions,
.operation-toolbar,
.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.operation-toolbar {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.operation-search {
  width: 260px;
}

.overview-panel {
  display: grid;
  grid-template-columns: minmax(260px, 0.85fr) minmax(0, 1.6fr);
  gap: 14px;
  align-items: stretch;
}

.summary-box,
.overview-item,
.info-card,
.gate-card,
.change-card {
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.summary-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-box--note {
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.86), rgba(255, 255, 255, 0.96));
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.overview-item {
  min-height: 74px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  background: rgba(248, 250, 252, 0.88);
}

.overview-item strong {
  color: var(--plm-color-text-primary);
}

.overview-item strong.is-emphasis {
  color: var(--plm-color-primary);
}

.section-nav {
  margin-top: 18px;
  padding-top: 2px;
}

.section-switcher {
  width: 100%;
}

.detail-section {
  margin-top: 16px;
}

.overview-detail-grid,
.inner-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.inner-panel {
  min-height: 100%;
}

.object-lines {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--plm-color-text-secondary);
}

.object-lines strong {
  color: var(--plm-color-text-primary);
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.route-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: var(--plm-color-text-secondary);
  font-size: var(--plm-font-size-sm);
}

.route-meta--compact {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.impact-preview-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
  align-items: stretch;
}

.impact-list {
  max-width: none;
}

.impact-card {
  width: 100%;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.impact-card:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.impact-card.is-active {
  border-color: var(--plm-color-primary);
  background: rgba(37, 99, 235, 0.06);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.1);
}

.impact-card.is-active strong {
  color: var(--plm-color-primary);
}

.impact-preview-panel {
  min-height: 260px;
  padding: 16px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.impact-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 1280px) {
  .overview-panel,
  .overview-detail-grid,
  .inner-grid,
  .impact-preview-layout {
    grid-template-columns: 1fr;
  }

  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .detail-header,
  .header-actions,
  .operation-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .operation-search {
    width: 100%;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .impact-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
