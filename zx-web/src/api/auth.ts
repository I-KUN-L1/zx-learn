import { request } from './request'
import type { FirstChangePasswordDTO, LoginFormDTO, LoginResultVO } from '@/types/api'

/**
 * 认证服务（zx-auth）
 * 统一走网关：/accounts/**
 */

/** 统一登录（角色由后端账号属性决定，前端不做管理端/学员端区分） */
export function login(data: LoginFormDTO) {
  return request.post<LoginResultVO>('/accounts/login', data)
}

/** 首次登录修改初始密码 */
export function firstChangePassword(data: FirstChangePasswordDTO) {
  return request.post<null>('/accounts/password/first-change', data)
}

/** 退出登录 */
export function logout() {
  return request.post<null>('/accounts/logout')
}

/** 注册表单（学员/教师自助注册；管理员仅后端创建） */
export interface RegisterFormDTO {
  cellPhone: string
  password: string
  username?: string
  name?: string
}

/** 学员注册（zx-user：/students/register，网关白名单放行） */
export function registerStudent(data: RegisterFormDTO) {
  return request.post<null>('/students/register', data)
}

/** 教师注册（zx-user：/teachers/register，网关白名单放行；角色由后端强制指定） */
export function registerTeacher(data: RegisterFormDTO) {
  return request.post<null>('/teachers/register', data)
}
