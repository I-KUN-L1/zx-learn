/** localStorage / sessionStorage 封装（JSON 序列化） */

export function getStorage<T>(key: string, def: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : def
  } catch {
    return def
  }
}

export function setStorage(key: string, value: unknown) {
  localStorage.setItem(key, JSON.stringify(value))
}

export function removeStorage(key: string) {
  localStorage.removeItem(key)
}

export const STORAGE_KEYS = {
  token: 'zx_access_token',
  refreshToken: 'zx_refresh_token',
  user: 'zx_user_info',
  theme: 'zx_theme',
  redirect: 'zx_redirect',
} as const
