<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProductList } from '@/api/modules/product'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type { ProductSummary } from '@/types/product'
import { formatAmount } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const rows = ref<ProductSummary[]>([])
const loading = ref(false)

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码 / 名称 / 系列 / 客户 / 机型' },
  {
    prop: 'productType',
    label: '产品类型',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '新产品产品线', value: 'product_line' },
      { label: '型号扩展 / SKU 视图', value: 'model_variant' }
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
      { label: '已发布', value: 'released' }
    ]
  }
]

const table = useTable(rows, ['productCode', 'productName', 'seriesName', 'customerName', 'model', 'color'], (row, filters) => {
  const status = String(filters.status || '')
  const frozen = String(filters.frozen || '')
  const productType = String(filters.productType || '')

  if (status && row.status !== status) return false
  if (productType && row.productType !== productType) return false
  if (frozen === 'unfrozen' && row.frozenFlag) return false
  if (frozen === 'frozen' && !row.frozenFlag) return false

  return true
})

const stats = computed(() => {
  const list = table.filteredRows.value as unknown as ProductSummary[]
  return {
    total: list.length,
    lines: list.filter((item) => item.productType === 'product_line').length,
    sku: list.filter((item) => item.productType === 'model_variant').length,
    avgCost: list.length ? list.reduce((sum, item) => sum + item.estimatedCost, 0) / list.length : 0
  }
})

onMounted(async () => {
  table.setQuery({
    keyword: String(route.query.keyword || ''),
    status: String(route.query.status || ''),
    frozen: String(route.query.frozen || ''),
    productType: String(route.query.productType || '')
  })

  loading.value = true
  try {
    rows.value = await getProductList()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <PageContainer title="产品管理" description="统一承载新产品产品线与型号扩展视图，关联测试项、BOM 主版本、预估成本、资料冻结状态和审批轨迹。">
    <template #actions>
      <el-button @click="router.push('/sku-view')">SKU 视图</el-button>
      <el-button type="primary" @click="router.push('/products/create')">新建产品</el-button>
    </template>

    <section class="metric-grid">
      <div class="metric-card">
        <p class="metric-card__label">当前产品数</p>
        <p class="metric-card__value">{{ stats.total }}</p>
        <span class="metric-card__trend">当前筛选结果</span>
      </div>
      <button class="metric-card summary-button" type="button" @click="table.setQuery({ ...table.query, productType: 'product_line' })">
        <p class="metric-card__label">产品线</p>
        <p class="metric-card__value">{{ stats.lines }}</p>
        <span class="metric-card__trend">聚焦系列级开发</span>
      </button>
      <button class="metric-card summary-button" type="button" @click="router.push('/sku-view')">
        <p class="metric-card__label">SKU 视图项</p>
        <p class="metric-card__value">{{ stats.sku }}</p>
        <span class="metric-card__trend">查看机型 / 颜色扩展</span>
      </button>
      <div class="metric-card">
        <p class="metric-card__label">平均预估成本</p>
        <p class="metric-card__value metric-card__value--small">{{ formatAmount(stats.avgCost) }}</p>
        <span class="metric-card__trend">按当前列表计算</span>
      </div>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', status: '', frozen: '', productType: '' })"
    />

    <section class="page-panel" v-loading="loading">
      <el-table :data="table.pagedRows.value" border stripe>
        <el-table-column prop="productCode" label="产品编码" min-width="180" />
        <el-table-column label="产品信息" min-width="240">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.productName }}</strong>
              <span class="subtle-text">{{ row.seriesName }} / {{ row.model }} / {{ row.color }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="150">
          <template #default="{ row }">
            <el-tag :type="row.productType === 'product_line' ? 'primary' : 'success'" effect="light">
              {{ row.productType === 'product_line' ? '产品线' : 'SKU 视图' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentStage" label="当前阶段" min-width="150" />
        <el-table-column label="测试项" width="110">
          <template #default="{ row }">{{ row.testItemCount }} 项</template>
        </el-table-column>
        <el-table-column prop="activeBomVersion" label="BOM 主版本" width="130" />
        <el-table-column label="预估成本" width="140">
          <template #default="{ row }">{{ formatAmount(row.estimatedCost, row.estimatedCostCurrency) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" object-type="product" />
          </template>
        </el-table-column>
        <el-table-column label="资料完整率" min-width="180">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column prop="ownerUserName" label="责任人" width="110" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/products/${row.productId}`)">详情</el-button>
            <el-button link @click="router.push(`/products/${row.productId}/edit`)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
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

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-card__value--small {
  font-size: 20px;
}
</style>
