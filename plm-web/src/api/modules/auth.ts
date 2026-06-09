import { mockAccounts, rolePermissions } from '@/mock/data'
import type { UserProfile } from '@/types/common'
import { mockResolve } from '../request'

export async function loginByPassword(payload: { username: string; password: string }) {
  return mockResolve(() => {
    const account = mockAccounts.find(
      (item) => item.username === payload.username && item.password === payload.password
    )

    if (!account) {
      throw new Error('账号或密码错误，请使用演示账号登录。')
    }

    return {
      token: `mock-token-${account.username}`,
      profile: account.profile,
      permissions: rolePermissions[account.profile.roleName] || []
    }
  })
}

export async function getProfileByToken(token: string): Promise<{
  profile: UserProfile
  permissions: string[]
}> {
  return mockResolve(() => {
    const username = token.replace('mock-token-', '')
    const account = mockAccounts.find((item) => item.username === username) || mockAccounts[0]
    return {
      profile: account.profile,
      permissions: rolePermissions[account.profile.roleName] || []
    }
  })
}
