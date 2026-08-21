import { get, post } from '@/utils/request'
import type { LoginRequest, LoginResponse } from '@/types/api'

export function login(data: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', data)
}

export function logout(): Promise<void> {
  return post<void>('/auth/logout')
}

export function me(): Promise<number> {
  return get<number>('/auth/me')
}
