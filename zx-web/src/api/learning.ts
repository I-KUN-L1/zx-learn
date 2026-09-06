import { request } from './request'
import type { LearningLessonVO, LearningRecordVO, NoteVO, PageDTO, PageQuery } from '@/types/api'

/**
 * 学习服务（zx-learning）
 * 统一走网关：/lessons/**、/learning-records/**、/sign-ins/**、/notes/**
 */

export interface LessonPageParams extends PageQuery {
  status?: number | ''
}

/** 我的课表分页 */
export function pageLessons(params: LessonPageParams) {
  return request.get<PageDTO<LearningLessonVO>>('/lessons/page', params as Record<string, unknown>)
}

/** 上次在学课程 */
export function lessonNow() {
  return request.get<LearningLessonVO | null>('/lessons/now')
}

/** 提交学习进度（节流上报） */
export function submitProgress(lessonId: number, sectionId: number, moment: number, finished: boolean) {
  return request.post<null>('/learning-records/progress', { lessonId, sectionId, moment, finished })
}

/** 学习记录 */
export function myLearningRecords(userId: number | string) {
  return request.get<LearningRecordVO[]>(`/learning-records/users/${userId}/all`)
}

/** 今日签到状态 */
export function signInToday() {
  return request.get<{ signed: boolean }>('/sign-ins/today')
}

/** 签到 */
export function doSignIn() {
  return request.post<null>('/sign-ins')
}

/** 签到日期列表（YYYY-MM-DD） */
export function signInDates() {
  return request.get<string[]>('/sign-ins')
}

/** 笔记分页 */
export function pageNotes(params: PageQuery) {
  return request.get<PageDTO<NoteVO>>('/notes/page', params as Record<string, unknown>)
}

/** 新增笔记 */
export function addNote(data: { courseId: number; courseName?: string; content: string }) {
  return request.post<NoteVO>('/notes', data)
}

/** 删除笔记 */
export function deleteNote(id: number) {
  return request.delete<null>(`/notes/${id}`)
}
