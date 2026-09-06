import type { R } from '@/types/api'

/** 包装为成功响应 */
export function R_OK<T>(data: T): R<T> {
  return { code: 200, message: 'OK', data }
}

/** 包装为失败响应 */
export function R_ERR(code: number, message: string): R<null> {
  return { code, message, data: null }
}
