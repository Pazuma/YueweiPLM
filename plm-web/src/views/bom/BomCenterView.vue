<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getBomCenterRows, getProductPresentation } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import type { BomCenterRow, ProductBomItemRow, ProductDetailPresentation } from '@/types/foundation'
import { formatAmount } from '@/utils/format'

type BomConfirmState = 'finalized' | 'developing'
type BomStatusFilter = 'all' | 'finalized' | 'developing'
type SortMode = 'updated_desc' | 'code_asc'

interface BomVersionOption {
  versionNo: string
  statusLabel: string
  materialCost: number
  processCost: number
  totalCost: number
  delta: number
  isFinalizedVersion: boolean
}

interface BomProductListRow extends BomCenterRow {
  productTypeLabel: string
  finalizedVersionNo: string
  versionCount: number
  availableVersions: BomVersionOption[]
}

const router = useRouter()
const loading = ref(false)
const rows = ref<BomCenterRow[]>([])
const searchModel = ref({ keyword: '', bomType: '', bomStatus: 'all' as BomStatusFilter })
const sortMode = ref<SortMode>('code_asc')
const detailVisible = ref(false)
const detailRow = ref<BomProductListRow | null>(null)
const detailActiveVersion = ref('')
const productPresentationMap = ref<Record<number, ProductDetailPresentation>>({})

const bomStatusOptions = [
  { label: '全部', value: 'all' },
  { label: '已确定', value: 'finalized' },
  { label: '在开发', value: 'developing' }
] as const

function getFinalizedVersionNo(row: BomCenterRow, presentation?: ProductDetailPresentation) {
  if (!presentation) return ''
  const released = presentation.bomCompareRows.find((item) =>
    ['已发布', '已冻结', '已确定', '当前'].includes(item.statusLabel) && row.status === 'released'
  )
  if (released) return released.versionNo
  if (row.status === 'released' && presentation.defaultBomVersion) return presentation.defaultBomVersion
  return ''
}

function getProductTypeLabel(row: BomCenterRow): string {
  return row.productCode.includes('-IP') ? '型号' : '产品'
}

const bomProductRows = computed<BomProductListRow[]>(() =>
  rows.value.map((row) => {
    const presentation = productPresentationMap.value[row.productId]
    const finalizedVersionNo = getFinalizedVersionNo(row, presentation)
    const compareRows = presentation?.bomCompareRows?.length
      ? presentation.bomCompareRows
      : [{
          versionNo: row.currentVersion,
          statusLabel: row.status,
          materialCost: row.materialCost,
          processCost: row.processCost,
          totalCost: row.totalCost,
          delta: 0
        }]

    return {
      ...row,
      productTypeLabel: getProductTypeLabel(row),
      finalizedVersionNo,
      versionCount: compareRows.length,
      availableVersions: compareRows.map((version) => ({
        ...version,
        isFinalizedVersion: Boolean(finalizedVersionNo && version.versionNo === finalizedVersionNo)
      }))
    }
  })
)

const filteredProductRows = computed(() => {
  const keyword = searchModel.value.keyword.trim().toLowerCase()
  return bomProductRows.value.filter((row) => {
    const typeMatched = !searchModel.value.bomType || row.bomType === searchModel.value.bomType
    const statusMatched =
      searchModel.value.bomStatus === 'all' ||
      (searchModel.value.bomStatus === 'finalized' && Boolean(row.finalizedVersionNo)) ||
      (searchModel.value.bomStatus === 'developing' && !row.finalizedVersionNo)
    const keywordMatched =
      !keyword ||
      row.productCode.toLowerCase().includes(keyword) ||
      row.productName.toLowerCase().includes(keyword) ||
      row.bomType.toLowerCase().includes(keyword) ||
      row.availableVersions.some((item) => item.versionNo.toLowerCase().includes(keyword))
    return typeMatched && statusMatched && keywordMatched
  })
})

const sortedProductRows = computed(() => {
  const list = [...filteredProductRows.value]
  if (sortMode.value === 'code_asc') {
    return list.sort((a, b) => `${a.productCode}-${a.bomType}`.localeCompare(`${b.productCode}-${b.bomType}`))
  }
  return list.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
})

/* ========== 详情弹窗 ========== */

const detailPresentation = computed(() => {
  if (!detailRow.value) return null
  return productPresentationMap.value[detailRow.value.productId] || null
})

const activeVersionSummary = computed(() => {
  if (!detailRow.value) return null
  return (
    detailRow.value.availableVersions.find((item) => item.versionNo === detailActiveVersion.value) ||
    null
  )
})

const detailBomItems = computed<ProductBomItemRow[]>(() => {
  const row = detailRow.value
  const presentation = detailPresentation.value
  if (!row || !presentation || !detailActiveVersion.value) return []
  return presentation.bomItemsByVersion[detailActiveVersion.value] || []
})

const detailTitle = computed(() => {
  if (!detailRow.value) return 'BOM 详情'
  return `${detailRow.value.productCode} / ${detailRow.value.productName}`
})

function getBomTypeLabel(type: BomCenterRow['bomType']) {
  return type === 'PACK' ? '包装BOM' : type
}

function getBomTypeTone(type: BomCenterRow['bomType']) {
  if (type === 'EBOM') return 'primary'
  if (type === 'MBOM') return 'success'
  return 'warning'
}

function getBomChangeLabel(changeType?: string) {
  if (changeType === 'new') return '新增'
  if (changeType === 'replace') return '替换'
  if (changeType === 'remove') return '删除'
  return '沿用'
}

function getVersionOptionLabel(version: BomVersionOption) {
  return `${version.versionNo} / ${version.statusLabel}`
}

function handleReset() {
  searchModel.value = { keyword: '', bomType: '', bomStatus: 'all' }
  sortMode.value = 'code_asc'
}

function openDetail(row: BomProductListRow) {
  detailRow.value = row
  detailActiveVersion.value =
    row.finalizedVersionNo ||
    row.currentVersion ||
    row.availableVersions[0]?.versionNo ||
    ''
  detailVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    rows.value = await getBomCenterRows()
    const ids = [...new Set(rows.value.map((item) => item.productId))]
    const presentations = await Promise.all(ids.map((id) => getProductPresentation(id)))
    productPresentationMap.value = ids.reduce<Record<number, ProductDetailPresentation>>(
      (acc, id, i) => {
        acc[id] = presentations[i]
        return acc
      },
      {}
    )
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="BOM 管理" description="按产品 / 型号列表查看 BOM 版本，点击详情选择版本查看物料明细与总成本。">
    <template #actions>
      <el-button @click="router.push('/projects?tab=archived&archiveView=product')">产品管理</el-button>
      <el-button type="primary" @click="router.push('/projects?tab=archived&archiveView=sku')">SKU 视图</el-button>
    </template>

    <section class="page-panel bom-toolbar">
      <el-form inline>
        <el-form-item label="BOM 搜索">
          <el-input v-model="searchModel.keyword" placeholder="产品编码 / 名称 / BOM 类型 / 版本号" clearable style="width: 260px" />
        </el-form-item>
        <el-form-item label="BOM 类型">
          <el-select v-model="searchModel.bomType" clearable style="width: 130px">
            <el-option label="全部" value="" />
            <el-option label="EBOM" value="EBOM" />
            <el-option label="MBOM" value="MBOM" />
            <el-option label="PACK" value="PACK" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchModel.bomStatus" style="width: 130px">
            <el-option v-for="opt in bomStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="sortMode" style="width: 160px">
            <el-option label="按产品编码排序" value="code_asc" />
            <el-option label="按最近更新时间" value="updated_desc" />
          </el-select>
        </el-form-item>
        <div class="bom-toolbar__actions">
          <el-button type="primary" @click="() => {}">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </el-form>
    </section>

    <section class="page-panel" v-loading="loading">
      <div class="toolbar-row">
        <div>
          <h3 class="section-title">产品 / 型号列表</h3>
          <p class="page-panel-desc">以产品 / 型号为入口定位对象，版本物料与成本统一在详情弹窗内查看。</p>
        </div>
        <el-tag effect="light">{{ sortedProductRows.length }} 条</el-tag>
      </div>

      <div v-if="!sortedProductRows.length" class="empty-state">
        <strong>没有匹配的 BOM 记录</strong>
        <span class="subtle-text">调整搜索词、类型或状态后再试一次。</span>
      </div>

      <el-table v-else :data="sortedProductRows" border stripe>
        <el-table-column prop="productCode" label="产品 / 型号编码" min-width="190" />
        <el-table-column prop="productName" label="产品 / 型号名称" min-width="220" />
        <el-table-column prop="productTypeLabel" label="类型" width="80" />
        <el-table-column label="BOM 类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getBomTypeTone(row.bomType)" effect="light" size="small">{{ getBomTypeLabel(row.bomType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="产品状态" width="110">
          <template #default="{ row }">
            <StatusTag :status="row.status" object-type="product" />
          </template>
        </el-table-column>
        <el-table-column label="敲定版本" width="110">
          <template #default="{ row }">
            <span :class="{ 'subtle-text': !row.finalizedVersionNo }">{{ row.finalizedVersionNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="BOM 版本" width="100">
          <template #default="{ row }">{{ row.versionCount }} 个</template>
        </el-table-column>
        <el-table-column label="更新时间" width="130">
          <template #default="{ row }">{{ row.updatedAt }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="900px" destroy-on-close>
      <div v-if="detailRow" class="bom-detail-dialog">
        <!-- 对象概览 -->
        <div class="bom-detail-overview">
          <div class="info-card">
            <span class="subtle-text">产品编码</span>
            <strong>{{ detailRow.productCode }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">产品名称</span>
            <strong>{{ detailRow.productName }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">BOM 类型</span>
            <el-tag :type="getBomTypeTone(detailRow.bomType)" effect="light">{{ getBomTypeLabel(detailRow.bomType) }}</el-tag>
          </div>
          <div class="info-card">
            <span class="subtle-text">产品状态</span>
            <StatusTag :status="detailRow.status" object-type="product" />
          </div>
          <div class="info-card">
            <span class="subtle-text">敲定版本</span>
            <strong :class="{ 'subtle-text': !detailRow.finalizedVersionNo }">{{ detailRow.finalizedVersionNo || '-' }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">BOM 版本数</span>
            <strong>{{ detailRow.versionCount }} 个</strong>
          </div>
        </div>

        <!-- 红框：版本物料查看选择区 + 蓝框：版本总成本 -->
        <section class="bom-version-viewer">
          <div class="bom-version-viewer__select">
            <span class="subtle-text">版本物料查看</span>
            <el-select v-model="detailActiveVersion" class="bom-version-viewer__control">
              <el-option
                v-for="version in detailRow.availableVersions"
                :key="version.versionNo"
                :label="getVersionOptionLabel(version)"
                :value="version.versionNo"
              />
            </el-select>
          </div>

          <div class="bom-version-viewer__cost">
            <span class="subtle-text">版本总成本</span>
            <strong>{{ formatAmount(activeVersionSummary?.totalCost || 0) }}</strong>
            <div class="bom-version-viewer__cost-detail">
              <span class="subtle-text">材料 {{ formatAmount(activeVersionSummary?.materialCost || 0) }}</span>
              <span class="subtle-text">工艺 {{ formatAmount(activeVersionSummary?.processCost || 0) }}</span>
            </div>
          </div>
        </section>

        <!-- 物料明细 -->
        <section class="bom-items-panel">
          <div class="toolbar-row">
            <div>
              <h4 class="section-title">物料明细 / {{ detailActiveVersion }}</h4>
              <p class="page-panel-desc">当前选中 BOM 版本下的物料、用量、供应商、单价和差异标识。</p>
            </div>
            <el-tag effect="light">{{ detailBomItems.length }} 条明细</el-tag>
          </div>

          <el-table v-if="detailBomItems.length" :data="detailBomItems" border stripe>
            <el-table-column prop="inventoryCode" label="物料编码" min-width="150" />
            <el-table-column prop="inventoryName" label="物料名称" min-width="180" />
            <el-table-column prop="quantity" label="用量" width="80" />
            <el-table-column prop="stockUom" label="单位" width="80" />
            <el-table-column prop="supplierName" label="供应商" min-width="150" />
            <el-table-column label="单价" width="110">
              <template #default="{ row }">{{ formatAmount(row.unitCost) }}</template>
            </el-table-column>
            <el-table-column label="行成本" width="110">
              <template #default="{ row }">
                {{ formatAmount(Number(row.quantity || 0) * Number(row.unitCost || 0)) }}
              </template>
            </el-table-column>
            <el-table-column label="差异" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="row.changeType === 'new' ? 'success' : row.changeType === 'replace' ? 'warning' : row.changeType === 'remove' ? 'danger' : 'info'"
                  effect="light"
                  size="small"
                >
                  {{ getBomChangeLabel(row.changeType) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="当前版本暂无 BOM 明细" />
        </section>
      </div>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.bom-toolbar { margin-bottom: 0; }
.bom-toolbar .el-form { display: flex; flex-wrap: wrap; align-items: center; }
.bom-toolbar__actions { display: flex; gap: 8px; margin-left: auto; }
.empty-state { display: flex; flex-direction: column; gap: 8px; padding: 32px 16px; border: 1px dashed var(--plm-color-border); border-radius: var(--plm-radius-base); background: #fafcff; }

/* 详情弹窗 */
.bom-detail-dialog { display: flex; flex-direction: column; gap: 16px; }

.bom-detail-overview {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

/* 红框+蓝框：版本选择 + 版本总成本 */
.bom-version-viewer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px;
  gap: 12px;
  align-items: end;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.bom-version-viewer__select,
.bom-version-viewer__cost {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bom-version-viewer__control {
  width: 100%;
}

.bom-version-viewer__cost {
  min-height: 78px;
  justify-content: center;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}

.bom-version-viewer__cost strong {
  font-size: 22px;
  color: #1d4ed8;
}

.bom-version-viewer__cost-detail {
  display: flex;
  gap: 12px;
}

.bom-items-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

@media (max-width: 1080px) {
  .bom-detail-overview {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .bom-detail-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .bom-version-viewer {
    grid-template-columns: 1fr;
  }
}
</style>
