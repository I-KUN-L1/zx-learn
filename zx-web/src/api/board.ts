import { request } from './request'
import type { BoardReplyVO, BoardVO, Id, PageDTO, PageQuery } from '@/types/api'

/**
 * 课程讨论服务（zx-learning）
 * 统一走网关：/boards/**、/replies/**
 *
 * 参与讨论会自动累积积分（发帖 +5 / 回复 +2），由服务端在落库后发放，
 * 前端不参与积分计算。
 */

export interface BoardPageParams extends PageQuery {
  courseId?: Id
}

/** 课程讨论区话题分页（置顶优先，其余按时间倒序） */
export function pageBoards(params: BoardPageParams) {
  return request.get<PageDTO<BoardVO>>('/boards/page', params as Record<string, unknown>)
}

/** 话题详情（含全部回复） */
export function boardDetail(id: Id) {
  return request.get<BoardVO>(`/boards/${id}`)
}

/** 发布话题（学员） */
export function createBoard(data: { courseId: Id; title: string; content?: string }) {
  return request.post<Id>('/boards', data)
}

/** 删除话题（作者本人或管理员） */
export function deleteBoard(id: Id) {
  return request.delete<null>(`/boards/${id}`)
}

/** 回复话题（学员） */
export function createReply(data: { boardId: Id; content: string; parentId?: number }) {
  return request.post<Id>('/replies', data)
}

/** 删除回复（作者本人或管理员） */
export function deleteReply(id: Id) {
  return request.delete<null>(`/replies/${id}`)
}

export type { BoardReplyVO, BoardVO }
