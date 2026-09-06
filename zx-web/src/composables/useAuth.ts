import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

/** 登录/登出/回跳封装 */
export function useAuth() {
  const router = useRouter()
  const userStore = useUserStore()

  /** 登录成功后按 firstLogin / redirect / 角色决定去向 */
  async function handleLoginSuccess(cellPhone: string) {
    const redirect = (router.currentRoute.value.query.redirect as string) || ''
    if (userStore.firstLogin) {
      await router.replace('/password/first-change')
      return
    }
    await router.replace(redirect || '/').catch(() => router.replace('/'))
    void cellPhone
  }

  async function handleLogout() {
    await userStore.logout()
    await router.replace('/login')
  }

  return { handleLoginSuccess, handleLogout }
}
