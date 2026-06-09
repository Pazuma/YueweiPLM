import type { CommonStatus } from './common'

export interface FoundationProductRef {
  productId: number
  productCode: string
  productName: string
  seriesName: string
  model: string
  color: string
  customerName: string
  productType: 'product_line' | 'model_variant'
  parentProductId?: number | null
  versionNo: string
  status: CommonStatus
  currentStage: string
  estimatedCost: number
  actualCost: number
  lastActiveAt: string
  createdAt: string
}

export interface CostLine {
  label: string
  amount: number
  note: string
}

export interface ProductCostPanel {
  showEstimated: boolean
  estimatedTotal?: number
  estimatedLines?: CostLine[]
  actualTotal: number
  actualLines: CostLine[]
}

export interface BomCompareRow {
  versionNo: string
  statusLabel: string
  materialCost: number
  processCost: number
  totalCost: number
  delta: number
}

export interface ProductBomItemRow {
  inventoryCode: string
  inventoryName: string
  quantity: number
  stockUom: string
  supplierName: string
  unitCost: number
}

export interface ProductMaterialCategoryItem {
  itemCode: string
  itemName: string
  spec: string
  supplierName: string
  note: string
}

export interface ProductMaterialCategory {
  categoryKey: string
  categoryName: string
  items: ProductMaterialCategoryItem[]
}

export interface ProductSupplierSummary {
  supplierName: string
  role: string
  statusLabel: string
  note: string
}

export interface ProductDocumentSummary {
  fileName: string
  category: string
  versionNo: string
  updatedAt: string
}

export interface ProductQualitySummary {
  testItem: string
  resultLabel: string
  owner: string
  testedAt: string
  note: string
}

export interface ProductDetailPresentation {
  productId: number
  title: string
  flowLabel: string
  currentNode: string
  nextNode: string
  summary: string
  costPanel: ProductCostPanel
  bomCompareRows: BomCompareRow[]
  bomItems: ProductBomItemRow[]
  materialCategories: ProductMaterialCategory[]
  suppliers: ProductSupplierSummary[]
  documents: ProductDocumentSummary[]
  qualityRecords: ProductQualitySummary[]
}

export interface FileRecord {
  fileId: string
  fileName: string
  category: string
  owner: string
  uploadedAt: string
  versionNo: string
  productId: number
}

export interface FileProjectGroup {
  groupId: string
  projectName: string
  productCode: string
  productId: number
  owner: string
  updatedAt: string
  files: FileRecord[]
}

export interface FileSection {
  key: 'product_files' | 'variant_files'
  title: string
  description: string
  groups: FileProjectGroup[]
}

export interface TestCategoryItem {
  categoryId: string
  categoryName: string
  method: string
  defaultFrequency: string
  owner: string
}

export interface TestRecordItem {
  recordId: string
  productId: number
  productName: string
  testCategory: string
  result: '通过' | '不通过' | '复测中'
  owner: string
  testedAt: string
  note: string
}

export interface InventoryTreeNode {
  nodeId: string
  label: string
  children?: InventoryTreeNode[]
}

export interface InventoryListRow {
  itemId: string
  nodeId: string
  code: string
  name: string
  spec: string
  stock: string
  status: 'available' | 'reserved' | 'consumed' | 'in_use'
  supplierName: string
  updatedAt: string
}

export interface BomCenterRow {
  productId: number
  productCode: string
  productName: string
  bomType: 'EBOM' | 'MBOM' | 'PACK'
  currentVersion: string
  status: CommonStatus
  materialCost: number
  processCost: number
  totalCost: number
  supplierNote: string
  updatedAt: string
}

export interface ReportCenterCard {
  key: string
  title: string
  icon: string
  questionLines: string[]
  targetPath: string
}

export interface ReportAlertItem {
  title: string
  subtitle: string
  owner: string
  level: 'high' | 'medium' | 'low'
  targetPath: string
}

export interface ReportDistributionItem {
  label: string
  value: number
  hint: string
}

export interface ReportDetailSection {
  key: string
  title: string
  summary: string
  metrics: Array<{
    label: string
    value: string
    hint: string
  }>
  alerts: ReportAlertItem[]
  distribution: ReportDistributionItem[]
}

export interface ReportCenterSnapshot {
  rangeLabel: string
  cards: ReportCenterCard[]
  details: ReportDetailSection[]
}
