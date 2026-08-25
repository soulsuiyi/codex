import { del, get, post, put } from '@/utils/request'
import type {
  ApiKeyCreateRequest,
  ApiKeyUpdateRequest,
  ApiKeyVO,
  AuditLogVO,
  DictRequest,
  DictVO,
  MenuRequest,
  MenuVO,
  PageResult,
  RoleRequest,
  RoleVO,
  UserCreateRequest,
  UserUpdateRequest,
  UserVO,
} from '@/types/api'

// ---------- 用户管理 ----------
export function listUsers(page = 1, size = 10, keyword?: string): Promise<PageResult<UserVO>> {
  return get<PageResult<UserVO>>('/system/users', { page, size, keyword })
}

export function getUser(id: number): Promise<UserVO> {
  return get<UserVO>(`/system/users/${id}`)
}

export function createUser(data: UserCreateRequest): Promise<UserVO> {
  return post<UserVO>('/system/users', data)
}

export function updateUser(id: number, data: UserUpdateRequest): Promise<UserVO> {
  return put<UserVO>(`/system/users/${id}`, data)
}

export function updateUserStatus(id: number, status: number): Promise<void> {
  return put<void>(`/system/users/${id}/status`, { status })
}

export function resetUserPassword(id: number, password: string): Promise<void> {
  return put<void>(`/system/users/${id}/password`, { password })
}

// ---------- 角色管理 ----------
export function listRoles(): Promise<RoleVO[]> {
  return get<RoleVO[]>('/system/roles')
}

export function createRole(data: RoleRequest): Promise<RoleVO> {
  return post<RoleVO>('/system/roles', data)
}

export function updateRole(id: number, data: RoleRequest): Promise<RoleVO> {
  return put<RoleVO>(`/system/roles/${id}`, data)
}

export function deleteRole(id: number): Promise<void> {
  return del<void>(`/system/roles/${id}`)
}

// ---------- 菜单管理 ----------
export function menuTree(): Promise<MenuVO[]> {
  return get<MenuVO[]>('/system/menus/tree')
}

export function createMenu(data: MenuRequest): Promise<MenuVO> {
  return post<MenuVO>('/system/menus', data)
}

export function updateMenu(id: number, data: MenuRequest): Promise<MenuVO> {
  return put<MenuVO>(`/system/menus/${id}`, data)
}

export function deleteMenu(id: number): Promise<void> {
  return del<void>(`/system/menus/${id}`)
}

// ---------- 数据字典 ----------
export function listDicts(page = 1, size = 10, dictType?: string, keyword?: string): Promise<PageResult<DictVO>> {
  return get<PageResult<DictVO>>('/system/dicts', { page, size, dictType, keyword })
}

export function listDictByType(dictType: string): Promise<DictVO[]> {
  return get<DictVO[]>(`/system/dicts/type/${dictType}`)
}

export function createDict(data: DictRequest): Promise<DictVO> {
  return post<DictVO>('/system/dicts', data)
}

export function updateDict(id: number, data: DictRequest): Promise<DictVO> {
  return put<DictVO>(`/system/dicts/${id}`, data)
}

export function deleteDict(id: number): Promise<void> {
  return del<void>(`/system/dicts/${id}`)
}

// ---------- 开放 API 密钥 ----------
export function listApiKeys(page = 1, size = 10, keyword?: string): Promise<PageResult<ApiKeyVO>> {
  return get<PageResult<ApiKeyVO>>('/system/api-keys', { page, size, keyword })
}

export function createApiKey(data: ApiKeyCreateRequest): Promise<ApiKeyVO> {
  return post<ApiKeyVO>('/system/api-keys', data)
}

export function updateApiKey(id: number, data: ApiKeyUpdateRequest): Promise<ApiKeyVO> {
  return put<ApiKeyVO>(`/system/api-keys/${id}`, data)
}

export function deleteApiKey(id: number): Promise<void> {
  return del<void>(`/system/api-keys/${id}`)
}

// ---------- 操作审计日志 ----------
export interface AuditLogQuery {
  page?: number
  size?: number
  module?: string
  action?: string
  username?: string
  result?: string
  startTime?: string
  endTime?: string
}

export function listAuditLogs(params: AuditLogQuery): Promise<PageResult<AuditLogVO>> {
  return get<PageResult<AuditLogVO>>('/system/audit-logs', params)
}
