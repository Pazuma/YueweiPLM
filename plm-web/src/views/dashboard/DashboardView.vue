<script setup lang="ts">
import { ArrowRight, Document, Plus, Promotion, Tickets } from '@element-plus/icons-vue'
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useUserStore } from '@/stores/user'
import { normalizeLegacyProductTarget } from '@/utils/projectRoute'

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
  productId?: number
  productCode?: string
  seriesName?: string
  productType?: 'product_line' | 'model_variant'
  currentStepNo?: number
  ownerUserName?: string
  completionRate?: number
  status?: 'developing' | 'reviewing'
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
type ProgressAction = 'reject' | 'hold' | 'force' | 'advance'

interface DashboardProjectProgressTarget {
  productId: number
  productName: string
  productCode?: string
  seriesName?: string
  productType?: 'product_line' | 'model_variant'
  currentStage: string
  currentStepNo?: number
  ownerUserName?: string
  completionRate?: number
  status?: 'developing' | 'reviewing'
  targetPath: string
}

interface DashboardChildStep { stepNo: number; title: string; requireUpload?: boolean; uploadLabel?: string; requireApproval?: boolean }

interface DashboardProgressNode {
  nodeKey: string
  title: string
  phase: string
  status: 'completed' | 'current' | 'pending' | 'rejected'
  hint: string
  date?: string
  ownerRole?: string
  nextAction?: string
  riskText?: string
  checkItems?: string[]
  childSteps: DashboardChildStep[]
}

interface DashboardMetric {
  key: DashboardViewKey
  label: string
  value: number
  hint: string
}

type QuickActionType = 'route' | 'create_project'

interface QuickActionItem {
  label: string
  path?: string
  icon: 'plus' | 'promotion' | 'document' | 'tickets'
  actionType?: QuickActionType
}

/* ========== 新建项目弹窗类型 ========== */

type CreateProjectType = 'product_line' | 'model_variant'

interface ProjectOrderOption {
  orderCode: string
  orderName: string
  dingTalkApprovalNo: string
  customerName: string
  productName: string
  sourceType: 'customer' | 'market_internal'
}

interface ApprovalTemplateNodeOption {
  nodeKey: string
  nodeName: string
  approverRole: string
  required: boolean
}

interface ApprovalTemplateOption {
  templateId: string
  templateName: string
  projectType: CreateProjectType | 'all'
  nodes: ApprovalTemplateNodeOption[]
}

interface ApprovalNodeApproverValue {
  nodeKey: string
  approverUserId: string
  approverUserName: string
}

interface CreateProjectForm {
  relatedOrderCode: string
  dingTalkApprovalNo: string
  approvalTemplateId: string
  approvalNodeApprovers: ApprovalNodeApproverValue[]
  projectType: CreateProjectType
  productName: string
  seriesName: string
  parentProductId: number | null
  model: string
  color: string
  ownerUserName: string
  customerName: string
  currentStage: string
  expectedReleaseDate: string
  projectSummary: string
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
  { label: '新项目', icon: 'plus', actionType: 'create_project' },
  { label: '项目管理', path: '/projects?tab=in_progress', icon: 'promotion', actionType: 'route' },
  { label: '文件中心', path: '/files', icon: 'document', actionType: 'route' },
  { label: '需求订单', path: '/orders', icon: 'tickets', actionType: 'route' }
]

/* ========== 新建项目弹窗状态 ========== */

const createProjectVisible = ref(false)

const createProjectForm = reactive<CreateProjectForm>({
  relatedOrderCode: '',
  dingTalkApprovalNo: '',
  approvalTemplateId: '',
  approvalNodeApprovers: [],
  projectType: 'product_line',
  productName: '',
  seriesName: '',
  parentProductId: null,
  model: '',
  color: '',
  ownerUserName: '',
  customerName: '',
  currentStage: '立项确认',
  expectedReleaseDate: '',
  projectSummary: ''
})

const projectOrderOptions = computed<ProjectOrderOption[]>(() => [
  { orderCode: 'ORD-SAMPLE-0603', orderName: '超队 3.0 客户需求', dingTalkApprovalNo: '20260603-001', customerName: '北美渠道 A', productName: '超队 3.0', sourceType: 'customer' },
  { orderCode: 'ORD-DEV-0605', orderName: '亮甲 3.0 内部需求', dingTalkApprovalNo: '20260605-003', customerName: '内部立项', productName: '亮甲 3.0', sourceType: 'market_internal' },
  { orderCode: 'ORD-SAMPLE-0520', orderName: '骑士 2.0 客户需求', dingTalkApprovalNo: '20260520-008', customerName: '欧洲渠道 B', productName: '骑士 2.0', sourceType: 'customer' },
  { orderCode: 'ORD-DEV-0515', orderName: '圣宿 Case 内部研发', dingTalkApprovalNo: '20260515-012', customerName: '内部立项', productName: '圣宿 Case', sourceType: 'market_internal' }
])

const approvalTemplateOptions = computed<ApprovalTemplateOption[]>(() => [
  {
    templateId: 'tpl-product-line-001',
    templateName: '新产品线立项审批',
    projectType: 'product_line',
    nodes: [
      { nodeKey: 'manager-review', nodeName: '管理层评审', approverRole: '管理层', required: true },
      { nodeKey: 'engineering-review', nodeName: '工程评审', approverRole: '工程', required: true },
      { nodeKey: 'procurement-check', nodeName: '采购确认', approverRole: '采购', required: true },
      { nodeKey: 'quality-check', nodeName: '品质确认', approverRole: '品质', required: true },
      { nodeKey: 'finance-review', nodeName: '财务审核', approverRole: '财务', required: false }
    ]
  },
  {
    templateId: 'tpl-variant-001',
    templateName: '新型号线扩展审批',
    projectType: 'model_variant',
    nodes: [
      { nodeKey: 'pm-confirm', nodeName: '项目经理确认', approverRole: '项目经理', required: true },
      { nodeKey: 'engineering-check', nodeName: '工程确认', approverRole: '工程', required: true },
      { nodeKey: 'quality-check', nodeName: '品质确认', approverRole: '品质', required: true }
    ]
  },
  {
    templateId: 'tpl-general-001',
    templateName: '通用项目审批',
    projectType: 'all',
    nodes: [
      { nodeKey: 'manager-approve', nodeName: '管理层审批', approverRole: '管理层', required: true },
      { nodeKey: 'dept-approve', nodeName: '部门审批', approverRole: '项目经理', required: true }
    ]
  }
])

const approverOptions = computed(() => [
  { userId: 'u-001', userName: '王总', roleName: '管理层' },
  { userId: 'u-002', userName: '张敏', roleName: '项目经理' },
  { userId: 'u-003', userName: '李工', roleName: '工程' },
  { userId: 'u-004', userName: '赵工', roleName: '品质' },
  { userId: 'u-005', userName: '陈采购', roleName: '采购' },
  { userId: 'u-006', userName: '钱财务', roleName: '财务' }
])

const productLineOptions = computed(() => [
  { productId: 101, productName: '超队 3.0 磁吸手机壳' },
  { productId: 104, productName: '亮甲 3.0 镜面手机壳' }
])

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

const projectProgressVisible = ref(false)
const activeProgressProject = ref<DashboardProjectProgressTarget | null>(null)
const activeProgressNodeKey = ref<string | null>(null)

const newProductLineProgressTemplate = [
  { nodeKey: 'initiation', title: '立项确认', phase: '立项阶段', hint: '确认需求、成本、周期和投入边界。', ownerRole: '项目经理 / 管理层', childSteps: [{ stepNo: 1, title: '产品立项', requireUpload: true, uploadLabel: '上传立项资料' }, { stepNo: 2, title: '确认立项', requireApproval: true }] },
  { nodeKey: 'design', title: '设计确认', phase: '设计验证阶段', hint: '确认图纸、外观、结构和供应商可制造性。', ownerRole: '工程 / 供应商', childSteps: [{ stepNo: 3, title: '画图查看', requireUpload: true, uploadLabel: '上传图纸' }, { stepNo: 4, title: '供应商确认外观图纸', requireUpload: true, uploadLabel: '上传供应商确认资料' }] },
  { nodeKey: 'tooling', title: '开模试模', phase: '开模阶段', hint: '完成开模申请、模具制作和试模验证。', ownerRole: '工程 / 采购 / 模具', childSteps: [{ stepNo: 5, title: '申请开模', requireApproval: true }, { stepNo: 6, title: '制作模具', requireUpload: true, uploadLabel: '上传模具资料' }, { stepNo: 7, title: '测试模具', requireUpload: true, uploadLabel: '上传试模记录' }] },
  { nodeKey: 'sampling-process', title: '样品与工艺', phase: '样品 / 工艺定型阶段', hint: '签样、工艺、组件、红样、黄样和生产资料整理。', ownerRole: '工程 / 品质 / 生产', childSteps: [{ stepNo: 8, title: '签样确认', requireUpload: true, uploadLabel: '上传签样资料' }, { stepNo: 9, title: '加工艺', requireUpload: true, uploadLabel: '上传工艺方案' }, { stepNo: 10, title: '敲定工序', requireUpload: true, uploadLabel: '上传工序' }, { stepNo: 11, title: '确认组件' }, { stepNo: 12, title: '确认组件成品' }, { stepNo: 13, title: '最终外观确认样', requireUpload: true, uploadLabel: '上传外观确认样' }, { stepNo: 14, title: '红样测试', requireUpload: true, uploadLabel: '上传测试报告' }, { stepNo: 15, title: '整理生产资料', requireUpload: true, uploadLabel: '上传 SOP / SIP' }, { stepNo: 16, title: '黄样', requireUpload: true, uploadLabel: '上传黄样确认资料' }] },
  { nodeKey: 'pilot-mx', title: '小批与 MX 验证', phase: '市场验证阶段', hint: '验证产线、物流、MX 端承接和小批量跑通。', ownerRole: '生产 / 品质', childSteps: [{ stepNo: 17, title: '小批量测试', requireUpload: true, uploadLabel: '上传小批测试记录' }, { stepNo: 18, title: '运模' }, { stepNo: 19, title: 'MX 验收', requireUpload: true, uploadLabel: '上传 MX 验收记录' }, { stepNo: 20, title: '测试验证', requireUpload: true, uploadLabel: '上传品质验证报告' }, { stepNo: 21, title: 'MX 小批量测试', requireUpload: true, uploadLabel: '上传 MX 小批量测试记录' }] },
  { nodeKey: 'launch', title: '投产决策', phase: '投产发布阶段', hint: '根据验证结果决定投产或回退。', ownerRole: '管理层', childSteps: [{ stepNo: 22, title: '投产决策', requireApproval: true }] }
]

const modelVariantProgressTemplate = [
  { nodeKey: 'ext-confirm', title: '扩展确认', phase: '扩展确认阶段', hint: '确认父产品和新型号需求来源。', ownerRole: '项目经理', childSteps: [{ stepNo: 1, title: '新型号需求确认', requireUpload: true, uploadLabel: '上传需求资料' }, { stepNo: 2, title: 'Product 子版本建立' }] },
  { nodeKey: 'diff-design', title: '差异设计', phase: '差异调整阶段', hint: '聚焦孔位、尺寸、颜色、包装等差异。', ownerRole: '工程 / 供应商', childSteps: [{ stepNo: 3, title: '图纸与外观差异确认', requireUpload: true, uploadLabel: '上传差异图纸' }] },
  { nodeKey: 'mold-branch', title: '模具判断', phase: '模具决策阶段', hint: '体现改模、新开模、无需模具变更的分支。', ownerRole: '工程 / 模具', childSteps: [{ stepNo: 4, title: '开模/改模申请', requireApproval: true }, { stepNo: 5, title: '制作或修改模具', requireUpload: true, uploadLabel: '上传模具修改资料' }, { stepNo: 6, title: '测试模具', requireUpload: true, uploadLabel: '上传试模记录' }] },
  { nodeKey: 'diff-verify', title: '差异验证', phase: '验证阶段', hint: '只验证变化部分，不重复完整新产品验证。', ownerRole: '工程 / 品质', childSteps: [{ stepNo: 7, title: '差异组件/工艺确认', requireUpload: true, uploadLabel: '上传差异工艺' }, { stepNo: 8, title: '样品确认', requireUpload: true, uploadLabel: '上传样品确认资料' }, { stepNo: 9, title: '差异测试验证', requireUpload: true, uploadLabel: '上传差异测试报告' }, { stepNo: 10, title: '生产资料整理', requireUpload: true, uploadLabel: '上传增量 SOP/SIP' }] },
  { nodeKey: 'variant-pilot', title: '小批与 MX 验证', phase: '市场验证阶段', hint: '确认新型号在产线和 MX 端可稳定承接。', ownerRole: '生产 / 品质', childSteps: [{ stepNo: 11, title: '小批量测试', requireUpload: true, uploadLabel: '上传小批测试记录' }, { stepNo: 12, title: '运模' }, { stepNo: 13, title: 'MX 验收', requireUpload: true, uploadLabel: '上传 MX 验收记录' }, { stepNo: 14, title: 'MX 小批量验证', requireUpload: true, uploadLabel: '上传 MX 小批量验证记录' }] },
  { nodeKey: 'freeze-release', title: '冻结发布', phase: '投产发布阶段', hint: '冻结新型号 BOM、工艺、图纸和资料后正式发布。', ownerRole: '工程 / 管理层', childSteps: [{ stepNo: 15, title: '版本冻结', requireApproval: true }, { stepNo: 16, title: '正式发布', requireApproval: true }] }
]

const activeProgressTemplate = computed(() =>
  activeProgressProject.value?.productType === 'model_variant' ? modelVariantProgressTemplate : newProductLineProgressTemplate
)

const dashboardProgressNodes = computed<DashboardProgressNode[]>(() => {
  const currentStepNo = activeProgressProject.value?.currentStepNo || 1
  return activeProgressTemplate.value.map((node) => {
    const minStep = Math.min(...node.childSteps.map((s) => s.stepNo))
    const maxStep = Math.max(...node.childSteps.map((s) => s.stepNo))
    return { ...node, status: (currentStepNo > maxStep ? 'completed' : currentStepNo >= minStep && currentStepNo <= maxStep ? 'current' : 'pending') as DashboardProgressNode['status'] }
  })
})

const selectedProgressNode = computed(() =>
  dashboardProgressNodes.value.find((node) => node.nodeKey === activeProgressNodeKey.value)
    || dashboardProgressNodes.value.find((node) => node.status === 'current')
    || dashboardProgressNodes.value[0]
)

function toProgressTargetFromTask(task: DashboardTaskItem): DashboardProjectProgressTarget {
  return { productId: task.productId ?? Number(task.targetPath.split('/').pop()), productName: task.objectName, productCode: task.productCode, seriesName: task.seriesName, productType: task.productType, currentStage: task.nodeName, currentStepNo: task.currentStepNo, ownerUserName: task.ownerUserName || task.initiator, completionRate: task.completionRate, status: task.status, targetPath: task.targetPath }
}

function openProjectProgress(project: DashboardProjectProgressTarget) {
  activeProgressProject.value = project
  activeProgressNodeKey.value = null
  projectProgressVisible.value = true
}

function openActiveProjectDetail() {
  if (!activeProgressProject.value) return
  projectProgressVisible.value = false
  router.push(normalizeLegacyProductTarget(activeProgressProject.value.targetPath))
}

function getProgressNodeTagType(status: DashboardProgressNode['status']) {
  if (status === 'completed') return 'success'
  if (status === 'current') return 'warning'
  if (status === 'rejected') return 'danger'
  return 'info'
}

function getProgressNodeStatusText(status: DashboardProgressNode['status']) {
  if (status === 'completed') return '已完成'
  if (status === 'current') return '进行中'
  if (status === 'rejected') return '已驳回'
  return '待开始'
}

function handleProgressAction(action: ProgressAction) {
  const msg: Record<ProgressAction, string> = {
    reject: '已触发驳回占位，后续接入后端。',
    hold: '已记录暂不推进。',
    force: '已触发强制推进占位，后续须校验权限。',
    advance: '已触发推动进程占位，后续接入流程推进接口。'
  }
  alert(msg[action])
}

/* ========== 新建项目弹窗逻辑 ========== */

const selectedApprovalTemplate = computed(() => {
  return approvalTemplateOptions.value.find((item) => item.templateId === createProjectForm.approvalTemplateId) || null
})

const filteredApprovalTemplateOptions = computed(() => {
  return approvalTemplateOptions.value.filter((item) => {
    return item.projectType === 'all' || item.projectType === createProjectForm.projectType
  })
})

function handleQuickAction(action: QuickActionItem) {
  if (action.actionType === 'create_project') {
    openCreateProjectDialog()
    return
  }
  if (action.path) open(action.path)
}

function openCreateProjectDialog() {
  createProjectVisible.value = true
}

function handleRelatedOrderChange(orderCode: string) {
  const order = projectOrderOptions.value.find((item) => item.orderCode === orderCode)
  if (!order) return
  createProjectForm.dingTalkApprovalNo = order.dingTalkApprovalNo
  createProjectForm.customerName = order.customerName
  createProjectForm.productName = order.productName
}

function handleApprovalTemplateChange(templateId: string) {
  const template = approvalTemplateOptions.value.find((item) => item.templateId === templateId)
  createProjectForm.approvalNodeApprovers = (template?.nodes || []).map((node) => ({
    nodeKey: node.nodeKey,
    approverUserId: '',
    approverUserName: ''
  }))
}

function setApprovalNodeApprover(nodeKey: string, userId: string) {
  const user = approverOptions.value.find((item) => item.userId === userId)
  const node = createProjectForm.approvalNodeApprovers.find((item) => item.nodeKey === nodeKey)
  if (!user || !node) return
  node.approverUserId = user.userId
  node.approverUserName = user.userName
}

function validateCreateProjectForm() {
  if (!createProjectForm.relatedOrderCode) return '请选择关联订单'
  if (!createProjectForm.dingTalkApprovalNo.trim()) return '请填写钉钉审批号'
  if (!createProjectForm.approvalTemplateId) return '请选择审批流程模板'
  const missingApproverNode = selectedApprovalTemplate.value?.nodes.find((node) => {
    const approver = createProjectForm.approvalNodeApprovers.find((item) => item.nodeKey === node.nodeKey)
    return node.required && !approver?.approverUserId
  })
  if (missingApproverNode) return `请选择${missingApproverNode.nodeName}审批人`
  if (!createProjectForm.projectType) return '请选择项目类型'
  if (!createProjectForm.productName.trim()) return '请填写项目名称'
  if (!createProjectForm.seriesName.trim()) return '请填写产品系列'
  if (!createProjectForm.ownerUserName.trim()) return '请填写项目负责人'
  if (!createProjectForm.expectedReleaseDate) return '请选择预期发布时间'
  if (!createProjectForm.projectSummary.trim()) return '请填写项目说明'
  if (createProjectForm.projectType === 'model_variant') {
    if (!createProjectForm.parentProductId) return '新型号线必须选择所属产品线'
    if (!createProjectForm.model.trim()) return '新型号线必须填写机型'
    if (!createProjectForm.color.trim()) return '新型号线必须填写颜色'
  }
  return ''
}

function submitCreateProject() {
  const message = validateCreateProjectForm()
  if (message) {
    ElMessage.warning(message)
    return
  }
  ElMessage.success('项目已创建，后续接入后端保存接口')
  createProjectVisible.value = false
  router.push('/projects?tab=in_progress')
}

function open(path: string) {
  router.push(normalizeLegacyProductTarget(path))
}
</script>

<template>
  <PageContainer title="工作台">
    <section class="page-panel dashboard-toolbar-panel">
      <div class="dashboard-toolbar dashboard-toolbar--with-tabs">
        <nav class="dashboard-segment-bar" aria-label="工作台视图"><button v-for="metric in topMetrics" :key="metric.key" class="dashboard-segment-btn" :class="{ 'is-active': activeMetricView === metric.key }" type="button" @click="selectMetricView(metric.key)">{{ metric.label }}<span v-if="metric.value" class="dashboard-segment-count">{{ metric.value }}</span></button></nav>
        <div class="dashboard-toolbar__actions"><button v-for="action in quickActions" :key="action.label" class="quick-action-inline" type="button" @click="handleQuickAction(action)"><span class="quick-action-inline__label">{{ action.label }}</span></button></div>
      </div>
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
          @click="openProjectProgress({ productId: item.productId, productName: item.productName, productCode: item.productCode, seriesName: item.seriesName, currentStage: item.currentStage, ownerUserName: item.ownerUserName, completionRate: item.completionRate, status: item.status, targetPath: `/products/${item.productId}` })"
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
          <el-progress :percentage="Math.round(item.completionRate * 100)" :stroke-width="5" />
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
          @click="openProjectProgress(toProgressTargetFromTask(task))"
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
    <el-dialog v-model="projectProgressVisible" title="项目进度" width="1100px" destroy-on-close>
      <div v-if="activeProgressProject" class="project-progress-dialog">
        <header class="project-progress-dialog__head">
          <div><h3>{{ activeProgressProject.productName }}</h3><p class="subtle-text">{{ activeProgressProject.productCode || '--' }} / {{ activeProgressProject.seriesName || '--' }}</p></div>
          <div class="project-progress-dialog__head-actions">
            <StatusTag v-if="activeProgressProject.status" :status="activeProgressProject.status" object-type="product" />
            <el-button type="primary" plain @click="openActiveProjectDetail">项目详情</el-button>
          </div>
        </header>
        <section class="project-progress-layout">
          <aside class="project-progress-node-list">
            <button v-for="node in dashboardProgressNodes" :key="node.nodeKey" class="project-progress-node-card" :class="[`is-${node.status}`, { 'is-selected': selectedProgressNode?.nodeKey === node.nodeKey }]" type="button" @click="activeProgressNodeKey = node.nodeKey">
              <div class="project-progress-node-card__title"><strong>{{ node.title }}</strong><el-tag size="small" effect="light" :type="getProgressNodeTagType(node.status)">{{ getProgressNodeStatusText(node.status) }}</el-tag></div>
              <span class="subtle-text">{{ node.phase }}</span><span class="subtle-text">{{ node.date || '--' }}</span>
            </button>
          </aside>
          <section class="project-progress-node-detail" v-if="selectedProgressNode">
            <div class="project-progress-node-detail__head"><div><h4>{{ selectedProgressNode.title }}</h4><p class="page-panel-desc">{{ selectedProgressNode.hint }}</p></div><el-tag effect="light" :type="getProgressNodeTagType(selectedProgressNode.status)">{{ getProgressNodeStatusText(selectedProgressNode.status) }}</el-tag></div>
            <div class="project-progress-info-grid">
              <div><span class="subtle-text">责任角色</span><strong>{{ selectedProgressNode.ownerRole || '--' }}</strong></div>
              <div><span class="subtle-text">下一步动作</span><strong>{{ selectedProgressNode.nextAction || '--' }}</strong></div>
              <div><span class="subtle-text">风险提示</span><strong>{{ selectedProgressNode.riskText || '暂无风险' }}</strong></div>
            </div>
            <section class="project-progress-child-steps">
              <h5>包含的小节点</h5>
              <div class="project-progress-child-step-list">
                <article v-for="step in selectedProgressNode.childSteps" :key="step.stepNo" class="project-progress-child-step">
                  <div><strong>第 {{ step.stepNo }} 步：{{ step.title }}</strong><p v-if="step.uploadLabel" class="subtle-text">{{ step.uploadLabel }}</p></div>
                  <div class="project-progress-child-step__tags">
                    <el-tag v-if="step.requireUpload" size="small" type="warning" effect="light">需上传</el-tag>
                    <el-tag v-if="step.requireApproval" size="small" type="info" effect="light">审批</el-tag>
                  </div>
                </article>
              </div>
            </section>
            <footer class="project-progress-node-actions" v-if="selectedProgressNode.status === 'current'">
              <el-button type="danger" plain @click="handleProgressAction('reject')">驳回</el-button>
              <el-button plain @click="handleProgressAction('hold')">暂不推进</el-button>
              <el-button type="warning" plain @click="handleProgressAction('force')">强制推进</el-button>
              <el-button type="primary" @click="handleProgressAction('advance')">推动项目进程</el-button>
            </footer>
          </section>
        </section>
      </div>
    </el-dialog>

    <el-dialog v-model="createProjectVisible" title="新建项目" width="760px" destroy-on-close>
      <el-form label-width="110px" class="create-project-form">
        <el-form-item label="关联订单" required>
          <el-select
            v-model="createProjectForm.relatedOrderCode"
            filterable
            placeholder="选择需求订单"
            @change="handleRelatedOrderChange"
          >
            <el-option
              v-for="order in projectOrderOptions"
              :key="order.orderCode"
              :label="`${order.orderCode} / ${order.orderName}`"
              :value="order.orderCode"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="钉钉审批号" required>
          <el-input v-model="createProjectForm.dingTalkApprovalNo" placeholder="填写钉钉审批号，选择订单后自动带出" />
        </el-form-item>

        <el-form-item label="审批模板" required>
          <el-select
            v-model="createProjectForm.approvalTemplateId"
            filterable
            placeholder="选择审批流程模板"
            @change="handleApprovalTemplateChange"
          >
            <el-option
              v-for="template in filteredApprovalTemplateOptions"
              :key="template.templateId"
              :label="template.templateName"
              :value="template.templateId"
            />
          </el-select>
        </el-form-item>

        <section v-if="selectedApprovalTemplate" class="approval-node-approver-panel">
          <div
            v-for="node in selectedApprovalTemplate.nodes"
            :key="node.nodeKey"
            class="approval-node-approver-row"
          >
            <div>
              <strong>{{ node.nodeName }}</strong>
              <span v-if="node.required" class="required-mark">*</span>
              <span class="subtle-text">{{ node.approverRole }}</span>
            </div>
            <el-select
              :model-value="createProjectForm.approvalNodeApprovers.find((item) => item.nodeKey === node.nodeKey)?.approverUserId || ''"
              filterable
              placeholder="选择审批人"
              @change="(userId: string) => setApprovalNodeApprover(node.nodeKey, userId)"
            >
              <el-option
                v-for="user in approverOptions"
                :key="user.userId"
                :label="user.userName"
                :value="user.userId"
              />
            </el-select>
          </div>
        </section>

        <el-form-item label="项目类型" required>
          <el-segmented
            v-model="createProjectForm.projectType"
            :options="[
              { label: '新产品线', value: 'product_line' },
              { label: '新型号线', value: 'model_variant' }
            ]"
          />
        </el-form-item>

        <el-form-item label="项目名称" required>
          <el-input v-model="createProjectForm.productName" placeholder="填写项目 / 产品名称" />
        </el-form-item>

        <el-form-item label="产品系列" required>
          <el-input v-model="createProjectForm.seriesName" placeholder="填写产品系列" />
        </el-form-item>

        <el-form-item v-if="createProjectForm.projectType === 'model_variant'" label="所属产品线" required>
          <el-select v-model="createProjectForm.parentProductId" filterable placeholder="选择所属产品线">
            <el-option
              v-for="product in productLineOptions"
              :key="product.productId"
              :label="product.productName"
              :value="product.productId"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="createProjectForm.projectType === 'model_variant'" label="机型" required>
          <el-input v-model="createProjectForm.model" placeholder="例如 iPhone18" />
        </el-form-item>

        <el-form-item v-if="createProjectForm.projectType === 'model_variant'" label="颜色" required>
          <el-input v-model="createProjectForm.color" placeholder="例如 黑色 / 蓝色" />
        </el-form-item>

        <el-form-item label="项目负责人" required>
          <el-input v-model="createProjectForm.ownerUserName" placeholder="填写负责人" />
        </el-form-item>

        <el-form-item label="预期发布时间" required>
          <el-date-picker v-model="createProjectForm.expectedReleaseDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>

        <el-form-item label="项目说明" required>
          <el-input v-model="createProjectForm.projectSummary" type="textarea" :rows="3" placeholder="填写立项说明、目标和风险点" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createProjectVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateProject">创建项目</el-button>
      </template>
    </el-dialog>
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
.project-progress-dialog { display: flex; flex-direction: column; gap: 14px; }
.project-progress-dialog__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid #e5e7eb; }
.project-progress-dialog__head-actions { display: flex; align-items: center; gap: 10px; }
.project-progress-layout { display: grid; grid-template-columns: minmax(280px, 0.95fr) minmax(0, 1.35fr); gap: 16px; min-height: 480px; }
.project-progress-node-list { display: flex; flex-direction: column; gap: 10px; max-height: 62vh; overflow: auto; }
.project-progress-node-card { padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; text-align: left; cursor: pointer; }
.project-progress-node-card.is-selected { border-color: #3b82f6; box-shadow: 0 0 0 1px rgba(59,130,246,0.16); }
.project-progress-node-card.is-completed { background: #f0fdf4; }
.project-progress-node-card.is-current { background: #fff7ed; }
.project-progress-node-card__title { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.project-progress-node-detail { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.project-progress-info-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.project-progress-info-grid > div { padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.project-progress-child-steps { padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.project-progress-child-step-list { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.project-progress-child-step { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 8px; border-radius: 6px; background: #f8fafc; }
.project-progress-child-step__tags { display: flex; gap: 4px; flex-shrink: 0; }
.project-progress-node-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
.dashboard-toolbar--with-tabs { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.dashboard-segment-bar { display: flex; gap: 2px; background: #fff; border-radius: 8px; padding: 4px; overflow-x: auto; margin: 0; }
.dashboard-segment-btn { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border: 0; border-radius: 6px; background: transparent; color: #0f172a; font-size: 14px; cursor: pointer; white-space: nowrap; transition: background 0.16s; }
.dashboard-segment-btn:hover { background: #f1f5f9; }
.dashboard-segment-btn.is-active { background: #e5e7eb; font-weight: 600; }
.dashboard-segment-btn { position: relative; }
.dashboard-segment-count { position: absolute; top: -1px; right: -4px; min-width: 12px; height: 12px; display: inline-flex; align-items: center; justify-content: center; padding: 0 3px; border-radius: 999px; background: #FF0000; color: #fff; font-size: 9px; font-weight: 600; line-height: 1; }
@media (max-width: 900px) { .project-progress-layout { grid-template-columns: 1fr; } .project-progress-info-grid { grid-template-columns: 1fr; } .project-progress-node-actions { align-items: stretch; flex-direction: column; } }

.create-project-form {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}

.approval-node-approver-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.approval-node-approver-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.approval-node-approver-row > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 120px;
}

.approval-node-approver-row .el-select {
  width: 200px;
}

.required-mark {
  color: #f56c6c;
  margin-left: 2px;
}
</style>
