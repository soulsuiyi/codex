import { get } from '@/utils/request'
import type { PageResult, SearchResultVO } from '@/types/api'

export function searchFiles(keyword: string, page = 1, size = 10): Promise<PageResult<SearchResultVO>> {
  return get<PageResult<SearchResultVO>>('/search/files', { keyword, page, size })
}

export function searchFileDetail(id: number, keyword: string): Promise<SearchResultVO> {
  return get<SearchResultVO>(`/search/files/${id}`, { keyword })
}
