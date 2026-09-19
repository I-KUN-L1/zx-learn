import { request } from './request'
import type { Id, PageDTO, PageQuery, QuestionResultVO, QuestionVO, UserVO } from '@/types/api'

/**
 * 用户与考试服务（zx-user / zx-exam）
 */

/* ---------- 用户（管理端） ---------- */
export function pageUsers(params: PageQuery) {
  return request.get<PageDTO<UserVO>>('/users/page', params as Record<string, unknown>)
}

/** 重置用户密码为默认密码（后端 @RequireRole(STAFF)） */
export function resetUserPassword(userId: Id) {
  return request.put<null>(`/users/${userId}/password/default`)
}

/**
 * 启用 / 禁用账号（后端 @RequireRole(STAFF)）。
 * status：0-禁用 1-启用。后端强校验"不能禁用当前登录账号与最后一名启用中的管理员"。
 * 被禁用的账号在登录时会被拦截，前端提示"请联系管理员"。
 */
export function updateUserStatus(userId: Id, status: 0 | 1) {
  return request.put<null>(`/users/${userId}/status/${status}`)
}

/**
 * 删除用户（后端 @RequireRole(STAFF)）。
 * 后端禁止删除当前登录账号与系统最后一名管理员。
 */
export function deleteUser(userId: Id) {
  return request.delete<null>(`/users/${userId}`)
}

/* ---------- 考试练习（扩展模块） ---------- */

/** 题目列表 */
export function questionList() {
  return request.get<QuestionVO[]>('/questions/list')
}

/** 提交答题结果 */
export function submitAnswers(results: { questionId: number; answer: string; correct: boolean }[]) {
  return request.post<null>('/question-results', results)
}

/** 答题统计 */
export function questionStats(userId: number | string) {
  return request.get<{ total: number; correct: number; accuracy: number }>(`/question-results/users/${userId}/stats`)
}

export type { QuestionResultVO }
