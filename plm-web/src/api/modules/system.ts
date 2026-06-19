import { systemPermissionGroups, systemRoles, systemUsers } from '@/mock/users'
import { mockResolve } from '../request'

export function getSystemUsers() {
  return mockResolve(() => structuredClone(systemUsers))
}

export function getSystemRoles() {
  return mockResolve(() => structuredClone(systemRoles))
}

export function getSystemPermissionGroups() {
  return mockResolve(() => structuredClone(systemPermissionGroups))
}
