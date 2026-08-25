import { defineStore } from 'pinia'
import { getProfile, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { LoginRequest } from '@/types/api'
import { clearToken, getToken, setToken } from '@/utils/request'

interface UserState {
  token: string | null
  username: string | null
  realName: string | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getToken(),
    username: null,
    realName: null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    async login(payload: LoginRequest): Promise<void> {
      const res = await loginApi(payload)
      this.token = res.tokenValue
      setToken(res.tokenValue)
      await this.fetchProfile()
    },
    async fetchProfile(): Promise<void> {
      try {
        const profile = await getProfile()
        this.username = profile.username
        this.realName = profile.realName || profile.username
      } catch {
        // 资料拉取失败不阻断页面
      }
    },
    async logout(): Promise<void> {
      try {
        await logoutApi()
      } finally {
        this.token = null
        this.username = null
        this.realName = null
        clearToken()
      }
    },
  },
})
