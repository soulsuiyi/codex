import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/api'

const TOKEN_KEY = 'archive_token'
const TOKEN_NAME = 'Authorization'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

function redirectToLogin(): void {
  clearToken()
  const current = window.location.pathname + window.location.search
  if (!current.startsWith('/login')) {
    window.location.href = `/login?redirect=${encodeURIComponent(current)}`
  }
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set(TOKEN_NAME, token)
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as Result
    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        return body.data as unknown as typeof response
      }
      if (body.code === 401) {
        redirectToLogin()
      } else {
        ElMessage.error(body.msg || '请求失败')
      }
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.msg
    if (status === 401) {
      redirectToLogin()
    } else if (status === 403) {
      ElMessage.error(msg || '无权限访问')
    } else {
      ElMessage.error(msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return instance.request(config) as Promise<T>
}

export function get<T>(url: string, params?: object): Promise<T> {
  return request<T>({ url, method: 'get', params })
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'post', data })
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'put', data })
}

export function del<T>(url: string): Promise<T> {
  return request<T>({ url, method: 'delete' })
}
