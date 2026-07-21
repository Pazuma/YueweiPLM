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

export type BomScope = 'test' | 'formal' | 'candidate'
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
  lineCost?: number | null
  supplierCode?: string | null
  supplierName?: string | null
  currencyCode?: string | null
  materialSource?: 'inventory' | 'manual' | string | null
  unmatchedFlag?: number | null
  lookupMessage?: string | null
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
  colorItems?: Array<{ codeItemId: number; codeValue: string; codeName: string }>
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

export interface ProductionRouteSelection {
  processId: number
  productBomId: number
  productBomRouteId: number
  routeName?: string | null
  bomVersionNo?: string | null
  operationProcessIds: number[]
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

export interface BomImportRow {
  lineNo?: number
  routeCode?: string
  routeName?: string
  itemCode?: string
  itemName?: string
  specification?: string
  unit?: string
  quantity?: number
  supplierName?: string
  unitCost?: number | null
  lineCost?: number | null
  materialSource?: string | null
  unmatchedFlag?: number | null
  lookupMessage?: string | null
  remark?: string | null
}

export interface BomImportPreview {
  importToken: string
  status: 'ready' | 'invalid'
  totalRows: number
  validRows: number
  errorRows: number
  rows: BomImportRow[]
  errors: BomImportError[]
}
