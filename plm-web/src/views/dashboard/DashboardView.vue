<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getApprovalTasks } from '@/api/modules/approval'
import { getDashboardSnapshot } from '@/api/modules/dashboard'
import { getProductList } from '@/api/modules/product'
import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useUserStore } from '@/stores/user'
import type { ApprovalTask, DashboardRisk, DashboardSnapshot } from '@/types/common'
import type { ProductSummary } from '@/types/product'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const snapshot = ref<DashboardSnapshot | null>(null)
const products = ref<ProductSummary[]>([])
const approvalTasks = ref<ApprovalTask[]>([])

const currentUserName = computed(() => userStore.profile?.userName || '')

const inProgressProducts = computed(() =>
  products.value
    .filter((item) => ['developing', 'reviewing'].includes(item.status))
    .sort((a, b) => b.completionRate - a.completionRate)
    .slice(0, 5)
)

const myPendingTasks = computed(() =>
  approvalTasks.value
    .filter((item) => item.status === 'pending' && item.approver === currentUserName.value)
    .slice(0, 5)
)

const overdueRisks = computed(() => (snapshot.value?.risks || []).slice(0, 4))

const quickActions = [
  { label: '新建产品', hint: '新产品线或新型号需求建档', path: '/products/create' },
  { label: '项目管理', hint: '查看进行中 / 已完成 / 已放弃', path: '/projects?tab=in_progress' },
  { label: 'BOM 管理', hint: '处理版本、替代料与成本确认', path: '/bom' },
  { label: '文件中心', hint: '检查冻结资料与版本附件', path: '/files' }
]

const topMetrics = computed(() => {
  const pendingMine = myPendingTasks.value.length
  const inProgress = inProgressProducts.value.length
  const overdue = overdueRisks.value.length
  const frozenGap = products.value.filter((item) => !item.frozenFlag && item.status !== 'released').length

  return [
    { label: '进行中的产品', value: inProgress, hint: '点击进入项目管理进行中视图', path: '/projects?tab=in_progress' },
    { label: '我的待办', value: pendingMine, hint: '只显示当前登录人的待处理节点', path: '/approval-tasks?status=pending' },
    { label: '逾期预警', value: overdue, hint: '优先处理高风险节点', path: '/files' },
    { label: '待冻结资料', value: frozenGap, hint: '检查图纸、质量与客户确认资料', path: '/products?frozen=unfrozen' }
  ]
})

async function loadData() {
  loading.value = true
  try {
    const [dashboardSnapshot, productRows, taskRows] = await Promise.all([
      getDashboardSnapshot(),
      getProductList(),
      getApprovalTasks()
    ])

    snapshot.value = dashboardSnapshot
    products.value = productRows
    approvalTasks.value = taskRows
  } finally {
    loading.value = false
  }
}

function open(path: string) {
  router.push(path)
}

function openTask(task: ApprovalTask) {
  router.push(task.targetPath || '/approval-tasks')
}

function openRisk(risk: DashboardRisk) {
  router.push(risk.targetPath)
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="工作台" description="只保留正在进行、我的待办、逾期预警和快捷操作，让首页成为清晰的行动入口。">
    <div v-loading="loading" class="page-stack">
      <section class="page-panel dashboard-hero">
        <div>
          <h2 class="page-panel-title">{{ snapshot?.hero.title || 'Yuewei PLM 工作台' }}</h2>
          <p class="page-panel-desc">
            {{ snapshot?.hero.subtitle || '围绕新产品开发、样品验证、BOM 确认、资料冻结与发布节点推进协同。' }}
          </p>
        </div>
        <el-tag effect="light" size="large">{{ currentUserName || '当前用户' }}</el-tag>
      </section>

      <section class="metric-grid">
        <button
          v-for="metric in topMetrics"
          :key="metric.label"
          class="metric-card dashboard-button"
          type="button"
          @click="open(metric.path)"
        >
          <p class="metric-card__label">{{ metric.label }}</p>
          <p class="metric-card__value">{{ metric.value }}</p>
          <div class="card-footer">
            <span class="metric-card__trend">{{ metric.hint }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </button>
      </section>

      <section class="split-grid dashboard-main">
        <article class="page-panel">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">进行中的产品</h3>
              <p class="page-panel-desc">首页只保留当前推进中的对象，点击直接进入对应产品详情。</p>
            </div>
            <el-button text type="primary" @click="open('/projects?tab=in_progress')">查看全部项目</el-button>
          </div>
          <div class="page-stack">
            <button
              v-for="item in inProgressProducts"
              :key="item.productId"
              class="list-button"
              type="button"
              @click="open(`/products/${item.productId}`)"
            >
              <div class="toolbar-row">
                <div class="cell-stack">
                  <strong>{{ item.productName }}</strong>
                  <span class="subtle-text">{{ item.productCode }} / {{ item.seriesName }}</span>
                </div>
                <StatusTag :status="item.status" object-type="product" />
              </div>
              <div class="progress-row">
                <span class="subtle-text">{{ item.currentStage }}</span>
                <span class="subtle-text">{{ Math.round(item.completionRate * 100) }}%</span>
              </div>
              <el-progress :percentage="Math.round(item.completionRate * 100)" :stroke-width="8" />
              <div class="card-footer">
                <span class="subtle-text">{{ item.ownerUserName }} / BOM {{ item.activeBomVersion }}</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </button>
          </div>
        </article>

        <article class="page-panel">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">我的待办</h3>
              <p class="page-panel-desc">只显示当前登录人的待审批或待处理节点。</p>
            </div>
            <el-button text type="primary" @click="open('/approval-tasks')">审批中心</el-button>
          </div>
          <el-empty v-if="!myPendingTasks.length" description="当前没有待处理节点" />
          <div v-else class="page-stack">
            <button
              v-for="task in myPendingTasks"
              :key="task.taskId"
              class="list-button"
              type="button"
              @click="openTask(task)"
            >
              <div class="toolbar-row">
                <strong>{{ task.nodeName }}</strong>
                <el-tag type="warning" effect="light">待处理</el-tag>
              </div>
              <p class="page-panel-desc">{{ task.objectName }}</p>
              <div class="card-footer">
                <span class="subtle-text">{{ task.initiator }} 发起 / 截止 {{ task.dueDate }}</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </button>
          </div>
        </article>
      </section>

      <section class="split-grid dashboard-main">
        <article class="page-panel">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">逾期预警</h3>
              <p class="page-panel-desc">点击风险后回到对应项目节点或资料聚合页。</p>
            </div>
            <el-button text type="primary" @click="open('/products?frozen=unfrozen')">查看冻结缺口</el-button>
          </div>
          <div class="page-stack">
            <button
              v-for="risk in overdueRisks"
              :key="risk.title"
              class="list-button"
              type="button"
              @click="openRisk(risk)"
            >
              <div class="toolbar-row">
                <strong>{{ risk.title }}</strong>
                <el-tag :type="risk.level === '高' ? 'danger' : risk.level === '中' ? 'warning' : 'info'" effect="light">
                  {{ risk.level }}
                </el-tag>
              </div>
              <p class="page-panel-desc">责任人：{{ risk.owner }}</p>
              <div class="card-footer">
                <span class="subtle-text">{{ risk.action }}</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </button>
          </div>
        </article>

        <article class="page-panel">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">快捷操作</h3>
              <p class="page-panel-desc">保留高频入口，不在首页堆报表。</p>
            </div>
            <el-button text type="primary" @click="open('/products')">产品管理</el-button>
          </div>
          <div class="quick-grid">
            <button
              v-for="action in quickActions"
              :key="action.label"
              class="quick-button"
              type="button"
              @click="open(action.path)"
            >
              <strong>{{ action.label }}</strong>
              <span class="subtle-text">{{ action.hint }}</span>
            </button>
          </div>
        </article>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.dashboard-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-main {
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.95fr);
}

.dashboard-button,
.list-button,
.quick-button {
  width: 100%;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.dashboard-button:hover,
.list-button:hover,
.quick-button:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.list-button,
.quick-button {
  padding: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quick-button {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 104px;
  justify-content: space-between;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-row,
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.progress-row {
  margin: 10px 0 6px;
}

.card-footer {
  margin-top: 10px;
}

@media (max-width: 768px) {
  .dashboard-hero {
    flex-direction: column;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
