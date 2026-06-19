import { approvalTasks, approvalTemplateOptions, approvalTemplates } from '@/mock/users'
import { mockResolve } from '../request'

export function getApprovalTasks() {
  return mockResolve(() => approvalTasks)
}

export function getApprovalTemplates() {
  return mockResolve(() => approvalTemplates)
}

export function getApprovalTemplateOptions() {
  return mockResolve(() => approvalTemplateOptions)
}
