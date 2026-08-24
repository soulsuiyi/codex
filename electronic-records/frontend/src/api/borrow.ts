import { get, post, request } from '@/utils/request'
import type {
  BorrowApplyRequest,
  BorrowApplyVO,
  BorrowApprovalRequest,
  BorrowDetailVO,
  BorrowDownloadRequest,
  BorrowTokenVO,
  PageResult,
} from '@/types/api'

export function applyBorrow(data: BorrowApplyRequest): Promise<BorrowApplyVO> {
  return post<BorrowApplyVO>('/borrows/apply', data)
}

export function myBorrows(page = 1, size = 10): Promise<PageResult<BorrowApplyVO>> {
  return get<PageResult<BorrowApplyVO>>('/borrows/my', { page, size })
}

export function pendingBorrows(page = 1, size = 10): Promise<PageResult<BorrowApplyVO>> {
  return get<PageResult<BorrowApplyVO>>('/borrows/pending', { page, size })
}

export function borrowDetail(id: number): Promise<BorrowDetailVO> {
  return get<BorrowDetailVO>(`/borrows/${id}`)
}

export function approveBorrow(id: number, data: BorrowApprovalRequest): Promise<BorrowApplyVO> {
  return post<BorrowApplyVO>(`/borrows/${id}/approve`, data)
}

export function borrowToken(id: number): Promise<BorrowTokenVO> {
  return get<BorrowTokenVO>(`/borrows/${id}/token`)
}

export function borrowDownload(id: number, data: BorrowDownloadRequest): Promise<Blob | string> {
  return request<Blob | string>({
    url: `/borrows/${id}/download`,
    method: 'post',
    data,
    responseType: 'blob',
  })
}

export function returnBorrow(id: number): Promise<BorrowApplyVO> {
  return post<BorrowApplyVO>(`/borrows/${id}/return`)
}
