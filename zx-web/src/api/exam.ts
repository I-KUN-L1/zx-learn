import { request } from './request'
import type {
  AnswerOverviewVO,
  PageDTO,
  QuestionResultVO,
  QuestionVO,
  SubmitResultVO,
  WrongQuestionVO,
} from '@/types/api'

/**
 * 考试 / 题库服务（zx-exam）。
 * 走网关 8080，路径前缀 /questions/** 与 /question-results/**。
 *
 * 师生联动约定：
 * - 教师端：题库 CRUD + 发布/撤回 + 答题情况总览；
 * - 学员端：只读已发布题目（在线答题）、错题本、我的答题统计。
 */

/* ==================== 学员端 ==================== */

/** 题库练习分页（仅返回已发布题目，支持关键词 / 类型 / 课程筛选） */
export function practicePage(params: {
  pageNo?: number
  pageSize?: number
  keyword?: string
  type?: number
  courseId?: number
}) {
  return request.get<PageDTO<QuestionVO>>('/questions/page', params as Record<string, unknown>)
}

/** 题库练习列表（全量已发布题目，用于整卷练习） */
export function practiceList() {
  return request.get<QuestionVO[]>('/questions/list')
}

/**
 * 批量交卷。
 * 注意：只提交作答，正误与得分由**后端服务端判分**计算，前端不传 correct/score；
 * 返回值是逐题判分结果（对错 / 正确答案 / 解析），用于即时反馈。
 */
export function submitAnswers(results: { questionId: number; userAnswer: string }[]) {
  return request.post<SubmitResultVO[]>('/question-results', results)
}

/** 单题提交（错题重做 / 单题闯关） */
export function submitOneAnswer(result: { questionId: number; userAnswer: string }) {
  return request.post<SubmitResultVO>('/question-results/single', result)
}

/** 我的答题记录 */
export function myAnswerRecords() {
  return request.get<QuestionResultVO[]>('/question-results/mine')
}

/** 我的答题统计 {count, correct, accuracy} */
export function myAnswerStats() {
  return request.get<{ count: number; correct: number; accuracy: number }>('/question-results/mine/stats')
}

/** 我的错题本（题干 + 我的作答 + 正确答案 + 解析） */
export function myWrongBook() {
  return request.get<WrongQuestionVO[]>('/question-results/mine/wrong')
}

/* ==================== 教师端 ==================== */

export interface TeacherQuestionForm {
  id?: number
  name: string
  /** 1 单选 2 多选 3 判断 */
  type: number
  options?: string[]
  answer?: string
  difficulty?: number
  score?: number
  content?: string
  analysis?: string
  /** 关联课程（师生联动锚点，学员按课程练习） */
  courseId?: number
  /** 0 草稿 1 已发布 */
  status?: number
}

/** 题库分页（教师端，含草稿） */
export function teacherQuestionPage(params: {
  pageNo?: number
  pageSize?: number
  keyword?: string
  type?: number
  courseId?: number
}) {
  return request.get<PageDTO<QuestionVO>>('/questions/teacher/page', params as Record<string, unknown>)
}

/** 题库全量列表（教师端，含草稿） */
export function allQuestions() {
  return request.get<QuestionVO[]>('/questions/all')
}

/** 新增题目（教师） */
export function addQuestion(data: TeacherQuestionForm) {
  return request.post<number>('/questions', data)
}

/** 更新题目（教师） */
export function updateQuestion(id: number, data: TeacherQuestionForm) {
  return request.put<null>(`/questions/${id}`, data)
}

/** 删除题目（教师） */
export function deleteQuestion(id: number) {
  return request.delete<null>(`/questions/${id}`)
}

/** 发布 / 撤回题目（控制学员端可见性） */
export function publishQuestion(id: number, published: boolean) {
  return request.put<null>(`/questions/${id}/publish?published=${published}`)
}

/** 教师端答题情况总览：整体正确率 + 每题正确率 + 每位学员正确率 */
export function answerOverview() {
  return request.get<AnswerOverviewVO>('/question-results/teacher/overview')
}

/** 某题的作答明细（下钻） */
export function questionAnswerDetails(questionId: number) {
  return request.get<QuestionResultVO[]>(`/question-results/teacher/question/${questionId}`)
}
