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

/** 指定课程的课表项（校验是否已拥有该课程） */
export function lessonByCourse(courseId: number | string) {
  return request.get<LearningLessonVO | null>(`/lessons/${courseId}`)
}

/**
 * 我（当前学员）课表中已拥有的课程 id 集合。
 * <p>
 * 「已拥有」的权威口径：课表里有 → 已拥有；课表里没有 → 可购买。
 * 课程列表、课程详情与「我的课表」检索全部以它为准，保证两端状态实时联动一致。
 */
export function myLessonCourseIds() {
  return request.get<Array<number | string>>('/lessons/mine/course-ids')
}

/**
 * 上报学习进度。
 * <p>
 * 后端契约（LearningProgressDTO）：{ courseId, lessonId, progress, learnDuration }。
 * lessonId 对应课程目录中的小节 id；progress 只允许单调递增（后端强校验，倒退会 400）。
 * 服务端在进度首次达到 100% 时自动发放"完成学习"积分，无需前端调用积分接口。
 */
export function reportProgress(data: {
  courseId: number | string
  lessonId: number | string
  progress: number
  learnDuration?: number
}) {
  return request.post<number>('/learning-records/progress', data)
}

/** 学习记录 */
export function myLearningRecords() {
  return request.get<LearningRecordVO[]>('/learning-records/my')
}

/** 今日签到状态 */
export function signInToday() {
  return request.get<boolean>('/sign-ins/today')
}

/** 签到 */
export function doSignIn() {
  return request.post<null>('/sign-ins')
}

/** 签到日期列表（YYYY-MM-DD） */
export function signInDates() {
  return request.get<string[]>('/sign-ins')
}

export interface NotePageParams extends PageQuery {
  /** 只看某门课的笔记 */
  courseId?: number | string
  /** 只看某个小节的笔记（课程内容页「本节笔记」） */
  lessonId?: number | string
}

/** 笔记分页 */
export function pageNotes(params: NotePageParams) {
  return request.get<PageDTO<NoteVO>>('/notes/page', params as Record<string, unknown>)
}

/** 新增笔记（lessonId 传小节 id 即为"本节笔记"） */
export function addNote(data: {
  courseId: number | string
  courseName?: string
  lessonId?: number | string
  content: string
}) {
  return request.post<NoteVO>('/notes', data)
}

/** 删除笔记 */
export function deleteNote(id: number) {
  return request.delete<null>(`/notes/${id}`)
}
