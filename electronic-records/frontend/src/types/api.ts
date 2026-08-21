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
  fileIds: number[]
  reason?: string
  needDownload?: boolean
  expireTime?: string
  status: BorrowStatus
  createdAt?: string
  updatedAt?: string
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
