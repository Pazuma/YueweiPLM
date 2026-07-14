import type { ProductSummary } from '@/types/product'
import request, { unwrapPage, unwrapResponse } from '../request'

type ProductType = 'product_line' | 'model_variant'
type TimelineStatus = 'completed' | 'current' | 'pending' | 'rejected'

interface BackendProjectSummary {
  projectId: number
  productId: number
  productCode: string
  productName: string
  productType: ProductType
  productTypeName?: string
  model?: string | null
  color?: string | null
  versionNo: string
  status: ProductSummary['status']
  statusName?: string
  ownerUserId?: number | null
  ownerUserName?: string | null
  currentStepNo: number
  currentNodeName?: string | null
  documentCount?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

interface BackendProjectDetail extends BackendProjectSummary {
  parentProductId?: number | null
  customerId?: number | null
  seriesName?: string | null
  material?: string | null
  packageType?: string | null
  surfaceProcess?: string | null
  coreProcess?: string | null
  composition?: string | null
  timeline?: BackendTimelineDetail | null
  remark?: string | null
}

interface BackendTimelineNode {
  stepNo: number
  nodeCode: string
  nodeName: string
  nodeStatus: TimelineStatus
  documentCount?: number | null
  confirmed?: boolean | null
}

interface BackendTimelineDetail {
  projectId: number
  productId: number
  productType: ProductType
  currentStepNo: number
  currentConfirmed?: boolean | null
  confirmedNodeKey?: string | null
  lastAction?: string | null
  lastReason?: string | null
  lastOperatedAt?: string | null
  lastOperatorUserId?: number | null
  lastOperatorUserName?: string | null
  nodes: BackendTimelineNode[]
}

export type ProjectSummaryVO = ProductSummary

export interface ProjectDetailVO extends ProjectSummaryVO {
  projectId: number
  productId: number
  parentProductId?: number | null
  material: string
  packageType: string
  surfaceProcess: string
  coreProcess: string
  composition: string
  summary: string
  timeline?: TimelineDetailVO
}

export interface TimelineNodeVO {
  nodeKey: string
  nodeName: string
  phaseName: string
  stepNo: number
  status: TimelineStatus
  nodeStatus: TimelineStatus
  summary: string
  experienceSummary?: string
  ownerRole: string
  receiverRole?: string
  receiverUserName?: string
  receivedAt?: string
  promoterRole?: string
  promoterUserName?: string
  promotedAt?: string
  nextAction?: string
  riskNote?: string
  gateLabel?: string
  detailLines?: string[]
  documentCount: number
  confirmed?: boolean
}

export interface TimelineDetailVO {
  projectId: number
  productId?: number
  productType?: ProductType
  currentNode: string
  currentStepNo: number
  currentConfirmed?: boolean | null
  confirmedNodeKey?: string | null
  lastAction?: string | null
  lastReason?: string | null
  lastOperatedAt?: string | null
  lastOperatorUserId?: number | null
  lastOperatorUserName?: string | null
  nodes: TimelineNodeVO[]
}

export interface TimelineActionResultVO {
  projectId: number
  productId: number
  action: 'confirm' | 'advance' | 'return'
  nodeKey: string
  beforeStepNo: number
  currentStepNo: number
  currentNodeKey: string
  currentNodeName: string
  currentConfirmed: boolean
  productStatus: string
  logId: number
}

export interface TimelineActionPayload {
  remark?: string
  reason?: string
  returnToPrevious?: boolean
}

interface ProjectQueryParams {
  page?: number
  size?: number
  keyword?: string
  status?: string
  productType?: string
  ownerUserId?: number
}

function normalizeProductType(value?: string | null): ProductType {
  return value === 'model_variant' ? 'model_variant' : 'product_line'
}

function getSeriesName(item: Pick<BackendProjectSummary, 'productName'> & { seriesName?: string | null }) {
  return item.seriesName || item.productName.split(/\s+/)[0] || item.productName
}

function getCompletionRate(currentStepNo?: number | null) {
  const step = Math.max(1, Math.min(Number(currentStepNo || 1), 6))
  return Number((step / 6).toFixed(2))
}

function mapProjectSummary(item: BackendProjectSummary | BackendProjectDetail): ProductSummary {
  const productType = normalizeProductType(item.productType)
  const currentStepNo = Number(item.currentStepNo || 1)
  return {
    productId: Number(item.productId || item.projectId),
    parentProductId: 'parentProductId' in item ? item.parentProductId || null : null,
    productCode: item.productCode,
    productName: item.productName,
    productType,
    seriesName: getSeriesName(item),
    model: item.model || '--',
    color: item.color || '--',
    material: 'material' in item ? item.material || '' : '',
    ownerUserName: item.ownerUserName || '--',
    versionNo: item.versionNo || 'A',
    status: item.status,
    currentStage: item.currentNodeName || '待推进',
    currentStepNo,
    customerName: '',
    frozenFlag: false,
    releasedAt: ['released', 'archived'].includes(item.status) ? item.updatedAt || null : null,
    completionRate: getCompletionRate(currentStepNo),
    estimatedCost: 0,
    estimatedCostCurrency: 'CNY',
    actualCost: null,
    rdCost: null,
    productCost: null,
    totalCost: null,
    testItemCount: 0,
    activeBomVersion: '',
    productFlowMode: productType === 'model_variant' ? 'new_model_variant' : 'new_product_line',
    lifecycle: 'initiation',
    moldAction: productType === 'model_variant' ? 'modify' : null,
    nextAction: item.currentNodeName ? `继续处理：${item.currentNodeName}` : '继续推进当前节点',
    gateSummary: '来自后端 M2 项目时间轴'
  }
}

function mapTimelineNode(node: BackendTimelineNode): TimelineNodeVO {
  const status = node.nodeStatus
  return {
    nodeKey: node.nodeCode,
    nodeName: node.nodeName,
    phaseName: `第 ${node.stepNo} 节点`,
    stepNo: node.stepNo,
    status,
    nodeStatus: status,
    summary: node.confirmed ? '当前节点已确认' : '等待节点处理',
    experienceSummary: node.confirmed ? '节点确认记录已写入后端。' : '节点状态来自后端 M2 时间轴。',
    ownerRole: '工程',
    receiverRole: '工程',
    nextAction: status === 'current' ? '处理当前节点' : undefined,
    documentCount: Number(node.documentCount || 0),
    confirmed: Boolean(node.confirmed)
  }
}

function mapTimeline(detail: BackendTimelineDetail): TimelineDetailVO {
  const nodes = (detail.nodes || []).map(mapTimelineNode)
  const current = nodes.find((node) => node.status === 'current')
  return {
    projectId: Number(detail.projectId),
    productId: Number(detail.productId),
    productType: normalizeProductType(detail.productType),
    currentNode: current?.nodeName || nodes[0]?.nodeName || '待推进',
    currentStepNo: Number(detail.currentStepNo || current?.stepNo || 1),
    currentConfirmed: detail.currentConfirmed,
    confirmedNodeKey: detail.confirmedNodeKey,
    lastAction: detail.lastAction,
    lastReason: detail.lastReason,
    lastOperatedAt: detail.lastOperatedAt,
    lastOperatorUserId: detail.lastOperatorUserId,
    lastOperatorUserName: detail.lastOperatorUserName,
    nodes
  }
}

function mapProjectDetail(item: BackendProjectDetail): ProjectDetailVO {
  const summary = mapProjectSummary(item)
  return {
    ...summary,
    projectId: Number(item.projectId || item.productId),
    productId: Number(item.productId || item.projectId),
    parentProductId: item.parentProductId || null,
    material: item.material || '',
    packageType: item.packageType || '',
    surfaceProcess: item.surfaceProcess || '',
    coreProcess: item.coreProcess || '',
    composition: item.composition || '',
    summary: item.remark || item.currentNodeName || '',
    timeline: item.timeline ? mapTimeline(item.timeline) : undefined
  }
}

export async function getWorkbenchInProgressProjects(params?: ProjectQueryParams): Promise<ProjectSummaryVO[]> {
  const response = await request.get('/workbench/projects/in-progress', { params })
  return unwrapPage<BackendProjectSummary>(response).content.map(mapProjectSummary)
}

export async function getProjects(params?: ProjectQueryParams): Promise<ProjectSummaryVO[]> {
  const response = await request.get('/projects', { params })
  return unwrapPage<BackendProjectSummary>(response).content.map(mapProjectSummary)
}

export async function getProjectDetail(projectId: number): Promise<ProjectDetailVO> {
  const response = await request.get(`/projects/${projectId}`)
  return mapProjectDetail(unwrapResponse<BackendProjectDetail>(response))
}

export async function getProjectSummary(projectId: number): Promise<ProjectSummaryVO> {
  const response = await request.get(`/projects/${projectId}/summary`)
  return mapProjectSummary(unwrapResponse<BackendProjectSummary>(response))
}

export async function getProjectTimeline(projectId: number): Promise<TimelineDetailVO> {
  const response = await request.get(`/projects/${projectId}/timeline`)
  return mapTimeline(unwrapResponse<BackendTimelineDetail>(response))
}

function rejectUntestedM3Action(): Promise<never> {
  return Promise.reject(new Error('M3 时间轴动作接口尚未完成前端接入，本次只接入 M1/M2 查询接口。'))
}

export async function confirmTimelineNode(
  projectId: number,
  nodeKey: string,
  remark?: string
): Promise<TimelineActionResultVO> {
  const payload: TimelineActionPayload = remark?.trim() ? { remark: remark.trim() } : {}
  const response = await request.post(`/projects/${projectId}/timeline/${nodeKey}/confirm`, payload)
  return unwrapResponse<TimelineActionResultVO>(response)
}

export async function advanceTimelineNode(
  projectId: number,
  nodeKey: string,
  remark?: string
): Promise<TimelineActionResultVO> {
  const payload: TimelineActionPayload = remark?.trim() ? { remark: remark.trim() } : {}
  const response = await request.post(`/projects/${projectId}/timeline/${nodeKey}/advance`, payload)
  return unwrapResponse<TimelineActionResultVO>(response)
}

export async function returnTimelineNode(
  projectId: number,
  nodeKey: string,
  reason: string,
  returnToPrevious = true
): Promise<TimelineActionResultVO> {
  const cleanReason = reason.trim()
  if (!cleanReason) throw new Error('请填写退回原因')
  const response = await request.post(`/projects/${projectId}/timeline/${nodeKey}/return`, {
    reason: cleanReason,
    returnToPrevious
  })
  return unwrapResponse<TimelineActionResultVO>(response)
}

export function freezeProject(_projectId: number) {
  return rejectUntestedM3Action()
}

export function publishProject(_projectId: number) {
  return rejectUntestedM3Action()
}

export function archiveProject(_projectId: number) {
  return rejectUntestedM3Action()
}

export function abandonProject(_projectId: number, _reason: string) {
  return rejectUntestedM3Action()
}
