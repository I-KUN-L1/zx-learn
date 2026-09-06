import { request } from './request'
import type { PageDTO, PageQuery, QuestionResultVO, QuestionVO, UserVO } from '@/types/api'

/**
 * 用户与考试服务（zx-user / zx-exam）
 */

/* ---------- 用户（管理端） ---------- */
export function pageUsers(params: PageQuery) {
  return request.get<PageDTO<UserVO>>('/users/page', params as Record<string, unknown>)
}

/** 重置用户密码为默认密码（后端 @RequireRole(STAFF)） */
export function resetUserPassword(userId: number) {
  return request.put<null>(`/users/${userId}/password/default`)
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
