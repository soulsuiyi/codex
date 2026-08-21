import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import type { LoginRequest } from '@/types/api'
import { clearToken, getToken, setToken } from '@/utils/request'

interface UserState {
  token: string | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getToken(),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    async login(payload: LoginRequest): Promise<void> {
      const res = await loginApi(payload)
      this.token = res.tokenValue
      setToken(res.tokenValue)
    },
    async logout(): Promise<void> {
      try {
        await logoutApi()
      } finally {
        this.token = null
        clearToken()
      }
    },
  },
})
