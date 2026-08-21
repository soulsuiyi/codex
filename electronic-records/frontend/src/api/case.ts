import { get, post, put } from '@/utils/request'
import type {
  CaseCreateRequest,
  CaseUpdateRequest,
  CaseVO,
  CategoryCreateRequest,
  CategoryVO,
  PageResult,
} from '@/types/api'

export function listCases(page = 1, size = 10, keyword?: string): Promise<PageResult<CaseVO>> {
  return get<PageResult<CaseVO>>('/cases', { page, size, keyword })
}

export function getCase(id: number): Promise<CaseVO> {
  return get<CaseVO>(`/cases/${id}`)
}

export function createCase(data: CaseCreateRequest): Promise<CaseVO> {
  return post<CaseVO>('/cases', data)
}

export function updateCase(id: number, data: CaseUpdateRequest): Promise<CaseVO> {
  return put<CaseVO>(`/cases/${id}`, data)
}

export function categoryTree(): Promise<CategoryVO[]> {
  return get<CategoryVO[]>('/cases/categories')
}

export function createCategory(data: CategoryCreateRequest): Promise<CategoryVO> {
  return post<CategoryVO>('/cases/categories', data)
}
