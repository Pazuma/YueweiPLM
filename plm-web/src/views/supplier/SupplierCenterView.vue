<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getSupplierCenterSnapshot } from '@/api/modules/supplier'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type { SupplierCenterSnapshot, SupplierDetail, SupplierSupplyRecord } from '@/types/supplier'
import { formatAmount, formatDate } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const snapshot = ref<SupplierCenterSnapshot | null>(null)
const activeSupplierId = ref<number | null>(null)
const rows = computed(() => snapshot.value?.suppliers || [])

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '供应商编码 / 名称 / 联系人' },
  {
    prop: 'category',
    label: '供应品类',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '原材料', value: '原材料' },
      { label: '功能件', value: '功能件' },
      { label: '板材', value: '板材' },
      { label: '包材', value: '包材' },
      { label: '模具', value: '模具' },
      { label: '治具', value: '治具' }
    ]
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '启用', value: 'active' },
      { label: '草稿', value: 'draft' },
      { label: '停用', value: 'inactive' }
    ]
  }
]

const table = useTable(rows, ['supplierCode', 'supplierName', 'contactPerson', 'region'], (row, filters) => {
  const status = String(filters.status || '')
  const category = String(filters.category || '')
  if (status && row.status !== status) return false
  if (category && !row.supplyCategories.includes(category)) return false
  return true
})

const activeSupplier = computed(() => {
  const list = table.filteredRows.value
  if (!list.length) return null
  return list.find((item) => item.supplierId === activeSupplierId.value) || list[0]
})

const metrics = computed(() => {
  const suppliers = snapshot.value?.suppliers || []
  return [
    { label: '合作供应商', value: suppliers.length, hint: '材料、包材、模具统一维护' },
    {
      label: '交付风险',
      value: suppliers.filter((item) => item.deliveryRisk === '高').length,
      hint: '优先跟踪影响打样与发布的交付问题'
    },
    {
      label: '资质缺口',
      value: suppliers.reduce((sum, item) => sum + item.qualificationFiles.filter((file) => file.statusLabel !== '有效').length, 0),
      hint: '需要补齐合规文件与有效期管理'
    },
    {
      label: '参与项目',
      value: suppliers.reduce((sum, item) => sum + item.relatedProjects.length, 0),
      hint: '聚合查看历史合作项目与供应记录'
    }
  ]
})

async function loadSnapshot() {
  loading.value = true
  try {
    snapshot.value = await getSupplierCenterSnapshot()
    activeSupplierId.value = snapshot.value.suppliers[0]?.supplierId ?? null
  } finally {
    loading.value = false
  }
}

function openTarget(path: string) {
  router.push(path)
}

function selectSupplier(row: SupplierDetail) {
  activeSupplierId.value = row.supplierId
}

function supplyTypeLabel(type: SupplierSupplyRecord['supplyType']) {
  if (type === 'material') return '物料'
  if (type === 'tooling') return '模具/治具'
  return '包材'
}

onMounted(loadSnapshot)
</script>

<template>
  <PageContainer
    title="供应商管理"
    description="按优化文档收敛为统一入口：列表看基础信息，详情只看供应记录、参与项目与资质文件，不展示内部报价审批内容。"
  >
    <template #actions>
      <el-button @click="router.push('/inventories')">物料 / 模具</el-button>
      <el-button type="primary" @click="router.push('/products')">产品管理</el-button>
    </template>

    <section class="metric-grid" v-loading="loading">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <span class="metric-card__trend">{{ metric.hint }}</span>
      </div>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', category: '', status: '' })"
    />

    <section class="split-grid">
      <article class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">供应商列表</h3>
            <p class="page-panel-desc">点击任意行切换右侧详情与供应记录。</p>
          </div>
          <el-tag effect="light">统一入口</el-tag>
        </div>
        <el-table
          :data="table.pagedRows.value"
          border
          stripe
          highlight-current-row
          v-loading="loading"
          @row-click="(row: SupplierDetail) => selectSupplier(row)"
        >
          <el-table-column prop="supplierCode" label="供应商编码" min-width="170" />
          <el-table-column label="供应商" min-width="220">
            <template #default="{ row }">
              <div class="cell-stack">
                <strong>{{ row.supplierName }}</strong>
                <span class="subtle-text">{{ row.shortName }} / {{ row.region }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="供应品类" min-width="180">
            <template #default="{ row }">
              <div class="tag-wrap">
                <el-tag v-for="item in row.supplyCategories" :key="item" effect="light">{{ item }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="contactPerson" label="联系人" width="120" />
          <el-table-column prop="contactPhone" label="联系电话" min-width="150" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <StatusTag :status="row.status" object-type="customer" />
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article v-if="activeSupplier" class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">{{ activeSupplier.supplierName }}</h3>
            <p class="page-panel-desc">{{ activeSupplier.supplierCode }} / {{ activeSupplier.cooperationLevel }}</p>
          </div>
          <StatusTag :status="activeSupplier.status" object-type="customer" />
        </div>

        <section class="detail-grid">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="联系人">{{ activeSupplier.contactPerson }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ activeSupplier.contactPhone }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ activeSupplier.contactEmail }}</el-descriptions-item>
            <el-descriptions-item label="付款条件">{{ activeSupplier.paymentTerm }}</el-descriptions-item>
          </el-descriptions>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="区域">{{ activeSupplier.region }}</el-descriptions-item>
            <el-descriptions-item label="交付风险">{{ activeSupplier.deliveryRisk }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(activeSupplier.updatedAt, 'YYYY-MM-DD HH:mm') }}</el-descriptions-item>
            <el-descriptions-item label="供应范围">
              {{ activeSupplier.supplyCategories.join(' / ') }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <el-tabs class="section-gap">
          <el-tab-pane label="供应记录">
            <el-table :data="activeSupplier.supplyRecords" border stripe>
              <el-table-column label="供应类型" width="120">
                <template #default="{ row }">
                  {{ supplyTypeLabel(row.supplyType) }}
                </template>
              </el-table-column>
              <el-table-column prop="itemCode" label="编码" min-width="150" />
              <el-table-column prop="itemName" label="名称" min-width="180" />
              <el-table-column prop="relatedProduct" label="关联产品" min-width="180" />
              <el-table-column label="单价" width="130">
                <template #default="{ row }">
                  {{ formatAmount(row.unitPrice, row.currency) }}
                </template>
              </el-table-column>
              <el-table-column prop="lastDeliveryDate" label="最近交付" width="120" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <StatusTag :status="row.status" object-type="inventory" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openTarget(row.targetPath)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="参与项目">
            <div class="page-stack">
              <button
                v-for="item in activeSupplier.relatedProjects"
                :key="item.projectCode"
                class="risk-card summary-button"
                type="button"
                @click="openTarget(item.targetPath)"
              >
                <div class="toolbar-row">
                  <strong>{{ item.projectName }}</strong>
                  <el-tag effect="light">{{ item.stage }}</el-tag>
                </div>
                <p class="page-panel-desc">{{ item.projectCode }}</p>
                <div class="metric-card__footer">
                  <span class="subtle-text">{{ item.roleSummary }}</span>
                  <el-icon><ArrowRight /></el-icon>
                </div>
              </button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="资质文件">
            <el-table :data="activeSupplier.qualificationFiles" border stripe>
              <el-table-column prop="fileName" label="文件名" min-width="220" />
              <el-table-column prop="fileType" label="类型" width="130" />
              <el-table-column prop="validUntil" label="有效期" width="140" />
              <el-table-column prop="statusLabel" label="状态" width="120" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </article>
    </section>

    <section class="page-panel">
      <h3 class="section-title">风险提醒</h3>
      <div class="page-stack">
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
      </div>
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

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.risk-card {
  padding: 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.section-gap {
  margin-top: var(--plm-space-4);
}
</style>
