import { approvalTasks } from '@/mock/data'
import { mockResolve } from '../request'

export function getApprovalTasks() {
  return mockResolve(() => approvalTasks)
}
