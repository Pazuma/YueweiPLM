<script setup lang="ts">
import { ArrowRight, Document, Plus, Promotion, Tickets } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import {
  confirmTimelineNode,
  getProjectTimeline,
  getWorkbenchInProgressProjects,
  returnTimelineNode,
  type TimelineDetailVO,
  type TimelineNodeVO
} from '@/api/modules/project'
import { uploadTimelineAttachment } from '@/api/modules/attachment'
import { useUserStore } from '@/stores/user'
import { findCurrentTimelineStep, mapTimelineStages, type TimelineStageView } from '@/utils/timelineAdapter'
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
  status: 'draft' | 'developing' | 'reviewing'
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
  status?: 'draft' | 'developing' | 'reviewing'
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
type ProgressAction = 'confirm' | 'return' | 'force'

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
  status?: 'draft' | 'developing' | 'reviewing'
  targetPath: string
}

interface DashboardChildStep {
  nodeKey?: string
  stepNo: number
  title: string
  requireUpload?: boolean
  uploadLabel?: string
  requireApproval?: boolean
  requiredFileCategory?: string | null
  uploadCount?: number
  isCurrent?: boolean
  isConfirmed?: boolean
  hasUploaded?: boolean
  visualStatus?: ChildStepVisualStatus
}

type ChildStepVisualStatus = 'confirmed' | 'current' | 'uploaded' | 'missing-upload' | 'pending' | 'rejected'

interface DashboardChildStepView extends DashboardChildStep {
  nodeKey: string
  uploadCount: number
  isCurrent: boolean
  isConfirmed: boolean
  hasUploaded: boolean
  visualStatus: ChildStepVisualStatus
}

interface DashboardProgressNode {
  nodeKey: string
  title: string
  stageCode?: string | null
  stageName?: string | null
  phase: string
  requiredFileCategory?: string | null
  status: 'completed' | 'current' | 'pending' | 'rejected'
  hint: string
  date?: string
  ownerRole?: string
  nextAction?: string
  riskText?: string
  checkItems?: string[]
  childSteps: DashboardChildStep[]
  documentCount?: number
  confirmed?: boolean
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

const inProgressProducts = ref<DashboardProductItem[]>([])

async function loadInProgressProjects() {
  const projects = await getWorkbenchInProgressProjects({ page: 1, size: 20 })
  inProgressProducts.value = projects.map((item) => ({
    productId: item.productId,
    productName: item.productName,
    productCode: item.productCode,
    seriesName: item.seriesName,
    currentStage: item.currentStage,
    ownerUserName: item.ownerUserName,
    activeBomVersion: item.activeBomVersion,
    completionRate: item.completionRate,
    status: item.status as DashboardProductItem['status']
  }))
}

const myPendingTasks = computed<DashboardTaskItem[]>(() => [])

const overdueRisks = computed<DashboardRiskItem[]>(() => [])

const pendingFreezeItems = computed<DashboardFreezeItem[]>(() => [])

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

const projectOrderOptions = computed<ProjectOrderOption[]>(() => [])

const approvalTemplateOptions = computed<ApprovalTemplateOption[]>(() => [])

const approverOptions = computed<Array<{ userId: string; userName: string; roleName: string }>>(() => [])

const productLineOptions = computed<Array<{ productId: number; productName: string }>>(() => [])

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
const activeProgressTimeline = ref<TimelineDetailVO | null>(null)
const progressActionLoading = ref<false | ProgressAction | 'upload'>(false)
const progressUploadVisible = ref(false)
const progressUploadFile = ref<File | null>(null)
const progressUploadCategory = ref('other')
const progressUploadStepKey = ref('')

const progressUploadCategoryOptions = [
  { label: '图纸', value: 'drawing' },
  { label: 'SOP', value: 'sop' },
  { label: 'SIP', value: 'sip' },
  { label: '测试资料', value: 'testing' },
  { label: '其他资料', value: 'other' }
]

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

function findProgressTemplateNode(stepNo: number) {
  return activeProgressTemplate.value.find((node) => {
    const stepNos = node.childSteps.map((step) => step.stepNo)
    return stepNo >= Math.min(...stepNos) && stepNo <= Math.max(...stepNos)
  })
}

function toDashboardProgressNode(node: TimelineNodeVO): DashboardProgressNode {
  const templateNode = findProgressTemplateNode(node.stepNo)
  return {
    nodeKey: node.nodeKey,
    title: node.nodeName,
    stageCode: node.stageCode,
    stageName: node.stageName,
    phase: node.phaseName || templateNode?.phase || `第 ${node.stepNo} 节点`,
    requiredFileCategory: node.requiredFileCategory,
    status: node.status,
    hint: node.summary || templateNode?.hint || '节点状态来自后端时间轴',
    date: node.promotedAt,
    ownerRole: node.ownerRole || templateNode?.ownerRole,
    nextAction: node.nextAction || (node.status === 'current' ? '处理当前节点' : undefined),
    riskText: node.riskNote,
    checkItems: node.detailLines,
    childSteps: templateNode?.childSteps || [{ stepNo: node.stepNo, title: node.nodeName }],
    documentCount: node.documentCount,
    confirmed: Boolean(node.confirmed)
  }
}

function toDashboardProgressNodeFromStage(stage: TimelineStageView): DashboardProgressNode {
  const templateNode = findProgressTemplateNode(stage.currentStepNo)
  return {
    nodeKey: stage.stageCode,
    title: stage.stageName,
    stageCode: stage.stageCode,
    stageName: stage.stageName,
    phase: stage.phaseName || templateNode?.phase || stage.stageName,
    status: stage.status,
    hint: templateNode?.hint || '节点状态来自后端时间轴',
    ownerRole: templateNode?.ownerRole,
    nextAction: stage.status === 'current' ? '处理当前小步骤' : undefined,
    childSteps: stage.steps.map((step) => ({
      nodeKey: step.nodeKey,
      stepNo: step.stepNo,
      title: step.stepName,
      requireUpload: Boolean(step.requiredFileCategory),
      uploadLabel: step.requiredFileCategory ? `上传${step.stepName}资料` : undefined,
      requiredFileCategory: step.requiredFileCategory,
      uploadCount: step.documentCount,
      isCurrent: step.isCurrent,
      isConfirmed: step.isConfirmed,
      hasUploaded: step.hasUploaded,
      visualStatus: step.visualStatus
    })),
    documentCount: stage.documentCount,
    confirmed: stage.steps.some((step) => step.confirmed)
  }
}

const dashboardProgressNodes = computed<DashboardProgressNode[]>(() => {
  if (activeProgressTimeline.value?.nodes?.length) {
    return mapTimelineStages(activeProgressTimeline.value).map(toDashboardProgressNodeFromStage)
  }
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

const selectedProgressNodeConfirmed = computed(() => Boolean(selectedProgressNode.value?.confirmed))

const currentProgressStep = computed(() => findCurrentTimelineStep(activeProgressTimeline.value))

const currentProgressStepNo = computed(() => (
  currentProgressStep.value?.stepNo
  || activeProgressTimeline.value?.currentStepNo
  || activeProgressProject.value?.currentStepNo
  || 1
))

const currentProgressStepTitle = computed(() => {
  const stepNo = currentProgressStepNo.value
  if (currentProgressStep.value?.stepName) return currentProgressStep.value.stepName
  const stepTitle = selectedProgressNode.value?.childSteps.find((step) => step.stepNo === stepNo)?.title
  return activeProgressTimeline.value?.currentStepName || stepTitle || selectedProgressNode.value?.title || '--'
})

function getStepUploadCount(step: DashboardChildStep, node: DashboardProgressNode) {
  if (!step.requireUpload) return 0
  if (typeof step.uploadCount === 'number') return step.uploadCount
  if (step.stepNo !== currentProgressStepNo.value) return 0
  return Number(node.documentCount || 0)
}

const selectedProgressChildSteps = computed<DashboardChildStepView[]>(() => {
  const node = selectedProgressNode.value
  if (!node) return []
  return node.childSteps.map((step) => {
    const uploadCount = getStepUploadCount(step, node)
    const isCurrent = Boolean(step.isCurrent ?? step.stepNo === currentProgressStepNo.value)
    const isConfirmed = Boolean(step.isConfirmed ?? (step.stepNo < currentProgressStepNo.value || Boolean(node.confirmed && isCurrent)))
    const hasUploaded = Boolean(step.requireUpload && uploadCount > 0)
    const processRouteStep = isProcessRouteStep(step)
    const visualStatus: ChildStepVisualStatus = step.visualStatus || (isConfirmed
      ? 'confirmed'
      : hasUploaded
        ? 'uploaded'
        : isCurrent
          ? (step.requireUpload ? 'missing-upload' : 'current')
          : 'pending')

    return {
      ...step,
      nodeKey: step.nodeKey || node.nodeKey,
      uploadCount,
      isCurrent,
      isConfirmed,
      hasUploaded,
      uploadLabel: processRouteStep ? '新建工艺路线' : step.uploadLabel,
      visualStatus
    }
  })
})

const progressUploadStepOptions = computed(() => {
  return selectedProgressChildSteps.value
})

const processRouteStepKeys = new Set([
  'PRODUCT_LINE_PROCESS_ADD',
  'PRODUCT_LINE_PROCESS_CONFIRM',
  'MODEL_VARIANT_PROCESS_DIFF_CONFIRM'
])

function isProcessRouteStep(step?: { nodeKey?: string; stepNo?: number; title?: string; stepName?: string; nodeName?: string } | null) {
  if (!step) return false
  const name = step.title || step.stepName || step.nodeName || ''
  return Boolean(
    (step.nodeKey && processRouteStepKeys.has(step.nodeKey)) ||
    step.stepNo === 9 ||
    step.stepNo === 10 ||
    (step.stepNo === 7 && name.includes('工艺'))
  )
}

const isCurrentProcessRouteStep = computed(() => isProcessRouteStep(currentProgressStep.value))

function getProgressUploadOptionLabel(option: DashboardChildStepView) {
  return `第 ${option.stepNo} 步：${option.title}（${Number(option.uploadCount || 0)} 个附件）`
}

function toProgressTargetFromTask(task: DashboardTaskItem): DashboardProjectProgressTarget {
  return { productId: task.productId ?? Number(task.targetPath.split('/').pop()), productName: task.objectName, productCode: task.productCode, seriesName: task.seriesName, productType: task.productType, currentStage: task.nodeName, currentStepNo: task.currentStepNo, ownerUserName: task.ownerUserName || task.initiator, completionRate: task.completionRate, status: task.status, targetPath: task.targetPath }
}

async function loadActiveProgressTimeline(projectId: number) {
  try {
    const timeline = await getProjectTimeline(projectId)
    activeProgressTimeline.value = timeline
    const currentStep = findCurrentTimelineStep(timeline)
    activeProgressNodeKey.value = currentStep?.stageCode || currentStep?.nodeKey || timeline.nodes[0]?.stageCode || timeline.nodes[0]?.nodeKey || null
    if (activeProgressProject.value) {
      activeProgressProject.value = {
        ...activeProgressProject.value,
        currentStage: timeline.currentNode,
        currentStepNo: timeline.currentStepNo
      }
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

async function refreshActiveProgressProject() {
  if (!activeProgressProject.value) return
  await loadActiveProgressTimeline(activeProgressProject.value.productId)
  await loadInProgressProjects()
}

async function openProjectProgress(project: DashboardProjectProgressTarget) {
  activeProgressProject.value = project
  activeProgressNodeKey.value = null
  activeProgressTimeline.value = null
  projectProgressVisible.value = true
  await loadActiveProgressTimeline(project.productId)
}

function openActiveProjectDetail() {
  if (!activeProgressProject.value) return
  projectProgressVisible.value = false
  router.push(normalizeLegacyProductTarget(activeProgressProject.value.targetPath))
}

function openProcessRouteCreateFromDashboard() {
  if (!activeProgressProject.value) return
  projectProgressVisible.value = false
  router.push({
    path: '/projects',
    query: {
      tab: 'in_progress',
      productId: String(activeProgressProject.value.productId),
      section: 'process_detail',
      createProcessRoute: '1'
    }
  })
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
  if (action === 'confirm') {
    void confirmCurrentProgressNode()
    return
  }
  if (action === 'return') {
    void returnCurrentProgressNode()
    return
  }
  ElMessage.warning('强制推进需要后端权限和审计闭环，后续接入')
}

function getErrorMessage(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response
    if (response?.data?.message) return response.data.message
  }
  if (error instanceof Error) return error.message
  return '操作失败'
}

function requireProgressActionContext() {
  const currentStep = currentProgressStep.value
  if (!activeProgressProject.value || !selectedProgressNode.value || !currentStep) {
    ElMessage.warning('请先选择项目和当前节点')
    return null
  }
  if (selectedProgressNode.value.status !== 'current') {
    ElMessage.warning('只能操作当前推进中的节点')
    return null
  }
  return {
    project: activeProgressProject.value,
    node: currentStep
  }
}

async function confirmCurrentProgressNode() {
  const context = requireProgressActionContext()
  if (!context || progressActionLoading.value) return
  try {
    const { value } = await ElMessageBox.prompt('可填写当前节点确认备注，留空则只确认节点。', '确认当前节点', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：资料已确认，可以进入下一环节'
    })
    progressActionLoading.value = 'confirm'
    await confirmTimelineNode(context.project.productId, context.node.nodeKey, String(value || ''))
    await refreshActiveProgressProject()
    ElMessage.success('当前节点已确认')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  } finally {
    progressActionLoading.value = false
  }
}

async function returnCurrentProgressNode() {
  const context = requireProgressActionContext()
  if (!context || progressActionLoading.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入返回上一步原因，系统会写入项目时间轴操作记录。', '返回上一步', {
      confirmButtonText: '返回上一步',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：资料缺失，需要补充后重新确认'
    })
    progressActionLoading.value = 'return'
    await returnTimelineNode(context.project.productId, context.node.nodeKey, String(value || ''), true)
    await refreshActiveProgressProject()
    ElMessage.success('已返回上一步')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  } finally {
    progressActionLoading.value = false
  }
}

function openProgressUpload() {
  const context = requireProgressActionContext()
  if (!context) return
  progressUploadFile.value = null
  progressUploadStepKey.value = context.node.nodeKey
  progressUploadCategory.value = context.node.requiredFileCategory || 'other'
  progressUploadVisible.value = true
}

function handleProgressUploadFileChange(event: Event) {
  progressUploadFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

async function submitProgressUpload() {
  const context = requireProgressActionContext()
  if (!context || progressActionLoading.value) return
  if (!progressUploadFile.value) {
    ElMessage.warning('请先选择要上传的资料')
    return
  }
  if (!progressUploadStepKey.value) {
    ElMessage.warning('请选择资料归属步骤')
    return
  }
  try {
    progressActionLoading.value = 'upload'
    const attachment = await uploadTimelineAttachment(context.project.productId, progressUploadStepKey.value, progressUploadFile.value, {
      fileCategory: progressUploadCategory.value,
      remark: '工作台当前节点资料'
    })
    progressUploadVisible.value = false
    await refreshActiveProgressProject()
    activeProgressNodeKey.value = attachment.timelineStageCode || selectedProgressNode.value?.stageCode || activeProgressNodeKey.value
    ElMessage.success('资料已上传')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    progressActionLoading.value = false
  }
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

onMounted(loadInProgressProjects)
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
          :data-test="`dashboard-project-${item.productId}`"
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
              <div data-test="dashboard-current-step-summary"><span class="subtle-text">当前小节点</span><strong>第 {{ currentProgressStepNo }} 步：{{ currentProgressStepTitle }}</strong></div>
              <div><span class="subtle-text">下一步动作</span><strong>{{ selectedProgressNode.nextAction || '--' }}</strong></div>
              <div><span class="subtle-text">风险提示</span><strong>{{ selectedProgressNode.riskText || '暂无风险' }}</strong></div>
              <div><span class="subtle-text">节点资料</span><strong>{{ selectedProgressNode.documentCount || 0 }} 个附件</strong></div>
              <div><span class="subtle-text">确认状态</span><strong>{{ selectedProgressNodeConfirmed ? '已确认' : '未确认' }}</strong></div>
            </div>
            <section class="project-progress-child-steps">
              <h5>包含的小节点</h5>
              <div class="project-progress-child-step-list">
                <article
                  v-for="step in selectedProgressChildSteps"
                  :key="step.stepNo"
                  class="project-progress-child-step"
                  :class="`is-${step.visualStatus}`"
                  :data-test="`dashboard-child-step-${step.stepNo}`"
                >
                  <div><strong>第 {{ step.stepNo }} 步：{{ step.title }}</strong><p v-if="step.uploadLabel" class="subtle-text">{{ step.uploadLabel }}</p></div>
                  <div class="project-progress-child-step__tags">
                    <el-tag v-if="step.isCurrent" size="small" type="primary" effect="light">当前步骤</el-tag>
                    <el-tag v-if="step.isConfirmed" size="small" type="success" effect="light">已确认</el-tag>
                    <el-tag v-if="step.requireUpload && step.hasUploaded" size="small" type="success" effect="light">已上传 {{ step.uploadCount }} 个</el-tag>
                    <el-tag v-else-if="step.requireUpload" size="small" type="warning" effect="light">待上传</el-tag>
                    <el-tag v-if="step.requireApproval" size="small" type="info" effect="light">审批</el-tag>
                  </div>
                </article>
              </div>
            </section>
            <footer class="project-progress-node-actions" v-if="selectedProgressNode.status === 'current'">
              <el-button data-test="dashboard-progress-confirm" plain :loading="progressActionLoading === 'confirm'" @click="handleProgressAction('confirm')">确认当前节点</el-button>
              <el-button data-test="dashboard-progress-return" type="danger" plain :loading="progressActionLoading === 'return'" @click="handleProgressAction('return')">返回上一步</el-button>
              <el-button type="warning" plain @click="handleProgressAction('force')">强制推进</el-button>
              <el-button v-if="isCurrentProcessRouteStep" data-test="dashboard-process-route-create" type="primary" :icon="Plus" @click="openProcessRouteCreateFromDashboard">新建工艺路线</el-button>
              <el-button v-else data-test="dashboard-progress-upload-open" plain :loading="progressActionLoading === 'upload'" @click="openProgressUpload">上传节点资料</el-button>
            </footer>
          </section>
        </section>
      </div>
    </el-dialog>

    <el-dialog v-model="progressUploadVisible" title="上传节点资料" width="520px" destroy-on-close>
      <div class="project-progress-upload-form">
        <label>
          <span class="subtle-text">资料归属步骤</span>
          <select v-model="progressUploadStepKey" data-test="dashboard-progress-upload-step">
            <option v-for="option in progressUploadStepOptions" :key="option.nodeKey" :value="option.nodeKey">
              {{ getProgressUploadOptionLabel(option) }}
            </option>
          </select>
        </label>
        <label>
          <span class="subtle-text">资料类别</span>
          <select v-model="progressUploadCategory" data-test="dashboard-progress-upload-category">
            <option v-for="option in progressUploadCategoryOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>
          <span class="subtle-text">选择文件</span>
          <input data-test="dashboard-progress-upload-file" type="file" @change="handleProgressUploadFileChange" />
        </label>
        <p class="page-panel-desc">资料会上传到当前大节点内选择的小步骤，文件中心和阶段门禁读取同一份后端数据。</p>
      </div>
      <template #footer>
        <el-button @click="progressUploadVisible = false">取消</el-button>
        <el-button data-test="dashboard-progress-upload-submit" type="primary" :loading="progressActionLoading === 'upload'" @click="submitProgressUpload">上传</el-button>
      </template>
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
.project-progress-child-step { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 9px 10px; border: 1px solid #e5e7eb; border-radius: 6px; background: #f8fafc; }
.project-progress-child-step.is-confirmed,
.project-progress-child-step.is-uploaded { border-color: #86efac; background: #f0fdf4; }
.project-progress-child-step.is-current { border-color: #60a5fa; background: #eff6ff; }
.project-progress-child-step.is-missing-upload { border-color: #fdba74; background: #fff7ed; }
.project-progress-child-step.is-pending { border-color: #e5e7eb; background: #f8fafc; }
.project-progress-child-step__tags { display: flex; gap: 4px; flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; }
.project-progress-node-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
.project-progress-upload-form { display: flex; flex-direction: column; gap: 14px; }
.project-progress-upload-form label { display: flex; flex-direction: column; gap: 6px; }
.project-progress-upload-form select,
.project-progress-upload-form input[type="file"] { min-height: 36px; border: 1px solid #dcdfe6; border-radius: 6px; padding: 6px 10px; background: #fff; }
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
