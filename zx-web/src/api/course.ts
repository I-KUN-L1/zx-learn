import { request } from './request'
import type { Category, CourseFormDTO, CourseVO, PageDTO, PageQuery } from '@/types/api'

/**
 * 课程服务（zx-course）
 * 统一走网关：/courses/**、/categorys/**
 */

export interface CoursePageParams extends PageQuery {
  name?: string
  status?: number | ''
}

/** 课程分页 */
export function pageCourses(params: CoursePageParams) {
  return request.get<PageDTO<CourseVO>>('/courses/page', params as Record<string, unknown>)
}

/** 课程详情 */
export function getCourse(id: number | string) {
  return request.get<CourseVO>(`/courses/${id}`)
}

/** 课程编辑态信息 */
export function getCourseBaseInfo(id: number | string) {
  return request.get<CourseFormDTO>(`/courses/baseInfo/${id}`)
}

/** 保存课程基本信息（新增/更新） */
export function saveCourseBaseInfo(data: CourseFormDTO) {
  return request.post<number>('/courses/baseInfo/save', data)
}

/** 上架课程 */
export function upShelfCourse(id: number) {
  return request.post<null>('/courses/upShelf', { id })
}

/** 下架课程 */
export function downShelfCourse(id: number) {
  return request.post<null>('/courses/downShelf', { id })
}

/** 上架前校验 */
export function checkBeforeUpShelf(id: number) {
  return request.get<null>(`/courses/checkBeforeUpShelf/${id}`)
}

/** 删除课程 */
export function deleteCourse(id: number) {
  return request.delete<null>(`/courses/delete/${id}`)
}

/** 课程名称唯一性校验 */
export function checkCourseName(name: string) {
  return request.get<null>('/courses/checkName', { name })
}

/** 全部分类（树形组装前端完成） */
export function getCategoryAll() {
  return request.get<Category[]>('/categorys/all')
}
