import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { IS_MOCK } from '@/utils/auth'
import { promptLogin } from '@/utils/loginPrompt'

/**
 * 全局路由守卫（公共/业务两级访问控制）：
 * 1. 公共模块（首页、课程中心、课程详情等）自由浏览，不受登录状态影响
 * 2. 业务模块（requiresAuth 路由：学习中心、下单、考试、消息、管理端等）：
 *    未登录访问时弹窗提醒，确认则前往登录页（带回跳地址），取消则留在当前页；
 *    已登录但会话超时期间的业务请求 401，由 axios 拦截器按同样策略提示
 * 3. 首次登录 → 强制改密（改密成功前无法访问其他页面）
 *
 * 权限不在前端判定：接口访问由后端统一鉴权，
 * 无权限时后端返回 403，axios 拦截器跳转 /403 页。
 */
export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    // 页面标题
    document.title = to.meta.title ? `${to.meta.title} · 知行智学` : '知行智学 ZhiXing Learn'

    const userStore = useUserStore()

    // 登录页：已登录则回首页
    if (to.path === '/login') {
      if (userStore.isLoggedIn && !userStore.firstLogin) {
        return { path: '/' }
      }
      return true
    }

    // 首次改密页：放行（后端网关已将该路径加入 excludePaths）
    if (to.path === '/password/first-change') {
      // 非首次登录状态下直接回首页
      if (userStore.isLoggedIn && !userStore.firstLogin) {
        return { path: '/' }
      }
      return true
    }

    // 首次登录强制改密：改密成功前无法访问其他页面
    if (userStore.isLoggedIn && userStore.firstLogin) {
      return { path: '/password/first-change' }
    }

    // 未登录访问业务模块：弹窗提醒后决定去向（公共模块自由浏览，不受影响）
    if (to.matched.some((r) => r.meta.requiresAuth) && !userStore.isLoggedIn) {
      const goLogin = await promptLogin()
      if (goLogin) {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
      // 用户选择「暂不登录」：
      // - 页面内跳转（已有渲染中的路由）→ 中止本次导航，留在原页；
      // - 首次导航（直接输入/刷新受控地址，此时无任何已匹配路由）→ 回首页。
      //   若此处直接 return false，RouterView 将无内容可渲染，整页会变成空白。
      return router.currentRoute.value.matched.length > 0 ? false : { path: '/' }
    }

    // 角色级路由守卫：meta.roles 仅允许指定角色访问（如管理端 → admin；超管前端严格拦截，后端 403 兜底）
    const needRoles = to.matched.flatMap((r) => (r.meta.roles as string[] | undefined) ?? [])
    if (needRoles.length > 0 && !userStore.hasRole(...(needRoles as ('admin' | 'teacher' | 'student')[]))) {
      return { path: '/403' }
    }

    // 其余页面放行（公共模块自由浏览）
    return true
  })

  // Mock 模式：路由加载失败（如 Chunk 加载异常）兜底回首页
  router.onError((error) => {
    if (IS_MOCK) {
      console.error('[router]', error)
    }
  })
}
