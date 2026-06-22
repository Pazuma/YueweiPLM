<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getFoundationProducts, getProductPresentation } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import type { FoundationProductRef, ProductBomItemRow, ProductDetailPresentation } from '@/types/foundation'
import { formatAmount, formatDate } from '@/utils/format'

interface SkuDisplayRow extends FoundationProductRef {
  sampleImageUrl?: string
  sampleImageName?: string
  stockUom?: string
  projectSource?: string
}

type SkuPageStage = 'product-home' | 'sku-list'

const router = useRouter()
const loading = ref(false)
const rows = ref<SkuDisplayRow[]>([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailSku = ref<SkuDisplayRow | null>(null)
const detailPresentation = ref<ProductDetailPresentation | null>(null)
const detailBomVersion = ref('')

type SkuDetailSectionKey = 'basic' | 'cost' | 'bom' | 'process'
const activeDetailSection = ref<SkuDetailSectionKey>('basic')
const skuDetailSections = [
  { key: 'basic' as const, label: '基础信息' },
  { key: 'cost' as const, label: '成本' },
  { key: 'bom' as const, label: '当前版本 BOM' },
  { key: 'process' as const, label: '工艺路线' }
]

const detailBomItems = computed<ProductBomItemRow[]>(() => {
  if (!detailPresentation.value || !detailBomVersion.value) return []
  return detailPresentation.value.bomItemsByVersion[detailBomVersion.value] || []
})
const pageStage = ref<SkuPageStage>('product-home')
const activeProductId = ref<number | null>(null)
const keyword = ref('')
const selectedSkuIds = ref<number[]>([])
const previewImageVisible = ref(false)
const previewImageUrl = ref('')
const createSkuVisible = ref(false)
const deleteLoading = ref(false)

const productCards = computed(() =>
  rows.value.filter((item) => item.productType === 'product_line')
)

const activeProduct = computed(() =>
  productCards.value.find((item) => item.productId === activeProductId.value) || null
)

const currentSkuRows = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!activeProductId.value) return []

  return rows.value.filter((item) => {
    if (item.productType !== 'model_variant') return false

    const belongsToProduct =
      item.parentProductId === activeProductId.value ||
      item.seriesName === activeProduct.value?.seriesName

    const keywordMatched =
      !search ||
      item.productCode.toLowerCase().includes(search) ||
      item.productName.toLowerCase().includes(search) ||
      item.model.toLowerCase().includes(search) ||
      item.color.toLowerCase().includes(search)

    return belongsToProduct && keywordMatched
  })
})

function getSkuCount(productId: number) {
  return rows.value.filter(
    (item) =>
      item.productType === 'model_variant' &&
      (item.parentProductId === productId || item.seriesName === activeProduct.value?.seriesName)
  ).length
}

function getShortCode(item: FoundationProductRef) {
  return item.productCode.replace(/^PRD-/, '')
}

function openSkuList(productId: number) {
  activeProductId.value = productId
  selectedSkuIds.value = []
  keyword.value = ''
  pageStage.value = 'sku-list'
}

function backToProductHome() {
  activeProductId.value = null
  selectedSkuIds.value = []
  keyword.value = ''
  pageStage.value = 'product-home'
}

function handleSkuSelectionChange(rows: SkuDisplayRow[]) {
  selectedSkuIds.value = rows.map((row) => row.productId)
}

function openPreview(url?: string) {
  previewImageUrl.value = url || ''
  previewImageVisible.value = true
}

function deleteSelectedSkus() {
  deleteLoading.value = true
  try {
    rows.value = rows.value.filter((row) => !selectedSkuIds.value.includes(row.productId))
    selectedSkuIds.value = []
  } finally {
    deleteLoading.value = false
  }
}

async function openSkuDetail(row: SkuDisplayRow) {
  detailSku.value = row
  activeDetailSection.value = 'basic'
  detailVisible.value = true
  detailLoading.value = true
  try {
    const presentation = await getProductPresentation(row.productId)
    detailPresentation.value = presentation
    detailBomVersion.value = presentation.defaultBomVersion || presentation.bomCompareRows[0]?.versionNo || ''
  } finally {
    detailLoading.value = false
  }
}

function openProduct(productId: number) {
  router.push(`/products/${productId}`)
}

async function loadData() {
  loading.value = true
  try {
    rows.value = await getFoundationProducts()
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="SKU 管理" description="先按产品卡片定位，再进入该产品下的 SKU 列表进行管理。">
    <!-- product home -->
    <section v-if="pageStage === 'product-home'" class="page-panel sku-product-home">
      <div class="toolbar-row sku-product-home__head">
        <div>
          <h3 class="section-title">产品入口</h3>
          <p class="page-panel-desc">先按产品卡片定位，再进入该产品下的 SKU 列表。</p>
        </div>
        <el-button type="primary" @click="router.push('/products/create')">新建型号</el-button>
      </div>

      <div class="sku-product-grid" v-loading="loading">
        <button
          v-for="product in productCards"
          :key="product.productId"
          class="sku-product-card"
          type="button"
          @click="openSkuList(product.productId)"
        >
          <div class="sku-product-card__image">
            <span>{{ product.seriesName }}</span>
          </div>
          <div class="toolbar-row">
            <strong>{{ product.productName }}</strong>
            <StatusTag :status="product.status" object-type="product" />
          </div>
          <p class="subtle-text">{{ product.productCode }}</p>
          <p class="sku-product-card__series">{{ product.seriesName }}</p>
          <div class="sku-product-card__meta">
            <span>{{ getSkuCount(product.productId) }} 个 SKU</span>
            <span class="subtle-text">{{ product.lastActiveAt }}</span>
          </div>
        </button>
      </div>
    </section>

    <!-- sku list -->
    <section v-else class="page-panel sku-list-panel" v-loading="loading">
      <div class="toolbar-row sku-list-panel__head">
        <div>
          <el-button link type="primary" @click="backToProductHome">← 返回产品卡片</el-button>
          <h3 class="section-title">{{ activeProduct?.productName }} / SKU 列表</h3>
          <p class="page-panel-desc">查看当前产品下的 SKU 信息、示例图与版本状态。</p>
        </div>

        <div class="sku-list-panel__actions">
          <el-input
            v-model="keyword"
            clearable
            class="sku-list-panel__search"
            placeholder="搜索编码 / 名称 / 型号 / 颜色"
          />
          <el-button type="primary" @click="createSkuVisible = true">新增 SKU</el-button>
          <el-button
            type="danger"
            :disabled="!selectedSkuIds.length"
            :loading="deleteLoading"
            @click="deleteSelectedSkus"
          >
            删除 SKU
          </el-button>
        </div>
      </div>

      <el-table :data="currentSkuRows" border stripe @selection-change="handleSkuSelectionChange">
        <el-table-column type="selection" width="48" />
        <el-table-column label="示例图" min-width="150">
          <template #default="{ row }">
            <div class="sku-image-cell">
              <div class="sku-image-cell__thumb">
                <span>{{ row.model }}</span>
              </div>
              <el-button link type="primary" @click="openPreview(row.sampleImageUrl)">查看示例图</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="productCode" label="SKU 编码" min-width="180" />
        <el-table-column prop="productName" label="SKU名称" min-width="220" />
        <el-table-column prop="model" label="型号" width="140" />
        <el-table-column prop="stockUom" label="单位" width="80" />
        <el-table-column prop="projectSource" label="项目来源" min-width="150" />
        <el-table-column prop="color" label="颜色" width="120" />
        <el-table-column prop="versionNo" label="版本" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" object-type="product" />
          </template>
        </el-table-column>
        <el-table-column label="最近活跃" width="140">
          <template #default="{ row }">{{ formatDate(row.lastActiveAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSkuDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- detail dialog -->
    <el-dialog v-model="detailVisible" title="SKU 详情" width="1080px">
      <div v-loading="detailLoading" class="sku-detail-dialog">
        <nav class="sku-detail-breadcrumb" aria-label="SKU详情导航">
          <span class="sku-detail-breadcrumb__root">SKU 详情</span>
          <template v-for="section in skuDetailSections" :key="section.key">
            <span class="sku-detail-breadcrumb__separator">/</span>
            <button
              class="sku-detail-breadcrumb__item"
              :class="{ 'is-active': activeDetailSection === section.key }"
              type="button"
              @click="activeDetailSection = section.key"
            >{{ section.label }}</button>
          </template>
        </nav>

        <section v-show="activeDetailSection === 'basic'" class="sku-detail-section">
          <h4 class="section-title">基础信息</h4>
          <div class="sku-detail-grid">
            <div class="info-card"><span class="subtle-text">SKU 编码</span><strong>{{ detailSku?.productCode }}</strong></div>
            <div class="info-card"><span class="subtle-text">SKU名称</span><strong>{{ detailSku?.productName }}</strong></div>
            <div class="info-card"><span class="subtle-text">产品线</span><strong>{{ detailSku?.seriesName }}</strong></div>
            <div class="info-card"><span class="subtle-text">型号</span><strong>{{ detailSku?.model }}</strong></div>
            <div class="info-card"><span class="subtle-text">单位</span><strong>{{ detailSku?.stockUom || '--' }}</strong></div>
            <div class="info-card"><span class="subtle-text">项目来源</span><strong>{{ detailSku?.projectSource || '--' }}</strong></div>
            <div class="info-card"><span class="subtle-text">颜色</span><strong>{{ detailSku?.color }}</strong></div>
            <div class="info-card"><span class="subtle-text">版本</span><strong>{{ detailSku?.versionNo }}</strong></div>
            <div class="info-card"><span class="subtle-text">状态</span><StatusTag v-if="detailSku" :status="detailSku.status" object-type="product" /></div>
          </div>
        </section>

        <section v-show="activeDetailSection === 'cost'" class="sku-detail-section">
          <h4 class="section-title">成本</h4>
          <div class="sku-detail-cost-grid">
            <div class="info-card"><span class="subtle-text">实际成本</span><strong>{{ formatAmount(detailPresentation?.costPanel.actualTotal || 0) }}</strong></div>
            <div v-if="detailPresentation?.costPanel.showEstimated" class="info-card"><span class="subtle-text">预计成本</span><strong>{{ formatAmount(detailPresentation?.costPanel.estimatedTotal || 0) }}</strong></div>
            <div class="info-card"><span class="subtle-text">当前阶段</span><strong>{{ detailSku?.currentStage }}</strong></div>
          </div>
        </section>

        <section v-show="activeDetailSection === 'bom'" class="sku-detail-section">
          <div class="toolbar-row">
            <div><h4 class="section-title">当前版本 BOM</h4><p class="page-panel-desc">BOM 作为 Product 的版本化资料展示。</p></div>
            <el-select v-model="detailBomVersion" style="width: 160px">
              <el-option v-for="row in detailPresentation?.bomCompareRows || []" :key="row.versionNo" :label="row.versionNo" :value="row.versionNo" />
            </el-select>
          </div>
          <el-table :data="detailBomItems" border stripe>
            <el-table-column prop="inventoryCode" label="物料编码" min-width="150" />
            <el-table-column prop="inventoryName" label="物料名称" min-width="180" />
            <el-table-column prop="quantity" label="用量" width="90" />
            <el-table-column prop="stockUom" label="单位" width="90" />
            <el-table-column prop="supplierName" label="供应商" min-width="150" />
          </el-table>
        </section>

        <section v-show="activeDetailSection === 'process'" class="sku-detail-section">
          <h4 class="section-title">工艺路线</h4>
          <p class="page-panel-desc">工艺路线由 Process 承接，显示当前 SKU 对应的流程节点与进度。</p>
          <div class="sku-timeline-mini">
            <div v-for="node in (detailPresentation?.timeline || []).slice(0, 6)" :key="node.nodeKey" class="sku-timeline-mini__item" :class="[`is-${node.status}`]">
              <strong>{{ node.nodeName }}</strong>
              <span class="subtle-text">{{ node.summary }}</span>
            </div>
          </div>
        </section>

      </div>
    </el-dialog>

    <!-- preview dialog -->
    <el-dialog v-model="previewImageVisible" title="SKU 示例图" width="640px">
      <el-image v-if="previewImageUrl" :src="previewImageUrl" fit="contain" class="sku-preview-image" />
      <p v-else class="subtle-text" style="text-align:center;padding:40px">暂无示例图</p>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.sku-product-home__head {
  margin-bottom: 14px;
}

.sku-product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.sku-product-card {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.sku-product-card:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.sku-product-card__image {
  display: grid;
  place-items: center;
  min-height: 100px;
  background: linear-gradient(135deg, #e9eef8 0%, #f8fafc 100%);
  color: #3b4a63;
  font-weight: 600;
  border-radius: 8px;
}

.sku-product-card__series {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.sku-product-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
}

.sku-list-panel__head {
  align-items: flex-start;
  margin-bottom: 14px;
}

.sku-list-panel__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.sku-list-panel__search {
  width: 260px;
}

.sku-image-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sku-image-cell__thumb {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: linear-gradient(135deg, #e9eef8 0%, #f8fafc 100%);
  color: #3b4a63;
  font-size: 11px;
  font-weight: 600;
  overflow: hidden;
}

.sku-detail-breadcrumb { display: flex; align-items: center; gap: 0; padding-bottom: 14px; border-bottom: 1px solid rgba(148,163,184,0.2); }
.sku-detail-breadcrumb__root { font-weight: 600; color: #0f172a; font-size: 14px; margin-right: 6px; }
.sku-detail-breadcrumb__separator { color: #94a3b8; margin: 0 4px; font-size: 13px; }
.sku-detail-breadcrumb__item { padding: 4px 8px; border: 0; border-radius: 4px; background: transparent; color: #64748b; font-size: 13px; cursor: pointer; transition: background 0.16s, color 0.16s; }
.sku-detail-breadcrumb__item:hover { background: #f1f5f9; color: #334155; }
.sku-detail-breadcrumb__item.is-active { background: #eff6ff; color: #1d4ed8; font-weight: 600; }

.sku-detail-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sku-detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sku-detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.sku-detail-cost-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.sku-timeline-mini {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.sku-timeline-mini__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.sku-timeline-mini__item.is-completed {
  background: rgba(34, 197, 94, 0.08);
}

.sku-timeline-mini__item.is-current {
  border-color: var(--plm-color-primary);
  background: rgba(37, 99, 235, 0.08);
}

.sku-preview-image {
  width: 100%;
  max-height: 70vh;
}

@media (max-width: 1400px) {
  .sku-product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .sku-product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .sku-product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
