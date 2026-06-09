<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProcessCenterSnapshot } from '@/api/modules/process'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type {
  ProcessCenterSnapshot,
  ProcessCenterViewMode,
  ProcessRouteDetail,
  ProcessRouteListItem
} from '@/types/process'
import { formatAmount } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const snapshot = ref<ProcessCenterSnapshot | null>(null)
const activeRouteId = ref<number | null>(null)
const viewMode = ref<ProcessCenterViewMode>('route')

const rows = computed(() => snapshot.value?.routes || [])

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '路线编码 / 名称 / 产品编码 / 负责人' },
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
  const list = table.filteredRows.value as unknown as ProcessRouteListItem[]
  if (!list.length || !snapshot.value) return null
  const selected = list.find((item) => item.routeId === activeRouteId.value) || list[0]
  return snapshot.value.routeDetails[selected.routeId] || null
})

const routeMetrics = computed(() => {
  const detail = activeDetail.value
  if (!detail) return []
  return [
    { label: '工序数量', value: detail.operations.length, hint: '按结构化工序记录，而不是文本备注。' },
    { label: '工艺成本', value: formatAmount(detail.totalCost), hint: '用于联动 BOM 成本和报价。' },
    { label: '差异工序', value: detail.differenceOperationCount, hint: '新型号线只维护差异部分。' },
    { label: '门禁状态', value: detail.passedGate ? '已通过' : '未通过', hint: detail.currentGate }
  ]
})

const filteredOperationRows = computed(() => {
  const operations = activeDetail.value?.operations || []
  if (viewMode.value === 'route') return operations
  if (viewMode.value === 'operation') return operations.filter((item) => item.isKeyProcess || item.isDifferenceOperation)
  return operations.filter((item) => item.changedInCurrentVersion || item.isDifferenceOperation)
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
    activeRouteId.value = Number(route.query.routeId || snapshot.value.routes[0]?.routeId || 0) || null
    const mode = String(route.query.mode || 'route') as ProcessCenterViewMode
    viewMode.value = ['route', 'operation', 'change'].includes(mode) ? mode : 'route'
  } finally {
    loading.value = false
  }
}

function selectRoute(row: ProcessRouteListItem) {
  activeRouteId.value = row.routeId
}

function syncViewMode(mode: ProcessCenterViewMode) {
  viewMode.value = mode
}

function openTarget(path: string) {
  router.push(path)
}

function riskLabel(level: ProcessRouteListItem['riskLevel']) {
  if (level === 'high') return '高'
  if (level === 'medium') return '中'
  return '低'
}

function attachmentStatusLabel(status: string) {
  if (status === 'complete') return '齐套'
  if (status === 'partial') return '待补'
  return '缺失'
}

onMounted(loadSnapshot)
</script>

<template>
  <PageContainer
    title="工艺路线中心"
    description="围绕 Process 对象集中查看工艺路线、工序结构、确认门禁、资料挂接、版本变更和业务联动。"
  >
    <template #actions>
      <el-button @click="router.push('/products')">产品管理</el-button>
      <el-button type="primary" @click="router.push(activeDetail ? `/products/${activeDetail.productId}` : '/products')">
        查看关联产品
      </el-button>
    </template>

    <section class="metric-grid" v-loading="loading">
      <div v-for="metric in snapshot?.metrics || []" :key="metric.label" class="metric-card">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <span class="metric-card__trend">{{ metric.hint }}</span>
      </div>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', routeType: '', status: '', templateSource: '' })"
    />

    <section class="split-grid process-grid">
      <article class="page-panel route-list-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">路线列表</h3>
            <p class="page-panel-desc">先选路线，再看工序、门禁、资料和变更影响。</p>
          </div>
          <el-tag effect="light">共 {{ table.filteredRows.value.length }} 条</el-tag>
        </div>

        <div class="page-stack">
          <button
            v-for="item in (table.filteredRows.value as unknown as ProcessRouteListItem[])"
            :key="item.routeId"
            class="route-card"
            :class="{ 'is-active': item.routeId === activeDetail?.routeId }"
            type="button"
            @click="selectRoute(item)"
          >
            <div class="toolbar-row">
              <div class="cell-stack">
                <strong>{{ item.routeName }}</strong>
                <span class="subtle-text">{{ item.routeCode }} / {{ item.productCode }}</span>
              </div>
              <StatusTag :status="item.status" object-type="process" />
            </div>
            <div class="route-meta">
              <span>{{ item.routeType === 'new_product_line' ? '新产品线' : '新型号线' }}</span>
              <span>{{ item.templateSource === 'standard' ? '标准模板' : item.templateSource === 'inherited' ? '继承差异' : '手工调整' }}</span>
            </div>
            <div class="route-meta">
              <span>{{ item.operationCount }} 道工序</span>
              <span>{{ formatAmount(item.totalCost) }}</span>
            </div>
            <div class="route-meta">
              <span class="subtle-text">负责人：{{ item.owner }}</span>
              <el-tag :type="item.riskLevel === 'high' ? 'danger' : item.riskLevel === 'medium' ? 'warning' : 'info'" effect="light">
                风险 {{ riskLabel(item.riskLevel) }}
              </el-tag>
            </div>
          </button>
        </div>
      </article>

      <article v-if="activeDetail" class="page-panel detail-panel">
        <div class="toolbar-row detail-header">
          <div>
            <h3 class="section-title">{{ activeDetail.routeName }}</h3>
            <p class="page-panel-desc">
              {{ activeDetail.routeCode }} / {{ activeDetail.productName }} / 版本 {{ activeDetail.versionNo }}
            </p>
          </div>
          <div class="header-actions">
            <StatusTag :status="activeDetail.status" object-type="process" />
            <el-button link type="primary" @click="openTarget(`/products/${activeDetail.productId}`)">产品详情</el-button>
          </div>
        </div>

        <section class="metric-grid nested-metrics">
          <div v-for="metric in routeMetrics" :key="metric.label" class="metric-card nested-card">
            <p class="metric-card__label">{{ metric.label }}</p>
            <p class="metric-card__value metric-card__value--small">{{ metric.value }}</p>
            <span class="metric-card__trend">{{ metric.hint }}</span>
          </div>
        </section>

        <section class="summary-row">
          <div class="summary-box">
            <p class="subtle-text">路线概览</p>
            <strong>{{ activeDetail.overviewNote }}</strong>
            <p v-if="activeDetail.inheritedFrom" class="page-panel-desc">继承来源：{{ activeDetail.inheritedFrom }}</p>
          </div>
          <div class="summary-box">
            <p class="subtle-text">视图切换</p>
            <el-segmented
              :model-value="viewMode"
              :options="[
                { label: '路线视图', value: 'route' },
                { label: '关键工序', value: 'operation' },
                { label: '变更视图', value: 'change' }
              ]"
              @change="(value) => syncViewMode(value as ProcessCenterViewMode)"
            />
          </div>
        </section>

        <section class="page-stack">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">工序明细</h3>
              <p class="page-panel-desc">把工艺路线从文本备注变成结构化工序对象，便于工程、采购、质量共用同一事实。</p>
            </div>
          </div>
          <el-table :data="filteredOperationRows" border stripe>
            <el-table-column prop="sequenceNo" label="顺序" width="80" />
            <el-table-column prop="operationName" label="工序名称" min-width="150" />
            <el-table-column prop="operationType" label="工序类别" min-width="120" />
            <el-table-column prop="workstationName" label="执行位置" min-width="170" />
            <el-table-column prop="supplierName" label="供应商" min-width="140">
              <template #default="{ row }">{{ row.supplierName || '--' }}</template>
            </el-table-column>
            <el-table-column prop="parameterSummary" label="核心参数摘要" min-width="200" />
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

        <section class="split-grid inner-grid">
          <article class="page-panel inner-panel">
            <h3 class="section-title">确认与门禁</h3>
            <div class="page-stack">
              <div v-for="item in activeDetail.confirmations" :key="item.roleName" class="info-card">
                <div class="toolbar-row">
                  <strong>{{ item.roleName }}</strong>
                  <el-tag :type="item.status === 'approved' ? 'success' : item.status === 'blocked' ? 'danger' : 'warning'" effect="light">
                    {{ item.status === 'approved' ? '已确认' : item.status === 'blocked' ? '阻塞' : '待确认' }}
                  </el-tag>
                </div>
                <p class="page-panel-desc">{{ item.ownerName }}</p>
                <p class="subtle-text">{{ item.note }}</p>
              </div>

              <div v-for="item in activeDetail.gateChecks" :key="item.gateName" class="gate-card">
                <div class="toolbar-row">
                  <strong>{{ item.gateName }}</strong>
                  <el-tag :type="item.passed ? 'success' : 'danger'" effect="light">{{ item.passed ? '通过' : '未通过' }}</el-tag>
                </div>
                <p class="page-panel-desc">{{ item.note }}</p>
              </div>
            </div>
          </article>

          <article class="page-panel inner-panel">
            <h3 class="section-title">资料挂接</h3>
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
            </el-table>
          </article>
        </section>

        <section class="split-grid inner-grid">
          <article class="page-panel inner-panel">
            <h3 class="section-title">版本变更</h3>
            <div class="page-stack">
              <div v-for="item in activeDetail.changes" :key="`${item.versionNo}-${item.changedAt}`" class="change-card">
                <div class="toolbar-row">
                  <strong>版本 {{ item.versionNo }} / {{ item.changeType }}</strong>
                  <span>{{ item.changedAt }}</span>
                </div>
                <p class="page-panel-desc">{{ item.changeReason }}</p>
                <p class="subtle-text">涉及工序：{{ item.affectedOperations.join('、') }}</p>
                <div class="route-meta">
                  <span>成本变化 {{ formatAmount(item.costDelta) }}</span>
                  <span>交期变化 {{ item.leadDayDelta }} 天</span>
                </div>
              </div>
            </div>
          </article>

          <article class="page-panel inner-panel">
            <h3 class="section-title">联动影响</h3>
            <div class="page-stack">
              <button
                v-for="item in activeDetail.impacts"
                :key="item.label"
                class="impact-card"
                type="button"
                @click="openTarget(item.targetPath)"
              >
                <div class="toolbar-row">
                  <strong>{{ item.label }}</strong>
                  <el-icon><ArrowRight /></el-icon>
                </div>
                <p class="page-panel-desc">{{ item.summary }}</p>
              </button>
            </div>
          </article>
        </section>
      </article>
    </section>
  </PageContainer>
</template>

<style scoped>
.process-grid {
  grid-template-columns: minmax(320px, 0.82fr) minmax(0, 1.55fr);
}

.route-list-panel,
.detail-panel,
.inner-panel {
  min-height: 100%;
}

.route-card,
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

.route-card:hover,
.impact-card:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.route-card.is-active {
  border-color: var(--plm-color-primary);
  background: rgba(37, 99, 235, 0.04);
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

.detail-header {
  align-items: flex-start;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nested-metrics {
  margin-top: 4px;
}

.nested-card {
  background: rgba(248, 250, 252, 0.92);
}

.metric-card__value--small {
  font-size: 20px;
}

.summary-row {
  display: grid;
  grid-template-columns: 1.3fr 0.9fr;
  gap: 16px;
  margin-top: 16px;
}

.summary-box,
.info-card,
.gate-card,
.change-card {
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.inner-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 1280px) {
  .process-grid,
  .summary-row,
  .inner-grid {
    grid-template-columns: 1fr;
  }
}
</style>
