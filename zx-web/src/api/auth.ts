import { request } from './request'
import type { FirstChangePasswordDTO, LoginFormDTO, LoginResultVO } from '@/types/api'

/**
 * 认证服务（zx-auth）
 * 统一走网关：/accounts/**
 */

/** 用户端登录 */
export function login(data: LoginFormDTO) {
  return request.post<LoginResultVO>('/accounts/login', data)
}

/** 管理端登录 */
export function adminLogin(data: LoginFormDTO) {
  return request.post<LoginResultVO>('/accounts/admin/login', data)
}

/** 首次登录修改初始密码 */
export function firstChangePassword(data: FirstChangePasswordDTO) {
  return request.post<null>('/accounts/password/first-change', data)
}

/** 退出登录 */
export function logout() {
  return request.post<null>('/accounts/logout')
}
