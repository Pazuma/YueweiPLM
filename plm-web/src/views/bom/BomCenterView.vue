<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getBomCenterSnapshot } from '@/api/modules/bom'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type { BomCenterSnapshot, BomTrendPoint, BomVersionRecord } from '@/types/bom'
import { formatAmount, formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const snapshot = ref<BomCenterSnapshot | null>(null)
const rows = computed(() => snapshot.value?.versions || [])

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '产品编码 / 名称 / BOM 编码' },
  {
    prop: 'bomType',
    label: 'BOM 类型',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: 'EBOM', value: 'EBOM' },
      { label: 'MBOM', value: 'MBOM' },
      { label: 'PACK', value: 'PACK' }
    ]
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '开发中', value: 'developing' },
      { label: '评审中', value: 'reviewing' },
      { label: '已发布', value: 'released' },
      { label: '已归档', value: 'archived' }
    ]
  }
]

const table = useTable(rows, ['productCode', 'productName', 'bomCode', 'owner', 'supplierRiskNote'], (row, filters) => {
  const bomType = String(filters.bomType || '')
  const status = String(filters.status || '')
  if (bomType && row.bomType !== bomType) return false
  if (status && row.status !== status) return false
  return true
})

const selectedTrend = computed(() => {
  const risk = String(route.query.risk || '')
  const points = snapshot.value?.trend || []
  if (!risk) return points
  return points.slice(-3)
})

async function loadSnapshot() {
  loading.value = true
  try {
    snapshot.value = await getBomCenterSnapshot()
    table.setQuery({
      keyword: String(route.query.keyword || ''),
      bomType: String(route.query.bomType || ''),
      status: String(route.query.status || '')
    })
  } finally {
    loading.value = false
  }
}

function openTarget(path: string) {
  router.push(path)
}

function barWidth(value: number, max: number) {
  if (!max) return '0%'
  return `${Math.max(18, Math.round((value / max) * 100))}%`
}

const maxTrendCost = computed(() => Math.max(...selectedTrend.value.map((item) => item.estimatedCost), 0))

onMounted(loadSnapshot)
</script>

<template>
  <PageContainer
    title="BOM 资料中心"
    description="按优化文档聚焦版本成本、差异对比、替代料风险与发布前锁版状态，只做 Product 关联资料视图。"
  >
    <template #actions>
      <el-button @click="router.push('/products')">产品管理</el-button>
      <el-button type="primary" @click="router.push('/sku-view')">SKU 视图</el-button>
    </template>

    <section class="metric-grid" v-loading="loading">
      <button
        v-for="metric in snapshot?.metrics || []"
        :key="metric.label"
        class="metric-card summary-button"
        type="button"
        @click="openTarget(metric.targetPath)"
      >
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <div class="metric-card__footer">
          <span class="metric-card__trend">{{ metric.hint }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </button>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', bomType: '', status: '' })"
    />

    <section class="split-grid">
      <article class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">BOM 版本列表</h3>
            <p class="page-panel-desc">展示版本成本、成本变化、累计开发成本与资料完整率。</p>
          </div>
          <el-tag effect="light">仅前端假数据</el-tag>
        </div>
        <el-table :data="table.pagedRows.value" border stripe v-loading="loading" @row-click="(row: BomVersionRecord) => openTarget(row.targetPath)">
          <el-table-column prop="productCode" label="产品编码" min-width="170" />
          <el-table-column label="产品 / BOM" min-width="240">
            <template #default="{ row }">
              <div class="cell-stack">
                <strong>{{ row.productName }}</strong>
                <span class="subtle-text">{{ row.bomCode }} / {{ row.bomVersion }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag :type="row.bomType === 'EBOM' ? 'primary' : row.bomType === 'MBOM' ? 'success' : 'warning'" effect="light">
                {{ row.bomType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <StatusTag :status="row.status" object-type="product" />
            </template>
          </el-table-column>
          <el-table-column label="版本成本" width="130">
            <template #default="{ row }">
              {{ formatAmount(row.estimatedCost) }}
            </template>
          </el-table-column>
          <el-table-column label="成本变化" width="130">
            <template #default="{ row }">
              <span :class="row.costDelta > 0 ? 'text-danger' : row.costDelta < 0 ? 'text-success' : ''">
                {{ row.costDelta > 0 ? '+' : '' }}{{ formatAmount(row.costDelta) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="累计开发成本" width="150">
            <template #default="{ row }">
              {{ formatAmount(row.cumulativeCost) }}
            </template>
          </el-table-column>
          <el-table-column label="完整率" min-width="170">
            <template #default="{ row }">
              <el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="8" />
            </template>
          </el-table-column>
          <el-table-column prop="supplierRiskNote" label="供应备注" min-width="180" />
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.updatedAt) }}
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="page-panel">
        <h3 class="section-title">版本成本对比</h3>
        <div class="page-stack">
          <button
            v-for="point in selectedTrend"
            :key="point.versionLabel"
            class="trend-item summary-button"
            type="button"
            @click="openTarget(point.targetPath)"
          >
            <div class="toolbar-row">
              <strong>{{ point.versionLabel }}</strong>
              <span>{{ formatAmount(point.estimatedCost) }}</span>
            </div>
            <div class="trend-bar">
              <span class="trend-bar__fill" :style="{ width: barWidth(point.estimatedCost, maxTrendCost) }" />
            </div>
            <div class="metric-card__footer">
              <span class="subtle-text">变化：{{ point.changeLabel }}</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </button>
        </div>

        <h3 class="section-title section-gap">风险提醒</h3>
        <button
          v-for="risk in snapshot?.risks || []"
          :key="risk.title"
          class="risk-card summary-button"
          type="button"
          @click="openTarget(risk.targetPath)"
        >
          <div class="toolbar-row">
            <strong>{{ risk.title }}</strong>
            <el-tag :type="risk.level === 'high' ? 'danger' : risk.level === 'medium' ? 'warning' : 'info'" effect="light">
              {{ risk.level === 'high' ? '高' : risk.level === 'medium' ? '中' : '低' }}
            </el-tag>
          </div>
          <p class="page-panel-desc">责任人：{{ risk.owner }}</p>
          <div class="metric-card__footer">
            <span class="subtle-text">{{ risk.action }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </button>
      </article>
    </section>
  </PageContainer>
</template>

<style scoped>
.summary-button {
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.summary-button:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.metric-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trend-item,
.risk-card {
  padding: 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.trend-bar {
  margin-top: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--plm-color-bg-page);
  overflow: hidden;
}

.trend-bar__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #2563eb, #22c55e);
}

.section-gap {
  margin-top: var(--plm-space-5);
}

.text-danger {
  color: var(--el-color-danger);
}

.text-success {
  color: var(--el-color-success);
}
</style>
