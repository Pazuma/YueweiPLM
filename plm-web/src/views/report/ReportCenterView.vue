<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getReportCenterSnapshot } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import type { ReportCenterSnapshot } from '@/types/foundation'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const snapshot = ref<ReportCenterSnapshot | null>(null)

const currentReportKey = computed(() => String(route.query.report || ''))
const currentDetail = computed(() => snapshot.value?.details.find((item) => item.key === currentReportKey.value) || null)

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

function openTarget(targetPath: string) {
  router.push(targetPath)
}

function getAlertType(level: 'high' | 'medium' | 'low') {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'info'
}

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="报表中心"
    description="报表不是数据堆叠页，而是问题发现入口。先选问题，再看异常，再下钻到对应产品或节点。"
  >
    <template #actions>
      <el-select class="report-range" :model-value="snapshot?.rangeLabel || '2026年6月'" disabled>
        <el-option :label="snapshot?.rangeLabel || '2026年6月'" :value="snapshot?.rangeLabel || '2026年6月'" />
      </el-select>
    </template>

    <section class="report-card-grid page-panel" v-loading="loading">
      <button
        v-for="card in snapshot?.cards || []"
        :key="card.key"
        class="report-card"
        type="button"
        @click="openReport(card.targetPath)"
      >
        <div class="report-card__header">
          <strong>{{ card.title }}</strong>
          <el-tag effect="light" :type="currentReportKey === card.key ? 'primary' : 'info'">
            {{ currentReportKey === card.key ? '当前' : '查看' }}
          </el-tag>
        </div>
        <div class="report-card__questions">
          <p v-for="line in card.questionLines" :key="line">{{ line }}</p>
        </div>
        <span class="report-card__cta">进入报表</span>
      </button>
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
        <div v-for="metric in currentDetail.metrics" :key="metric.label" class="metric-card">
          <p class="metric-card__label">{{ metric.label }}</p>
          <p class="metric-card__value metric-card__value--small">{{ metric.value }}</p>
          <span class="metric-card__trend">{{ metric.hint }}</span>
        </div>
      </div>

      <div class="split-grid report-detail__body">
        <section class="report-section">
          <div class="toolbar-row">
            <div>
              <h4 class="section-title">需要关注的异常</h4>
              <p class="page-panel-desc">优先处理风险项，再回到对象详情继续推进。</p>
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
                  {{ alert.level === 'high' ? '高风险' : alert.level === 'medium' ? '关注' : '可跟进' }}
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
              <p class="page-panel-desc">用简化分布展示当前结构，不把报表做成复杂大屏。</p>
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
.alert-card:hover {
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

.metric-card__value--small {
  font-size: 20px;
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
  .report-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
