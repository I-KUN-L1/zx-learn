import { getToken } from './auth'

/**
 * 角色枚举（与后端 UserRole.alias 对齐：admin/student/teacher，见 zx-common UserRole）
 */
export type RoleStr = 'admin' | 'teacher' | 'student'

/**
 * 从 accessToken 解析角色：
 * - Mock token（mock-admin-token-xxx / mock-student-token-xxx）按前缀判定；
 * - 真实 JWT（header.payload.signature）读取 payload 的 role claim。
 * 解析失败返回 null（视为游客，不特殊放行任何受控入口）。
 */
export function parseRoleFromToken(token: string | null): RoleStr | null {
  if (!token) return null
  if (token.startsWith('mock-admin-')) return 'admin'
  if (token.startsWith('mock-teacher-')) return 'teacher'
  if (token.startsWith('mock-student-')) return 'student'
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    const raw = atob(parts[1])
    // 兼容 UTF-8 中文 username 的 base64 解码
    const payload = JSON.parse(decodeURIComponent(escape(raw)))
    // 后端 JWT 将用户类型写入 roleId claim（见 zx-auth JwtConstants.PAYLOAD_ROLE_KEY）；
    // 依次兼容 role / roleId / type 三种 key，避免真实后端登录后解析不到角色导致按钮显隐错乱。
    const role = payload?.role ?? payload?.roleId ?? payload?.type
    if (role === 1 || role === 'admin') return 'admin'
    if (role === 3 || role === 'teacher') return 'teacher'
    if (role === 2 || role === 'student') return 'student'
    return null
  } catch {
    return null
  }
}