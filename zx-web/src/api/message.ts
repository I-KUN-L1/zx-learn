import { request } from './request'
import type { InboxVO } from '@/types/api'

/**
 * 消息服务（zx-message）
 * 统一走网关：/inboxes/**
 */

/** 站内信列表 */
export function inboxList() {
  return request.get<InboxVO[]>('/inboxes')
}

/** 标记已读 */
export function readInbox(id: number) {
  return request.post<null>('/inboxes/read', { id })
}

/** 全部已读 */
export function readAllInbox() {
  return request.post<null>('/inboxes/read-all')
}
