import { processCenterData } from '@/mock/process'
import type { ProcessCenterSnapshot } from '@/types/process'
import { mockResolve } from '../request'

function clone<T>(value: T): T {
  return structuredClone(value)
}

export function getProcessCenterSnapshot(): Promise<ProcessCenterSnapshot> {
  return mockResolve(() => clone(processCenterData))
}
