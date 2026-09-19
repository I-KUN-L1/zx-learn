import { request } from './request'
import type { Id, InsightProfileVO, PageDTO, PageQuery, UserVO } from '@/types/api'

/**
 * 教师端接口（学员列表 / 学员学情）。
 * <p>
 * 题库相关接口已迁移至 {@link ./exam}（`@/api/exam`），
 * 避免题库契约在两处重复维护导致漂移。
 * 后端权限：学员列表教师/管理员；学员学情画像教师/管理员可查（学员间不可互查）。
 */

/** 学员分页列表（教师查看学员） */
export function pageStudents(params: PageQuery) {
  return request.get<PageDTO<UserVO>>('/users/page', {
    ...params,
    type: 2,
  } as Record<string, unknown>)
}

/** 学员学情画像（教师查看指定学员）。id 为雪花数时后端以字符串下发，统一收 `Id` */
export function studentProfile(userId: Id) {
  return request.get<InsightProfileVO>(`/insight/teacher/students/${userId}`)
}
