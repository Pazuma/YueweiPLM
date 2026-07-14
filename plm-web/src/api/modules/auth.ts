import request, { unwrapResponse } from '../request'
import type { UserProfile } from '@/types/common'

export interface LoginRequest {
  username: string
  password: string
}

interface BackendCurrentUser {
  userId: number
  username: string
  displayName: string
  allPermissions: boolean
}

interface BackendLoginResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  user: BackendCurrentUser
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  profile: UserProfile
  permissions: string[]
  allPermissions: boolean
}

export interface ProfileResponse {
  profile: UserProfile
  permissions: string[]
  allPermissions: boolean
}

function toProfile(user: BackendCurrentUser): UserProfile {
  return {
    userId: Number(user.userId),
    userName: user.displayName || user.username,
    roleName: user.allPermissions ? '全部权限' : '工程',
    department: '工程部'
  }
}

function toPermissions(user: BackendCurrentUser): string[] {
  return user.allPermissions ? ['*'] : []
}

function toSession(data: BackendLoginResponse): LoginResponse {
  return {
    token: data.token,
    tokenType: data.tokenType,
    expiresInSeconds: data.expiresInSeconds,
    profile: toProfile(data.user),
    permissions: toPermissions(data.user),
    allPermissions: data.user.allPermissions
  }
}

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const response = await request.post('/auth/login', payload)
  return toSession(unwrapResponse<BackendLoginResponse>(response))
}

export const loginByPassword = login

export async function getProfile(): Promise<ProfileResponse> {
  const response = await request.get('/auth/profile')
  const user = unwrapResponse<BackendCurrentUser>(response)
  return {
    profile: toProfile(user),
    permissions: toPermissions(user),
    allPermissions: user.allPermissions
  }
}

export async function getProfileByToken(_token: string): Promise<{
  profile: UserProfile
  permissions: string[]
}> {
  const session = await getProfile()
  return {
    profile: session.profile,
    permissions: session.permissions
  }
}

export async function logout(): Promise<void> {
  await request.post('/auth/logout')
}
