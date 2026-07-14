import { processCenterData } from '@/mock/process'
import type { ProcessCenterSnapshot } from '@/types/process'
import request, { mockResolve, unwrapResponse } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

/* ========== 工艺中心 ========== */

export function getProcessCenterSnapshot(): Promise<ProcessCenterSnapshot> {
  return mockResolve(() => clone(processCenterData))
}

/* ========== 项目级工艺路线（文档 7.5） ========== */

export interface ProcessRouteVO {
  processId: number
  productId: number
  processCode: string
  processName: string
  processType: string
  versionNo: string
  status: 'draft' | 'locked'
  frozenAt?: string | null
  frozenBy?: string | null
  remark?: string | null
  operations: ProcessOperationVO[]
}

export interface ProcessOperationVO {
  processId: number
  parentProcessId: number
  sequenceNo: number
  processName: string
  processParamJson?: string | null
  standardTimeMins?: number | null
  qualityRequirement: string
  status: 'draft' | 'locked'
  remark?: string | null
}

export interface ProcessOperationSavePayload {
  sequenceNo: number
  processName: string
  processParamJson?: string
  standardTimeMins?: number | null
  qualityRequirement: string
  remark?: string
}

export interface ProcessRouteSavePayload {
  processName: string
  versionNo: string
  remark?: string
  operations: ProcessOperationSavePayload[]
}

/** GET /api/v1/projects/{projectId}/process-routes */
export async function getProjectProcessRoutes(projectId: number): Promise<ProcessRouteVO[]> {
  const response = await request.get(`/projects/${projectId}/process-routes`)
  return unwrapResponse<ProcessRouteVO[]>(response)
}

/** GET /api/v1/process-routes/{processId} */
export async function getProcessRouteDetail(processId: number): Promise<ProcessRouteVO> {
  const response = await request.get(`/process-routes/${processId}`)
  return unwrapResponse<ProcessRouteVO>(response)
}

/** POST /api/v1/projects/{projectId}/process-routes */
export async function createProcessRoute(projectId: number, payload: ProcessRouteSavePayload) {
  const response = await request.post(`/projects/${projectId}/process-routes`, payload)
  return unwrapResponse<ProcessRouteVO>(response)
}

/** PUT /api/v1/process-routes/{processId} */
export async function updateProcessRoute(processId: number, payload: ProcessRouteSavePayload) {
  const response = await request.put(`/process-routes/${processId}`, payload)
  return unwrapResponse<ProcessRouteVO>(response)
}

/** POST /api/v1/process-routes/{processId}/freeze */
export async function freezeProcessRoute(processId: number) {
  const response = await request.post(`/process-routes/${processId}/freeze`)
  return unwrapResponse<ProcessRouteVO>(response)
}
