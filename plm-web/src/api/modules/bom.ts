import { bomCenterData } from '@/mock/data'
import type { BomCenterSnapshot } from '@/types/bom'
import request, { mockResolve, unwrapResponse } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

/* ========== BOM 中心 ========== */

export function getBomCenterSnapshot(): Promise<BomCenterSnapshot> {
  return mockResolve(() => clone(bomCenterData))
}

/* ========== 项目级 BOM（文档 7.4） ========== */

export type ProductBomStatus = 'draft' | 'frozen'

export interface ProductBomItemVO {
  productBomItemId: number
  productBomId: number
  inventoryId?: number | null
  itemCode?: string | null
  itemName: string
  specification?: string | null
  lineNo: number
  quantity: number
  unit: string
  lossRate?: number | null
  substituteFlag?: number | null
  remark?: string | null
  status: ProductBomStatus
}

export interface ProductBomVO {
  productBomId: number
  productId: number
  bomCode: string
  bomName: string
  bomType: string
  versionNo: string
  status: ProductBomStatus
  frozenAt?: string | null
  frozenBy?: string | null
  remark?: string | null
  items: ProductBomItemVO[]
}

export interface ProductBomSavePayload {
  bomName: string
  bomType: string
  versionNo: string
  remark?: string
}

export interface ProductBomItemSavePayload {
  inventoryId?: number | null
  itemCode?: string
  itemName: string
  specification?: string
  lineNo: number
  quantity: number
  unit: string
  lossRate?: number | null
  substituteFlag?: number | null
  remark?: string
}

/** GET /api/v1/projects/{projectId}/boms */
export async function getProjectBoms(projectId: number): Promise<ProductBomVO[]> {
  const response = await request.get(`/projects/${projectId}/boms`)
  return unwrapResponse<ProductBomVO[]>(response)
}

/** GET /api/v1/boms/{bomId} */
export async function getBomDetail(bomId: number): Promise<ProductBomVO> {
  const response = await request.get(`/boms/${bomId}`)
  return unwrapResponse<ProductBomVO>(response)
}

/** POST /api/v1/projects/{projectId}/boms */
export async function createProjectBom(projectId: number, payload: ProductBomSavePayload) {
  const response = await request.post(`/projects/${projectId}/boms`, payload)
  return unwrapResponse<ProductBomVO>(response)
}

/** PUT /api/v1/boms/{bomId} */
export async function updateBom(bomId: number, payload: ProductBomSavePayload) {
  const response = await request.put(`/boms/${bomId}`, payload)
  return unwrapResponse<ProductBomVO>(response)
}

/** POST /api/v1/boms/{bomId}/items */
export async function addBomItem(bomId: number, payload: ProductBomItemSavePayload) {
  const response = await request.post(`/boms/${bomId}/items`, payload)
  return unwrapResponse<ProductBomVO>(response)
}

/** PUT /api/v1/boms/{bomId}/items/{itemId} */
export async function updateBomItem(bomId: number, itemId: number, payload: ProductBomItemSavePayload) {
  const response = await request.put(`/boms/${bomId}/items/${itemId}`, payload)
  return unwrapResponse<ProductBomVO>(response)
}

/** DELETE /api/v1/boms/{bomId}/items/{itemId} */
export async function deleteBomItem(bomId: number, itemId: number) {
  const response = await request.delete(`/boms/${bomId}/items/${itemId}`)
  return unwrapResponse<ProductBomVO>(response)
}

/** POST /api/v1/boms/{bomId}/freeze */
export async function freezeBom(bomId: number) {
  const response = await request.post(`/boms/${bomId}/freeze`)
  return unwrapResponse<ProductBomVO>(response)
}
