import { del, get, post, request } from '@/utils/request'
import type { FileVO, VersionListVO } from '@/types/api'

export function uploadFile(caseNo: string, file: File): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('caseNo', caseNo)
  return post<FileVO>('/files/upload', form)
}

export function uploadChunk(params: {
  file: Blob
  caseNo: string
  identifier: string
  chunkIndex: number
  totalChunks: number
}): Promise<void> {
  const form = new FormData()
  form.append('file', params.file)
  form.append('caseNo', params.caseNo)
  form.append('identifier', params.identifier)
  form.append('chunkIndex', String(params.chunkIndex))
  form.append('totalChunks', String(params.totalChunks))
  return post<void>('/files/upload/chunk', form)
}

export function mergeChunks(params: {
  caseNo: string
  identifier: string
  fileName: string
  totalChunks: number
}): Promise<FileVO> {
  const form = new FormData()
  form.append('caseNo', params.caseNo)
  form.append('identifier', params.identifier)
  form.append('fileName', params.fileName)
  form.append('totalChunks', String(params.totalChunks))
  return post<FileVO>('/files/upload/chunk/merge', form)
}

export function listCaseFiles(caseNo: string): Promise<FileVO[]> {
  return get<FileVO[]>(`/cases/${caseNo}/files`)
}

export function fileVersions(id: number): Promise<VersionListVO> {
  return get<VersionListVO>(`/files/${id}/versions`)
}

export function deleteFile(id: number): Promise<void> {
  return del<void>(`/files/${id}`)
}

/** 下载：图片/PDF 返回带水印流，其他类型返回 Pre-signed URL（JSON） */
export function downloadFile(id: number): Promise<Blob | string> {
  return request<Blob | string>({
    url: `/files/${id}/download`,
    method: 'get',
    responseType: 'blob',
  })
}

/** 预览：PDF/图片返回带水印流 */
export function previewFile(id: number): Promise<Blob> {
  return request<Blob>({
    url: `/files/${id}/preview`,
    method: 'get',
    responseType: 'blob',
  })
}
