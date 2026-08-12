import type {
  BomCenterRow,
  FileSection,
  FoundationProductRef,
  InventoryListRow,
  InventoryTreeNode,
  ProductDetailPresentation,
  ReportCenterSnapshot,
  TestCategoryItem,
  TestRecordItem
} from '@/types/foundation'

import request, { unwrapResponse } from '../request'
import { notConnected } from '../notConnected'

export function getFoundationProducts(): Promise<FoundationProductRef[]> {
  return notConnected('基础产品引用')
}

export function getFileSections(): Promise<FileSection[]> {
  return notConnected('文件中心分组')
}

export function getTestCenterSnapshot(): Promise<{
  categories: TestCategoryItem[]
  records: TestRecordItem[]
}> {
  return notConnected('测试中心')
}

export async function getInventoryCenterSnapshot(): Promise<{
  tree: InventoryTreeNode[]
  items: InventoryListRow[]
}> {
  return unwrapResponse(await request.get('/inventories/center-snapshot'))
}

export function getBomCenterRows(): Promise<BomCenterRow[]> {
  return notConnected('BOM 中心列表')
}

export function getProductPresentation(_productId: number): Promise<ProductDetailPresentation> {
  return notConnected('产品详情展示')
}

export function getReportCenterSnapshot(): Promise<ReportCenterSnapshot> {
  return notConnected('报表中心')
}
