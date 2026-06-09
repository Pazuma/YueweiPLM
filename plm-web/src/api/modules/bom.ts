import { bomCenterData } from '@/mock/data'
import type { BomCenterSnapshot } from '@/types/bom'
import { mockResolve } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

export function getBomCenterSnapshot(): Promise<BomCenterSnapshot> {
  return mockResolve(() => clone(bomCenterData))
}
