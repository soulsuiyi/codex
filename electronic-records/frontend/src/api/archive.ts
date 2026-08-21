import { get, post } from '@/utils/request'
import type { ArchiveVO, PageResult } from '@/types/api'

export function archiveCase(caseNo: string): Promise<ArchiveVO> {
  return post<ArchiveVO>('/archives/archive', { caseNo })
}

export function listArchives(page = 1, size = 10, caseNo?: string): Promise<PageResult<ArchiveVO>> {
  return get<PageResult<ArchiveVO>>('/archives', { page, size, caseNo })
}

export function getArchive(id: number): Promise<ArchiveVO> {
  return get<ArchiveVO>(`/archives/${id}`)
}
