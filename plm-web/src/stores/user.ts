import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { getProfileByToken, loginByPassword, logout as logoutApi } from '@/api/modules/auth'
import type { UserProfile } from '@/types/common'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('plm_token') || '')
  const profile = ref<UserProfile | null>(null)
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => Boolean(token.value))

  function setSession(payload: { token: string; profile: UserProfile; permissions: string[] }) {
    token.value = payload.token
    profile.value = payload.profile
    permissions.value = payload.permissions
    localStorage.setItem('plm_token', payload.token)
  }

  async function login(payload: { username: string; password: string }) {
    const session = await loginByPassword(payload)
    setSession(session)
    return session
  }

  async function restore() {
    if (!token.value) {
      return
    }

    try {
      const session = await getProfileByToken(token.value)
      profile.value = session.profile
      permissions.value = session.permissions
    } catch (error) {
      token.value = ''
      profile.value = null
      permissions.value = []
      localStorage.removeItem('plm_token')
      throw error
    }
  }

  async function logout() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      token.value = ''
      profile.value = null
      permissions.value = []
      localStorage.removeItem('plm_token')
    }
  }

  function hasPermission(permission?: string) {
    if (!permission) {
      return true
    }
    return permissions.value.includes('*') || permissions.value.includes(permission)
  }

  return {
    token,
    profile,
    permissions,
    isLoggedIn,
    login,
    restore,
    logout,
    hasPermission
  }
})
