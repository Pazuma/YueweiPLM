<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getReportCenterSnapshot } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import type { ReportCenterSnapshot, ReportMetricItem } from '@/types/foundation'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const snapshot = ref<ReportCenterSnapshot | null>(null)
const activeMetricKey = ref('')
const reportMonth = ref('2026-06')
const reportRange = ref('all')

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

onMounted(loadData)
</script>

<template>
  <PageContainer
    class="report-center-page"
    title="报表中心"
    :description="'报表中心先选择报表类型和时间范围，再进入对应对象和节点。'"
  >
    <!-- 报表类型面包屑与月份筛选 -->
    <div class="report-control-bar">
      <div class="report-breadcrumb">
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
      </div>
      <el-select v-model="reportMonth" class="report-month-select" placeholder="选择月份">
        <el-option label="2026年6月" value="2026-06" />
        <el-option label="2026年5月" value="2026-05" />
        <el-option label="2026年4月" value="2026-04" />
      </el-select>
      <div class="report-control-actions">
        <el-select v-model="reportRange" class="report-range-select" placeholder="时间范围">
          <el-option label="全部时间" value="all" />
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
          <el-option label="最近 90 天" value="90d" />
        </el-select>
        <el-button type="primary" plain>导出占位</el-button>
      </div>
    </div>

    <section v-if="currentDetail" class="report-detail-flat" v-loading="loading">
      <div class="report-detail-flat__header">
        <div>
          <h3>{{ currentDetail.title }}</h3>
          <p>{{ currentDetail.summary }}</p>
        </div>
      </div>

      <div class="report-metric-tabs">
        <button
          v-for="metric in currentDetail.metrics"
          :key="metric.key"
          class="report-metric-tab"
          :class="{ 'is-active': activeMetric?.key === metric.key }"
          type="button"
          @click="selectMetric(metric.key)"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <em>{{ metric.hint }}</em>
        </button>
      </div>

      <section v-if="activeMetric" class="report-list-section">
        <div class="report-list-section__head">
          <h4>{{ activeMetric.detailTitle }}</h4>
          <p>{{ activeMetric.detailSummary }}</p>
        </div>

        <div class="report-row-list">
          <button
            v-for="item in activeMetric.detailItems"
            :key="item.itemId"
            class="report-row"
            type="button"
            @click="openTarget(item.targetPath)"
          >
            <div class="report-row__head">
              <div class="report-row__title">
                <strong>{{ item.title }}</strong>
                <span class="subtle-text">{{ item.subtitle }}</span>
              </div>
              <el-button link type="primary" @click.stop="openTarget(item.targetPath)">查看</el-button>
            </div>
            <div class="report-row__meta">
              <span>当前节点：{{ item.currentNode }}</span>
              <span>负责人：{{ item.owner }}</span>
              <span>停留：{{ item.durationText }}</span>
            </div>
            <el-tag v-if="item.riskText" size="small" type="warning" effect="light">{{ item.riskText }}</el-tag>
          </button>
        </div>
      </section>

      <div class="report-detail-flat__body">
        <section>
          <div class="report-section-head">
            <h4>需要关注的异常</h4>
            <p>先处理风险项，再回到对应产品或节点继续推进。</p>
          </div>
          <div class="report-alert-list">
            <button
              v-for="alert in currentDetail.alerts"
              :key="`${alert.title}-${alert.subtitle}`"
              class="report-alert-row"
              type="button"
              @click="openTarget(alert.targetPath)"
            >
              <div class="report-alert-row__main">
                <strong>{{ alert.title }}</strong>
                <el-tag :type="getAlertType(alert.level)" effect="light">{{ getAlertLabel(alert.level) }}</el-tag>
              </div>
              <p>{{ alert.subtitle }}</p>
              <span class="subtle-text">责任人：{{ alert.owner }}</span>
            </button>
          </div>
        </section>

        <section>
          <div class="report-section-head">
            <h4>分布与结构</h4>
            <p>用简化分布帮助判断现状。</p>
          </div>
          <div class="report-distribution-list">
            <div v-for="item in currentDetail.distribution" :key="item.label" class="report-distribution-row">
              <div class="report-distribution-row__head">
                <strong>{{ item.label }}</strong>
                <span>{{ item.value }}</span>
              </div>
              <div class="report-distribution-row__bar">
                <span class="report-distribution-row__fill" :style="{ width: `${Math.min(item.value, 100)}%` }" />
              </div>
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
  margin-bottom: 20px;
  flex-wrap: wrap;
  background: #fff;
}

.report-breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
}

.report-breadcrumb__item {
  padding: 6px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.16s, color 0.16s;
}

.report-breadcrumb__item:hover { background: #f1f5f9; color: #334155; }

.report-breadcrumb__item.is-active { background: #eff6ff; color: #1d4ed8; font-weight: 600; }

.report-month-select { width: 180px; }

.report-detail-flat { display: flex; flex-direction: column; gap: 24px; background: #fff; }

.report-detail-flat__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }

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

.report-section-head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.report-section-head h4 { margin: 0; font-size: 15px; }
.report-section-head p { margin: 0; color: #64748b; font-size: 13px; }

.report-row-list { display: flex; flex-direction: column; gap: 8px; }

.report-row { display: flex; flex-direction: column; gap: 8px; width: 100%; padding: 14px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; transition: border-color 0.16s; }
.report-row:hover { border-color: #93c5fd; }

.report-row__head, .report-row__title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.report-row__title { flex-direction: column; gap: 4px; }
.report-row__meta { display: flex; flex-wrap: wrap; gap: 16px; color: #64748b; font-size: 13px; }
.report-control-actions { display: flex; align-items: center; gap: 12px; margin-left: auto; }
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

@media (max-width: 768px) { .report-detail-flat__body { grid-template-columns: 1fr; } }
</style>
