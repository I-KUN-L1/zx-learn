/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Axios 基地址 */
  readonly VITE_API_BASE_URL: string
  /** Vite 开发代理目标（网关地址） */
  readonly VITE_PROXY_TARGET?: string
  /** Mock 开关 */
  readonly VITE_USE_MOCK: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
