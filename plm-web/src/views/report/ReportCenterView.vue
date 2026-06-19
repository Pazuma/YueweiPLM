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

const currentReportKey = computed(() => String(route.query.report || snapshot.value?.cards[0]?.key || ''))
const currentDetail = computed(() => snapshot.value?.details.find((item) => item.key === currentReportKey.value) || null)
const activeMetric = computed<ReportMetricItem | null>(() => {
  if (!currentDetail.value) return null
  return currentDetail.value.metrics.find((item) => item.key === activeMetricKey.value) || currentDetail.value.metrics[0] || null
})

watch(
  currentDetail,
  (detail) => {
    if (!detail) {
      activeMetricKey.value = ''
      return
    }

    const exists = detail.metrics.some((item) => item.key === activeMetricKey.value)
    activeMetricKey.value = exists ? activeMetricKey.value : detail.metrics[0]?.key || ''
  },
  { immediate: true }
)

async function loadData() {
  loading.value = true
  try {
    snapshot.value = await getReportCenterSnapshot()
  } finally {
    loading.value = false
  }
}

function openReport(targetPath: string) {
  router.push(targetPath)
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
    title="报表中心"
    description="报表中心先回答“我要看什么问题”，再进入对应对象和节点，不再把首页做成拥挤的大屏。"
  >
    <template #actions>
      <el-select class="report-range" :model-value="snapshot?.rangeLabel || '2026年6月'" disabled>
        <el-option :label="snapshot?.rangeLabel || '2026年6月'" :value="snapshot?.rangeLabel || '2026年6月'" />
      </el-select>
    </template>

    <section class="page-panel" v-loading="loading">
      <div class="toolbar-row">
        <h3 class="section-title">查看报表类型</h3>
      </div>
      <el-select v-model="currentReportKey" class="report-type-select" placeholder="选择报表类型" @change="(key: string) => openReport(`/reports?report=${key}`)">
        <el-option v-for="card in snapshot?.cards || []" :key="card.key" :label="card.title" :value="card.key" />
      </el-select>
    </section>

    <section v-if="currentDetail" class="page-panel report-detail">
      <div class="toolbar-row report-detail__header">
        <div>
          <h3 class="section-title">{{ currentDetail.title }}</h3>
          <p class="page-panel-desc">{{ currentDetail.summary }}</p>
        </div>
        <el-button type="primary" plain>导出占位</el-button>
      </div>

      <div class="metric-grid report-metrics">
        <button
          v-for="metric in currentDetail.metrics"
          :key="metric.key"
          class="metric-card metric-card--action"
          :class="{ 'is-active': activeMetric?.key === metric.key }"
          type="button"
          @click="selectMetric(metric.key)"
        >
          <div class="metric-card__top">
            <p class="metric-card__label">{{ metric.label }}</p>
            <span v-if="activeMetric?.key === metric.key" class="metric-card__state">当前展开</span>
          </div>
          <p class="metric-card__value metric-card__value--small">{{ metric.value }}</p>
          <span class="metric-card__trend">{{ metric.hint }}</span>
        </button>
      </div>

      <section v-if="activeMetric" class="report-metric-detail">
        <div class="toolbar-row">
          <div>
            <h4 class="section-title">{{ activeMetric.detailTitle }}</h4>
            <p class="page-panel-desc">{{ activeMetric.detailSummary }}</p>
          </div>
        </div>

        <div class="metric-detail-list">
          <button
            v-for="item in activeMetric.detailItems"
            :key="item.itemId"
            class="metric-detail-card"
            type="button"
            @click="openTarget(item.targetPath)"
          >
            <div class="metric-detail-card__head">
              <div class="metric-detail-card__title">
                <strong>{{ item.title }}</strong>
                <span class="subtle-text">{{ item.subtitle }}</span>
              </div>
              <el-button link type="primary" @click.stop="openTarget(item.targetPath)">查看</el-button>
            </div>

            <div class="metric-detail-card__meta">
              <span>当前节点：{{ item.currentNode }}</span>
              <span>负责人：{{ item.owner }}</span>
              <span>停留：{{ item.durationText }}</span>
            </div>

            <p v-if="item.riskText" class="metric-detail-card__risk">{{ item.riskText }}</p>
          </button>
        </div>
      </section>

      <div class="split-grid report-detail__body">
        <section class="report-section">
          <div class="toolbar-row">
            <div>
              <h4 class="section-title">需要关注的异常</h4>
              <p class="page-panel-desc">先处理风险项，再回到对应产品或节点继续推进。</p>
            </div>
          </div>

          <div class="alert-list">
            <button
              v-for="alert in currentDetail.alerts"
              :key="`${alert.title}-${alert.subtitle}`"
              class="alert-card"
              type="button"
              @click="openTarget(alert.targetPath)"
            >
              <div class="toolbar-row">
                <strong>{{ alert.title }}</strong>
                <el-tag :type="getAlertType(alert.level)" effect="light">
                  {{ getAlertLabel(alert.level) }}
                </el-tag>
              </div>
              <p class="page-panel-desc">{{ alert.subtitle }}</p>
              <span class="subtle-text">责任人：{{ alert.owner }}</span>
            </button>
          </div>
        </section>

        <section class="report-section">
          <div class="toolbar-row">
            <div>
              <h4 class="section-title">分布与结构</h4>
              <p class="page-panel-desc">用简化分布帮助判断现状，不在这里展开复杂业务细节。</p>
            </div>
          </div>

          <div class="distribution-list">
            <div v-for="item in currentDetail.distribution" :key="item.label" class="distribution-row">
              <div class="distribution-row__head">
                <strong>{{ item.label }}</strong>
                <span>{{ item.value }}</span>
              </div>
              <div class="distribution-row__bar">
                <span class="distribution-row__fill" :style="{ width: `${Math.min(item.value, 100)}%` }" />
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
.report-range {
  width: 160px;
}

.report-type-select { width: 280px; }

.report-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.report-card {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.report-card:hover,
.alert-card:hover,
.metric-card--action:hover,
.metric-detail-card:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.report-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.report-card__questions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.report-card__questions p {
  margin: 0;
  color: var(--plm-color-text-secondary);
  line-height: 1.6;
}

.report-card__cta {
  color: var(--plm-color-primary);
  font-weight: 600;
}

.report-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.metric-card__value--small {
  font-size: 20px;
}

.metric-card--action {
  width: 100%;
  border: 1px solid var(--plm-color-border-light);
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.metric-card--action.is-active {
  border-color: #1d4ed8;
  background: rgba(37, 99, 235, 0.08);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.12);
}

.metric-card__state {
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
}

.report-metric-detail {
  padding: 16px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.metric-detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.metric-detail-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.metric-detail-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.metric-detail-card__title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-detail-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--plm-color-text-secondary);
  font-size: var(--plm-font-size-sm);
}

.metric-detail-card__risk {
  margin: 0;
  color: var(--el-color-danger);
  font-size: var(--plm-font-size-sm);
}

.report-detail__body {
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr);
}

.report-section {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
}

.alert-list,
.distribution-list {
  display: flex;
  min-height: 0;
  max-height: 420px;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
  padding-right: 4px;
}

.alert-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.distribution-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.distribution-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.distribution-row__bar {
  height: 8px;
  border-radius: 999px;
  background: #eef2f7;
  overflow: hidden;
}

.distribution-row__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--plm-color-primary);
}

@media (max-width: 1200px) {
  .report-card-grid,
  .report-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .report-card-grid,
  .report-metrics,
  .report-detail__body {
    grid-template-columns: 1fr;
  }

  .metric-detail-card__head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
