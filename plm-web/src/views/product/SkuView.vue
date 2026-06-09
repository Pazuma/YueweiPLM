<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getProductList } from '@/api/modules/product'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type { ProductSummary } from '@/types/product'
import { formatAmount } from '@/utils/format'

const router = useRouter()
const rows = ref<ProductSummary[]>([])
const loading = ref(false)

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '系列 / 机型 / 颜色 / 客户' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '开发中', value: 'developing' },
      { label: '评审中', value: 'reviewing' },
      { label: '已发布', value: 'released' }
    ]
  }
]

const table = useTable(rows, ['productName', 'seriesName', 'model', 'color', 'customerName'], (row, filters) => {
  if (row.productType !== 'model_variant') return false
  const status = String(filters.status || '')
  return !status || row.status === status
})

const filteredRows = computed(() => table.filteredRows.value as unknown as ProductSummary[])

const groupedSeries = computed(() => {
  const map = new Map<string, ProductSummary[]>()
  filteredRows.value.forEach((item) => {
    const list = map.get(item.seriesName) || []
    list.push(item)
    map.set(item.seriesName, list)
  })
  return Array.from(map.entries()).map(([series, items]) => ({ series, items }))
})

onMounted(async () => {
  loading.value = true
  try {
    rows.value = await getProductList()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <PageContainer title="SKU 视图" description="以 Product 为根对象，从系列、机型、颜色和客户扩展维度查看型号版本，不新增独立 SKU 根对象。">
    <template #actions>
      <el-button @click="router.push('/products')">返回产品管理</el-button>
    </template>

    <section class="metric-grid">
      <div class="metric-card">
        <p class="metric-card__label">SKU 视图总数</p>
        <p class="metric-card__value">{{ filteredRows.length }}</p>
        <span class="metric-card__trend">当前筛选结果</span>
      </div>
      <div class="metric-card">
        <p class="metric-card__label">覆盖系列</p>
        <p class="metric-card__value">{{ groupedSeries.length }}</p>
        <span class="metric-card__trend">按系列归类展示</span>
      </div>
      <div class="metric-card">
        <p class="metric-card__label">评审中 SKU</p>
        <p class="metric-card__value">{{ filteredRows.filter((item) => item.status === 'reviewing').length }}</p>
        <span class="metric-card__trend">优先关注 BOM 与资料冻结</span>
      </div>
      <div class="metric-card">
        <p class="metric-card__label">平均预估成本</p>
        <p class="metric-card__value metric-card__value--small">
          {{ formatAmount(filteredRows.reduce((sum, item) => sum + item.estimatedCost, 0) / (filteredRows.length || 1)) }}
        </p>
        <span class="metric-card__trend">便于对比不同机型颜色版本</span>
      </div>
    </section>

    <SearchBar :fields="searchFields" :model-value="table.query" @search="table.setQuery" @reset="table.resetQuery({ keyword: '', status: '' })" />

    <section class="page-panel" v-loading="loading">
      <div class="page-stack">
        <section v-for="group in groupedSeries" :key="group.series" class="sku-group">
          <div class="toolbar-row sku-group__header">
            <div>
              <h3 class="section-title">{{ group.series }}</h3>
              <p class="page-panel-desc">{{ group.items.length }} 个型号扩展视图</p>
            </div>
          </div>
          <el-table :data="group.items" border stripe>
            <el-table-column prop="productCode" label="产品编码" min-width="170" />
            <el-table-column prop="productName" label="产品名称" min-width="220" />
            <el-table-column prop="model" label="机型" width="120" />
            <el-table-column prop="color" label="颜色" width="100" />
            <el-table-column prop="customerName" label="客户 / 来源" min-width="180" />
            <el-table-column prop="activeBomVersion" label="BOM 版本" width="120" />
            <el-table-column label="预估成本" width="130">
              <template #default="{ row }">
                {{ formatAmount(row.estimatedCost, row.estimatedCostCurrency) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <StatusTag :status="row.status" object-type="product" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="router.push(`/products/${row.productId}`)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.metric-card__value--small {
  font-size: 20px;
}

.sku-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sku-group__header {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--plm-color-border-light);
}
</style>
