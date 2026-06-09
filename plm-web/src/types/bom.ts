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
