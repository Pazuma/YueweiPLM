import { dashboardData } from '@/mock/data'
import { mockResolve } from '../request'

export function getDashboardSnapshot() {
  return mockResolve(() => dashboardData)
}
