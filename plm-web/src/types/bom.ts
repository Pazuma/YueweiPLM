import type { CommonStatus } from './common'

export interface BomMetric {
  label: string
  value: string
  hint: string
  targetPath: string
}

export interface BomVersionRecord {
  bomId: number
  productId: number
  productCode: string
  productName: string
  bomCode: string
  bomType: 'EBOM' | 'MBOM' | 'PACK'
  bomVersion: string
  owner: string
  status: CommonStatus
  estimatedCost: number
  costDelta: number
  cumulativeCost: number
  completionRate: number
  supplierRiskNote: string
  updatedAt: string
  targetPath: string
}

export interface BomRiskItem {
  title: string
  level: 'high' | 'medium' | 'low'
  owner: string
  action: string
  targetPath: string
}

export interface BomTrendPoint {
  versionLabel: string
  estimatedCost: number
  changeLabel: string
  targetPath: string
}

export interface BomCenterSnapshot {
  metrics: BomMetric[]
  versions: BomVersionRecord[]
  risks: BomRiskItem[]
  trend: BomTrendPoint[]
}

export type BomScope = 'test' | 'formal'
export type BomLifecycleStatus = 'draft' | 'reviewing' | 'released' | 'archived' | 'confirmed'

export interface BomItem {
  productBomItemId?: number
  inventoryId?: number | null
  itemCode?: string | null
  itemName: string
  specification?: string | null
  lineNo: number
  quantity: number
  unit: string
  lossRate?: number | null
  unitCost?: number | null
  substituteFlag?: number | null
  remark?: string | null
}

export interface BomCostSnapshot {
  materialCost: number
  lossCost: number
  processCost: number
  packageCost: number
  laborCost: number
  toolingCost: number
  otherCost: number
  totalCost: number
  currencyCode: string
  calculatedAt: string
}

export interface BomRoute {
  productBomRouteId?: number
  productBomId?: number
  processId: number
  routeCode: string
  routeName: string
  status?: 'active' | 'inactive'
  colors: string[]
  items: BomItem[]
  costSnapshot?: BomCostSnapshot | null
  processCost?: number
  packageCost?: number
  laborCost?: number
  toolingCost?: number
  otherCost?: number
}

export interface BomWorkbench {
  productBomId: number
  productId: number
  bomCode: string
  bomName: string
  bomScope: BomScope
  versionNo: string
  status: BomLifecycleStatus
  testTotalCost?: number | null
  calculatedAt?: string | null
  testItems: BomItem[]
  routes: BomRoute[]
}

export interface BomLedgerRow {
  productBomId: number
  productId: number
  bomCode: string
  productCode: string
  productName: string
  model?: string | null
  versionNo: string
  routeCount: number
  skuCount: number
  status: BomLifecycleStatus
  sourceType: 'manual' | 'import' | 'inherited'
  updatedAt: string
}

export interface BomSkuRow {
  productId: number
  skuCode: string
  productName: string
  phoneModel: string
  color: string
  status: string
  productBomRouteId?: number | null
  routeCode?: string | null
}

export interface BomSummary {
  testTotalCost?: number | null
  testCalculatedAt?: string | null
  testVersionNo?: string | null
  formalVersions: BomWorkbench[]
}

export interface BomImportError {
  rowNo: number
  field: string
  originalValue: string
  reason: string
}

export interface BomImportPreview {
  importToken: string
  status: 'ready' | 'invalid'
  totalRows: number
  validRows: number
  errorRows: number
  rows: Array<Record<string, unknown>>
  errors: BomImportError[]
}
