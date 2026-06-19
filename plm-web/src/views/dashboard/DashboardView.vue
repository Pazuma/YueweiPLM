<script setup lang="ts">
import { ArrowRight, Document, Plus, Promotion, Tickets } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useUserStore } from '@/stores/user'

interface DashboardProductItem {
  productId: number
  productName: string
  productCode: string
  seriesName: string
  currentStage: string
  ownerUserName: string
  activeBomVersion: string
  completionRate: number
  status: 'developing' | 'reviewing'
}

interface DashboardTaskItem {
  taskId: number
  nodeName: string
  objectName: string
  initiator: string
  dueDate: string
  targetPath: string
}

interface DashboardRiskItem {
  title: string
  stage: string
  plannedDate: string
  overdueDays: number
  owner: string
  targetPath: string
}

interface DashboardFreezeItem {
  productId: number
  productName: string
  versionNo: string
  missingItems: string[]
  ownerUserName: string
  dueDate: string
  targetPath: string
}

type DashboardViewKey = 'products' | 'tasks' | 'risks' | 'freeze'

interface DashboardMetric {
  key: DashboardViewKey
  label: string
  value: number
  hint: string
}

interface QuickActionItem {
  label: string
  path: string
  icon: 'plus' | 'promotion' | 'document' | 'tickets'
}

const router = useRouter()
const userStore = useUserStore()

const currentUserName = computed(() => userStore.profile?.userName || '')
const activeMetricView = ref<DashboardViewKey>('products')

const inProgressProducts = computed<DashboardProductItem[]>(() => [
  {
    productId: 101,
    productName: '超队 3.0 磁吸手机壳',
    productCode: 'PRD-CD30-001',
    seriesName: '超队 3.0',
    currentStage: '红样测试',
    ownerUserName: '张敏',
    activeBomVersion: 'A.3',
    completionRate: 0.82,
    status: 'reviewing'
  },
  {
    productId: 102,
    productName: '超队 3.0 iPhone18 黑色',
    productCode: 'PRD-CD30-IP18-BLK',
    seriesName: '超队 3.0',
    currentStage: '差异测试验证',
    ownerUserName: '刘浩',
    activeBomVersion: 'A.2',
    completionRate: 0.76,
    status: 'reviewing'
  },
  {
    productId: 103,
    productName: '超队 3.0 iPhone18 蓝色',
    productCode: 'PRD-CD30-IP18-BLU',
    seriesName: '超队 3.0',
    currentStage: '样品确认',
    ownerUserName: '刘浩',
    activeBomVersion: 'A.1',
    completionRate: 0.54,
    status: 'developing'
  }
])

const myPendingTasks = computed<DashboardTaskItem[]>(() => [
  {
    taskId: 1,
    nodeName: 'BOM 会签',
    objectName: '超队 3.0 iPhone18 黑色',
    initiator: '刘浩',
    dueDate: '2026-06-10',
    targetPath: '/products/102'
  },
  {
    taskId: 2,
    nodeName: '资料冻结确认',
    objectName: '超队 3.0 磁吸手机壳',
    initiator: '张敏',
    dueDate: '2026-06-11',
    targetPath: '/products/101'
  },
  {
    taskId: 3,
    nodeName: '样品确认',
    objectName: '超队 3.0 iPhone18 蓝色',
    initiator: '刘浩',
    dueDate: '2026-06-12',
    targetPath: '/products/103'
  }
])

const overdueRisks = computed<DashboardRiskItem[]>(() => [
  {
    title: '亮甲 3.0',
    stage: '模具阶段',
    plannedDate: '06-05',
    overdueDays: 5,
    owner: '李工程',
    targetPath: '/products/104'
  },
  {
    title: '超队 3.0 iPhone18 黑色',
    stage: '半成品阶段',
    plannedDate: '06-08',
    overdueDays: 2,
    owner: '张经理',
    targetPath: '/products/102'
  },
  {
    title: '超队 3.0 磁吸手机壳',
    stage: '资料冻结',
    plannedDate: '06-08',
    overdueDays: 1,
    owner: '张敏',
    targetPath: '/products/101'
  }
])

const pendingFreezeItems = computed<DashboardFreezeItem[]>(() => [
  {
    productId: 101,
    productName: '超队 3.0 磁吸手机壳',
    versionNo: 'A.3',
    missingItems: ['客户确认样', 'SOP'],
    ownerUserName: '张敏',
    dueDate: '2026-06-11',
    targetPath: '/products/101'
  },
  {
    productId: 102,
    productName: '超队 3.0 iPhone18 黑色',
    versionNo: 'A.2',
    missingItems: ['图纸冻结', '质量测试记录'],
    ownerUserName: '刘浩',
    dueDate: '2026-06-12',
    targetPath: '/products/102'
  },
  {
    productId: 103,
    productName: '超队 3.0 iPhone18 蓝色',
    versionNo: 'A.1',
    missingItems: ['BOM 会签', '客户颜色确认'],
    ownerUserName: '刘浩',
    dueDate: '2026-06-13',
    targetPath: '/products/103'
  },
  {
    productId: 104,
    productName: '亮甲 3.0',
    versionNo: 'B.1',
    missingItems: ['模具验收', '红样报告'],
    ownerUserName: '李工程',
    dueDate: '2026-06-14',
    targetPath: '/products/104'
  }
])

const quickActions: QuickActionItem[] = [
  { label: '新建产品', path: '/products/create', icon: 'plus' },
  { label: '项目管理', path: '/projects?tab=in_progress', icon: 'promotion' },
  { label: '文件中心', path: '/files', icon: 'document' },
  { label: '需求订单', path: '/orders', icon: 'tickets' }
]

const topMetrics = computed<DashboardMetric[]>(() => [
  {
    key: 'products',
    label: '进行中的产品',
    value: inProgressProducts.value.length,
    hint: '点击查看当前推进中的产品列表'
  },
  {
    key: 'tasks',
    label: '我的待办',
    value: myPendingTasks.value.length,
    hint: '点击查看我的待处理任务'
  },
  {
    key: 'risks',
    label: '逾期预警',
    value: overdueRisks.value.length,
    hint: '点击查看逾期或风险项目'
  },
  {
    key: 'freeze',
    label: '待冻结资料',
    value: pendingFreezeItems.value.length,
    hint: '点击查看待冻结的资料清单'
  }
])

const activeMetric = computed(() => topMetrics.value.find((item) => item.key === activeMetricView.value) || topMetrics.value[0])

const activeSectionTitle = computed(() => {
  switch (activeMetricView.value) {
    case 'tasks':
      return '我的待办'
    case 'risks':
      return '逾期预警'
    case 'freeze':
      return '待冻结资料'
    default:
      return '进行中的产品'
  }
})

const activeSectionDesc = computed(() => {
  switch (activeMetricView.value) {
    case 'tasks':
      return '只显示当前登录人的待处理节点，避免工作台内容发散。'
    case 'risks':
      return '只展示当前选中的逾期风险列表，便于集中处理阻塞。'
    case 'freeze':
      return '集中查看哪些产品版本还没有完成冻结资料，减少上下翻找。'
    default:
      return '只保留当前推进中的对象，点击后直接进入对应产品详情。'
  }
})

const activeSectionActionLabel = computed(() => {
  switch (activeMetricView.value) {
    case 'tasks':
      return '审批中心'
    case 'risks':
      return '风险详情'
    case 'freeze':
      return '冻结缺口'
    default:
      return '查看全部项目'
  }
})

const activeSectionActionPath = computed(() => {
  switch (activeMetricView.value) {
    case 'tasks':
      return '/approval-tasks'
    case 'risks':
      return '/products?risk=overdue'
    case 'freeze':
      return '/products?frozen=unfrozen'
    default:
      return '/projects?tab=in_progress'
  }
})

function selectMetricView(view: DashboardViewKey) {
  activeMetricView.value = view
}

function open(path: string) {
  router.push(path)
}
</script>

<template>
  <PageContainer title="工作台" description="首页直接展示要处理的问题、要推进的对象和最高频入口，不再只做抽象概览。">
    <section class="page-panel dashboard-toolbar-panel">
      <div class="dashboard-toolbar">
        <div class="dashboard-toolbar__summary">
          <p class="page-panel-desc dashboard-toolbar__desc">
            聚焦当前推进中的产品、我的待办、逾期风险和基础资料入口，让首页成为真实的行动面板。
          </p>
        </div>
        <div class="dashboard-toolbar__actions">
          <button
            v-for="action in quickActions"
            :key="action.label"
            class="quick-action-inline"
            type="button"
            @click="open(action.path)"
          >
            <span class="quick-action-inline__icon">
              <el-icon v-if="action.icon === 'plus'"><Plus /></el-icon>
              <el-icon v-else-if="action.icon === 'promotion'"><Promotion /></el-icon>
              <el-icon v-else-if="action.icon === 'document'"><Document /></el-icon>
              <el-icon v-else><Tickets /></el-icon>
            </span>
            <span class="quick-action-inline__label">{{ action.label }}</span>
          </button>
        </div>
      </div>
    </section>

    <section class="metric-grid">
      <button
        v-for="metric in topMetrics"
        :key="metric.key"
        class="metric-card dashboard-button"
        :class="{ 'is-active': activeMetricView === metric.key }"
        type="button"
        @click="selectMetricView(metric.key)"
      >
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <div class="card-footer">
          <span class="metric-card__trend">{{ metric.hint }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </button>
    </section>

    <section class="page-panel dashboard-content-panel">
      <div class="toolbar-row">
        <div>
          <h3 class="section-title">{{ activeSectionTitle }}</h3>
          <p class="page-panel-desc">{{ activeSectionDesc }}</p>
        </div>
        <div class="dashboard-content-panel__meta">
          <el-tag effect="light">{{ activeMetric.label }}</el-tag>
          <el-button text type="primary" @click="open(activeSectionActionPath)">
            {{ activeSectionActionLabel }}
          </el-button>
        </div>
      </div>

      <div v-if="activeMetricView === 'products'" class="page-stack">
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

      <div v-else-if="activeMetricView === 'tasks'" class="page-stack">
        <button
          v-for="task in myPendingTasks"
          :key="task.taskId"
          class="list-button"
          type="button"
          @click="open(task.targetPath)"
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

      <div v-else-if="activeMetricView === 'risks'" class="page-stack">
        <button
          v-for="risk in overdueRisks"
          :key="`${risk.title}-${risk.stage}`"
          class="risk-button"
          type="button"
          @click="open(risk.targetPath)"
        >
          <div class="toolbar-row">
            <strong>{{ risk.title }}</strong>
            <el-tag :type="risk.overdueDays >= 3 ? 'danger' : 'warning'" effect="light">
              已逾期 {{ risk.overdueDays }} 天
            </el-tag>
          </div>
          <div class="risk-meta">
            <span class="subtle-text">{{ risk.stage }}</span>
            <span class="subtle-text">计划完成：{{ risk.plannedDate }}</span>
          </div>
          <div class="card-footer">
            <span class="subtle-text">责任人：{{ risk.owner }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </button>
      </div>

      <div v-else class="page-stack">
        <button
          v-for="item in pendingFreezeItems"
          :key="`${item.productId}-${item.versionNo}`"
          class="list-button"
          type="button"
          @click="open(item.targetPath)"
        >
          <div class="toolbar-row">
            <div class="cell-stack">
              <strong>{{ item.productName }}</strong>
              <span class="subtle-text">版本 {{ item.versionNo }}</span>
            </div>
            <el-tag type="danger" effect="light">待冻结</el-tag>
          </div>
          <p class="page-panel-desc">缺失资料：{{ item.missingItems.join('、') }}</p>
          <div class="card-footer">
            <span class="subtle-text">{{ item.ownerUserName }} / 截止 {{ item.dueDate }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </button>
      </div>
    </section>
  </PageContainer>
</template>

<style scoped>
.dashboard-toolbar-panel,
.dashboard-content-panel {
  padding: 14px;
}

.dashboard-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.dashboard-toolbar__summary {
  min-width: 0;
  flex: 1 1 auto;
}

.dashboard-toolbar__desc {
  margin: 0;
}

.dashboard-toolbar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex: 0 0 auto;
  flex-wrap: wrap;
}

.dashboard-button,
.list-button,
.risk-button,
.quick-action-inline {
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease, background 0.16s ease;
}

.dashboard-button,
.list-button,
.risk-button {
  width: 100%;
}

.dashboard-button:hover,
.list-button:hover,
.risk-button:hover,
.quick-action-inline:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.dashboard-button.is-active {
  border-color: var(--plm-color-primary);
  background: rgba(59, 130, 246, 0.06);
  box-shadow: var(--plm-shadow-sm);
}

.quick-action-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
}

.quick-action-inline__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(59, 130, 246, 0.1);
  color: var(--plm-color-primary);
}

.quick-action-inline__label {
  white-space: nowrap;
  font-weight: 600;
}

.dashboard-content-panel__meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.list-button,
.risk-button {
  padding: 12px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-row,
.card-footer,
.risk-meta {
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

.risk-meta {
  margin-top: 8px;
}

@media (max-width: 1280px) {
  .dashboard-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .dashboard-toolbar__actions {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .dashboard-content-panel__meta {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
