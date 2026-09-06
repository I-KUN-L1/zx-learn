import { getStorage, setStorage, removeStorage, STORAGE_KEYS } from './storage'
import type { LoginResultVO } from '@/types/api'

/** accessToken（明文存储，与 Pinia user store 双写同步） */
export function getToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.token)
}

export function setToken(token: string) {
  localStorage.setItem(STORAGE_KEYS.token, token)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.refreshToken)
}

export function setRefreshToken(token: string) {
  localStorage.setItem(STORAGE_KEYS.refreshToken, token)
}

export function getLoginUser(): LoginResultVO | null {
  return getStorage<LoginResultVO | null>(STORAGE_KEYS.user, null)
}

export function setLoginUser(user: LoginResultVO) {
  setStorage(STORAGE_KEYS.user, user)
}

/** 清空全部登录凭据 */
export function clearAuth() {
  removeStorage(STORAGE_KEYS.token)
  removeStorage(STORAGE_KEYS.refreshToken)
  removeStorage(STORAGE_KEYS.user)
}

/** Mock 开关 */
export const IS_MOCK = import.meta.env.VITE_USE_MOCK === 'true'
