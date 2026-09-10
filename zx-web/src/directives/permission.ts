import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'
import type { RoleStr } from '@/utils/permission'

/**
 * 按钮/组件级 RBAC 控制指令 v-perm。
 *
 * 用法：
 *   <el-button v-perm="'admin'">仅管理员可见</el-button>
 *   <el-button v-perm="['admin', 'teacher']">管理员/教师可见</el-button>
 *
 * 未授权时直接将该元素从 DOM 中移除（不渲染），从而彻底隐藏越权按钮。
 */
export const perm: Directive<HTMLElement, RoleStr | RoleStr[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const roles = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!userStore.hasRole(...roles)) {
      el.parentNode?.removeChild(el)
    }
  },
  // 角色可能在运行期变化（登录/登出），同步更新显隐
  updated(el, binding) {
    const userStore = useUserStore()
    const roles = Array.isArray(binding.value) ? binding.value : [binding.value]
    const allow = userStore.hasRole(...roles)
    // 只处理元素仍挂在文档中的场景，保持与 mounted 一致的判定
    if (!allow && el.parentNode) {
      el.remove()
    }
  },
}

export default perm