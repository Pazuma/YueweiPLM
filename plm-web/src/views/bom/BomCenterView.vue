<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getBomCenterRows, getProductPresentation } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import type { BomCenterRow, ProductBomItemRow, ProductDetailPresentation } from '@/types/foundation'
import { formatAmount } from '@/utils/format'

type BomCenterDisplayRow = BomCenterRow & {
  delta: number
  versionStatusLabel: string
}

type DetailSummaryCard = {
  label: string
  value: string
  type?: 'bomType'
  tone?: 'primary' | 'success' | 'warning'
  className?: string
}

type SortMode = 'updated_desc' | 'code_asc'

const router = useRouter()
const loading = ref(false)
const rows = ref<BomCenterRow[]>([])
const searchModel = ref({
  keyword: '',
  bomType: ''
})
const sortMode = ref<SortMode>('updated_desc')
const detailVisible = ref(false)
const detailRow = ref<BomCenterDisplayRow | null>(null)
const productPresentationMap = ref<Record<number, ProductDetailPresentation>>({})

// ---- enriched / filtered / sorted ----
const enrichedRows = computed<BomCenterDisplayRow[]>(() =>
  rows.value.map((row) => {
    const presentation = productPresentationMap.value[row.productId]
    const compareRow = presentation?.bomCompareRows.find((item) => item.versionNo === row.currentVersion)
    return {
      ...row,
      delta: compareRow?.delta ?? 0,
      versionStatusLabel: compareRow?.statusLabel || row.status
    }
  })
)

const filteredRows = computed(() => {
  const keyword = searchModel.value.keyword.trim().toLowerCase()
  return enrichedRows.value.filter((row) => {
    const typeMatched = !searchModel.value.bomType || row.bomType === searchModel.value.bomType
    const keywordMatched =
      !keyword ||
      row.productCode.toLowerCase().includes(keyword) ||
      row.productName.toLowerCase().includes(keyword) ||
      row.bomType.toLowerCase().includes(keyword) ||
      row.currentVersion.toLowerCase().includes(keyword)
    return typeMatched && keywordMatched
  })
})

const sortedRows = computed(() => {
  const list = [...filteredRows.value]
  if (sortMode.value === 'code_asc') {
    return list.sort((a, b) => {
      return `${a.productCode}-${a.currentVersion}-${a.bomType}`.localeCompare(
        `${b.productCode}-${b.currentVersion}-${b.bomType}`
      )
    })
  }
  return list.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
})

// ---- detail dialog ----
const detailPresentation = computed(() => {
  if (!detailRow.value) return null
  return productPresentationMap.value[detailRow.value.productId] || null
})

const detailCompareRow = computed(() => {
  const row = detailRow.value
  const presentation = detailPresentation.value
  if (!row || !presentation) return null
  return presentation.bomCompareRows.find((item) => item.versionNo === row.currentVersion) || null
})

const detailBomItems = computed<ProductBomItemRow[]>(() => {
  const row = detailRow.value
  const presentation = detailPresentation.value
  if (!row || !presentation) return []
  return presentation.bomItemsByVersion[row.currentVersion] || presentation.bomItems || []
})

const detailTitle = computed(() => {
  if (!detailRow.value) return 'BOM 详情'
  return `${detailRow.value.productName} - ${detailRow.value.currentVersion} ${getBomTypeLabel(detailRow.value.bomType)} 详情`
})

const detailSummaryCards = computed<DetailSummaryCard[]>(() => {
  if (!detailRow.value) return []
  return [
    { label: 'BOM 类型', value: getBomTypeLabel(detailRow.value.bomType), type: 'bomType', tone: getBomTypeTone(detailRow.value.bomType) },
    { label: '所属产品', value: detailRow.value.productName },
    { label: '版本状态', value: detailCompareRow.value?.statusLabel || detailRow.value.versionStatusLabel || '--' },
    { label: '总成本', value: formatAmount(detailRow.value.totalCost) },
    { label: '成本变化', value: formatDelta(detailRow.value.delta), className: getDeltaClass(detailRow.value.delta) },
    { label: '材料成本', value: formatAmount(detailRow.value.materialCost) },
    { label: '工艺成本', value: formatAmount(detailRow.value.processCost) },
    { label: '当前页面成本汇总', value: formatAmount(detailPresentation.value?.bomCostSummary?.totalCost ?? detailRow.value.totalCost) }
  ]
})

function getBomTypeLabel(type: BomCenterRow['bomType']) {
  if (type === 'PACK') return '包装BOM'
  return type
}

function getBomTypeTone(type: BomCenterRow['bomType']) {
  if (type === 'EBOM') return 'primary'
  if (type === 'MBOM') return 'success'
  return 'warning'
}

function getDeltaClass(delta: number) {
  if (delta > 0) return 'text-danger'
  if (delta < 0) return 'text-success'
  return ''
}

function formatDelta(delta: number) {
  if (delta === 0) return '--'
  return `${delta > 0 ? '+' : '-'}${formatAmount(Math.abs(delta))}`
}

// ---- actions ----
function handleSearch() {
  // reactive searchModel already bound, just trigger re-render
}

function handleReset() {
  searchModel.value = { keyword: '', bomType: '' }
  sortMode.value = 'updated_desc'
}

function openDetail(row: BomCenterDisplayRow) {
  detailRow.value = row
  detailVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    rows.value = await getBomCenterRows()
    const productIds = [...new Set(rows.value.map((item) => item.productId))]
    const presentations = await Promise.all(productIds.map((productId) => getProductPresentation(productId)))
    productPresentationMap.value = productIds.reduce<Record<number, ProductDetailPresentation>>((acc, productId, index) => {
      acc[productId] = presentations[index]
      return acc
    }, {})
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="BOM 管理" description="按产品查看 BOM 版本状态、成本和变化，点击详情快速核对物料明细。">
    <template #actions>
      <el-button @click="router.push('/products')">产品管理</el-button>
      <el-button type="primary" @click="router.push('/sku-view')">SKU 视图</el-button>
    </template>

    <!-- search bar -->
    <section class="page-panel bom-toolbar">
      <el-form inline>
        <el-form-item label="BOM 搜索">
          <el-input v-model="searchModel.keyword" placeholder="产品编码 / 产品名称 / BOM 类型 / 版本号" clearable style="width: 280px" />
        </el-form-item>
        <el-form-item label="BOM 类型">
          <el-select v-model="searchModel.bomType" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="EBOM" value="EBOM" />
            <el-option label="MBOM" value="MBOM" />
            <el-option label="PACK" value="PACK" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序方式">
          <el-select v-model="sortMode" style="width: 180px">
            <el-option label="按最近更新时间" value="updated_desc" />
            <el-option label="按产品编码排序" value="code_asc" />
          </el-select>
        </el-form-item>
        <div class="bom-toolbar__actions">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </el-form>
    </section>

    <!-- table -->
    <section class="page-panel" v-loading="loading">
      <div class="toolbar-row">
        <div>
          <h3 class="section-title">BOM 版本列表</h3>
          <p class="page-panel-desc">按产品查看当前版本、物料成本、工艺成本、总成本和时间信息。</p>
        </div>
        <el-tag effect="light">{{ sortedRows.length }} 条</el-tag>
      </div>

      <div v-if="!sortedRows.length" class="empty-state">
        <strong>没有匹配的 BOM 记录</strong>
        <span class="subtle-text">调整搜索词或 BOM 类型后再试一次。</span>
      </div>

      <el-table v-else :data="sortedRows" border stripe>
        <el-table-column prop="productCode" label="产品编码" min-width="160" />
        <el-table-column prop="productName" label="产品名称" min-width="220" />
        <el-table-column prop="bomType" label="BOM 类型" width="110" />
        <el-table-column prop="currentVersion" label="当前版本" width="110" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" object-type="product" />
          </template>
        </el-table-column>
        <el-table-column label="材料成本" width="140">
          <template #default="{ row }">{{ formatAmount(row.materialCost) }}</template>
        </el-table-column>
        <el-table-column label="工艺成本" width="140">
          <template #default="{ row }">{{ formatAmount(row.processCost) }}</template>
        </el-table-column>
        <el-table-column label="总成本" width="140">
          <template #default="{ row }">
            <span>{{ formatAmount(row.totalCost) }}</span>
            <span v-if="row.delta !== 0" :class="getDeltaClass(row.delta)" class="bom-delta-inline">
              {{ formatDelta(row.delta) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="140" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- detail dialog -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="920px">
      <div v-if="detailRow" class="bom-detail-dialog">
        <div class="bom-detail-dialog__summary-grid">
          <div v-for="card in detailSummaryCards" :key="card.label" class="info-card bom-detail-dialog__summary-card">
            <span class="subtle-text">{{ card.label }}</span>
            <el-tag v-if="card.type === 'bomType'" :type="card.tone" effect="light" size="large">{{ card.value }}</el-tag>
            <strong v-else :class="card.className">{{ card.value }}</strong>
          </div>
        </div>

        <div class="toolbar-row bom-detail-dialog__table-header">
          <div>
            <h4 class="section-title">物料明细</h4>
            <p class="page-panel-desc">当前版本下的物料、用量、供应商和差异标识统一收在这一张表里。</p>
          </div>
          <el-tag effect="light">{{ detailBomItems.length }} 条明细</el-tag>
        </div>

        <el-table :data="detailBomItems" border stripe>
          <el-table-column prop="inventoryCode" label="物料编码" min-width="150" />
          <el-table-column prop="inventoryName" label="物料名称" min-width="180" />
          <el-table-column prop="quantity" label="用量" width="90" />
          <el-table-column prop="stockUom" label="单位" width="90" />
          <el-table-column prop="supplierName" label="供应商" min-width="160" />
          <el-table-column label="单价" width="120">
            <template #default="{ row }">{{ formatAmount(row.unitCost) }}</template>
          </el-table-column>
          <el-table-column label="差异标识" width="120">
            <template #default="{ row }">
              <el-tag
                :type="row.changeType === 'new' ? 'success' : row.changeType === 'replace' ? 'warning' : row.changeType === 'remove' ? 'danger' : 'info'"
                effect="light"
              >
                {{ row.changeType === 'new' ? '新增' : row.changeType === 'replace' ? '替换' : row.changeType === 'remove' ? '删除' : '沿用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.bom-toolbar {
  margin-bottom: 0;
}

.bom-toolbar .el-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.bom-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.bom-delta-inline {
  margin-left: 6px;
  font-size: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 32px 16px;
  border: 1px dashed var(--plm-color-border);
  border-radius: var(--plm-radius-base);
  background: #fafcff;
}

.bom-detail-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.bom-detail-dialog__summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.bom-detail-dialog__summary-card {
  min-height: 88px;
}

.bom-detail-dialog__table-header {
  align-items: flex-start;
}

.text-danger {
  color: var(--el-color-danger);
}

.text-success {
  color: var(--el-color-success);
}

@media (max-width: 1080px) {
  .bom-detail-dialog__summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
