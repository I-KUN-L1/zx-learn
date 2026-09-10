import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  clearAuth,
  getLoginUser,
  getToken,
  setLoginUser,
  setRefreshToken,
  setToken,
} from '@/utils/auth'
import { parseRoleFromToken } from '@/utils/permission'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import type { LoginFormDTO, LoginResultVO } from '@/types/api'
import type { RoleStr } from '@/utils/permission'

/**
 * 当前登录用户状态（JWT 双 Token）。
 * 权限不在前端判定：菜单入口对所有人可见，接口级鉴权全部由后端完成
 * （JWT role claim → 网关 role-info → 服务端 RoleInterceptor，无权限返回 403，
 * 由 axios 拦截器统一跳转 403 页）。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() ?? '')
  const userId = ref<number>(0)
  const username = ref<string>('')
  /** 首次登录强制改密标记 */
  const firstLogin = ref<boolean>(false)
  /** 当前角色（admin/teacher/student，从登录响应或 JWT 解析） */
  const role = ref<RoleStr | ''>('')
  /** 登录页记住的手机号（供改密页回填） */
  const pendingCellPhone = ref<string>('')

  const isLoggedIn = computed(() => !!token.value)

  /** RBAC：当前角色是否命中给定的任一角色 */
  function hasRole(...roles: RoleStr[]): boolean {
    return roles.includes(role.value as RoleStr)
  }

  /** 启动时从本地恢复 */
  function restore() {
    const saved = getLoginUser()
    if (saved) {
      token.value = getToken() ?? ''
      userId.value = saved.userId ?? 0
      username.value = saved.username ?? ''
      firstLogin.value = saved.firstLogin ?? false
      role.value = saved.role ?? parseRoleFromToken(token.value) ?? ''
    }
  }

  function applyLogin(result: LoginResultVO) {
    token.value = result.accessToken
    setToken(result.accessToken)
    setRefreshToken(result.refreshToken)
    userId.value = result.userId
    username.value = result.username ?? ''
    firstLogin.value = result.firstLogin ?? false
    // 优先取登录响应里显式返回的角色，其次尝试从 JWT 解析
    role.value = result.role ?? parseRoleFromToken(result.accessToken) ?? ''
    // ''（无角色）在 LoginResultVO.role 上定义成 undefined，避免类型不匹配
    setLoginUser({ ...result, role: (role.value || undefined) })
  }

  /** 统一登录：角色由后端账号属性决定（前端不做管理端/学员端区分） */
  async function login(form: LoginFormDTO) {
    pendingCellPhone.value = form.cellPhone
    const result = await loginApi(form)
    applyLogin(result)
    return result
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      /* 后端登出失败不阻断本地清理 */
    }
    resetLocal()
  }

  function resetLocal() {
    token.value = ''
    userId.value = 0
    username.value = ''
    firstLogin.value = false
    role.value = ''
    clearAuth()
  }

  /** 首次改密成功后解除强制改密状态 */
  function finishFirstChange() {
    firstLogin.value = false
    const saved = getLoginUser()
    if (saved) setLoginUser({ ...saved, firstLogin: false })
  }

  return {
    token,
    userId,
    username,
    firstLogin,
    role,
    pendingCellPhone,
    isLoggedIn,
    hasRole,
    isAdmin: computed(() => role.value === 'admin'),
    isTeacher: computed(() => role.value === 'teacher'),
    isStudent: computed(() => role.value === 'student'),
    restore,
    login,
    logout,
    resetLocal,
    finishFirstChange,
  }
})
