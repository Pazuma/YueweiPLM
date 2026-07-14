<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProductPresentation } from '@/api/modules/foundation'
import {
  advanceTimelineNode,
  confirmTimelineNode,
  getProjects,
  getProjectTimeline,
  returnTimelineNode,
  type TimelineDetailVO
} from '@/api/modules/project'
import FilePreview from '@/components/FilePreview/index.vue'
import PageContainer from '@/components/PageContainer/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import ProjectBomPanel from './components/ProjectBomPanel.vue'
import ProjectProcessRoutePanel from './components/ProjectProcessRoutePanel.vue'
import TimelineAttachmentPanel from './components/TimelineAttachmentPanel.vue'
import type { CommonStatus } from '@/types/common'
import type { BomCompareRow, ProductBomItemRow, ProductDetailPresentation, ProductTimelineNode, ProductionDocumentPreviewFile, SkuProcessRouteRow } from '@/types/foundation'
import type { ProductSummary } from '@/types/product'
import { formatAmount, formatDate } from '@/utils/format'
import { toArchivedProductRoute } from '@/utils/projectRoute'

type ProjectTab = 'all_projects' | 'in_progress' | 'archived' | 'abandoned'
type ProjectQuickTag = 'all_projects' | 'in_progress' | 'archived_product' | 'archived_sku' | 'abandoned'
type ProjectFlowFilter = 'all' | 'product_line' | 'model_variant'
type ArchiveView = 'overview' | 'product' | 'sku'
type DetailSectionKey =
  | 'basic'
  | 'current_node'
  | 'project_flow'
  | 'bom_manage'
  | 'process_detail'
  | 'materials'
  | 'business'
  | 'quality'
  | 'cost'
  | 'bom'
  | 'process'
  | 'production_docs'
type SkuPageStage = 'product-home' | 'sku-list'
type ProductFlowStageStatus = ProductTimelineNode['status']
type ProcessDetailFilter = 'all' | 'key' | 'changed'
type ProductProcessDetailTag = 'key' | 'changed' | 'outsourced'
type TimelinePresentationNode = ProductTimelineNode & { confirmed?: boolean }

interface AbandonedProject {
  productId: number
  productCode: string
  productName: string
  ownerUserName: string
  currentStage: string
  abandonReason: string
  abandonedAt: string
  reusableAssets: string
}

interface ProjectTimelineNode {
  nodeKey: string
  title: string
  phase: string
  gate?: boolean
  hint: string
  childStepNos: number[]
  childNodes: string[]
  count: number
}

type ProjectTimelineDefinition = Omit<ProjectTimelineNode, 'count'>

interface ProductFlowStageNode {
  stepNo: number
  nodeKey: string
  nodeName: string
  status: ProductFlowStageStatus
  actionLabel?: string
}

interface ProductFlowStage {
  stageKey: string
  stageName: string
  phaseName: string
  status: ProductFlowStageStatus
  summary: string
  receiverRole?: string
  receiverUserName?: string
  receivedAt?: string
  promoterRole?: string
  promoterUserName?: string
  promotedAt?: string
  nextAction?: string
  riskNote?: string
  childNodes: ProductFlowStageNode[]
}

interface ProductProcessDetailRow {
  sequenceNo: number
  processCode: string
  processName: string
  processType: string
  workstationName?: string | null
  supplierName?: string | null
  confirmerName?: string
  confirmerRole?: string
  processParamSummary?: string
  qualityRequirement?: string
  unitProcessCost?: number
  tags: ProductProcessDetailTag[]
}

interface SkuProjectFlowRow {
  seqNo: number
  stageKey: string
  stageName: string
  nodeKey: string
  nodeName: string
  phaseName: string
  status: ProductFlowStageStatus
  experienceSummary: string
  receiverRole?: string
  receiverUserName?: string
  receivedAt?: string
  promoterRole?: string
  promoterUserName?: string
  promotedAt?: string
  nextAction?: string
  riskNote?: string
  gateLabel?: string
  documentCount?: number
}

interface AllProjectRow {
  productId: number
  productCode: string
  productName: string
  projectType: string
  projectTag: string
  currentStage: string
  ownerUserName: string
  versionNo: string
  updatedAt: string
  sourceStatus?: CommonStatus
  abandoned?: boolean
  source?: ProductSummary
}

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const rows = ref<ProductSummary[]>([])
const keyword = ref('')
const activeTab = ref<ProjectTab>('in_progress')
const activeFlow = ref<ProjectFlowFilter>('all')
const selectedTimelineNode = ref<ProjectTimelineNode | null>(null)

const overviewVisible = ref(false)
const overviewProject = ref<AllProjectRow | null>(null)
const importVisible = ref(false)
const importType = ref<'product' | 'sku'>('product')

const detailVisible = ref(false)
const detailTarget = ref<ProductSummary | null>(null)
const detailLoading = ref(false)
const detailPresentation = ref<ProductDetailPresentation | null>(null)
const detailBomVersion = ref('')
const timelineActionLoading = ref<false | 'confirm' | 'advance' | 'return'>(false)
const timelineCurrentConfirmed = ref<boolean | null>(null)
const timelineLastAction = ref<string | null>(null)
const timelineLastReason = ref<string | null>(null)
const timelineLastOperatedAt = ref<string | null>(null)
const timelineLastOperatorUserName = ref<string | null>(null)
const activeDetailSection = ref<DetailSectionKey>('basic')
const activeProductFlowStageKey = ref('')
const activeSkuFlowStageKey = ref('')
const processDetailFilter = ref<ProcessDetailFilter>('all')
const processDetailKeyword = ref('')
const skuFlowTableExpanded = ref<string[]>([])

/* 生产资料预览 */
const productionPreviewVisible = ref(false)
const activeProductionDoc = ref<ProductionDocumentPreviewFile | null>(null)

const skuPageStage = ref<SkuPageStage>('product-home')
const skuActiveProductId = ref<number | null>(null)
const skuKeyword = ref('')

const productDetailSections = [
  { key: 'current_node' as const, label: '当前节点' },
  { key: 'basic' as const, label: '基础信息' },
  { key: 'project_flow' as const, label: '项目流程' },
  { key: 'bom_manage' as const, label: 'BOM管理' },
  { key: 'process_detail' as const, label: '工序明细' },
  { key: 'materials' as const, label: '资料区' },
  { key: 'business' as const, label: '商务区' },
  { key: 'quality' as const, label: '质量区' }
]

const skuDetailSections = [
  { key: 'basic' as const, label: '基础信息' },
  { key: 'cost' as const, label: '成本' },
  { key: 'bom' as const, label: '当前版本 BOM' },
  { key: 'process' as const, label: '工艺路线' },
  { key: 'project_flow' as const, label: '项目流程' },
  { key: 'production_docs' as const, label: '生产资料' }
]

const projectQuickTags = [
  { label: '全部项目', value: 'all_projects' },
  { label: '进行中', value: 'in_progress' },
  { label: '已归档（产品）', value: 'archived_product' },
  { label: '已归档（SKU）', value: 'archived_sku' },
  { label: '已放弃', value: 'abandoned' }
] as const

const flowFilters = [
  { label: '全部', value: 'all' },
  { label: '新产品线', value: 'product_line' },
  { label: '新型号线', value: 'model_variant' }
] as const

const newProductLineTimeline: ProjectTimelineDefinition[] = [
  { nodeKey: 'initiation', title: '立项确认', phase: '立项阶段', gate: true, hint: '确认需求、成本、周期和投入边界。', childStepNos: [1, 2], childNodes: ['产品立项', '确认立项'] },
  { nodeKey: 'design', title: '设计确认', phase: '设计验证阶段', hint: '收敛图纸、外观、结构和供应商可制造性。', childStepNos: [3, 4], childNodes: ['画图查看', '供应商确认外观图纸'] },
  { nodeKey: 'tooling', title: '开模试模', phase: '开模阶段', gate: true, hint: '完成开模申请、模具制作和试模验证。', childStepNos: [5, 6, 7], childNodes: ['申请开模', '制作模具', '测试模具'] },
  { nodeKey: 'sampling-process', title: '样品与工艺', phase: '样品 / 工艺定型阶段', gate: true, hint: '签样、工艺、组件、红样、黄样和生产资料整理。', childStepNos: [8, 9, 10, 11, 12, 13, 14, 15, 16], childNodes: ['签样确认', '加工艺', '敲定工序', '整理生产资料', '黄样'] },
  { nodeKey: 'pilot-mx', title: '小批与 MX 验证', phase: '市场验证阶段', gate: true, hint: '验证产线、物流、MX 端承接和小批量跑通。', childStepNos: [17, 18, 19, 20, 21], childNodes: ['小批量测试', '运模', 'MX 验收', '测试验证', 'MX 小批量测试'] },
  { nodeKey: 'launch', title: '投产决策', phase: '投产发布阶段', gate: true, hint: '根据验证结果决定投产或回退。', childStepNos: [22], childNodes: ['投产决策'] }
]

const modelVariantTimeline: ProjectTimelineDefinition[] = [
  { nodeKey: 'ext-confirm', title: '扩展确认', phase: '扩展确认阶段', gate: true, hint: '确认父产品和新型号需求来源。', childStepNos: [1, 2], childNodes: ['新型号需求确认', 'Product 子版本建立'] },
  { nodeKey: 'diff-design', title: '差异设计', phase: '差异调整阶段', hint: '聚焦孔位、尺寸、颜色、包装等差异。', childStepNos: [3], childNodes: ['图纸与外观差异确认'] },
  { nodeKey: 'mold-branch', title: '模具判断', phase: '模具决策阶段', gate: true, hint: '体现改模、新开模、无模具变更的分支。', childStepNos: [4, 5, 6], childNodes: ['开模 / 改模申请', '制作或修改模具', '测试模具'] },
  { nodeKey: 'diff-verify', title: '差异验证', phase: '验证阶段', gate: true, hint: '只验证变化部分，不重复完整新产品验证。', childStepNos: [7, 8, 9, 10], childNodes: ['差异组件 / 工艺确认', '样品确认', '差异测试验证', '生产资料整理'] },
  { nodeKey: 'variant-pilot', title: '小批与 MX 验证', phase: '市场验证阶段', gate: true, hint: '确认新型号在产线和 MX 端可稳定承接。', childStepNos: [11, 12, 13, 14], childNodes: ['小批量测试', '运模', 'MX 验收', 'MX 小批量验证'] },
  { nodeKey: 'freeze-release', title: '冻结发布', phase: '投产发布阶段', gate: true, hint: '作为父产品线下子版本发布。', childStepNos: [15, 16], childNodes: ['版本冻结', '正式发布'] }
]

const abandonedProjects = ref<AbandonedProject[]>([
  {
    productId: 901,
    productCode: 'PRD-SC29-ABN-001',
    productName: '超星 2.9 iPhone17 联名款',
    ownerUserName: '张敏',
    currentStage: '样品验证',
    abandonReason: '市场需求撤回，联名渠道取消。',
    abandonedAt: '2026-05-18',
    reusableAssets: '外观图纸、包装结构和 TPU 材料验证记录可复用。'
  },
  {
    productId: 902,
    productCode: 'PRD-LJ29-ABN-003',
    productName: '亮甲 2.9 镜面片试验版',
    ownerUserName: '刘浩',
    currentStage: '工艺验证',
    abandonReason: '镜面片良率不稳定，工艺成本过高。',
    abandonedAt: '2026-05-26',
    reusableAssets: '测试项模板、镜面片样品记录和供应商对比可复用。'
  }
])

const archiveView = computed<ArchiveView>(() => {
  const value = String(route.query.archiveView || 'overview')
  return ['overview', 'product', 'sku'].includes(value) ? (value as ArchiveView) : 'overview'
})

const projectQuickTag = computed<ProjectQuickTag>(() => {
  if (activeTab.value === 'all_projects') return 'all_projects'
  if (activeTab.value === 'in_progress') return 'in_progress'
  if (activeTab.value === 'abandoned') return 'abandoned'
  return archiveView.value === 'sku' ? 'archived_sku' : 'archived_product'
})

const filteredRows = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) return rows.value
  return rows.value.filter((item) =>
    [item.productCode, item.productName, item.seriesName, item.ownerUserName, item.model, item.color]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(search))
  )
})

const runningProjects = computed(() =>
  filteredRows.value.filter((item) => ['developing', 'reviewing', 'pending'].includes(item.status))
)

const flowFilteredProjects = computed(() => {
  if (activeFlow.value === 'all') return runningProjects.value
  return runningProjects.value.filter((item) => item.productType === activeFlow.value)
})

const flowTimeline = computed<ProjectTimelineNode[]>(() => {
  const source = activeFlow.value === 'product_line' ? newProductLineTimeline : modelVariantTimeline
  return source.map((node) => ({
    ...node,
    count: flowFilteredProjects.value.filter((project) => node.childStepNos.includes(project.currentStepNo || -1)).length
  }))
})

const timelineProjects = computed(() => {
  if (activeFlow.value === 'all') return flowFilteredProjects.value
  if (!selectedTimelineNode.value) return flowFilteredProjects.value
  return flowFilteredProjects.value.filter((item) => selectedTimelineNode.value!.childStepNos.includes(item.currentStepNo || -1))
})

const archivedProjects = computed(() =>
  rows.value.filter((item) => ['released', 'archived'].includes(item.status) && item.completionRate >= 1)
)
const archivedProductRows = computed(() => archivedProjects.value.filter((item) => item.productType === 'product_line'))
const archivedSkuRows = computed(() =>
  archivedProjects.value.filter((item) => item.productType === 'model_variant' || Boolean(item.parentProductId))
)

const archiveSummary = computed(() => ({
  total: archivedProjects.value.length,
  products: archivedProductRows.value.length,
  skus: archivedSkuRows.value.length
}))

const allProjectRows = computed<AllProjectRow[]>(() => [
  ...rows.value.map((item) => ({
    productId: item.productId,
    productCode: item.productCode,
    productName: item.productName,
    projectType: item.productType === 'product_line' ? '产品' : 'SKU',
    projectTag: ['released', 'archived'].includes(item.status)
      ? item.productType === 'product_line'
        ? '已归档（产品）'
        : '已归档（SKU）'
      : '进行中',
    currentStage: item.currentStage,
    ownerUserName: item.ownerUserName,
    versionNo: item.versionNo,
    updatedAt: item.releasedAt || '',
    sourceStatus: item.status,
    source: item
  })),
  ...abandonedProjects.value.map((item) => ({
    productId: item.productId,
    productCode: item.productCode,
    productName: item.productName,
    projectType: '项目',
    projectTag: '已放弃',
    currentStage: item.currentStage,
    ownerUserName: item.ownerUserName,
    versionNo: '--',
    updatedAt: item.abandonedAt,
    abandoned: true
  }))
])

const skuProductCards = computed(() =>
  archivedSkuRows.value.reduce<ProductSummary[]>((acc, item) => {
    const parent = rows.value.find((row) => row.productId === item.parentProductId)
    if (parent && !acc.some((row) => row.productId === parent.productId)) acc.push(parent)
    return acc
  }, [])
)

const skuActiveProduct = computed(() =>
  skuProductCards.value.find((item) => item.productId === skuActiveProductId.value) || null
)

const skuCurrentSkuRows = computed(() => {
  const search = skuKeyword.value.trim().toLowerCase()
  if (!skuActiveProductId.value) return []
  return archivedSkuRows.value.filter((item) => {
    const belongsTo = item.parentProductId === skuActiveProductId.value || item.seriesName === skuActiveProduct.value?.seriesName
    const keywordMatched =
      !search ||
      item.productCode.toLowerCase().includes(search) ||
      item.productName.toLowerCase().includes(search) ||
      item.model.toLowerCase().includes(search) ||
      item.color.toLowerCase().includes(search)
    return belongsTo && keywordMatched
  })
})

const currentModuleCount = computed(() => {
  if (projectQuickTag.value === 'all_projects') return allProjectRows.value.length
  if (projectQuickTag.value === 'in_progress') return runningProjects.value.length
  if (projectQuickTag.value === 'archived_product') return archivedProductRows.value.length
  if (projectQuickTag.value === 'archived_sku') return archivedSkuRows.value.length
  return abandonedProjects.value.length
})

const currentModuleTitle = computed(() => {
  return projectQuickTags.find((item) => item.value === projectQuickTag.value)?.label || '全部项目'
})

const currentModuleDescription = computed(() => {
  if (projectQuickTag.value === 'all_projects') return '汇总进行中、已归档和已放弃项目，可快速打开项目概览。'
  if (projectQuickTag.value === 'in_progress') return '展示正在推进的新产品线和新型号线项目，可按关键节点查看当前环节。'
  if (projectQuickTag.value === 'archived_product') return '只展示已完成并已发布 / 已归档的新产品线产品，详情弹窗用于追溯最终版本资料和流程结果。'
  if (projectQuickTag.value === 'archived_sku') return '按产品卡片进入已归档 SKU 列表与详情，仍通过 Product 对象承载。'
  return '已放弃项目永久保留，可查看放弃原因和可复用资产。'
})

const detailBomItems = computed<ProductBomItemRow[]>(() => {
  if (!detailPresentation.value || !detailBomVersion.value) return []
  return detailPresentation.value.bomItemsByVersion[detailBomVersion.value] || []
})

const isProductDetailDialog = computed(() => detailTarget.value?.productType === 'product_line')

const activeDetailSections = computed(() =>
  isProductDetailDialog.value ? productDetailSections : skuDetailSections
)

const activeProductFlowNode = computed<TimelinePresentationNode | null>(() => {
  const nodes = (detailPresentation.value?.timeline || []) as TimelinePresentationNode[]
  return nodes.find((item) => item.status === 'current') || nodes[0] || null
})

const currentTimelineConfirmed = computed(() => Boolean(activeProductFlowNode.value?.confirmed ?? timelineCurrentConfirmed.value))

const currentTimelineActionLabel = computed(() => {
  if (!activeProductFlowNode.value) return '暂无当前节点'
  return currentTimelineConfirmed.value ? '可推进到下一节点' : '等待确认当前节点'
})

const canAdvanceCurrentTimelineNode = computed(() => {
  if (!activeProductFlowNode.value) return false
  if (activeProductFlowNode.value.status !== 'current') return false
  const nodes = detailPresentation.value?.timeline || []
  const currentIndex = nodes.findIndex((node) => node.nodeKey === activeProductFlowNode.value?.nodeKey)
  return currentTimelineConfirmed.value && currentIndex >= 0 && currentIndex < nodes.length - 1
})

function stageMatchesNode(stage: ProjectTimelineDefinition, node: ProductTimelineNode, stepNo: number) {
  const phaseMatched = node.phaseName ? node.phaseName === stage.phase : false
  const nameMatched = stage.childNodes.some((childName) => node.nodeName.includes(childName) || childName.includes(node.nodeName))
  const stepMatched = !node.phaseName && !nameMatched && stage.childStepNos.includes(stepNo)
  return phaseMatched || nameMatched || stepMatched
}

function buildFlowStages(timelineNodes: ProductTimelineNode[], definitions: ProjectTimelineDefinition[]) {
  return definitions.map((stage, stageIndex) => {
    const childNodes = timelineNodes
      .map((node, index) => ({ node, stepNo: index + 1 }))
      .filter(({ node, stepNo }) => stageMatchesNode(stage, node, stepNo))

    const representativeNode =
      childNodes.find(({ node }) => node.status === 'current')?.node ||
      childNodes.find(({ node }) => node.status === 'rejected')?.node ||
      childNodes[childNodes.length - 1]?.node ||
      null

    const currentStageIndex = definitions.findIndex((item) =>
      timelineNodes.some((node, index) => node.status === 'current' && stageMatchesNode(item, node, index + 1))
    )

    let status: ProductFlowStageStatus = 'pending'
    if (childNodes.some(({ node }) => node.status === 'rejected')) {
      status = 'rejected'
    } else if (childNodes.some(({ node }) => node.status === 'current')) {
      status = 'current'
    } else if (childNodes.length && childNodes.every(({ node }) => node.status === 'completed')) {
      status = 'completed'
    } else if (currentStageIndex >= 0 && stageIndex < currentStageIndex) {
      status = 'completed'
    }

    return {
      stageKey: stage.nodeKey,
      stageName: stage.title,
      phaseName: stage.phase,
      status,
      summary: representativeNode?.experienceSummary || representativeNode?.summary || stage.hint,
      receiverRole: representativeNode?.receiverRole,
      receiverUserName: representativeNode?.receiverUserName,
      receivedAt: representativeNode?.receivedAt || representativeNode?.actualDate,
      promoterRole: representativeNode?.promoterRole || representativeNode?.ownerRole,
      promoterUserName: representativeNode?.promoterUserName,
      promotedAt: representativeNode?.promotedAt || representativeNode?.actualDate,
      nextAction: representativeNode?.nextAction || (status === 'completed' ? '已完成' : '待推进'),
      riskNote: representativeNode?.riskNote,
      childNodes:
        childNodes.length > 0
          ? childNodes.map(({ node, stepNo }) => ({
              stepNo,
              nodeKey: node.nodeKey,
              nodeName: node.nodeName,
              status: node.status,
              actionLabel: node.nextAction
            }))
          : stage.childNodes.map((nodeName, index) => ({
              stepNo: stage.childStepNos[index] || index + 1,
              nodeKey: `${stage.nodeKey}-${index}`,
              nodeName,
              status: (status === 'completed' ? 'completed' : 'pending') as ProductFlowStageStatus
            }))
    }
  })
}

const productFlowStages = computed<ProductFlowStage[]>(() => {
  const timelineNodes = detailPresentation.value?.timeline || []
  return buildFlowStages(timelineNodes, newProductLineTimeline)
})

const activeProductFlowStage = computed(() => {
  return (
    productFlowStages.value.find((stage) => stage.stageKey === activeProductFlowStageKey.value) ||
    productFlowStages.value.find((stage) => stage.status === 'current') ||
    productFlowStages.value[0] ||
    null
  )
})

const skuProjectFlowStages = computed<ProductFlowStage[]>(() => {
  const timelineNodes = detailPresentation.value?.timeline || []
  return buildFlowStages(timelineNodes, modelVariantTimeline)
})

const skuProjectFlowRows = computed<SkuProjectFlowRow[]>(() => {
  const timelineNodes = detailPresentation.value?.timeline || []
  return timelineNodes.map((node, index) => {
    const stage = modelVariantTimeline.find((item) => stageMatchesNode(item, node, index + 1))
    return {
      seqNo: index + 1,
      stageKey: stage?.nodeKey || node.phaseName || node.nodeKey,
      stageName: stage?.title || node.phaseName || '新型号线流程',
      nodeKey: node.nodeKey,
      nodeName: node.nodeName,
      phaseName: node.phaseName || stage?.phase || '--',
      status: node.status,
      experienceSummary: node.experienceSummary || node.summary || '--',
      receiverRole: node.receiverRole,
      receiverUserName: node.receiverUserName,
      receivedAt: node.receivedAt || node.actualDate,
      promoterRole: node.promoterRole || node.ownerRole,
      promoterUserName: node.promoterUserName,
      promotedAt: node.promotedAt || node.actualDate,
      nextAction: node.nextAction,
      riskNote: node.riskNote,
      gateLabel: node.gateLabel,
      documentCount: node.documentCount
    }
  })
})

const currentSkuProjectFlowRow = computed(() =>
  skuProjectFlowRows.value.find((row) => row.status === 'current') || skuProjectFlowRows.value[0] || null
)

const activeSkuFlowStage = computed(() => {
  return (
    skuProjectFlowStages.value.find((stage) => stage.stageKey === activeSkuFlowStageKey.value) ||
    skuProjectFlowStages.value.find((stage) => stage.status === 'current') ||
    skuProjectFlowStages.value[0] ||
    null
  )
})

const canEditDetailTarget = computed(() => {
  if (!detailTarget.value) return false
  return !['released', 'archived'].includes(detailTarget.value.status)
})

const activeBomVersionSummary = computed<BomCompareRow | null>(() => {
  return (
    detailPresentation.value?.bomCompareRows.find((item) => item.versionNo === detailBomVersion.value) ||
    null
  )
})

const processDetailRows = computed<ProductProcessDetailRow[]>(() =>
  (detailPresentation.value?.processRoutes || []).map((row) => ({
    sequenceNo: row.sequenceNo,
    processCode: row.processCode,
    processName: row.processName,
    processType: row.processType,
    workstationName: row.workstationName,
    supplierName: row.supplierName,
    confirmerName: getProcessConfirmerName(row),
    confirmerRole: getProcessConfirmerRole(row),
    processParamSummary: row.summary || row.inventoryName || '--',
    qualityRequirement: row.qualityRequirement || '--',
    unitProcessCost: getProcessCost(row),
    tags: getProcessTags(row)
  }))
)

const filteredProcessDetailRows = computed(() => {
  const keyword = processDetailKeyword.value.trim().toLowerCase()
  return processDetailRows.value.filter((row) => {
    const filterMatched =
      processDetailFilter.value === 'all' ||
      row.tags.includes(processDetailFilter.value === 'key' ? 'key' : 'changed')
    const keywordMatched =
      !keyword ||
      row.processCode.toLowerCase().includes(keyword) ||
      row.processName.toLowerCase().includes(keyword) ||
      String(row.workstationName || '').toLowerCase().includes(keyword) ||
      String(row.supplierName || '').toLowerCase().includes(keyword)
    return filterMatched && keywordMatched
  })
})

const productionDocuments = computed(() =>
  (detailPresentation.value?.documents || []).filter((item) => item.stageKey === 'production' || item.category === '生产资料')
)

const detailDocumentStats = computed(() => {
  const documents = detailPresentation.value?.documents || []
  return {
    total: documents.length,
    production: productionDocuments.value.length,
    frozen: documents.filter((item) => item.status === '已冻结' || item.status === '已归档').length
  }
})

function getProjectTypeLabel(row: ProductSummary) {
  return row.productType === 'product_line' ? '新产品线' : '新型号线'
}

function getMoldActionLabel(row: ProductSummary) {
  if (row.productType === 'product_line') return '完整开模链路'
  if (row.moldAction === 'modify') return '改模'
  if (row.moldAction === 'new') return '新开模'
  if (row.moldAction === 'none') return '无需模具变更'
  return '待判断'
}

function getNextGate(row: ProductSummary) {
  return row.gateSummary || (row.productType === 'product_line' ? '关注签样、小批量和 MX 关口。' : '关注差异验证、版本冻结与子版本发布。')
}

function getRecentUpdate(row: ProductSummary) {
  return formatDate(row.releasedAt || undefined)
}

function getProjectTagType(tag: string) {
  if (tag === '进行中') return 'warning'
  if (tag === '已放弃') return 'danger'
  if (tag.includes('SKU')) return 'success'
  return 'info'
}

function getSkuCountForProduct(productId: number) {
  return archivedSkuRows.value.filter((item) => item.parentProductId === productId).length
}

function selectTimelineNode(node: ProjectTimelineNode) {
  selectedTimelineNode.value = selectedTimelineNode.value?.nodeKey === node.nodeKey ? null : node
}

function selectProjectQuickTag(tag: ProjectQuickTag) {
  selectedTimelineNode.value = null
  if (tag === 'all_projects') {
    router.push({ path: '/projects', query: { tab: 'all_projects' } })
    return
  }
  if (tag === 'in_progress') {
    router.push({ path: '/projects', query: { tab: 'in_progress' } })
    return
  }
  if (tag === 'archived_product') {
    router.push({ path: '/projects', query: { tab: 'archived', archiveView: 'product' } })
    return
  }
  if (tag === 'archived_sku') {
    router.push({ path: '/projects', query: { tab: 'archived', archiveView: 'sku' } })
    return
  }
  router.push({ path: '/projects', query: { tab: 'abandoned' } })
}

function selectFlow(value: ProjectFlowFilter) {
  activeFlow.value = value
  selectedTimelineNode.value = null
}

function isProductSummary(row: ProductSummary | AllProjectRow): row is ProductSummary {
  return 'productType' in row
}

function openProjectOverview(row: ProductSummary | AllProjectRow) {
  if (isProductSummary(row)) {
    overviewProject.value = {
      productId: row.productId,
      productCode: row.productCode,
      productName: row.productName,
      projectType: row.productType === 'product_line' ? '产品' : 'SKU',
      projectTag: ['released', 'archived'].includes(row.status) ? '已归档' : '进行中',
      currentStage: row.currentStage,
      ownerUserName: row.ownerUserName,
      versionNo: row.versionNo,
      updatedAt: row.releasedAt || '',
      sourceStatus: row.status,
      source: row
    }
  } else {
    overviewProject.value = row
  }
  overviewVisible.value = true
}

function openProduct(productId: number) {
  const target = rows.value.find((item) => item.productId === productId)
  if (target) {
    openDetail(target)
    return
  }
  router.push(toArchivedProductRoute(productId))
}

function openArchiveImport(type: 'product' | 'sku') {
  importType.value = type
  importVisible.value = true
}

function skuOpenSkuList(productId: number) {
  skuActiveProductId.value = productId
  skuKeyword.value = ''
  skuPageStage.value = 'sku-list'
}

function skuBackToProductHome() {
  skuActiveProductId.value = null
  skuKeyword.value = ''
  skuPageStage.value = 'product-home'
}

function getTimelineActionLabel(action?: string | null) {
  if (action === 'confirm') return '已确认当前节点'
  if (action === 'advance') return '已推进到下一节点'
  if (action === 'return') return '已退回处理'
  return '暂无动作'
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error) return error.message
  return '操作失败，请稍后重试'
}

function mapTimelineToPresentationNodes(timeline: TimelineDetailVO): TimelinePresentationNode[] {
  return timeline.nodes.map((node) => ({
    nodeKey: node.nodeKey,
    nodeName: node.nodeName,
    status: node.status,
    ownerRole: node.ownerRole,
    summary: node.summary,
    nextAction: node.nextAction,
    riskNote: node.riskNote,
    gateLabel: node.gateLabel,
    detailLines: node.detailLines,
    receiverRole: node.receiverRole,
    receiverUserName: node.receiverUserName,
    receivedAt: node.receivedAt,
    promoterRole: node.promoterRole,
    promoterUserName: node.promoterUserName,
    promotedAt: node.promotedAt,
    experienceSummary: node.experienceSummary,
    documentCount: node.documentCount,
    phaseName: node.phaseName,
    confirmed: node.confirmed,
    canAdvance: node.status === 'current' && Boolean(node.confirmed),
    canReject: node.status === 'current'
  }))
}

function applyTimelineMetadata(timeline: TimelineDetailVO) {
  timelineCurrentConfirmed.value = timeline.currentConfirmed ?? null
  timelineLastAction.value = timeline.lastAction || null
  timelineLastReason.value = timeline.lastReason || null
  timelineLastOperatedAt.value = timeline.lastOperatedAt || null
  timelineLastOperatorUserName.value = timeline.lastOperatorUserName || null
}

async function refreshProjectTimeline(projectId: number) {
  if (!detailPresentation.value) return
  const timeline = await getProjectTimeline(projectId)
  applyTimelineMetadata(timeline)
  const nodes = mapTimelineToPresentationNodes(timeline)
  const currentNode = nodes.find((node) => node.status === 'current')
  detailPresentation.value = {
    ...detailPresentation.value,
    currentNode: currentNode?.nodeName || timeline.currentNode || detailPresentation.value.currentNode,
    nextNode: currentNode?.nextAction || detailPresentation.value.nextNode,
    timeline: nodes.length ? nodes : detailPresentation.value.timeline
  }
  if (detailTarget.value) {
    detailTarget.value = {
      ...detailTarget.value,
      currentStage: currentNode?.nodeName || detailTarget.value.currentStage,
      currentStepNo: timeline.currentStepNo
    }
  }
}

async function handleM4AttachmentChanged() {
  if (!detailTarget.value) return
  await refreshProjectTimeline(detailTarget.value.productId)
}

async function handleConfirmCurrentNode() {
  if (!detailTarget.value || !activeProductFlowNode.value || timelineActionLoading.value) return
  try {
    const { value } = await ElMessageBox.prompt('可填写确认备注，留空则只确认当前节点。', '确认当前节点', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：资料已检查，进入可推进状态'
    })
    timelineActionLoading.value = 'confirm'
    await confirmTimelineNode(detailTarget.value.productId, activeProductFlowNode.value.nodeKey, String(value || ''))
    await refreshProjectTimeline(detailTarget.value.productId)
    await loadData()
    ElMessage.success('节点已确认')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  } finally {
    timelineActionLoading.value = false
  }
}

async function handleAdvanceCurrentNode() {
  if (!detailTarget.value || !activeProductFlowNode.value || timelineActionLoading.value) return
  if (!canAdvanceCurrentTimelineNode.value) {
    ElMessage.warning(currentTimelineConfirmed.value ? '最后一个节点不能继续推进' : '请先确认当前节点')
    return
  }
  try {
    await ElMessageBox.confirm('推进后项目会进入下一节点，新节点会回到未确认状态。', '推进下一节点', {
      confirmButtonText: '推进',
      cancelButtonText: '取消',
      type: 'warning'
    })
    timelineActionLoading.value = 'advance'
    await advanceTimelineNode(detailTarget.value.productId, activeProductFlowNode.value.nodeKey)
    await refreshProjectTimeline(detailTarget.value.productId)
    await loadData()
    ElMessage.success('已推进到下一节点')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  } finally {
    timelineActionLoading.value = false
  }
}

async function handleReturnCurrentNode(returnToPrevious: boolean) {
  if (!detailTarget.value || !activeProductFlowNode.value || timelineActionLoading.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      returnToPrevious ? '请输入退回上一节点的原因。' : '请输入退回当前节点修改的原因。',
      returnToPrevious ? '退回上一节点' : '退回当前节点修改',
      {
        confirmButtonText: '退回',
        cancelButtonText: '取消',
        inputPlaceholder: '必须填写，例如：图纸资料需补充',
        inputValidator: (value) => Boolean(String(value || '').trim()),
        inputErrorMessage: '请填写退回原因'
      }
    )
    timelineActionLoading.value = 'return'
    await returnTimelineNode(
      detailTarget.value.productId,
      activeProductFlowNode.value.nodeKey,
      String(value || ''),
      returnToPrevious
    )
    await refreshProjectTimeline(detailTarget.value.productId)
    await loadData()
    ElMessage.success('已退回处理')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  } finally {
    timelineActionLoading.value = false
  }
}

function handleReturnCommand(command: string | number | object) {
  handleReturnCurrentNode(command === 'previous')
}

async function openDetail(row: ProductSummary) {
  detailPresentation.value = null
  detailBomVersion.value = ''
  detailTarget.value = row
  activeDetailSection.value = row.productType === 'product_line' ? 'current_node' : 'basic'
  activeProductFlowStageKey.value = ''
  activeSkuFlowStageKey.value = ''
  processDetailFilter.value = 'all'
  processDetailKeyword.value = ''
  timelineCurrentConfirmed.value = null
  timelineLastAction.value = null
  timelineLastReason.value = null
  timelineLastOperatedAt.value = null
  timelineLastOperatorUserName.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    const presentation = await getProductPresentation(row.productId)
    try {
      const timeline = await getProjectTimeline(row.productId)
      applyTimelineMetadata(timeline)
      const nodes = mapTimelineToPresentationNodes(timeline)
      const currentNode = nodes.find((node) => node.status === 'current')
      detailPresentation.value = {
        ...presentation,
        currentNode: currentNode?.nodeName || timeline.currentNode || presentation.currentNode,
        nextNode: currentNode?.nextAction || presentation.nextNode,
        timeline: nodes.length ? nodes : presentation.timeline
      }
    } catch {
      detailPresentation.value = presentation
    }
    detailBomVersion.value =
      detailPresentation.value.defaultBomVersion ||
      detailPresentation.value.bomCompareRows.find((item) => item.statusLabel === '当前')?.versionNo ||
      detailPresentation.value.bomCompareRows[0]?.versionNo ||
      ''
  } finally {
    detailLoading.value = false
  }
}

function openDetailEdit() {
  if (!detailTarget.value || !canEditDetailTarget.value) return
  const targetId = detailTarget.value.productId
  router.push({ path: `/products/${targetId}/edit` })
}

function getBomRemark(row: ProductBomItemRow) {
  if (row.changeType === 'new') return '新增物料'
  if (row.changeType === 'replace') return '替代料'
  if (row.changeType === 'remove') return '本版本移除'
  if (row.changeType === 'inherit') return '沿用父产品'
  return '--'
}

function getBomLineCost(row: ProductBomItemRow) {
  return Number(row.unitCost || 0) * Number(row.quantity || 0)
}

function getDocumentStatusType(status?: string) {
  if (status === '已冻结' || status === '已归档') return 'success'
  if (status === '草稿') return 'warning'
  return 'info'
}

function getFlowStatusLabel(status: ProductFlowStageStatus) {
  if (status === 'completed') return '已完成'
  if (status === 'current') return '进行中'
  if (status === 'rejected') return '已驳回'
  return '待开始'
}

function getFlowStatusType(status: ProductFlowStageStatus) {
  if (status === 'completed') return 'success'
  if (status === 'current') return 'warning'
  if (status === 'rejected') return 'danger'
  return 'info'
}

function getProcessTypeLabel(type: string) {
  if (type === 'quality_gate') return '质量门禁'
  if (type === 'packaging') return '包装'
  if (type === 'outsourced') return '外协'
  return '工序'
}

function getProcessConfirmerName(row: SkuProcessRouteRow) {
  if (row.processType === 'quality_gate') return '王质'
  if (row.supplierName) return '李采'
  if (row.processName.includes('包装')) return '陈包'
  return '刘浩'
}

function getProcessConfirmerRole(row: SkuProcessRouteRow) {
  if (row.processType === 'quality_gate') return '品质确认'
  if (row.supplierName) return '采购确认'
  if (row.processName.includes('包装')) return '包装确认'
  return '工程确认'
}

function getProcessCost(row: SkuProcessRouteRow) {
  if (row.processType === 'quality_gate') return 0.4
  if (row.processName.includes('包装')) return 0.3
  if (row.processName.includes('喷') || row.processName.includes('表面')) return 1.2
  if (row.supplierName) return 1.6
  return 0.8
}

function getProcessTags(row: SkuProcessRouteRow): ProductProcessDetailTag[] {
  const tags: ProductProcessDetailTag[] = []
  if (row.processType === 'quality_gate' || row.processName.includes('磁铁') || row.processName.includes('镜面')) tags.push('key')
  if (row.processName.includes('改') || row.processName.includes('黑色') || row.processName.includes('表面')) tags.push('changed')
  if (row.supplierName) tags.push('outsourced')
  return tags
}

function openProductionDocPreview(file: { fileId?: string; fileName: string; category: string; versionNo: string; owner?: string; updatedAt: string; status?: string; previewUrl?: string; downloadUrl?: string }) {
  activeProductionDoc.value = {
    fileId: file.fileId || '',
    fileName: file.fileName,
    category: file.category,
    versionNo: file.versionNo,
    owner: file.owner,
    updatedAt: file.updatedAt,
    status: file.status,
    previewUrl: file.previewUrl,
    downloadUrl: file.downloadUrl
  }
  productionPreviewVisible.value = true
}

function syncTabFromRoute() {
  const routeTab = String(route.query.tab || 'in_progress')
  activeTab.value = ['all_projects', 'in_progress', 'archived', 'abandoned'].includes(routeTab)
    ? (routeTab as ProjectTab)
    : 'in_progress'
}

async function loadData() {
  loading.value = true
  try {
    rows.value = await getProjects({ page: 1, size: 100 })
  } finally {
    loading.value = false
  }
}

watch(() => route.query.tab, syncTabFromRoute, { immediate: true })

watch(activeFlow, () => {
  selectedTimelineNode.value = null
})

watch(
  () => route.query.archiveView,
  () => {
    skuPageStage.value = 'product-home'
    skuActiveProductId.value = null
  }
)

watch(
  () => [route.query.archiveView, route.query.productId, route.query.skuId, rows.value.length],
  () => {
    const productId = Number(route.query.productId || 0)
    if (productId && archiveView.value === 'product') {
      const target = archivedProductRows.value.find((item) => item.productId === productId) || rows.value.find((item) => item.productId === productId)
      if (target) openDetail(target)
      return
    }

    const skuId = Number(route.query.skuId || 0)
    if (skuId && archiveView.value === 'sku') {
      const target = archivedSkuRows.value.find((item) => item.productId === skuId) || rows.value.find((item) => item.productId === skuId)
      if (target) openDetail(target)
    }
  },
  { immediate: true }
)

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="项目管理"
    description="项目管理以 Product 作为承载对象，按全部项目、进行中、已归档和已放弃组织。"
  >
    <section class="project-filter-bar" aria-label="项目筛选">
      <nav class="project-tag-bar" aria-label="项目视图">
        <button
          v-for="tag in projectQuickTags"
          :key="tag.value"
          class="project-tag-bar__item"
          :class="{ 'is-active': projectQuickTag === tag.value }"
          type="button"
          @click="selectProjectQuickTag(tag.value)"
        >
          {{ tag.label }}
        </button>
      </nav>
      <el-input v-model="keyword" clearable class="project-filter-search" placeholder="搜索项目编码 / 名称 / 系列 / 负责人" />
    </section>

    <section class="project-module-panel">
      <header class="project-module-panel__header">
        <div>
          <div class="project-current-heading">
            <strong>{{ currentModuleTitle }}</strong>
            <span class="project-current-count">{{ currentModuleCount }} 个</span>
          </div>
          <p class="project-module-panel__desc">{{ currentModuleDescription }}</p>
        </div>
        <div class="project-module-panel__actions">
          <el-button v-if="projectQuickTag === 'archived_product'" type="primary" @click="openArchiveImport('product')">导入数据</el-button>
          <el-button v-if="projectQuickTag === 'archived_sku'" type="primary" @click="openArchiveImport('sku')">导入数据</el-button>
        </div>
      </header>

      <div class="project-module-panel__body">
        <template v-if="activeTab === 'all_projects'">
          <section class="project-list-shell" v-loading="loading">
            <el-table :data="allProjectRows" border stripe>
              <el-table-column label="概览" width="88" fixed="left">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="openProjectOverview(row)">概览</el-button>
                </template>
              </el-table-column>
              <el-table-column prop="productCode" label="项目编码" min-width="170" />
              <el-table-column prop="productName" label="项目名称" min-width="220" />
              <el-table-column prop="projectType" label="类型" width="110" />
              <el-table-column label="标签" width="150">
                <template #default="{ row }">
                  <el-tag :type="getProjectTagType(row.projectTag)" effect="light">{{ row.projectTag }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="currentStage" label="当前阶段" min-width="170" />
              <el-table-column prop="ownerUserName" label="负责人" width="110" />
              <el-table-column prop="versionNo" label="版本" width="100" />
              <el-table-column label="最近更新时间" width="140">
                <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
              </el-table-column>
            </el-table>
          </section>
        </template>

        <template v-else-if="activeTab === 'in_progress'">
          <section class="flow-filter-bar" aria-label="进行中项目类型">
            <button
              v-for="item in flowFilters"
              :key="item.value"
              class="flow-filter-bar__item"
              :class="{ 'is-active': activeFlow === item.value }"
              type="button"
              @click="selectFlow(item.value)"
            >
              {{ item.label }}
            </button>
          </section>

          <div v-if="activeFlow === 'all'" class="list-context-bar">
            <strong>全部进行中项目</strong>
            <span class="subtle-text">选择新产品线或新型号线后，会展示对应的重要节点时间轴。</span>
          </div>

          <template v-if="activeFlow !== 'all'">
            <section class="project-timeline-panel">
              <div class="toolbar-row">
                <h3 class="section-title">{{ activeFlow === 'product_line' ? '新产品线' : '新型号线' }}重要节点</h3>
              </div>
              <div class="timeline-row">
                <button
                  v-for="node in flowTimeline"
                  :key="node.nodeKey"
                  class="timeline-node"
                  :class="{
                    'is-selected': selectedTimelineNode?.nodeKey === node.nodeKey,
                    'is-gate': node.gate,
                    'is-empty': node.count === 0
                  }"
                  type="button"
                  @click="selectTimelineNode(node)"
                >
                  <strong>{{ node.title }}</strong>
                  <span class="timeline-node__phase">{{ node.phase }}</span>
                  <span class="timeline-node__hint">{{ node.hint }}</span>
                  <span class="timeline-node__children">包含：{{ node.childNodes.join('、') }}</span>
                  <span class="timeline-node__count">{{ node.count }} 个项目</span>
                </button>
              </div>
            </section>

            <div class="selected-node-bar" v-if="selectedTimelineNode">
              <strong>{{ selectedTimelineNode.title }} / 第 {{ selectedTimelineNode.childStepNos.join('、') }} 步</strong>
              <span class="subtle-text">当前在这个环节的项目：{{ timelineProjects.length }} 个</span>
            </div>
          </template>

          <section class="project-list-shell" v-loading="loading">
            <el-table v-if="timelineProjects.length" :data="timelineProjects" border stripe>
              <el-table-column prop="productCode" label="产品编码" min-width="170" />
              <el-table-column label="项目对象" min-width="240">
                <template #default="{ row }">
                  <div class="cell-stack">
                    <strong>{{ row.productName }}</strong>
                    <span class="subtle-text">{{ row.seriesName }} / {{ row.ownerUserName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="流程类型" width="130">
                <template #default="{ row }">
                  <el-tag :type="row.productType === 'product_line' ? 'primary' : 'success'" effect="light">
                    {{ getProjectTypeLabel(row) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="模具策略" width="140">
                <template #default="{ row }">{{ getMoldActionLabel(row) }}</template>
              </el-table-column>
              <el-table-column label="当前节点" min-width="180">
                <template #default="{ row }">
                  <div class="cell-stack">
                    <strong>第 {{ row.currentStepNo || '--' }} 步</strong>
                    <span class="subtle-text">{{ row.currentStage }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="下一关口" min-width="220">
                <template #default="{ row }">{{ getNextGate(row) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }"><StatusTag :status="row.status" object-type="product" /></template>
              </el-table-column>
              <el-table-column label="完成度" min-width="160">
                <template #default="{ row }">
                  <el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="8" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="96" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无匹配的项目" />
          </section>
        </template>

        <template v-else-if="activeTab === 'archived'">
          <template v-if="archiveView === 'overview'">
            <div class="list-context-bar">
              <strong>已归档汇总</strong>
              <span class="subtle-text">通过顶部标签进入已归档产品或已归档 SKU 明细。</span>
            </div>
            <section class="metric-grid">
              <div class="metric-card"><p class="metric-card__label">已归档总数</p><p class="metric-card__value">{{ archiveSummary.total }}</p></div>
              <div class="metric-card"><p class="metric-card__label">产品</p><p class="metric-card__value">{{ archiveSummary.products }}</p></div>
              <div class="metric-card"><p class="metric-card__label">SKU</p><p class="metric-card__value">{{ archiveSummary.skus }}</p></div>
            </section>
          </template>

          <template v-else-if="archiveView === 'product'">
            <div class="list-context-bar">
              <strong>产品管理</strong>
              <span class="subtle-text">只展示已归档的新产品线产品，详情弹窗按 SKU 详情形式展示。</span>
              <el-button type="primary" @click="openArchiveImport('product')">导入数据</el-button>
            </div>
            <section class="project-list-shell" v-loading="loading">
              <el-table :data="archivedProductRows" border stripe>
                <el-table-column prop="productCode" label="产品编码" min-width="180" />
                <el-table-column prop="productName" label="产品名称" min-width="220" />
                <el-table-column prop="seriesName" label="系列" width="140" />
                <el-table-column prop="ownerUserName" label="负责人" width="110" />
                <el-table-column prop="versionNo" label="版本" width="100" />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }"><StatusTag :status="row.status" object-type="product" /></template>
                </el-table-column>
                <el-table-column prop="activeBomVersion" label="BOM 主版本" width="130" />
                <el-table-column label="资料完整率" min-width="160">
                  <template #default="{ row }"><el-progress :percentage="Math.round(row.completionRate * 100)" :stroke-width="8" /></template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template>
                </el-table-column>
              </el-table>
            </section>
          </template>

          <template v-else>
            <section v-if="skuPageStage === 'product-home'" class="page-panel sku-product-home">
              <div class="toolbar-row sku-product-home__head">
                <div>
                  <h3 class="section-title">SKU 管理</h3>
                  <p class="page-panel-desc">先按产品卡片定位，再进入该产品下的 SKU 列表。</p>
                </div>
                <el-button type="primary" @click="openArchiveImport('sku')">导入数据</el-button>
              </div>
              <div class="sku-product-grid" v-loading="loading">
                <button
                  v-for="product in skuProductCards"
                  :key="product.productId"
                  class="sku-product-card"
                  type="button"
                  @click="skuOpenSkuList(product.productId)"
                >
                  <div class="sku-product-card__image"><span>{{ product.seriesName }}</span></div>
                  <div class="toolbar-row">
                    <strong>{{ product.productName }}</strong>
                    <StatusTag :status="product.status" object-type="product" />
                  </div>
                  <p class="subtle-text">{{ product.productCode }}</p>
                  <p class="sku-product-card__series">{{ product.seriesName }}</p>
                  <div class="sku-product-card__meta">
                    <span>{{ getSkuCountForProduct(product.productId) }} 个 SKU</span>
                    <span class="subtle-text">{{ getRecentUpdate(product) }}</span>
                  </div>
                </button>
              </div>
            </section>

            <section v-else class="page-panel sku-list-panel" v-loading="loading">
              <div class="toolbar-row sku-list-panel__head">
                <div>
                  <el-button link type="primary" @click="skuBackToProductHome">返回产品卡片</el-button>
                  <h3 class="section-title">{{ skuActiveProduct?.productName }} / SKU 列表</h3>
                  <p class="page-panel-desc">查看当前产品下的 SKU 信息、版本状态和归档资料。</p>
                </div>
                <div class="sku-list-panel__actions">
                  <el-input v-model="skuKeyword" clearable class="sku-list-panel__search" placeholder="搜索编码 / 名称 / 型号 / 颜色" />
                  <el-button type="primary" @click="openArchiveImport('sku')">导入数据</el-button>
                </div>
              </div>
              <el-table :data="skuCurrentSkuRows" border stripe>
                <el-table-column label="示例图" min-width="130">
                  <template #default="{ row }">
                    <div class="sku-image-cell"><div class="sku-image-cell__thumb"><span>{{ row.model }}</span></div></div>
                  </template>
                </el-table-column>
                <el-table-column prop="productCode" label="SKU 编码" min-width="180" />
                <el-table-column prop="productName" label="SKU 名称" min-width="220" />
                <el-table-column prop="model" label="型号" width="140" />
                <el-table-column label="单位" width="80"><template #default>pcs</template></el-table-column>
                <el-table-column label="项目来源" min-width="140"><template #default>历史归档</template></el-table-column>
                <el-table-column prop="color" label="颜色" width="120" />
                <el-table-column prop="versionNo" label="版本" width="100" />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }"><StatusTag :status="row.status" object-type="product" /></template>
                </el-table-column>
                <el-table-column label="最近更新时间" width="140">
                  <template #default="{ row }">{{ getRecentUpdate(row) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="80" fixed="right">
                  <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template>
                </el-table-column>
              </el-table>
            </section>
          </template>
        </template>

        <template v-else>
          <div class="list-context-bar">
            <strong>已放弃项目</strong>
            <span class="subtle-text">已放弃项目永久保留，可查看原因和可复用资产。</span>
          </div>
          <section class="project-list-shell" v-loading="loading">
            <el-table :data="abandonedProjects" border stripe>
              <el-table-column prop="productCode" label="项目编码" min-width="170" />
              <el-table-column prop="productName" label="项目对象" min-width="220" />
              <el-table-column prop="currentStage" label="停止阶段" min-width="140" />
              <el-table-column prop="ownerUserName" label="负责人" width="110" />
              <el-table-column prop="abandonedAt" label="放弃日期" width="130" />
              <el-table-column prop="abandonReason" label="放弃原因" min-width="220" />
              <el-table-column prop="reusableAssets" label="可复用资产" min-width="240" />
            </el-table>
          </section>
        </template>
      </div>
    </section>

    <el-dialog v-model="overviewVisible" title="项目概览" width="640px" destroy-on-close>
      <template v-if="overviewProject">
        <div class="project-overview-dialog">
          <div class="project-overview-dialog__title">
            <strong>{{ overviewProject.productName }}</strong>
            <el-tag :type="getProjectTagType(overviewProject.projectTag)" effect="light">{{ overviewProject.projectTag }}</el-tag>
          </div>
          <dl class="project-overview-grid">
            <div><dt>项目编码</dt><dd>{{ overviewProject.productCode }}</dd></div>
            <div><dt>项目类型</dt><dd>{{ overviewProject.projectType }}</dd></div>
            <div><dt>当前阶段</dt><dd>{{ overviewProject.currentStage }}</dd></div>
            <div><dt>负责人</dt><dd>{{ overviewProject.ownerUserName }}</dd></div>
            <div><dt>版本</dt><dd>{{ overviewProject.versionNo }}</dd></div>
            <div><dt>最近更新时间</dt><dd>{{ formatDate(overviewProject.updatedAt) }}</dd></div>
          </dl>
          <section class="project-overview-block">
            <p class="subtle-text">说明</p>
            <strong>
              {{
                overviewProject.abandoned
                  ? '该项目已放弃，保留原因与可复用资产用于后续追溯。'
                  : getNextGate(overviewProject.source!)
              }}
            </strong>
          </section>
        </div>
      </template>
      <template #footer>
        <el-button @click="overviewVisible = false">关闭</el-button>
        <el-button
          v-if="overviewProject && !overviewProject.abandoned"
          type="primary"
          @click="openProduct(overviewProject.productId)"
        >
          进入详情
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" width="1080px" destroy-on-close>
      <template #header>
        <div class="detail-dialog-header">
          <div>
            <strong class="detail-dialog-header__title">
              {{ detailTarget?.productType === 'product_line' ? '产品详情' : 'SKU 详情' }}
            </strong>
            <div class="detail-dialog-header__meta" v-if="detailTarget">
              <span>{{ detailTarget.productCode }}</span>
              <span v-if="!isProductDetailDialog">型号 {{ detailTarget.model }} / {{ detailTarget.color }}</span>
              <span>系列 {{ detailTarget.seriesName }}</span>
              <span>版本 {{ detailTarget.versionNo }}</span>
            </div>
          </div>
          <div class="detail-dialog-header__actions">
            <StatusTag :status="detailTarget?.status || 'draft'" object-type="product" />
            <el-tooltip
              :disabled="canEditDetailTarget"
              :content="detailTarget?.status === 'released' || detailTarget?.status === 'archived' ? '已发布/已归档产品不可直接编辑，如需变更请发起变更流程' : '当前角色无编辑权限'"
              placement="top"
            >
              <span>
                <el-button
                  type="primary"
                  plain
                  size="small"
                  :disabled="!canEditDetailTarget"
                  @click="openDetailEdit"
                >
                  {{ isProductDetailDialog ? '编辑产品' : '编辑 SKU' }}
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
      </template>
      <div v-if="detailTarget" v-loading="detailLoading" class="detail-dialog">
        <nav class="detail-breadcrumb" aria-label="详情分区">
          <button
            v-for="section in activeDetailSections"
            :key="section.key"
            class="detail-breadcrumb__item"
            :class="{ 'is-active': activeDetailSection === section.key }"
            type="button"
            @click="activeDetailSection = section.key"
          >
            {{ section.label }}
          </button>
        </nav>

        <section v-if="isProductDetailDialog" v-show="activeDetailSection === 'current_node'" class="detail-section">
          <div class="product-node-hero">
            <div>
              <p class="subtle-text">当前节点</p>
              <h4 class="product-node-hero__title">{{ activeProductFlowNode?.nodeName || detailTarget.currentStage }}</h4>
              <p class="page-panel-desc">{{ activeProductFlowNode?.experienceSummary || activeProductFlowNode?.summary || detailPresentation?.summary || '暂无当前节点说明。' }}</p>
            </div>
            <div class="product-node-hero__actions">
              <StatusTag :status="detailTarget.status" object-type="product" />
            </div>
          </div>

          <div class="timeline-action-panel">
            <div class="timeline-action-panel__status">
              <el-tag :type="currentTimelineConfirmed ? 'success' : 'warning'" effect="light">
                {{ currentTimelineConfirmed ? '已确认' : '未确认' }}
              </el-tag>
              <span>{{ currentTimelineActionLabel }}</span>
            </div>
            <div class="timeline-action-panel__buttons">
              <el-button
                v-if="activeProductFlowNode && !currentTimelineConfirmed"
                type="primary"
                :loading="timelineActionLoading === 'confirm'"
                :disabled="Boolean(timelineActionLoading)"
                @click="handleConfirmCurrentNode"
              >
                确认当前节点
              </el-button>
              <el-button
                v-if="activeProductFlowNode && currentTimelineConfirmed"
                type="primary"
                :loading="timelineActionLoading === 'advance'"
                :disabled="Boolean(timelineActionLoading) || !canAdvanceCurrentTimelineNode"
                @click="handleAdvanceCurrentNode"
              >
                推进下一节点
              </el-button>
              <el-dropdown
                v-if="activeProductFlowNode"
                :disabled="Boolean(timelineActionLoading)"
                @command="handleReturnCommand"
              >
                <el-button :loading="timelineActionLoading === 'return'">退回</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="current">退回当前节点修改</el-dropdown-item>
                    <el-dropdown-item command="previous">退回上一节点</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <div class="detail-grid">
            <div class="info-card">
              <span class="subtle-text">确认状态</span>
              <strong>{{ currentTimelineConfirmed ? '已确认' : '未确认' }}</strong>
            </div>
            <div class="info-card">
              <span class="subtle-text">最近动作</span>
              <strong>{{ getTimelineActionLabel(timelineLastAction) }}</strong>
              <span class="subtle-text">{{ timelineLastOperatedAt ? formatDate(timelineLastOperatedAt) : '--' }}</span>
            </div>
            <div class="info-card">
              <span class="subtle-text">最近操作人</span>
              <strong>{{ timelineLastOperatorUserName || '--' }}</strong>
            </div>
            <div class="info-card">
              <span class="subtle-text">最近退回原因</span>
              <strong>{{ timelineLastReason || '--' }}</strong>
            </div>
            <div class="info-card">
              <span class="subtle-text">下一步动作</span>
              <strong>{{ activeProductFlowNode?.nextAction || detailTarget.nextAction || detailPresentation?.nextNode || '--' }}</strong>
            </div>
            <div class="info-card">
              <span class="subtle-text">接收人 / 时间</span>
              <strong>{{ activeProductFlowNode?.receiverUserName || activeProductFlowNode?.receiverRole || '--' }}</strong>
              <span class="subtle-text">{{ activeProductFlowNode?.receivedAt || '--' }}</span>
            </div>
            <div class="info-card">
              <span class="subtle-text">推动人 / 时间</span>
              <strong>{{ activeProductFlowNode?.promoterUserName || activeProductFlowNode?.promoterRole || activeProductFlowNode?.ownerRole || '--' }}</strong>
              <span class="subtle-text">{{ activeProductFlowNode?.promotedAt || (activeProductFlowNode?.status === 'current' ? '推进中' : '--') }}</span>
            </div>
            <div class="info-card">
              <span class="subtle-text">下一接收方</span>
              <strong>{{ activeProductFlowNode?.nextReceiverUserName || activeProductFlowNode?.nextReceiverRole || '--' }}</strong>
            </div>
          </div>

          <div v-if="activeProductFlowNode?.riskNote || activeProductFlowNode?.detailLines?.length" class="product-node-checks">
            <el-alert v-if="activeProductFlowNode?.riskNote" type="warning" show-icon :closable="false" :title="activeProductFlowNode.riskNote" />
            <ul v-if="activeProductFlowNode?.detailLines?.length" class="node-check-list">
              <li v-for="line in activeProductFlowNode.detailLines" :key="line">{{ line }}</li>
            </ul>
          </div>
        </section>

        <section v-show="activeDetailSection === 'basic'" class="detail-section">
          <h4 class="section-title">基础信息</h4>
          <div class="detail-grid">
            <div class="info-card"><span class="subtle-text">{{ detailTarget.productType === 'product_line' ? '产品编码' : 'SKU 编码' }}</span><strong>{{ detailTarget.productCode }}</strong></div>
            <div class="info-card"><span class="subtle-text">{{ detailTarget.productType === 'product_line' ? '产品名称' : 'SKU 名称' }}</span><strong>{{ detailTarget.productName }}</strong></div>
            <div class="info-card"><span class="subtle-text">系列</span><strong>{{ detailTarget.seriesName }}</strong></div>
            <div v-if="isProductDetailDialog" class="info-card"><span class="subtle-text">产品类型</span><strong>{{ getProjectTypeLabel(detailTarget) }}</strong></div>
            <div class="info-card"><span class="subtle-text">版本</span><strong>{{ detailTarget.versionNo }}</strong></div>
            <div class="info-card"><span class="subtle-text">型号</span><strong>{{ detailTarget.model || '--' }}</strong></div>
            <div class="info-card"><span class="subtle-text">颜色</span><strong>{{ detailTarget.color || '--' }}</strong></div>
            <div v-if="isProductDetailDialog" class="info-card"><span class="subtle-text">材质</span><strong>{{ detailTarget.material || '--' }}</strong></div>
            <div v-if="isProductDetailDialog" class="info-card"><span class="subtle-text">客户来源</span><strong>{{ detailTarget.customerName || '内部立项' }}</strong></div>
            <div v-if="isProductDetailDialog" class="info-card"><span class="subtle-text">负责人</span><strong>{{ detailTarget.ownerUserName }}</strong></div>
            <div class="info-card"><span class="subtle-text">状态</span><StatusTag :status="detailTarget.status" object-type="product" /></div>
            <div class="info-card"><span class="subtle-text">当前阶段</span><strong>{{ detailTarget.currentStage }}</strong></div>
            <div v-if="isProductDetailDialog" class="info-card"><span class="subtle-text">资料完整率</span><strong>{{ Math.round(detailTarget.completionRate * 100) }}%</strong></div>
          </div>
        </section>

        <section v-if="isProductDetailDialog" v-show="activeDetailSection === 'project_flow'" class="detail-section">
          <div class="detail-section__head">
            <div>
              <h4 class="section-title">项目流程</h4>
              <p class="page-panel-desc">展示每个环节经历了什么、由谁接收、由谁推动以及推动时间。</p>
            </div>
          </div>
          <section class="product-flow-board">
            <aside class="product-flow-board__stages">
              <button
                v-for="stage in productFlowStages"
                :key="stage.stageKey"
                class="flow-stage-card"
                :class="{ 'is-active': stage.stageKey === activeProductFlowStage?.stageKey }"
                type="button"
                @click="activeProductFlowStageKey = stage.stageKey"
              >
                <span class="flow-stage-card__name">{{ stage.stageName }}</span>
                <span class="flow-stage-card__phase">{{ stage.phaseName }}</span>
                <el-tag size="small" effect="light" :type="getFlowStatusType(stage.status)">
                  {{ getFlowStatusLabel(stage.status) }}
                </el-tag>
              </button>
            </aside>

            <main v-if="activeProductFlowStage" class="product-flow-board__detail">
              <div class="flow-stage-summary">
                <div>
                  <h4>{{ activeProductFlowStage.stageName }}</h4>
                  <p>{{ activeProductFlowStage.summary }}</p>
                </div>
                <el-tag :type="getFlowStatusType(activeProductFlowStage.status)" effect="light">
                  {{ getFlowStatusLabel(activeProductFlowStage.status) }}
                </el-tag>
              </div>

              <div class="flow-stage-meta-grid">
                <div class="info-card">
                  <span class="subtle-text">责任角色</span>
                  <strong>{{ activeProductFlowStage.receiverRole || '--' }}</strong>
                </div>
                <div class="info-card">
                  <span class="subtle-text">接收人 / 时间</span>
                  <strong>{{ activeProductFlowStage.receiverUserName || '--' }}</strong>
                  <span class="subtle-text">{{ activeProductFlowStage.receivedAt || '--' }}</span>
                </div>
                <div class="info-card">
                  <span class="subtle-text">推动人 / 时间</span>
                  <strong>{{ activeProductFlowStage.promoterUserName || activeProductFlowStage.promoterRole || '--' }}</strong>
                  <span class="subtle-text">{{ activeProductFlowStage.promotedAt || '--' }}</span>
                </div>
                <div class="info-card">
                  <span class="subtle-text">下一步</span>
                  <strong>{{ activeProductFlowStage.nextAction || '--' }}</strong>
                </div>
                <div class="info-card">
                  <span class="subtle-text">风险提示</span>
                  <strong>{{ activeProductFlowStage.riskNote || '暂无风险' }}</strong>
                </div>
              </div>

              <div class="flow-child-node-list">
                <div v-for="node in activeProductFlowStage.childNodes" :key="node.nodeKey" class="flow-child-node">
                  <strong>第 {{ node.stepNo }} 步：{{ node.nodeName }}</strong>
                  <div>
                    <span v-if="node.actionLabel" class="subtle-text">{{ node.actionLabel }}</span>
                    <el-tag size="small" effect="light" :type="getFlowStatusType(node.status)">
                      {{ getFlowStatusLabel(node.status) }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </main>
          </section>
        </section>

        <section v-if="isProductDetailDialog" v-show="activeDetailSection === 'bom_manage'" class="detail-section">
          <ProjectBomPanel :project-id="detailTarget.productId" />
        </section>

        <section v-if="isProductDetailDialog" v-show="activeDetailSection === 'process_detail'" class="detail-section">
          <ProjectProcessRoutePanel :project-id="detailTarget.productId" />
        </section>

        <section v-if="isProductDetailDialog && activeDetailSection === 'materials'" class="detail-section">
          <TimelineAttachmentPanel
            :project-id="detailTarget.productId"
            :node-key="activeProductFlowNode?.nodeKey || null"
            @changed="handleM4AttachmentChanged"
          />
        </section>

        <section v-if="isProductDetailDialog" v-show="activeDetailSection === 'business'" class="detail-section">
          <h4 class="section-title">商务区</h4>
          <div class="detail-cost-grid">
            <div class="info-card"><span class="subtle-text">实际成本</span><strong>{{ formatAmount(detailPresentation?.costPanel.actualTotal || detailTarget.actualCost || detailTarget.totalCost || 0) }}</strong></div>
            <div class="info-card"><span class="subtle-text">预计成本</span><strong>{{ formatAmount(detailPresentation?.costPanel.estimatedTotal || detailTarget.estimatedCost || 0) }}</strong></div>
            <div class="info-card"><span class="subtle-text">供应商数量</span><strong>{{ detailPresentation?.suppliers.length || 0 }} 家</strong></div>
          </div>
          <el-table :data="detailPresentation?.costPanel.actualLines || []" border stripe size="small">
            <el-table-column prop="label" label="成本项" width="150" />
            <el-table-column label="金额" width="140"><template #default="{ row }">{{ formatAmount(row.amount) }}</template></el-table-column>
            <el-table-column prop="note" label="说明" min-width="240" />
          </el-table>
          <el-table :data="detailPresentation?.suppliers || []" border stripe size="small">
            <el-table-column prop="supplierName" label="供应商" min-width="160" />
            <el-table-column prop="role" label="角色" width="140" />
            <el-table-column prop="statusLabel" label="状态" width="120" />
            <el-table-column prop="note" label="说明" min-width="220" />
          </el-table>
        </section>

        <section v-if="isProductDetailDialog" v-show="activeDetailSection === 'quality'" class="detail-section">
          <h4 class="section-title">质量区</h4>
          <el-table :data="detailPresentation?.qualityRecords || []" border stripe size="small">
            <el-table-column prop="testItem" label="测试项目" min-width="150" />
            <el-table-column prop="resultLabel" label="测试结果" width="120" />
            <el-table-column prop="owner" label="负责人" width="140" />
            <el-table-column prop="testedAt" label="测试时间" width="130" />
            <el-table-column prop="note" label="结论说明" min-width="260" />
          </el-table>
          <el-empty v-if="!detailPresentation?.qualityRecords?.length" description="暂无质量记录" />
        </section>

        <section v-show="activeDetailSection === 'cost'" class="detail-section">
          <h4 class="section-title">成本</h4>
          <div class="detail-cost-grid">
            <div class="info-card"><span class="subtle-text">实际成本</span><strong>{{ formatAmount(detailPresentation?.costPanel.actualTotal || detailTarget.actualCost || 0) }}</strong></div>
            <div v-if="detailPresentation?.costPanel.showEstimated" class="info-card"><span class="subtle-text">预计成本</span><strong>{{ formatAmount(detailPresentation?.costPanel.estimatedTotal || detailTarget.estimatedCost || 0) }}</strong></div>
            <div class="info-card"><span class="subtle-text">BOM 主版本</span><strong>{{ detailTarget.activeBomVersion || detailBomVersion || '--' }}</strong></div>
          </div>
        </section>

        <section v-show="activeDetailSection === 'bom'" class="detail-section">
          <ProjectBomPanel :project-id="detailTarget.productId" />
        </section>

        <section v-show="activeDetailSection === 'process'" class="detail-section">
          <ProjectProcessRoutePanel :project-id="detailTarget.productId" />
        </section>

        <section v-if="!isProductDetailDialog" v-show="activeDetailSection === 'project_flow'" class="detail-section">
          <div class="detail-section__head">
            <div>
              <h4 class="section-title">项目流程</h4>
              <p class="page-panel-desc">SKU 对应新型号线流程，展示差异扩展从需求确认到版本发布的接收、推动与时间记录。</p>
            </div>
          </div>

          <section v-if="skuProjectFlowStages.length" class="product-flow-board">
            <aside class="product-flow-board__stages">
              <button
                v-for="stage in skuProjectFlowStages"
                :key="stage.stageKey"
                class="flow-stage-card"
                :class="{ 'is-active': stage.stageKey === activeSkuFlowStage?.stageKey }"
                type="button"
                @click="activeSkuFlowStageKey = stage.stageKey"
              >
                <span class="flow-stage-card__name">{{ stage.stageName }}</span>
                <span class="flow-stage-card__phase">{{ stage.phaseName }}</span>
                <el-tag size="small" effect="light" :type="getFlowStatusType(stage.status)">
                  {{ getFlowStatusLabel(stage.status) }}
                </el-tag>
              </button>
            </aside>

            <main v-if="activeSkuFlowStage" class="product-flow-board__detail">
              <div class="flow-stage-summary">
                <div>
                  <h4>{{ activeSkuFlowStage.stageName }}</h4>
                  <p>{{ activeSkuFlowStage.summary }}</p>
                </div>
                <el-tag :type="getFlowStatusType(activeSkuFlowStage.status)" effect="light">
                  {{ getFlowStatusLabel(activeSkuFlowStage.status) }}
                </el-tag>
              </div>

              <div class="flow-stage-meta-grid">
                <div class="info-card">
                  <span class="subtle-text">责任角色</span>
                  <strong>{{ activeSkuFlowStage.receiverRole || '--' }}</strong>
                </div>
                <div class="info-card">
                  <span class="subtle-text">接收人 / 时间</span>
                  <strong>{{ activeSkuFlowStage.receiverUserName || '--' }}</strong>
                  <span class="subtle-text">{{ activeSkuFlowStage.receivedAt || '--' }}</span>
                </div>
                <div class="info-card">
                  <span class="subtle-text">推动人 / 时间</span>
                  <strong>{{ activeSkuFlowStage.promoterUserName || activeSkuFlowStage.promoterRole || '--' }}</strong>
                  <span class="subtle-text">{{ activeSkuFlowStage.promotedAt || '--' }}</span>
                </div>
                <div class="info-card">
                  <span class="subtle-text">下一步</span>
                  <strong>{{ activeSkuFlowStage.nextAction || '--' }}</strong>
                </div>
                <div class="info-card">
                  <span class="subtle-text">风险提示</span>
                  <strong>{{ activeSkuFlowStage.riskNote || '暂无风险' }}</strong>
                </div>
              </div>

              <div class="flow-child-node-list">
                <div v-for="node in activeSkuFlowStage.childNodes" :key="node.nodeKey" class="flow-child-node">
                  <strong>第 {{ node.stepNo }} 步：{{ node.nodeName }}</strong>
                  <div>
                    <span v-if="node.actionLabel" class="subtle-text">{{ node.actionLabel }}</span>
                    <el-tag size="small" effect="light" :type="getFlowStatusType(node.status)">
                      {{ getFlowStatusLabel(node.status) }}
                    </el-tag>
                  </div>
                </div>
              </div>

              <el-collapse v-model="skuFlowTableExpanded" class="sku-flow-table-collapse">
                <el-collapse-item name="detail">
                  <template #title>
                    <span>查看明细记录（{{ skuProjectFlowRows.length }} 条）</span>
                  </template>
                  <el-table :data="skuProjectFlowRows" border stripe size="small">
                    <el-table-column prop="seqNo" label="顺序" width="70" />
                    <el-table-column prop="stageName" label="新型号线阶段" min-width="140" />
                    <el-table-column prop="nodeName" label="环节" min-width="140" />
                    <el-table-column prop="phaseName" label="阶段" min-width="110" />
                    <el-table-column prop="experienceSummary" label="经历内容" min-width="200" />
                    <el-table-column label="接收人" width="110">
                      <template #default="{ row }">{{ row.receiverUserName || row.receiverRole || '--' }}</template>
                    </el-table-column>
                    <el-table-column label="接收时间" width="110">
                      <template #default="{ row }">{{ row.receivedAt || '--' }}</template>
                    </el-table-column>
                    <el-table-column label="推动人" width="110">
                      <template #default="{ row }">{{ row.promoterUserName || row.promoterRole || '--' }}</template>
                    </el-table-column>
                    <el-table-column label="推动时间" width="110">
                      <template #default="{ row }">{{ row.promotedAt || '--' }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="100">
                      <template #default="{ row }">
                        <el-tag :type="getFlowStatusType(row.status)" effect="light" size="small">
                          {{ getFlowStatusLabel(row.status) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="下一步" min-width="160">
                      <template #default="{ row }">{{ row.nextAction || '--' }}</template>
                    </el-table-column>
                    <el-table-column label="资料数" width="80">
                      <template #default="{ row }">{{ row.documentCount ?? 0 }}</template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
              </el-collapse>
            </main>
          </section>
          <el-empty v-else description="暂无项目流程" />
        </section>

        <section v-if="!isProductDetailDialog && activeDetailSection === 'production_docs'" class="detail-section">
          <TimelineAttachmentPanel
            :project-id="detailTarget.productId"
            :node-key="activeProductFlowNode?.nodeKey || null"
            @changed="handleM4AttachmentChanged"
          />
        </section>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>

      <!-- 生产资料预览 -->
      <FilePreview
        v-model="productionPreviewVisible"
        :file="activeProductionDoc"
      />
    </el-dialog>

    <el-dialog v-model="importVisible" :title="importType === 'product' ? '导入产品数据' : '导入 SKU 数据'" width="640px">
      <el-alert type="info" show-icon :closable="false" title="用于导入历史归档数据，导入后仍按 Product 对象保存和追溯。" />
      <el-upload drag action="" :auto-upload="false" accept=".xlsx,.xls,.csv" class="archive-upload">
        <el-icon><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或点击选择文件</div>
        <template #tip><div class="el-upload__tip">支持 xlsx / xls / csv</div></template>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="importVisible = false">开始导入</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.project-filter-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.project-filter-search { flex: 0 0 320px; max-width: 360px; }

.project-module-panel { overflow: hidden; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.project-module-panel__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 14px 16px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.project-module-panel__body { padding: 12px 16px 16px; background: #fff; }
.project-module-panel__desc { margin: 6px 0 0; color: #64748b; font-size: 13px; }

.project-tag-bar { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; padding: 0; border: 0; background: transparent; }
.flow-filter-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; padding: 10px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }

.project-tag-bar__item,
.flow-filter-bar__item {
  padding: 7px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.project-tag-bar__item.is-active,
.flow-filter-bar__item.is-active {
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
}

.list-context-bar,
.selected-node-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.05);
  margin-bottom: 14px;
}

.project-timeline-panel {
  margin: 16px 0;
  padding: 16px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 8px;
  background: #fff;
}

.timeline-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.timeline-node {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s, box-shadow 0.16s;
}

.timeline-node:hover {
  border-color: #2563eb;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.1);
}

.timeline-node.is-selected {
  border-color: #2563eb;
  background: rgba(37, 99, 235, 0.05);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.12);
}

.timeline-node.is-gate {
  border-left: 3px solid #2563eb;
}

.timeline-node.is-empty {
  opacity: 0.58;
}

.timeline-node__phase,
.timeline-node__hint,
.timeline-node__children {
  color: #64748b;
  font-size: 12px;
}

.timeline-node__children {
  color: #94a3b8;
}

.timeline-node__count {
  margin-top: auto;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

.project-list-shell {
  margin-top: 14px;
}

.cell-stack,
.detail-dialog,
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sku-product-home__head {
  margin-bottom: 14px;
}

.sku-product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.sku-product-card {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s, box-shadow 0.16s, transform 0.16s;
}

.sku-product-card:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.sku-product-card__image,
.sku-image-cell__thumb {
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #e9eef8 0%, #f8fafc 100%);
  color: #3b4a63;
  font-weight: 600;
}

.sku-product-card__image {
  min-height: 100px;
}

.sku-product-card__series {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.sku-product-card__meta,
.sku-list-panel__actions,
.sku-image-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sku-product-card__meta {
  justify-content: space-between;
  margin-top: auto;
}

.sku-list-panel__head {
  align-items: flex-start;
  margin-bottom: 14px;
}

.sku-list-panel__search {
  width: 260px;
}

.sku-image-cell__thumb {
  width: 52px;
  height: 52px;
  font-size: 11px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-cost-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.product-node-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 8px;
  background: #f8fafc;
}

.product-node-hero__title {
  margin: 4px 0 6px;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.35;
}

.product-node-hero__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.product-node-checks {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.node-check-list {
  margin: 0;
  padding: 12px 16px 12px 28px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: #fff;
  color: #334155;
  line-height: 1.7;
}

.product-flow-board {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 14px;
}

.product-flow-board__stages,
.product-flow-board__detail {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 8px;
  background: #fff;
}

.product-flow-board__stages {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
}

.product-flow-board__detail {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}

.flow-stage-card {
  display: flex;
  min-height: 82px;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s, background 0.16s, box-shadow 0.16s;
}

.flow-stage-card:hover,
.flow-stage-card.is-active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.1);
}

.flow-stage-card__name {
  color: #0f172a;
  font-weight: 600;
}

.flow-stage-card__phase {
  color: #64748b;
  font-size: 12px;
}

.flow-stage-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.flow-stage-summary h4 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 17px;
}

.flow-stage-summary p {
  margin: 0;
  color: #475569;
  line-height: 1.6;
}

.flow-stage-meta-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.flow-child-node-list {
  display: grid;
  gap: 8px;
}

.flow-child-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: #f8fafc;
}

.flow-child-node > div {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.bom-version-select-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: #f8fafc;
}

.bom-summary-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #334155;
  font-size: 13px;
}

.bom-summary-row span {
  padding: 6px 10px;
  border-radius: 6px;
  background: #eef2ff;
}

.process-detail-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.process-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.detail-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.detail-dialog-header__title {
  color: #0f172a;
  font-size: 17px;
  font-weight: 600;
}

.detail-dialog-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.detail-dialog-header__actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.sku-flow-table-collapse {
  margin-top: 2px;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  padding-top: 10px;
}

.detail-breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.detail-breadcrumb__item {
  padding: 4px 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
}

.detail-breadcrumb__item:hover {
  background: #f8fafc;
  color: #334155;
}

.detail-breadcrumb__item.is-active {
  color: #0f172a;
  font-weight: 600;
}

.project-overview-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.project-overview-dialog__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.project-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
  margin: 0;
}

.project-overview-grid div,
.project-overview-block {
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: #f8fafc;
}

.project-overview-grid dt {
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
}

.project-overview-grid dd {
  margin: 0;
  color: #0f172a;
  font-weight: 600;
}

.archive-upload {
  margin-top: 16px;
}

.project-current-heading {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.project-current-count {
  color: #94a3b8;
  font-size: 13px;
  font-weight: 400;
}

.timeline-action-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 0;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.timeline-action-panel__status,
.timeline-action-panel__buttons {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 1280px) {
  .timeline-row,
  .sku-product-grid,
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .flow-stage-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sku-flow-stage-strip {
    grid-template-columns: repeat(3, minmax(120px, 1fr));
  }
}

@media (max-width: 768px) {
  .timeline-row,
  .sku-product-grid,
  .detail-grid,
  .detail-cost-grid,
  .product-flow-board,
  .flow-stage-meta-grid,
  .sku-flow-stage-strip,
  .project-overview-grid {
    grid-template-columns: 1fr;
  }

  .project-filter-bar,
  .list-context-bar,
  .selected-node-bar,
  .sku-list-panel__actions,
  .flow-stage-summary,
  .flow-child-node,
  .sku-flow-current,
  .timeline-action-panel,
  .bom-version-select-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .project-filter-search,
  .sku-list-panel__search,
  .process-detail-tools,
  .process-detail-tools :deep(.el-input) {
    width: 100%;
    max-width: none;
  }
}
</style>
