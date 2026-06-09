import type { ApprovalStep, CommonStatus, TimelineItem } from './common'

export interface ProductSummary {
  productId: number
  parentProductId?: number | null
  productCode: string
  productName: string
  productType: 'product_line' | 'model_variant'
  seriesName: string
  model: string
  color: string
  material: string
  ownerUserName: string
  versionNo: string
  status: CommonStatus
  currentStage: string
  customerName: string
  frozenFlag: boolean
  releasedAt: string | null
  completionRate: number
  estimatedCost: number
  estimatedCostCurrency: string
  testItemCount: number
  activeBomVersion: string
}

export interface ProductBomItem {
  inventoryCode: string
  inventoryName: string
  quantity: number
  stockUom: string
  unitCost: number
  supplierName: string
}

export interface ProductCostBreakdownItem {
  category: string
  amount: number
  ratio: number
  note?: string
}

export interface ProductTestItem {
  name: string
  method: string
  owner: string
  frequency: string
  result: string
  dueDate: string
}

export interface ProductAttachment {
  attachmentId: number
  fileName: string
  fileCategory: string
  versionNo: string
  uploadedBy: string
  uploadedAt: string
  status: CommonStatus
}

export interface ProductQualityRecord {
  testItem: string
  result: string
  owner: string
  dueDate: string
}

export interface ProductOperateLog {
  time: string
  operator: string
  action: string
  level: 'normal' | 'danger'
}

export interface ProductVersionRecord {
  versionNo: string
  releasedAt: string | null
  releasedBy: string
  changeSummary: string
  status: CommonStatus
  bomVersion: string
  estimatedCost: number
  actualCost?: number | null
}

export interface ProductBasicInfo {
  productCode: string
  productName: string
  seriesName: string
  productType: string
  ownerUserName: string
  status: CommonStatus
  versionNo: string
  material: string
  packageType: string
  surfaceProcess: string
  coreProcess: string
  composition: string
  customerName: string
  currentStage: string
  expectedReleaseDate: string | null
  model: string
  color: string
  estimatedCost: number
  estimatedCostCurrency: string
  productTypeLabel: string
}

export interface ProductDetail {
  productId: number
  basicInfo: ProductBasicInfo
  statusTimeline: TimelineItem[]
  approvalTimeline: ApprovalStep[]
  bomItems: ProductBomItem[]
  attachments: ProductAttachment[]
  qualityRecords: ProductQualityRecord[]
  operationLogs: ProductOperateLog[]
  versionHistory: ProductVersionRecord[]
  costBreakdown: ProductCostBreakdownItem[]
  testItems: ProductTestItem[]
}

export interface ProductFormPayload {
  productCode: string
  productName: string
  seriesName: string
  productType: 'product_line' | 'model_variant'
  ownerUserName: string
  versionNo: string
  material: string
  packageType: string
  surfaceProcess: string
  coreProcess: string
  composition: string
  customerName: string
  currentStage: string
  expectedReleaseDate: string
  model: string
  color: string
  estimatedCost: number
  estimatedCostCurrency: string
  costBreakdown: ProductCostBreakdownItem[]
  testItems: ProductTestItem[]
}
