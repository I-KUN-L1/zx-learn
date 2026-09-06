import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题 */
    title?: string
    /** 是否需要登录 */
    requiresAuth?: boolean
    /** 允许访问的角色（RBAC） */
    roles?: string[]
  }
}
