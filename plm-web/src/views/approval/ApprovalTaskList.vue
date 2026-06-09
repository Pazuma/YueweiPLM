<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getApprovalTasks } from '@/api/modules/approval'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { ApprovalTask, SearchField } from '@/types/common'

const route = useRoute()
const router = useRouter()
const rows = ref<ApprovalTask[]>([])
const loading = ref(false)

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '业务对象 / 审批节点 / 发起人' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '待处理', value: 'pending' },
      { label: '已通过', value: 'approved' },
      { label: '已驳回', value: 'rejected' }
    ]
  }
]

const table = useTable(rows, ['objectName', 'nodeName', 'initiator', 'approver'], (row, filters) => {
  const status = String(filters.status || '')
  return !status || row.status === status
})

const summary = computed(() => ({
  pending: rows.value.filter((item) => item.status === 'pending').length,
  approved: rows.value.filter((item) => item.status === 'approved').length,
  rejected: rows.value.filter((item) => item.status === 'rejected').length
}))

async function loadTasks() {
  loading.value = true
  try {
    rows.value = await getApprovalTasks()
  } finally {
    loading.value = false
  }
}

function openTask(task: ApprovalTask) {
  router.push(task.targetPath || '/approval-tasks')
}

onMounted(async () => {
  table.setQuery({
    keyword: String(route.query.keyword || ''),
    status: String(route.query.status || '')
  })
  await loadTasks()
})
</script>

<template>
  <PageContainer title="审批中心" description="统一查看待审批节点，并直接跳转到对应产品、订单或资料节点处理。">
    <section class="metric-grid">
      <button class="metric-card summary-button" type="button" @click="table.setQuery({ ...table.query, status: 'pending' })">
        <p class="metric-card__label">待处理</p>
        <p class="metric-card__value">{{ summary.pending }}</p>
        <span class="metric-card__trend">优先处理当前审批</span>
      </button>
      <button class="metric-card summary-button" type="button" @click="table.setQuery({ ...table.query, status: 'approved' })">
        <p class="metric-card__label">已通过</p>
        <p class="metric-card__value">{{ summary.approved }}</p>
        <span class="metric-card__trend">查看已完成流转</span>
      </button>
      <button class="metric-card summary-button" type="button" @click="table.setQuery({ ...table.query, status: 'rejected' })">
        <p class="metric-card__label">已驳回</p>
        <p class="metric-card__value">{{ summary.rejected }}</p>
        <span class="metric-card__trend">定位异常节点</span>
      </button>
      <button class="metric-card summary-button" type="button" @click="table.resetQuery({ keyword: '', status: '' })">
        <p class="metric-card__label">全部任务</p>
        <p class="metric-card__value">{{ rows.length }}</p>
        <span class="metric-card__trend">恢复完整列表</span>
      </button>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', status: '' })"
    />

    <section class="page-panel" v-loading="loading">
      <el-table :data="table.pagedRows.value" border stripe @row-click="openTask">
        <el-table-column prop="objectName" label="业务对象" min-width="260" />
        <el-table-column prop="nodeName" label="审批节点" min-width="180" />
        <el-table-column prop="initiator" label="发起人" width="120" />
        <el-table-column prop="approver" label="审批人" width="120" />
        <el-table-column prop="dueDate" label="截止日期" width="140" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" object-type="product" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openTask(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="toolbar-row pager-row">
        <span class="subtle-text">共 {{ table.filteredRows.value.length }} 条</span>
        <el-pagination
          v-model:current-page="table.currentPage.value"
          v-model:page-size="table.pageSize.value"
          layout="prev, pager, next"
          :total="table.filteredRows.value.length"
        />
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

.pager-row {
  margin-top: 16px;
}
</style>
