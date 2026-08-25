import { get } from '@/utils/request'
import type { DictVO } from '@/types/api'

/**
 * 按类型查询字典选项（所有登录用户可用）。
 */
export function listDictOptions(dictType: string): Promise<DictVO[]> {
  return get<DictVO[]>(`/dicts/type/${dictType}`)
}
