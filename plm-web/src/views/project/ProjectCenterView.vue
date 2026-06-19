<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProductList } from '@/api/modules/product'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type { ProductSummary } from '@/types/product'

type ProjectTab = 'in_progress' | 'completed' | 'archived' | 'abandoned'
type ProjectFlowFilter = 'all' | 'product_line' | 'model_variant'
type ArchiveView = 'overview' | 'product' | 'sku'

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

interface ProjectTimelineNode {
  nodeKey: string
  title: string
  phase: string
  gate?: boolean
  hint: string
  childStepNos: number[]
  childNodes: string[]
  count: number
}

const newProductLineTimeline: Omit<ProjectTimelineNode, 'count'>[] = [
  { nodeKey: 'initiation', title: '立项确认', phase: '立项阶段', gate: true, hint: '确认需求、成本、周期、投入边界。', childStepNos: [1, 2], childNodes: ['产品立项', '确认立项'] },
  { nodeKey: 'design', title: '设计确认', phase: '设计验证阶段', hint: '收敛图纸、外观、结构和供应商可制造性。', childStepNos: [3, 4], childNodes: ['画图查看', '供应商确认外观图纸'] },
  { nodeKey: 'tooling', title: '开模试模', phase: '开模阶段', gate: true, hint: '完成开模申请、模具制作和试模验证。', childStepNos: [5, 6, 7], childNodes: ['申请开模', '制作模具', '测试模具'] },
  { nodeKey: 'sampling-process', title: '样品与工艺', phase: '样品 / 工艺定型阶段', gate: true, hint: '签样、工艺、组件、红样、黄样和生产资料整理。', childStepNos: [8, 9, 10, 11, 12, 13, 14, 15, 16], childNodes: ['签样确认', '加工艺', '敲定工序', '确认组件', '确认组件成品', '最终外观确认样', '红样测试', '整理生产资料', '黄样'] },
  { nodeKey: 'pilot-mx', title: '小批与 MX 验证', phase: '市场验证阶段', gate: true, hint: '验证产线、物流、MX 端承接和小批量跑通。', childStepNos: [17, 18, 19, 20, 21], childNodes: ['小批量测试', '运模', 'MX 验收', '测试验证', 'MX 小批量测试'] },
  { nodeKey: 'launch', title: '投产决策', phase: '投产发布阶段', gate: true, hint: '根据验证结果决定投产或回退。', childStepNos: [22], childNodes: ['投产决策'] }
]

const modelVariantTimeline: Omit<ProjectTimelineNode, 'count'>[] = [
  { nodeKey: 'ext-confirm', title: '扩展确认', phase: '扩展确认阶段', gate: true, hint: '确认父产品和子版本入口。', childStepNos: [1, 2], childNodes: ['新型号需求确认', 'Product 子版本建立'] },
  { nodeKey: 'diff-design', title: '差异设计', phase: '差异调整阶段', hint: '聚焦孔位、尺寸、颜色、包装等差异。', childStepNos: [3], childNodes: ['图纸与外观差异确认'] },
  { nodeKey: 'mold-branch', title: '模具判断', phase: '模具决策阶段', gate: true, hint: '体现改模、新开模、无需模具变更的分支。', childStepNos: [4, 5, 6], childNodes: ['开模/改模申请', '制作/修改模具', '测试模具'] },
  { nodeKey: 'diff-verify', title: '差异验证', phase: '验证阶段', gate: true, hint: '只验证变化部分，不重复完整新产品验证。', childStepNos: [7, 8, 9, 10], childNodes: ['差异组件/工艺确认', '样品确认', '差异测试验证', '生产资料整理'] },
  { nodeKey: 'variant-pilot', title: '小批与 MX 验证', phase: '市场验证阶段', gate: true, hint: '确认新型号在产线和 MX 端可稳定承接。', childStepNos: [11, 12, 13, 14], childNodes: ['小批量测试', '运模', 'MX 验收', '墨西哥小批量验证'] },
  { nodeKey: 'freeze-release', title: '冻结发布', phase: '投产发布阶段', gate: true, hint: '作为父产品线下子版本发布。', childStepNos: [15, 16], childNodes: ['版本冻结', '正式发布'] }
]

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const activeTab = ref<ProjectTab>('in_progress')
const selectedTimelineNode = ref<ProjectTimelineNode | null>(null)
const overviewVisible = ref(false)
const overviewProject = ref<ProductSummary | null>(null)
const rows = ref<ProductSummary[]>([])

const abandonedProjects = ref<AbandonedProject[]>([
  { productId: 901, productCode: 'PRD-SC29-ABN-001', productName: '超星 2.9 iPhone17 联名款', ownerUserName: '张敏', currentStage: '样品验证', abandonReason: '市场需求撤回，联名渠道取消。', abandonedAt: '2026-05-18', reusableAssets: '外观图纸、包装结构和TPU材料验证记录可复用。' },
  { productId: 902, productCode: 'PRD-LJ29-ABN-003', productName: '亮甲 2.9 镜面片试验版', ownerUserName: '刘浩', currentStage: '工艺验证', abandonReason: '镜面片良率不稳定，工艺成本过高。', abandonedAt: '2026-05-26', reusableAssets: '测试项模板、镜面片样品记录和供应商对比可复用。' }
])

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键字', type: 'input', placeholder: '产品编码 / 名称 / 系列 / 负责人' },
  { prop: 'productType', label: '类型', type: 'select', options: [{ label: '全部', value: '' }, { label: '新产品线', value: 'product_line' }, { label: '新型号线', value: 'model_variant' }] }
]

const table = useTable(rows, ['productCode', 'productName', 'seriesName', 'ownerUserName'], (row, filters) => {
  const productType = String(filters.productType || '')
  if (productType && row.productType !== productType) return false
  return true
})

const activeFlow = computed<ProjectFlowFilter>(() => {
  const pt = String(table.query.productType || '')
  if (pt === 'product_line') return 'product_line'
  if (pt === 'model_variant') return 'model_variant'
  return 'all'
})

const runningProjects = computed(() =>
  (table.filteredRows.value as unknown as ProductSummary[]).filter((item) => ['developing', 'reviewing'].includes(item.status))
)

const flowFilteredProjects = computed(() => {
  if (activeFlow.value === 'all') return runningProjects.value
  return runningProjects.value.filter((item) => item.productType === activeFlow.value)
})

const flowTimeline = computed<ProjectTimelineNode[]>(() => {
  const source = activeFlow.value === 'product_line' ? newProductLineTimeline : modelVariantTimeline
  return source.map((node) => ({
    ...node,
    count: flowFilteredProjects.value.filter((p) => node.childStepNos.includes(p.currentStepNo || -1)).length
  }))
})

const timelineProjects = computed(() => {
  if (activeFlow.value === 'all') return flowFilteredProjects.value
  if (!selectedTimelineNode.value) return flowFilteredProjects.value
  return flowFilteredProjects.value.filter((item) => selectedTimelineNode.value!.childStepNos.includes(item.currentStepNo || -1))
})

const archiveView = computed<ArchiveView>(() => {
  const v = String(route.query.archiveView || 'overview')
  return ['overview', 'product', 'sku'].includes(v) ? (v as ArchiveView) : 'overview'
})

const archivedProjects = computed(() =>
  rows.value.filter((item) => ['released', 'archived'].includes(item.status))
)

const archivedProductRows = computed(() =>
  archivedProjects.value.filter((item) => item.productType === 'product_line')
)

const archivedSkuRows = computed(() =>
  archivedProjects.value.filter((item) => item.productType === 'model_variant' || Boolean(item.parentProductId))
)

const archiveSummary = computed(() => ({
  total: archivedProjects.value.length,
  products: archivedProductRows.value.length,
  skus: archivedSkuRows.value.length
}))

const importVisible = ref(false)
const importType = ref<'product' | 'sku'>('product')

function openArchiveImport(type: 'product' | 'sku') {
  importType.value = type
  importVisible.value = true
}

// ---- archived SKU management (two-layer: product cards → SKU list → detail dialog) ----
type SkuPageStage = 'product-home' | 'sku-list'
const skuPageStage = ref<SkuPageStage>('product-home')
const skuActiveProductId = ref<number | null>(null)
const skuKeyword = ref('')
const skuDetailVisible = ref(false)
const skuDetailLoading = ref(false)
const skuDetailSku = ref<ProductSummary | null>(null)
const skuDetailBomVersion = ref('')

const skuProductCards = computed(() =>
  archivedSkuRows.value
    .filter((item) => item.parentProductId)
    .reduce<ProductSummary[]>((acc, item) => {
      const parent = rows.value.find((r) => r.productId === item.parentProductId)
      if (parent && !acc.find((a) => a.productId === parent.productId)) acc.push(parent)
      return acc
    }, [])
)

const skuActiveProduct = computed(() =>
  skuProductCards.value.find((item) => item.productId === skuActiveProductId.value) || null
)

const skuCurrentSkuRows = computed(() => {
  const search = skuKeyword.value.trim().toLowerCase()
  if (!skuActiveProductId.value) return []
  return archivedSkuRows.value.filter((item) => {
    const belongsTo = item.parentProductId === skuActiveProductId.value || item.seriesName === skuActiveProduct.value?.seriesName
    const kw =
      !search ||
      item.productCode.toLowerCase().includes(search) ||
      item.productName.toLowerCase().includes(search) ||
      item.model.toLowerCase().includes(search) ||
      item.color.toLowerCase().includes(search)
    return belongsTo && kw
  })
})

function getSkuCountForProduct(productId: number) {
  return archivedSkuRows.value.filter((item) => item.parentProductId === productId).length
}

function skuOpenSkuList(productId: number) {
  skuActiveProductId.value = productId
  skuKeyword.value = ''
  skuPageStage.value = 'sku-list'
}

function skuBackToProductHome() {
  skuActiveProductId.value = null
  skuKeyword.value = ''
  skuPageStage.value = 'product-home'
}

function skuOpenDetail(row: ProductSummary) {
  skuDetailSku.value = row
  skuDetailVisible.value = true
}

const metrics = computed(() => [
  { label: '进行中', value: rows.value.filter((item) => ['developing', 'reviewing'].includes(item.status)).length, tab: 'in_progress' as ProjectTab },
  { label: '已归档', value: rows.value.filter((item) => item.status === 'released').length, tab: 'archived' as ProjectTab },
  { label: '已放弃', value: abandonedProjects.value.length, tab: 'abandoned' as ProjectTab },
  { label: '关键关口', value: rows.value.filter((item) => item.status === 'reviewing').length, tab: 'in_progress' as ProjectTab }
])

function getProjectTypeLabel(row: ProductSummary) {
  return row.productType === 'product_line' ? '新产品线' : '新型号线'
}

function getMoldActionLabel(row: ProductSummary) {
  if (row.productType === 'product_line') return '完整开模链路'
  if (row.moldAction === 'modify') return '改模'
  if (row.moldAction === 'new') return '新开模'
  if (row.moldAction === 'none') return '无需模具变更'
  return '待判断'
}

function getNextGate(row: ProductSummary) {
  return row.gateSummary || (row.productType === 'product_line' ? '关注签样、小批量与MX关口。' : '关注差异验证、版本冻结与子版本发布。')
}

function getRecentUpdate(row: ProductSummary) {
  return row.releasedAt ? row.releasedAt.slice(0, 10) : '--'
}

function selectTimelineNode(node: ProjectTimelineNode) {
  selectedTimelineNode.value = selectedTimelineNode.value?.nodeKey === node.nodeKey ? null : node
}

function openProjectOverview(row: ProductSummary) {
  overviewProject.value = row
  overviewVisible.value = true
}

function openProduct(productId: number) {
  router.push(`/products/${productId}`)
}

function syncTabFromRoute() {
  const routeTab = String(route.query.tab || 'in_progress') as ProjectTab
  activeTab.value = ['in_progress', 'archived', 'abandoned'].includes(routeTab) ? routeTab : 'in_progress'
}

function updateTab(tab: ProjectTab) {
  router.push({ path: '/projects', query: { ...route.query, tab } })
}

async function loadData() {
  loading.value = true
  try { rows.value = await getProductList() } finally { loading.value = false }
}

watch(() => table.query.productType, () => { selectedTimelineNode.value = null })
watch(() => route.query.tab, () => syncTabFromRoute(), { immediate: true })
watch(activeTab, (tab) => { if (String(route.query.tab || 'in_progress') !== tab) updateTab(tab) })
onMounted(loadData)
</script>

<template>
  <PageContainer title="项目管理" description="项目管理以Product作为承载对象，按进行中、已归档、已放弃三类组织。">
    <section class="metric-grid">
      <button v-for="metric in metrics" :key="metric.label" class="metric-card project-metric" type="button" @click="activeTab = metric.tab">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <span class="metric-card__trend">切换到对应视图</span>
      </button>
    </section>

    <SearchBar v-if="activeTab === 'in_progress'" :fields="searchFields" :model-value="table.query" @search="table.setQuery" @reset="table.resetQuery({ keyword: '', productType: '' })" />

    <!-- in_progress -->
    <template v-if="activeTab === 'in_progress'">
      <div v-if="activeFlow === 'all'" class="list-context-bar">
        <strong>全部进行中项目</strong>
        <span class="subtle-text">当前展示所有正在推进的新产品线和新型号线项目，选择具体类型以查看时间轴。</span>
      </div>

      <template v-if="activeFlow !== 'all'">
        <section class="project-timeline-panel">
          <div class="toolbar-row">
            <h3 class="section-title">{{ activeFlow === 'product_line' ? '新产品线' : '新型号线' }} 重要节点</h3>
          </div>
          <div class="timeline-row">
            <button
              v-for="node in flowTimeline"
              :key="node.nodeKey"
              class="timeline-node"
              :class="{ 'is-selected': selectedTimelineNode?.nodeKey === node.nodeKey, 'is-gate': node.gate, 'is-empty': node.count === 0 }"
              type="button"
              @click="selectTimelineNode(node)"
            >
              <strong>{{ node.title }}</strong>
              <span class="timeline-node__hint">{{ node.hint }}</span>
              <span class="timeline-node__children">包含：{{ node.childNodes.join('、') }}</span>
              <span class="timeline-node__count">{{ node.count }} 个项目</span>
            </button>
          </div>
        </section>

        <div class="selected-node-bar" v-if="selectedTimelineNode">
          <strong>{{ activeFlow === 'product_line' ? '新产品线' : '新型号线' }} / {{ selectedTimelineNode.title }} / 第 {{ selectedTimelineNode.childStepNos.join('、') }} 步</strong>
          <span class="subtle-text">当前在这个环节的项目：{{ timelineProjects.length }} 个</span>
        </div>
      </template>

      <section class="project-list-shell" v-loading="loading">
        <el-table v-if="timelineProjects.length" :data="timelineProjects" border stripe>
          <el-table-column label="概览" width="88" fixed="left">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openProjectOverview(row)">概览</el-button>
            </template>
          </el-table-column>
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
              <el-tag :type="row.productType === 'product_line' ? 'primary' : 'success'" effect="light">{{ getProjectTypeLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="模具策略" width="140">
            <template #default="{ row }"><span class="subtle-text">{{ getMoldActionLabel(row) }}</span></template>
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
            <template #default="{ row }"><span class="subtle-text">{{ getNextGate(row) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }"><StatusTag :status="row.status" object-type="product" /></template>
          </el-table-column>
          <el-table-column label="完成度" min-width="160">
            <template #default="{ row }"><el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="8" /></template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无匹配的项目" />
      </section>
    </template>

    <!-- archived -->
    <template v-else-if="activeTab === 'archived'">
      <!-- overview -->
      <template v-if="archiveView === 'overview'">
        <div class="list-context-bar">
          <strong>已归档汇总</strong>
          <span class="subtle-text">查看归档数据总览，通过子节点进入产品或 SKU 明细。</span>
        </div>
        <section class="metric-grid">
          <div class="metric-card"><p class="metric-card__label">已归档总数</p><p class="metric-card__value">{{ archiveSummary.total }}</p></div>
          <div class="metric-card"><p class="metric-card__label">已归档产品</p><p class="metric-card__value">{{ archiveSummary.products }}</p></div>
          <div class="metric-card"><p class="metric-card__label">已归档 SKU</p><p class="metric-card__value">{{ archiveSummary.skus }}</p></div>
          <div class="metric-card"><p class="metric-card__label">数据质量</p><p class="metric-card__value">{{ archiveSummary.total > 0 ? Math.round(archiveSummary.products / archiveSummary.total * 100) : 0 }}%</p></div>
        </section>
      </template>

      <!-- product management -->
      <template v-else-if="archiveView === 'product'">
        <div class="list-context-bar">
          <strong>产品管理</strong>
          <span class="subtle-text">只展示已归档的新产品线产品。</span>
          <el-button type="primary" @click="openArchiveImport('product')">导入数据</el-button>
        </div>
        <section class="project-list-shell" v-loading="loading">
          <el-table :data="archivedProductRows" border stripe>
            <el-table-column prop="productCode" label="产品编码" min-width="180" />
            <el-table-column prop="productName" label="产品名称" min-width="220" />
            <el-table-column prop="seriesName" label="系列" width="140" />
            <el-table-column prop="ownerUserName" label="负责人" width="110" />
            <el-table-column prop="versionNo" label="版本" width="100" />
            <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" object-type="product" /></template></el-table-column>
            <el-table-column prop="activeBomVersion" label="BOM 主版本" width="130" />
            <el-table-column label="资料完整率" min-width="160"><template #default="{ row }"><el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="8" /></template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openProduct(row.productId)">详情</el-button></template></el-table-column>
          </el-table>
        </section>
      </template>

      <!-- sku management: product cards → SKU list -->
      <template v-else>
        <!-- product home -->
        <section v-if="skuPageStage === 'product-home'" class="page-panel sku-product-home">
          <div class="toolbar-row sku-product-home__head">
            <div>
              <h3 class="section-title">SKU管理</h3>
              <p class="page-panel-desc">先按产品卡片定位，再进入该产品下的 SKU 列表。</p>
            </div>
            <el-button type="primary" @click="openArchiveImport('sku')">导入数据</el-button>
          </div>
          <div class="sku-product-grid" v-loading="loading">
            <button
              v-for="product in skuProductCards"
              :key="product.productId"
              class="sku-product-card"
              type="button"
              @click="skuOpenSkuList(product.productId)"
            >
              <div class="sku-product-card__image"><span>{{ product.seriesName }}</span></div>
              <div class="toolbar-row"><strong>{{ product.productName }}</strong><StatusTag :status="product.status" object-type="product" /></div>
              <p class="subtle-text">{{ product.productCode }}</p>
              <p class="sku-product-card__series">{{ product.seriesName }}</p>
              <div class="sku-product-card__meta">
                <span>{{ getSkuCountForProduct(product.productId) }} 个 SKU</span>
                <span class="subtle-text">{{ getRecentUpdate(product) }}</span>
              </div>
            </button>
          </div>
        </section>

        <!-- sku list -->
        <section v-else class="page-panel sku-list-panel" v-loading="loading">
          <div class="toolbar-row sku-list-panel__head">
            <div>
              <el-button link type="primary" @click="skuBackToProductHome">← 返回产品卡片</el-button>
              <h3 class="section-title">{{ skuActiveProduct?.productName }} / SKU 列表</h3>
              <p class="page-panel-desc">查看当前产品下的 SKU 信息、示例图与版本状态。</p>
            </div>
            <div class="sku-list-panel__actions">
              <el-input v-model="skuKeyword" clearable class="sku-list-panel__search" placeholder="搜索编码 / 名称 / 型号 / 颜色" />
              <el-button type="primary" @click="openArchiveImport('sku')">导入数据</el-button>
            </div>
          </div>
          <el-table :data="skuCurrentSkuRows" border stripe>
            <el-table-column label="示例图" min-width="130">
              <template #default="{ row }">
                <div class="sku-image-cell"><div class="sku-image-cell__thumb"><span>{{ row.model }}</span></div></div>
              </template>
            </el-table-column>
            <el-table-column prop="productCode" label="SKU 编码" min-width="180" />
            <el-table-column prop="productName" label="SKU名称" min-width="220" />
            <el-table-column prop="model" label="型号" width="140" />
            <el-table-column label="单位" width="80"><template #default><span class="subtle-text">pcs</span></template></el-table-column>
            <el-table-column label="项目来源" min-width="140"><template #default><span class="subtle-text">历史归档</span></template></el-table-column>
            <el-table-column prop="color" label="颜色" width="120" />
            <el-table-column prop="versionNo" label="版本" width="100" />
            <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" object-type="product" /></template></el-table-column>
            <el-table-column label="最近更新时间" width="130"><template #default="{ row }">{{ getRecentUpdate(row) }}</template></el-table-column>
            <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="skuOpenDetail(row)">详情</el-button></template></el-table-column>
          </el-table>
        </section>

        <!-- sku detail dialog -->
        <el-dialog v-model="skuDetailVisible" title="SKU 详情" width="960px" destroy-on-close>
          <template v-if="skuDetailSku">
            <div class="sku-detail-dialog">
              <section class="sku-detail-section">
                <h4 class="section-title">基础信息</h4>
                <div class="sku-detail-grid">
                  <div class="info-card"><span class="subtle-text">SKU 编码</span><strong>{{ skuDetailSku.productCode }}</strong></div>
                  <div class="info-card"><span class="subtle-text">SKU名称</span><strong>{{ skuDetailSku.productName }}</strong></div>
                  <div class="info-card"><span class="subtle-text">产品线</span><strong>{{ skuDetailSku.seriesName }}</strong></div>
                  <div class="info-card"><span class="subtle-text">型号</span><strong>{{ skuDetailSku.model }}</strong></div>
                  <div class="info-card"><span class="subtle-text">颜色</span><strong>{{ skuDetailSku.color }}</strong></div>
                  <div class="info-card"><span class="subtle-text">版本</span><strong>{{ skuDetailSku.versionNo }}</strong></div>
                  <div class="info-card"><span class="subtle-text">状态</span><StatusTag :status="skuDetailSku.status" object-type="product" /></div>
                  <div class="info-card"><span class="subtle-text">当前阶段</span><strong>{{ skuDetailSku.currentStage }}</strong></div>
                </div>
              </section>
              <section class="sku-detail-section">
                <h4 class="section-title">流程提示</h4>
                <span>{{ skuDetailSku.productType === 'product_line' ? '新产品线从立项、开模、签样、工艺、红黄样、小批量到MX验证逐步推进。' : '新型号线基于父产品继承基础资产，只处理差异图纸、模具分支、差异BOM和差异测试。' }}</span>
              </section>
            </div>
          </template>
          <template #footer>
            <el-button @click="skuDetailVisible = false">关闭</el-button>
            <el-button type="primary" @click="openProduct(skuDetailSku!.productId)">进入详情</el-button>
          </template>
        </el-dialog>
      </template>
    </template>

    <!-- abandoned -->
    <template v-else>
      <div class="list-context-bar">
        <strong>已放弃项目</strong>
        <span class="subtle-text">已放弃的项目数据永久保留可查看。</span>
      </div>
      <section class="project-list-shell" v-loading="loading">
        <el-table :data="abandonedProjects" border stripe>
          <el-table-column prop="productCode" label="产品编码" min-width="170" />
          <el-table-column prop="productName" label="项目对象" min-width="220" />
          <el-table-column prop="currentStage" label="停止阶段" min-width="140" />
          <el-table-column prop="ownerUserName" label="负责人" width="110" />
          <el-table-column prop="abandonedAt" label="放弃日期" width="130" />
          <el-table-column prop="abandonReason" label="放弃原因" min-width="220" />
        </el-table>
      </section>
    </template>

    <!-- overview dialog -->
    <el-dialog v-model="overviewVisible" title="项目概览" width="640px" destroy-on-close>
      <template v-if="overviewProject">
        <div class="project-overview-dialog">
          <div class="project-overview-dialog__title">
            <strong>{{ overviewProject.productName }}</strong>
            <StatusTag :status="overviewProject.status" object-type="product" />
          </div>
          <dl class="project-overview-grid">
            <div><dt>产品编码</dt><dd>{{ overviewProject.productCode }}</dd></div>
            <div><dt>项目形态</dt><dd>{{ getProjectTypeLabel(overviewProject) }}</dd></div>
            <div><dt>当前节点</dt><dd>第 {{ overviewProject.currentStepNo || '--' }} 步 / {{ overviewProject.currentStage }}</dd></div>
            <div><dt>负责人</dt><dd>{{ overviewProject.ownerUserName }}</dd></div>
            <div><dt>版本</dt><dd>{{ overviewProject.versionNo }}</dd></div>
            <div><dt>完成度</dt><dd>{{ Math.round(overviewProject.completionRate * 100) }}%</dd></div>
          </dl>
          <section class="project-overview-block">
            <p class="subtle-text">流程提示</p>
            <strong>{{ getNextGate(overviewProject) }}</strong>
          </section>
          <section class="project-overview-block">
            <p class="subtle-text">下一步动作</p>
            <span>{{ overviewProject.nextAction || '进入详情继续推进当前节点。' }}</span>
          </section>
        </div>
      </template>
      <template #footer>
        <el-button @click="overviewVisible = false">关闭</el-button>
        <el-button v-if="overviewProject" type="primary" @click="openProduct(overviewProject.productId)">进入详情</el-button>
      </template>
    </el-dialog>

    <!-- import dialog -->
    <el-dialog v-model="importVisible" :title="importType === 'product' ? '导入产品数据' : '导入SKU数据'" width="640px">
      <el-alert type="info" show-icon :closable="false" title="用于导入历史归档数据，导入后仍按 Product 对象保存和追溯。" />
      <el-upload drag action="" :auto-upload="false" accept=".xlsx,.xls,.csv" style="margin-top:16px">
        <el-icon><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或点击选择文件</div>
        <template #tip><div class="el-upload__tip">支持 xlsx / xls / csv</div></template>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="importVisible = false">开始导入</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.project-metric { width: 100%; text-align: left; cursor: pointer; transition: border-color 0.16s, box-shadow 0.16s, transform 0.16s; }
.project-metric:hover { border-color: var(--plm-color-primary); box-shadow: var(--plm-shadow-md); transform: translateY(-1px); }

.list-context-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; border-radius: 8px; background: rgba(37,99,235,0.05); margin-bottom: 14px; }

.project-timeline-panel { margin: 16px 0; padding: 16px; border: 1px solid rgba(37,99,235,0.18); border-radius: 8px; background: #fff; }

.timeline-row { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }

.timeline-node { display: flex; flex-direction: column; gap: 6px; padding: 12px; border: 1px solid rgba(148,163,184,0.24); border-radius: var(--plm-radius-base); background: #fff; text-align: left; cursor: pointer; transition: border-color 0.16s, box-shadow 0.16s; }
.timeline-node:hover { border-color: #2563eb; box-shadow: 0 4px 12px rgba(37,99,235,0.1); }
.timeline-node.is-selected { border-color: #2563eb; background: rgba(37,99,235,0.05); box-shadow: 0 4px 12px rgba(37,99,235,0.12); }
.timeline-node.is-gate { border-left: 3px solid #2563eb; }
.timeline-node.is-empty { opacity: 0.58; }
.timeline-node__hint { font-size: 12px; color: #64748b; }
.timeline-node__children { font-size: 11px; color: #94a3b8; }
.timeline-node__count { font-size: 13px; font-weight: 600; color: #2563eb; }

.selected-node-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; padding: 10px 14px; border-radius: 8px; background: rgba(37,99,235,0.06); }

.project-list-shell { margin-top: 14px; }

.cell-stack { display: flex; flex-direction: column; gap: 6px; }

.sku-product-home__head { margin-bottom: 14px; }
.sku-product-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.sku-product-card { display: flex; min-height: 220px; flex-direction: column; gap: 10px; padding: 16px; border: 1px solid var(--plm-color-border-light); border-radius: var(--plm-radius-base); background: #fff; text-align: left; cursor: pointer; transition: border-color 0.16s, box-shadow 0.16s, transform 0.16s; }
.sku-product-card:hover { border-color: var(--plm-color-primary); box-shadow: var(--plm-shadow-md); transform: translateY(-1px); }
.sku-product-card__image { display: grid; place-items: center; min-height: 100px; background: linear-gradient(135deg, #e9eef8 0%, #f8fafc 100%); color: #3b4a63; font-weight: 600; border-radius: 8px; }
.sku-product-card__series { margin: 0; font-size: 18px; font-weight: 600; }
.sku-product-card__meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: auto; }
.sku-list-panel__head { align-items: flex-start; margin-bottom: 14px; }
.sku-list-panel__actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.sku-list-panel__search { width: 260px; }
.sku-image-cell { display: flex; align-items: center; gap: 10px; }
.sku-image-cell__thumb { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 8px; background: linear-gradient(135deg, #e9eef8 0%, #f8fafc 100%); color: #3b4a63; font-size: 11px; font-weight: 600; }
.sku-detail-dialog { display: flex; flex-direction: column; gap: 16px; }
.sku-detail-section { display: flex; flex-direction: column; gap: 12px; }
.sku-detail-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }

.project-overview-dialog { display: flex; flex-direction: column; gap: 16px; }
.project-overview-dialog__title { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 12px; border-bottom: 1px solid rgba(148,163,184,0.2); }
.project-overview-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 18px; margin: 0; }
.project-overview-grid div, .project-overview-block { padding: 12px; border: 1px solid rgba(148,163,184,0.18); border-radius: 8px; background: #f8fafc; }
.project-overview-grid dt { margin-bottom: 6px; color: #64748b; font-size: 12px; }
.project-overview-grid dd { margin: 0; color: #0f172a; font-weight: 600; }
</style>
