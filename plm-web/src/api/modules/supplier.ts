import { supplierCenterData } from '@/mock/data'
import type { SupplierCenterSnapshot } from '@/types/supplier'
import { mockResolve } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

export function getSupplierCenterSnapshot(): Promise<SupplierCenterSnapshot> {
  return mockResolve(() => clone(supplierCenterData))
}
