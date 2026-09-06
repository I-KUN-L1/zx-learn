import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { IS_MOCK } from '@/utils/auth'

/**
 * 全局路由守卫：
 * 1. 未登录 → 跳登录页（记录回跳地址）
 * 2. 首次登录 → 强制改密（改密成功前无法访问其他页面）
 *
 * 权限不在前端判定：页面入口全部放行，接口访问由后端统一鉴权，
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

    // 未登录 → 登录页（记录回跳地址）
    if (to.meta.requiresAuth && !userStore.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    // 首次登录强制改密：改密成功前无法访问其他页面
    if (userStore.isLoggedIn && userStore.firstLogin) {
      return { path: '/password/first-change' }
    }

    return true
  })

  // Mock 模式：路由加载失败（如 Chunk 加载异常）兜底回首页
  router.onError((error) => {
    if (IS_MOCK) {
      console.error('[router]', error)
    }
  })
}
