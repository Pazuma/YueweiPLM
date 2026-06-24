<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getReportCenterSnapshot } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import type { ReportCenterSnapshot, ReportMetricItem } from '@/types/foundation'

type ReportRangeKey = 'all' | 'one_month' | 'three_months' | 'half_year' | 'one_year'
type ReportRiskTag = { label: string; type: 'danger' | 'warning' | 'info' }

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const snapshot = ref<ReportCenterSnapshot | null>(null)
const activeMetricKey = ref('')
const reportMonth = ref('2026-06')
const reportRange = ref<ReportRangeKey>('all')

const reportRangeOptions = [
  { label: '全部', value: 'all' },
  { label: '最近一个月', value: 'one_month' },
  { label: '三个月', value: 'three_months' },
  { label: '半年', value: 'half_year' },
  { label: '一年', value: 'one_year' }
] as const

const currentReportKey = computed(() => String(route.query.report || snapshot.value?.cards[0]?.key || ''))
const selectedReportKey = computed({
  get: () => currentReportKey.value,
  set: (key: string) => {
    router.push({ path: '/reports', query: { ...route.query, report: key } })
  }
})
const currentDetail = computed(() => snapshot.value?.details.find((item) => item.key === currentReportKey.value) || null)
const activeMetric = computed<ReportMetricItem | null>(() => {
  if (!currentDetail.value) return null
  return currentDetail.value.metrics.find((item) => item.key === activeMetricKey.value) || currentDetail.value.metrics[0] || null
})

watch(
  () => snapshot.value?.rangeLabel,
  (rangeLabel) => {
    if (!rangeLabel) return
    const matched = rangeLabel.match(/(\d{4})年(\d{1,2})月/)
    if (matched) {
      reportMonth.value = `${matched[1]}-${matched[2].padStart(2, '0')}`
    }
  },
  { immediate: true }
)

watch(
  currentDetail,
  (detail) => {
    if (!detail) { activeMetricKey.value = ''; return }
    const exists = detail.metrics.some((item) => item.key === activeMetricKey.value)
    activeMetricKey.value = exists ? activeMetricKey.value : detail.metrics[0]?.key || ''
  },
  { immediate: true }
)

async function loadData() {
  loading.value = true
  try { snapshot.value = await getReportCenterSnapshot() } finally { loading.value = false }
}

function openTarget(targetPath: string | undefined) {
  if (!targetPath) return
  router.push(targetPath)
}

function selectMetric(metricKey: string) {
  activeMetricKey.value = metricKey
}

function getAlertType(level: 'high' | 'medium' | 'low') {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'info'
}

function getAlertLabel(level: 'high' | 'medium' | 'low') {
  if (level === 'high') return '高风险'
  if (level === 'medium') return '关注'
  return '可跟进'
}

function getRiskTag(text?: string): ReportRiskTag | null {
  if (!text) return null
  if (text.includes('未通过') || text.includes('高风险')) return { label: '高风险', type: 'danger' }
  if (text.includes('停留') || text.includes('超时') || text.includes('逾期')) return { label: '超时', type: 'warning' }
  return { label: '待验证', type: 'info' }
}

const projectTypeFilter = ref<'all' | 'new_product' | 'new_model'>('all')
const ownerFilter = ref('all')
const durationSort = ref<'default' | 'duration_desc'>('duration_desc')

const stageOverviewRows = computed(() => {
  const detail = currentDetail.value
  if (!detail) return []
  const total = detail.metrics.reduce((sum, item) => sum + Number(item.value || 0), 0) || 1
  return detail.metrics.map((metric) => ({ key: metric.key, label: metric.label, count: Number(metric.value || 0), hint: metric.hint, percent: Math.round((Number(metric.value || 0) / total) * 100), active: activeMetric.value?.key === metric.key }))
})

const ownerOptions = computed(() => {
  const items = activeMetric.value?.detailItems || []
  return [{ label: '全部负责人', value: 'all' }, ...Array.from(new Set(items.map((item) => item.owner).filter(Boolean))).map((owner) => ({ label: owner, value: owner }))]
})

function parseDurationDays(text: string) { const m = text.match(/(\d+)/); return m ? Number(m[1]) : 0 }

const filteredReportItems = computed(() => {
  const items = [...(activeMetric.value?.detailItems || [])]
  const filtered = items.filter((item) => {
    const o = ownerFilter.value === 'all' || item.owner === ownerFilter.value
    const t = `${item.title} ${item.subtitle}`
    const typeMatched = projectTypeFilter.value === 'all' || (projectTypeFilter.value === 'new_product' && !t.includes('iPhone')) || (projectTypeFilter.value === 'new_model' && t.includes('iPhone'))
    return o && typeMatched
  })
  if (durationSort.value === 'duration_desc') filtered.sort((a, b) => parseDurationDays(b.durationText) - parseDurationDays(a.durationText))
  return filtered
})

function exportCurrentReport() { console.info('export report', { report: currentReportKey.value, month: reportMonth.value, filters: { projectType: projectTypeFilter.value, owner: ownerFilter.value } }) }

onMounted(loadData)
</script>

<template>
  <PageContainer
    class="report-center-page"
    title="报表中心"
    :description="'报表中心先选择报表类型和时间范围，再进入对应对象和节点。'"
  >
    <!-- 报表类型面包屑、时间范围、月份与导出统一放在标题卡片下方 -->
    <div class="report-control-bar">
      <nav class="report-breadcrumb" aria-label="报表类型">
        <button
          v-for="card in snapshot?.cards || []"
          :key="card.key"
          class="report-breadcrumb__item"
          :class="{ 'is-active': currentReportKey === card.key }"
          type="button"
          @click="selectedReportKey = card.key"
        >
          {{ card.title }}
        </button>
      </nav>
      <div class="report-control-actions">
        <el-select v-model="reportRange" class="report-range-select" placeholder="时间范围">
          <el-option
            v-for="option in reportRangeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="reportMonth" class="report-month-select" placeholder="选择月份">
          <el-option label="2026年6月" value="2026-06" />
          <el-option label="2026年5月" value="2026-05" />
          <el-option label="2026年4月" value="2026-04" />
        </el-select>
        <el-button type="primary" plain @click="exportCurrentReport">导出当前筛选</el-button>
      </div>
    </div>

    <section v-if="currentDetail" class="report-detail-flat" v-loading="loading">

      <section class="report-stage-overview">
        <button v-for="stage in stageOverviewRows" :key="stage.key" class="report-stage-row" :class="{ 'is-active': stage.active }" type="button" @click="selectMetric(stage.key)">
          <div class="report-stage-row__main"><strong>{{ stage.label }}</strong><span>{{ stage.count }} 个</span></div>
          <div class="report-stage-row__bar"><span :style="{ width: `${stage.percent}%` }" /></div>
          <p>{{ stage.hint }}</p>
        </button>
      </section>

      <div class="report-filter-line">
        <el-segmented v-model="projectTypeFilter" :options="[{ label: '全部', value: 'all' }, { label: '新产品', value: 'new_product' }, { label: '新型号', value: 'new_model' }]" />
        <el-select v-model="ownerFilter" placeholder="负责人" class="report-owner-select"><el-option v-for="owner in ownerOptions" :key="owner.value" :label="owner.label" :value="owner.value" /></el-select>
        <el-select v-model="durationSort" placeholder="排序" class="report-sort-select"><el-option label="默认排序" value="default" /><el-option label="停留时间从长到短" value="duration_desc" /></el-select>
      </div>

      <div class="report-main-grid" v-if="activeMetric">
        <section class="report-list-section">
          <div class="report-list-section__head"><h4>{{ activeMetric.detailTitle }}</h4><p>{{ activeMetric.detailSummary }}</p></div>
          <el-table :data="filteredReportItems" border stripe class="report-progress-table">
            <el-table-column label="项目" min-width="220"><template #default="{ row }"><div class="report-project-cell"><strong>{{ row.title }}</strong><span class="subtle-text">{{ row.subtitle }}</span></div></template></el-table-column>
            <el-table-column prop="currentNode" label="当前节点" min-width="140" />
            <el-table-column prop="owner" label="负责人" width="120" />
            <el-table-column prop="durationText" label="停留时间" width="120" />
            <el-table-column label="操作" width="170" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openTarget(row.targetPath)">查看进度</el-button><el-button link @click="openTarget(row.targetPath)">详情</el-button></template></el-table-column>
          </el-table>
        </section>

        <section class="report-structure-summary">
          <div class="report-section-head"><h4>进度结构摘要</h4><p>用于判断当前报表范围内的项目分布。</p></div>
          <div class="report-distribution-list">
            <div v-for="item in currentDetail.distribution" :key="item.label" class="report-distribution-row">
              <div class="report-distribution-row__head"><strong>{{ item.label }}</strong><span>{{ item.value }}</span></div>
              <div class="report-distribution-row__bar"><span class="report-distribution-row__fill" :style="{ width: `${Math.min(item.value, 100)}%` }" /></div>
              <span class="subtle-text">{{ item.hint }}</span>
            </div>
          </div>
        </section>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.report-center-page {
  min-height: calc(100vh - 40px);
  background: #fff;
}

.report-center-page :deep(.page-panel) {
  background: #fff;
  box-shadow: none;
}

.report-control-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: -4px 0 22px;
  padding: 12px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  flex-wrap: wrap;
  background: #fff;
}

.report-breadcrumb {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
}

.report-breadcrumb__item {
  padding: 6px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.16s, color 0.16s;
}

.report-breadcrumb__item:hover { background: #f1f5f9; color: #0f172a; }

.report-breadcrumb__item.is-active { background: #eff6ff; color: #1d4ed8; font-weight: 600; }

.report-month-select { width: 160px; }

.report-detail-flat { display: flex; flex-direction: column; gap: 24px; padding-top: 2px; background: #fff; }

.report-detail-flat__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 0 16px 14px;
  border-bottom: 1px solid #e5e7eb;
}

.report-detail-flat__header h3,
.report-detail-flat__header p,
.report-list-section__head h4,
.report-list-section__head p {
  margin: 0;
}

.report-detail-flat__header p,
.report-list-section__head p {
  margin-top: 6px;
  color: #64748b;
}

.report-metric-tabs {
  display: flex;
  gap: 0;
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.report-metric-tab {
  display: flex;
  flex: 1;
  min-width: 150px;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border: 0;
  border-right: 1px solid #e5e7eb;
  border-radius: 0;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s;
}
.report-metric-tab:last-child { border-right: 0; }
.report-metric-tab:hover { background: #f8fafc; }
.report-metric-tab.is-active { background: #fff; box-shadow: inset 0 -2px 0 #2563eb; }
.report-metric-tab span { font-size: 13px; color: #64748b; }
.report-metric-tab strong { font-size: 22px; color: #0f172a; }
.report-metric-tab em { font-size: 12px; font-style: normal; color: #94a3b8; }

.report-list-section { display: flex; flex-direction: column; gap: 14px; background: #fff; border-radius: 0; padding: 0; }

.report-list-section__head {
  padding: 0 16px;
}

.report-section-head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.report-section-head h4 { margin: 0; font-size: 15px; }
.report-section-head p { margin: 0; color: #64748b; font-size: 13px; }

.report-row-list { display: flex; flex-direction: column; gap: 8px; }

.report-row { display: flex; flex-direction: column; gap: 8px; width: 100%; padding: 14px 16px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; transition: border-color 0.16s; }
.report-row:hover { border-color: #93c5fd; }

.report-row__head, .report-row__title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.report-row__title { flex-direction: column; gap: 4px; }
.report-row__meta { display: flex; flex-wrap: wrap; gap: 16px; color: #64748b; font-size: 13px; }
.report-row__tags { display: flex; align-items: center; gap: 8px; }
.report-row__risk-text { color: #64748b; font-size: 12px; }
.report-control-actions { display: flex; flex-shrink: 0; align-items: center; gap: 10px; margin-left: auto; }
.report-range-select { width: 150px; }

.report-detail-flat__body { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr); gap: 20px; }

.report-alert-list { display: flex; flex-direction: column; gap: 8px; }

.report-alert-row { display: flex; flex-direction: column; gap: 6px; width: 100%; padding: 14px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; transition: border-color 0.16s; }
.report-alert-row:hover { border-color: #93c5fd; }
.report-alert-row__main { display: flex; align-items: center; justify-content: space-between; gap: 12px; }

.report-distribution-list { display: flex; flex-direction: column; gap: 10px; }

.report-distribution-row { display: flex; flex-direction: column; gap: 8px; padding: 14px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; }
.report-distribution-row__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.report-distribution-row__bar { height: 8px; border-radius: 999px; background: #eef2f7; overflow: hidden; }
.report-distribution-row__fill { display: block; height: 100%; border-radius: 999px; background: var(--plm-color-primary); }

.report-stage-overview { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin-bottom: 14px; }
.report-stage-row { padding: 12px 14px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; }
.report-stage-row.is-active { border-color: #2563eb; box-shadow: inset 0 0 0 1px #2563eb; }
.report-stage-row__main { display: flex; justify-content: space-between; gap: 12px; }
.report-stage-row__bar { height: 6px; margin: 10px 0 8px; overflow: hidden; border-radius: 999px; background: #eef2f7; }
.report-stage-row__bar span { display: block; height: 100%; border-radius: 999px; background: #2563eb; }
.report-stage-row p { margin: 0; color: #64748b; font-size: 12px; }
.report-filter-line { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.report-owner-select { width: 150px; }
.report-sort-select { width: 200px; }
.report-main-grid { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 16px; align-items: start; }
.report-project-cell { display: flex; flex-direction: column; gap: 2px; }
.report-structure-summary { position: sticky; top: 12px; padding: 14px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; }
@media (max-width: 960px) { .report-main-grid { grid-template-columns: 1fr; } .report-structure-summary { position: static; } }
</style>
