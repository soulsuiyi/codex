import { get, post } from '@/utils/request'
import { put } from '@/utils/request'
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  ProfileUpdateRequest,
  ProfileVO,
} from '@/types/api'

export function login(data: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', data)
}

export function logout(): Promise<void> {
  return post<void>('/auth/logout')
}

export function me(): Promise<number> {
  return get<number>('/auth/me')
}

export function getProfile(): Promise<ProfileVO> {
  return get<ProfileVO>('/auth/profile')
}

export function updateProfile(data: ProfileUpdateRequest): Promise<ProfileVO> {
  return put<ProfileVO>('/auth/profile', data)
}

export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return put<void>('/auth/password', data)
}
