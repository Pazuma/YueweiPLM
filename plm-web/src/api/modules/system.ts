import type { SystemPermissionGroup, SystemRoleItem, SystemUserItem } from '@/types/common'

import { notConnected } from '../notConnected'

export function getSystemUsers(): Promise<SystemUserItem[]> {
  return notConnected('系统用户')
}

export function getSystemRoles(): Promise<SystemRoleItem[]> {
  return notConnected('系统角色')
}

export function getSystemPermissionGroups(): Promise<SystemPermissionGroup[]> {
  return notConnected('系统权限组')
}
