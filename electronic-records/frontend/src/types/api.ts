/** 统一响应体 { code, msg, data, timestamp } */
export interface Result<T = unknown> {
  code: number
  msg: string
  data: T
  timestamp: number
}

/** 统一分页载体 { records, total, page, size } */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  tokenName: string
  tokenValue: string
}

export type CaseStatus = 'ACTIVE' | 'CLOSED' | 'ARCHIVED'

export interface CaseVO {
  id: number
  caseNo: string
  caseName: string
  categoryId?: number
  caseType?: string
  handlerId?: number
  status: CaseStatus
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface CaseCreateRequest {
  caseNo: string
  caseName: string
  categoryId?: number
  caseType?: string
  handlerId?: number
  remark?: string
}

export interface CaseUpdateRequest {
  caseName?: string
  categoryId?: number
  caseType?: string
  handlerId?: number
  remark?: string
}

export interface CategoryVO {
  id: number
  parentId: number
  name: string
  sortOrder?: number
  level?: number
  children?: CategoryVO[]
}

export interface CategoryCreateRequest {
  parentId?: number
  name: string
  sortOrder?: number
}

export interface CategoryUpdateRequest {
  name: string
  sortOrder?: number
}

export type FileStage = 'STAGING' | 'ARCHIVED'

export interface FileVO {
  id: number
  caseId: number
  caseNo: string
  fileName: string
  fileSize: number
  mimeType: string
  storageBucket?: string
  stage: FileStage
  version?: number
  isLatest?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface FileVersionVO {
  id: number
  fileId: number
  version: number
  fileName: string
  fileSize: number
  changeDesc?: string
  createdAt?: string
}

export interface VersionListVO {
  current: FileVO
  history: FileVersionVO[]
}

export interface ArchiveVO {
  id: number
  caseId: number
  caseNo: string
  fileCount: number
  structDocCount: number
  status: 'SUCCESS' | 'FAILED'
  remark?: string
  archivedAt?: string
}

export type BorrowStatus =
  | 'PENDING_SECRETARY'
  | 'PENDING_ADMIN'
  | 'ACTIVE'
  | 'REJECTED'
  | 'EXPIRED'
  | 'RETURNED'

export type ApprovalResult = 'APPROVED' | 'REJECTED'

export interface BorrowApplyVO {
  id: number
  caseId: number
  caseNo: string
  applicantName?: string
  fileIds: number[]
  reason?: string
  needDownload?: boolean
  expireTime?: string
  status: BorrowStatus
  createdAt?: string
  updatedAt?: string
}

export interface BorrowApprovalVO {
  id: number
  approverId: number
  approverName?: string
  approvalStep: 'SECRETARY' | 'ADMIN'
  result: ApprovalResult
  comment?: string
  createdAt?: string
}

export interface BorrowDetailVO {
  apply: BorrowApplyVO
  files: FileVO[]
  approvals: BorrowApprovalVO[]
}

export interface BorrowApplyRequest {
  caseNo: string
  fileIds: number[]
  reason?: string
  needDownload?: boolean
  expireTime?: string
}

export interface BorrowApprovalRequest {
  result: ApprovalResult
  comment?: string
}

export interface BorrowTokenVO {
  tokenValue: string
  expireTime?: string
  allowDownload?: boolean
}

export interface BorrowDownloadRequest {
  tokenValue: string
  fileId: number
}

export interface SearchResultVO {
  file: FileVO
  snippet: string
}

// ---------- 系统管理 ----------
export interface UserVO {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  deptId?: number
  status: number
  lastLoginTime?: string
  createdAt?: string
  updatedAt?: string
  roleIds?: number[]
  roleCodes?: string[]
}

export interface UserCreateRequest {
  username: string
  password: string
  realName?: string
  phone?: string
  email?: string
  deptId?: number
  status?: number
  roleIds?: number[]
}

export interface UserUpdateRequest {
  realName?: string
  phone?: string
  email?: string
  deptId?: number
  status?: number
  roleIds?: number[]
}

export interface RoleVO {
  id: number
  roleCode: string
  roleName: string
  description?: string
  createdAt?: string
}

export interface RoleRequest {
  roleCode: string
  roleName: string
  description?: string
}

export type MenuType = 'M' | 'C' | 'B'

export interface MenuVO {
  id: number
  parentId: number
  menuName: string
  menuType: MenuType
  perms?: string
  path?: string
  component?: string
  sortOrder?: number
  visible?: number
  children?: MenuVO[]
}

export interface MenuRequest {
  parentId?: number
  menuName: string
  menuType: MenuType
  perms?: string
  path?: string
  component?: string
  sortOrder?: number
  visible?: number
}

export interface DictVO {
  id: number
  dictType: string
  dictCode: string
  dictLabel: string
  dictValue: string
  sortOrder?: number
  remark?: string
}

export interface DictRequest {
  dictType: string
  dictCode: string
  dictLabel: string
  dictValue: string
  sortOrder?: number
  remark?: string
}

export interface ApiKeyVO {
  id: number
  appName: string
  apiKey: string
  apiSecret?: string
  ipWhitelist?: string
  status: number
  expireTime?: string
  createdAt?: string
}

export interface ApiKeyCreateRequest {
  appName: string
  ipWhitelist?: string
  expireTime?: string
}

export interface ApiKeyUpdateRequest {
  appName?: string
  ipWhitelist?: string
  status?: number
  expireTime?: string
}

export interface AuditLogVO {
  id: number
  userId?: number
  username?: string
  module: string
  action: string
  targetType?: string
  targetId?: string
  ip?: string
  userAgent?: string
  detail?: string
  result: 'SUCCESS' | 'FAILED'
  createdAt?: string
}

export interface StatsVO {
  userCount: number
  caseCount: number
  fileCount: number
  archivedFileCount: number
  archiveRecordCount: number
  borrowApplyCount: number
  activeBorrowCount: number
  apiKeyCount: number
}
