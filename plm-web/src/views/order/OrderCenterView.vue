<script setup lang="ts">
import { computed, ref } from 'vue'

import PageContainer from '@/components/PageContainer/index.vue'

type OrderStatus = 'draft' | 'confirmed' | 'in_production' | 'completed' | 'closed'
type OrderSource = 'customer' | 'market_internal'

interface OrderRow {
  orderId: number
  systemCode: string
  dingTalkCode: string
  typeLabel: string
  productName: string
  status: OrderStatus
  source: OrderSource
  createdAt: string
}

type OrderProgressFilter = 'all' | 'in_progress' | 'history'
type OrderSourceFilter = 'all' | 'customer' | 'market_internal'

const progressFilter = ref<OrderProgressFilter>('all')
const sourceFilter = ref<OrderSourceFilter>('all')
const keyword = ref('')

const rows: OrderRow[] = [
  {
    orderId: 1,
    systemCode: 'ORD-SAMPLE-0603',
    dingTalkCode: '20260603-001',
    typeLabel: '客户需求',
    productName: '超队 3.0',
    status: 'confirmed',
    source: 'customer',
    createdAt: '06-03'
  },
  {
    orderId: 2,
    systemCode: 'ORD-DEV-0605',
    dingTalkCode: '20260605-003',
    typeLabel: '市场需求',
    productName: '亮甲 3.0',
    status: 'in_production',
    source: 'market_internal',
    createdAt: '06-05'
  },
  {
    orderId: 3,
    systemCode: 'ORD-SAMPLE-0520',
    dingTalkCode: '20260520-008',
    typeLabel: '客户需求',
    productName: '骑士 2.0',
    status: 'completed',
    source: 'customer',
    createdAt: '05-20'
  },
  {
    orderId: 4,
    systemCode: 'ORD-DEV-0515',
    dingTalkCode: '20260515-012',
    typeLabel: '市场需求',
    productName: '圣宿 Case',
    status: 'closed',
    source: 'market_internal',
    createdAt: '05-15'
  }
]

const filteredRows = computed(() => {
  const search = keyword.value.trim().toLowerCase()

  return rows.filter((row) => {
    const progressMatched =
      progressFilter.value === 'all' ||
      (progressFilter.value === 'in_progress' && ['draft', 'confirmed', 'in_production'].includes(row.status)) ||
      (progressFilter.value === 'history' && ['completed', 'closed'].includes(row.status))

    const sourceMatched =
      sourceFilter.value === 'all' || row.source === sourceFilter.value

    const keywordMatched =
      !search ||
      row.systemCode.toLowerCase().includes(search) ||
      row.dingTalkCode.toLowerCase().includes(search) ||
      row.productName.toLowerCase().includes(search) ||
      row.typeLabel.toLowerCase().includes(search)

    return progressMatched && sourceMatched && keywordMatched
  })
})

const metrics = computed(() => [
  { label: '进行中订单', value: rows.filter((row) => ['draft', 'confirmed', 'in_production'].includes(row.status)).length, hint: '默认优先关注当前业务' },
  { label: '历史订单', value: rows.filter((row) => ['completed', 'closed'].includes(row.status)).length, hint: '支持按来源追溯' },
  { label: '客户需求', value: rows.filter((row) => row.source === 'customer').length, hint: '客户直接触发的订单需求' },
  { label: '市场需求', value: rows.filter((row) => row.source === 'market_internal').length, hint: '内部判断与市场驱动需求' }
])

function statusLabel(status: OrderStatus) {
  return {
    draft: '草稿',
    confirmed: '已确认',
    in_production: '进行中',
    completed: '已完成',
    closed: '已关闭'
  }[status]
}

function statusTag(status: OrderStatus) {
  return {
    draft: 'info',
    confirmed: 'warning',
    in_production: 'primary',
    completed: 'success',
    closed: 'info'
  }[status]
}
</script>

<template>
  <PageContainer title="需求订单">
    <section class="page-panel order-toolbar">
      <el-select v-model="progressFilter" class="order-toolbar__select" placeholder="选择订单阶段">
        <el-option label="全部" value="all" />
        <el-option label="进行中订单" value="in_progress" />
        <el-option label="历史订单" value="history" />
      </el-select>

      <el-select v-model="sourceFilter" class="order-toolbar__select" placeholder="选择需求来源">
        <el-option label="全部" value="all" />
        <el-option label="客户需求" value="customer" />
        <el-option label="市场需求" value="market_internal" />
      </el-select>

      <el-input
        v-model="keyword"
        clearable
        class="order-toolbar__search"
        placeholder="搜索系统编号 / 钉钉编号 / 产品 / 类型"
      />
    </section>

    <section class="page-panel">
      <div class="toolbar-row">
        <div>
          <h3 class="section-title">订单列表</h3>
          <p class="page-panel-desc">
            {{ progressFilter === 'in_progress' ? '只显示当前仍在推进的订单，并支持按需求来源筛选。' : progressFilter === 'history' ? '只显示已完成或已关闭的历史订单，并支持按需求来源统一追溯。' : '展示全部需求订单，并支持按阶段、来源和关键字组合筛选。' }}
          </p>
        </div>
        <el-tag effect="light">{{ filteredRows.length }} 条</el-tag>
      </div>

      <el-table :data="filteredRows" border stripe size="small">
        <el-table-column prop="systemCode" label="系统编号" min-width="170" />
        <el-table-column prop="dingTalkCode" label="钉钉审批编号" min-width="160" />
        <el-table-column prop="typeLabel" label="类型" width="120" />
        <el-table-column prop="productName" label="关联产品" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="100" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default>
            <el-dropdown trigger="click">
              <el-button link type="primary">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item>查看详情</el-dropdown-item>
                  <el-dropdown-item>查看审批</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </PageContainer>
</template>

<style scoped>
.order-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.order-toolbar__select {
  width: 180px;
}

.order-toolbar__search {
  max-width: 360px;
  margin-left: auto;
}

@media (max-width: 900px) {
  .order-toolbar__search {
    max-width: none;
    margin-left: 0;
    width: 100%;
  }
}
</style>
