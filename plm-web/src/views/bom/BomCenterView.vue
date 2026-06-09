<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getBomCenterRows } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import type { SearchField } from '@/types/common'
import type { BomCenterRow } from '@/types/foundation'
import { formatAmount } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const rows = ref<BomCenterRow[]>([])
const keyword = ref('')
const bomType = ref('')

const searchFields: SearchField[] = [
  { prop: 'keyword', label: 'BOM 搜索', type: 'input', placeholder: '产品编码 / 产品名 / BOM 类型' },
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
  }
]

const filteredRows = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const typeMatched = !bomType.value || row.bomType === bomType.value
    const keywordMatched =
      !search ||
      row.productCode.toLowerCase().includes(search) ||
      row.productName.toLowerCase().includes(search) ||
      row.bomType.toLowerCase().includes(search)
    return typeMatched && keywordMatched
  })
})

const metrics = computed(() => [
  { label: 'BOM 版本', value: rows.value.length, hint: '只保留列表比对，不做图表' },
  { label: '评审中', value: rows.value.filter((item) => item.status === 'reviewing').length, hint: '优先回到产品详情处理' },
  { label: '已发布', value: rows.value.filter((item) => item.status === 'released').length, hint: '可直接追溯历史版本' },
  {
    label: '当前均值',
    value: formatAmount(rows.value.reduce((sum, item) => sum + item.totalCost, 0) / (rows.value.length || 1)),
    hint: '按当前 BOM 版本总成本口径'
  }
])

async function loadData() {
  loading.value = true
  try {
    rows.value = await getBomCenterRows()
  } finally {
    loading.value = false
  }
}

function openProduct(productId: number) {
  router.push(`/products/${productId}`)
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="BOM 管理" description="BOM 页面只保留版本列表和当前成本信息。版本成本对比回到产品详情页里看。">
    <template #actions>
      <el-button @click="router.push('/products')">产品管理</el-button>
      <el-button type="primary" @click="router.push('/sku-view')">SKU 视图</el-button>
    </template>

    <section class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value metric-card__value--small">{{ metric.value }}</p>
        <span class="metric-card__trend">{{ metric.hint }}</span>
      </div>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="{ keyword, bomType }"
      @search="
        (value) => {
          keyword = String(value.keyword || '')
          bomType = String(value.bomType || '')
        }
      "
      @reset="
        () => {
          keyword = ''
          bomType = ''
        }
      "
    />

    <section class="page-panel" v-loading="loading">
      <div class="toolbar-row">
        <div>
          <h3 class="section-title">BOM 版本列表</h3>
          <p class="page-panel-desc">按产品直接查看当前版本、物料成本、工艺成本和供应备注。</p>
        </div>
        <el-tag effect="light">{{ filteredRows.length }} 条</el-tag>
      </div>

      <el-table :data="filteredRows" border stripe>
        <el-table-column prop="productCode" label="产品编码" min-width="160" />
        <el-table-column prop="productName" label="产品名称" min-width="220" />
        <el-table-column prop="bomType" label="BOM 类型" width="110" />
        <el-table-column prop="currentVersion" label="当前版本" width="110" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" object-type="product" />
          </template>
        </el-table-column>
        <el-table-column label="物料成本" width="140">
          <template #default="{ row }">{{ formatAmount(row.materialCost) }}</template>
        </el-table-column>
        <el-table-column label="工艺成本" width="140">
          <template #default="{ row }">{{ formatAmount(row.processCost) }}</template>
        </el-table-column>
        <el-table-column label="总成本" width="140">
          <template #default="{ row }">{{ formatAmount(row.totalCost) }}</template>
        </el-table-column>
        <el-table-column prop="supplierNote" label="供应备注" min-width="220" />
        <el-table-column prop="updatedAt" label="更新时间" width="140" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openProduct(row.productId)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </PageContainer>
</template>

<style scoped>
.metric-card__value--small {
  font-size: 20px;
}
</style>
