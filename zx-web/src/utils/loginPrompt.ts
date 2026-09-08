import { ElMessageBox } from 'element-plus'

/**
 * 登录提醒共享工具（request 拦截器与路由守卫共用）。
 *
 * 职责分离：promptLogin 仅负责弹窗询问（返回用户选择），
 * 导航动作由调用方决定——守卫确认后跳 /login?redirect=目标页，
 * 请求拦截器确认后走 forceLogout 回跳来源页。
 */

/** 是否正在展示登录确认弹窗（防止并发 401 触发弹窗风暴） */
let loginPrompting = false

/**
 * 清空凭据并跳转登录页（记录回跳地址）。
 * 同时重置 Pinia 内存态与本地存储，避免内存 token 残留
 * 导致路由守卫 isLoggedIn 误判（已登录）而弹回首页。
 */
export async function forceLogout(): Promise<void> {
  const { useUserStore } = await import('@/stores/user')
  useUserStore().resetLocal()
  const { default: router } = await import('@/router')
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    await router.replace({
      path: '/login',
      query: current.path === '/' ? {} : { redirect: current.fullPath },
    })
  }
}

/**
 * 登录提醒弹窗：询问用户是否前往登录。
 * @returns true=用户确认前往登录；false=取消或已有弹窗在展示
 */
export async function promptLogin(): Promise<boolean> {
  if (loginPrompting) return false
  loginPrompting = true
  try {
    await ElMessageBox.confirm('该操作需要登录后才能继续，是否前往登录？', '需要登录', {
      confirmButtonText: '前往登录',
      cancelButtonText: '暂不登录',
      type: 'info',
    })
    return true
  } catch {
    /* 用户取消：留在当前页，由调用方决定后续 */
    return false
  } finally {
    loginPrompting = false
  }
}
