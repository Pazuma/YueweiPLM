<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProductList } from '@/api/modules/product'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type { ProductSummary } from '@/types/product'

type ProjectTab = 'in_progress' | 'completed' | 'abandoned'

interface AbandonedProject {
  productId: number
  productCode: string
  productName: string
  ownerUserName: string
  currentStage: string
  abandonReason: string
  abandonedAt: string
  reusableAssets: string
}

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const activeTab = ref<ProjectTab>('in_progress')
const rows = ref<ProductSummary[]>([])

const abandonedProjects = ref<AbandonedProject[]>([
  {
    productId: 901,
    productCode: 'PRD-SC29-ABN-001',
    productName: '超星 2.9 iPhone17 联名款',
    ownerUserName: '张敏',
    currentStage: '样品验证',
    abandonReason: '市场需求撤回，联名渠道取消。',
    abandonedAt: '2026-05-18',
    reusableAssets: '外观图纸、包装结构和TPU材料验证记录可复用。'
  },
  {
    productId: 902,
    productCode: 'PRD-LJ29-ABN-003',
    productName: '亮甲 2.9 镜面片试验版',
    ownerUserName: '刘浩',
    currentStage: '工艺验证',
    abandonReason: '镜面片良率不稳定，工艺成本过高。',
    abandonedAt: '2026-05-26',
    reusableAssets: '测试项模板、镜面片样品记录和供应商对比可复用。'
  }
])

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键字', type: 'input', placeholder: '产品编码 / 名称 / 系列 / 负责人' },
  {
    prop: 'productType',
    label: '类型',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '新产品线', value: 'product_line' },
      { label: '新型号线', value: 'model_variant' }
    ]
  }
]

const table = useTable(rows, ['productCode', 'productName', 'seriesName', 'ownerUserName'], (row, filters) => {
  const productType = String(filters.productType || '')
  if (productType && row.productType !== productType) return false
  return true
})

const visibleProjects = computed(() => {
  const filtered = table.filteredRows.value as unknown as ProductSummary[]
  if (activeTab.value === 'completed') {
    return filtered.filter((item) => item.status === 'released')
  }
  if (activeTab.value === 'in_progress') {
    return filtered.filter((item) => ['developing', 'reviewing'].includes(item.status))
  }
  return []
})

const selectedProductPreview = computed<ProductSummary | null>(() => {
  if (activeTab.value === 'abandoned') return null
  return visibleProjects.value[0] || null
})

const selectedAbandonedPreview = computed<AbandonedProject | null>(() => {
  if (activeTab.value !== 'abandoned') return null
  return abandonedProjects.value[0] || null
})

const metrics = computed(() => [
  {
    label: '进行中',
    value: rows.value.filter((item) => ['developing', 'reviewing'].includes(item.status)).length,
    tab: 'in_progress' as ProjectTab
  },
  {
    label: '已完成',
    value: rows.value.filter((item) => item.status === 'released').length,
    tab: 'completed' as ProjectTab
  },
  {
    label: '已放弃',
    value: abandonedProjects.value.length,
    tab: 'abandoned' as ProjectTab
  },
  {
    label: '关键关口',
    value: rows.value.filter((item) => item.status === 'reviewing').length,
    tab: 'in_progress' as ProjectTab
  }
])

const phaseSummary = computed(() => {
  const source = visibleProjects.value
  return [
    {
      label: '新产品线',
      value: source.filter((item) => item.productType === 'product_line').length,
      hint: '完整22步研发链路'
    },
    {
      label: '新型号线',
      value: source.filter((item) => item.productType === 'model_variant').length,
      hint: '差异扩展链路'
    },
    {
      label: '待冻结',
      value: source.filter((item) => !item.frozenFlag).length,
      hint: '图纸、BOM、资料待收口'
    }
  ]
})

function getProjectTypeLabel(row: ProductSummary) {
  return row.productType === 'product_line' ? '新产品线' : '新型号线'
}

function getNextGate(row: ProductSummary) {
  return row.gateSummary || (row.productType === 'product_line' ? '关注签样、小批量与MX关口。' : '关注差异验证、版本冻结与子版本发布。')
}

function syncTabFromRoute() {
  const routeTab = String(route.query.tab || 'in_progress') as ProjectTab
  activeTab.value = ['in_progress', 'completed', 'abandoned'].includes(routeTab) ? routeTab : 'in_progress'
}

async function loadData() {
  loading.value = true
  try {
    rows.value = await getProductList()
  } finally {
    loading.value = false
  }
}

function updateTab(tab: ProjectTab) {
  router.push({
    path: '/projects',
    query: {
      ...route.query,
      tab
    }
  })
}

function openProduct(productId: number) {
  router.push(`/products/${productId}`)
}

watch(
  () => route.query.tab,
  () => syncTabFromRoute(),
  { immediate: true }
)

watch(activeTab, (tab) => {
  if (String(route.query.tab || 'in_progress') !== tab) {
    updateTab(tab)
  }
})

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="项目管理"
    description="项目管理以Product作为承载对象，按进行中、已完成、已放弃三类组织，不新增独立Project根对象。"
  >
    <section class="metric-grid">
      <button
        v-for="metric in metrics"
        :key="metric.label"
        class="metric-card project-metric"
        type="button"
        @click="activeTab = metric.tab"
      >
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <span class="metric-card__trend">切换到对应项目视图</span>
      </button>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', productType: '' })"
    />

    <section class="page-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="进行中" name="in_progress" />
        <el-tab-pane label="已完成" name="completed" />
        <el-tab-pane label="已放弃" name="abandoned" />
      </el-tabs>

      <div class="metric-grid nested-metric-grid" v-if="activeTab !== 'abandoned'">
        <div v-for="item in phaseSummary" :key="item.label" class="metric-card nested-card">
          <p class="metric-card__label">{{ item.label }}</p>
          <p class="metric-card__value">{{ item.value }}</p>
          <span class="metric-card__trend">{{ item.hint }}</span>
        </div>
      </div>

      <div class="split-grid project-grid" v-loading="loading">
        <article class="page-panel project-list-panel">
          <template v-if="activeTab !== 'abandoned'">
            <el-table :data="visibleProjects" border stripe @row-click="(row: ProductSummary) => openProduct(row.productId)">
              <el-table-column prop="productCode" label="产品编码" min-width="170" />
              <el-table-column label="项目对象" min-width="240">
                <template #default="{ row }">
                  <div class="cell-stack">
                    <strong>{{ row.productName }}</strong>
                    <span class="subtle-text">{{ row.seriesName }} / {{ row.ownerUserName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="流程类型" width="130">
                <template #default="{ row }">
                  <el-tag :type="row.productType === 'product_line' ? 'primary' : 'success'" effect="light">
                    {{ getProjectTypeLabel(row) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="当前节点" min-width="180">
                <template #default="{ row }">
                  <div class="cell-stack">
                    <strong>第 {{ row.currentStepNo || '--' }} 步</strong>
                    <span class="subtle-text">{{ row.currentStage }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="下一关口" min-width="200">
                <template #default="{ row }">
                  <span class="subtle-text">{{ getNextGate(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <StatusTag :status="row.status" object-type="product" />
                </template>
              </el-table-column>
              <el-table-column label="完成度" min-width="160">
                <template #default="{ row }">
                  <el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="8" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="openProduct(row.productId)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template v-else>
            <el-table :data="abandonedProjects" border stripe>
              <el-table-column prop="productCode" label="产品编码" min-width="170" />
              <el-table-column prop="productName" label="项目对象" min-width="220" />
              <el-table-column prop="currentStage" label="停止阶段" min-width="140" />
              <el-table-column prop="ownerUserName" label="负责人" width="110" />
              <el-table-column prop="abandonedAt" label="放弃日期" width="130" />
              <el-table-column prop="abandonReason" label="放弃原因" min-width="220" />
            </el-table>
          </template>
        </article>

        <article class="page-panel">
          <h3 class="section-title">项目预览</h3>

          <template v-if="selectedAbandonedPreview">
            <div class="page-stack">
              <div class="info-block">
                <strong>{{ selectedAbandonedPreview.productName }}</strong>
                <p class="page-panel-desc">{{ selectedAbandonedPreview.productCode }}</p>
              </div>
              <div class="info-block">
                <p class="subtle-text">放弃原因</p>
                <strong>{{ selectedAbandonedPreview.abandonReason }}</strong>
              </div>
              <div class="info-block">
                <p class="subtle-text">可复用资产</p>
                <span>{{ selectedAbandonedPreview.reusableAssets }}</span>
              </div>
            </div>
          </template>

          <template v-else-if="selectedProductPreview">
            <div class="page-stack">
              <div class="info-block">
                <div class="toolbar-row">
                  <strong>{{ selectedProductPreview.productName }}</strong>
                  <StatusTag :status="selectedProductPreview.status" object-type="product" />
                </div>
                <p class="page-panel-desc">
                  {{ selectedProductPreview.productCode }} / 第 {{ selectedProductPreview.currentStepNo || '--' }} 步 / {{ selectedProductPreview.currentStage }}
                </p>
              </div>
              <div class="info-block">
                <p class="subtle-text">项目形态</p>
                <strong>{{ getProjectTypeLabel(selectedProductPreview) }}</strong>
              </div>
              <div class="info-block">
                <p class="subtle-text">流程提示</p>
                <span>
                  {{
                    selectedProductPreview.productType === 'product_line'
                      ? '新产品线从立项、开模、签样、工艺、红黄样、小批量到MX验证逐步推进。'
                      : '新型号线基于父产品继承基础资产，只处理差异图纸、模具分支、差异BOM和差异测试。'
                  }}
                </span>
              </div>
              <div class="info-block">
                <p class="subtle-text">下一步动作</p>
                <span>{{ selectedProductPreview.nextAction || '进入详情继续推进当前节点。' }}</span>
              </div>
              <el-button type="primary" @click="openProduct(selectedProductPreview.productId)">进入项目详情</el-button>
            </div>
          </template>
        </article>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.project-metric {
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.project-metric:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.nested-metric-grid {
  margin-bottom: 16px;
}

.nested-card {
  background: rgba(248, 250, 252, 0.9);
}

.project-grid {
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.85fr);
}

.project-list-panel {
  padding: 0;
  border: 0;
  box-shadow: none;
  background: transparent;
}

.cell-stack,
.info-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
