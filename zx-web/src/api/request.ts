import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, setToken, IS_MOCK } from '@/utils/auth'
import { forceLogout, promptLogin } from '@/utils/loginPrompt'
import { mockAdapter } from '@/api/mock/adapter'
import type { R } from '@/types/api'

/** 是否为 R<T> 结构（后端真实字段为 msg，message 为文档别名） */
function isRWrapper(payload: unknown): payload is R {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'code' in payload &&
    ('message' in payload || 'msg' in payload) &&
    'data' in payload
  )
}

/** 提取 R 结构的错误信息 */
function rMessage(payload: R): string {
  return payload.message || payload.msg || '请求失败'
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  // refreshToken 位于 HttpOnly Cookie，需携带凭据
  withCredentials: true,
})

// Mock 模式：使用内置 Mock Adapter，无需后端
if (IS_MOCK) {
  service.defaults.adapter = mockAdapter
}

/* ================= 请求拦截器：自动携带 Bearer Token ================= */
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/* ================= 401 静默续期（单飞 + 请求队列重放） ================= */
let refreshingPromise: Promise<string | null> | null = null

function refreshTokenRequest(): Promise<string | null> {
  if (!refreshingPromise) {
    refreshingPromise = axios
      .get<R<{ accessToken: string }>>(`${service.defaults.baseURL}/accounts/refresh`, {
        withCredentials: true,
        timeout: 10000,
      })
      .then((res) => {
        if (res.data.code === 200 && res.data.data?.accessToken) {
          const newToken = res.data.data.accessToken
          setToken(newToken)
          return newToken
        }
        return null
      })
      .catch(() => null)
      .finally(() => {
        refreshingPromise = null
      })
  }
  return refreshingPromise
}

/**
 * 401 统一处理（登录超时/未登录），按页面上下文区分响应策略：
 * 1. 登出请求的 401：静默（logout 调用方 catch 自行清理本地态）
 * 2. 登录/注册页的 401（如密码错误）：仅 ElMessage 透出原始原因，不弹登录提醒
 * 3. 业务页面（requiresAuth 路由）的 401：弹窗提醒，确认后回跳登录页
 * 4. 公共页面浏览类请求（GET）：静默降级为游客态，不弹窗（自由浏览体验）
 * 5. 公共页面业务动作（POST/PUT/DELETE，如课程详情加入购物车）：弹窗提醒
 */
async function handleUnauthorized(config?: InternalAxiosRequestConfig, message?: string): Promise<void> {
  // 登出接口的 401：token 已失效属预期，静默处理
  if (config?.url?.includes('/accounts/logout')) {
    return
  }
  const { default: router } = await import('@/router')
  const route = router.currentRoute.value
  // 登录/注册页：透出原始错误（如"用户名或密码错误"），不弹登录提醒、不清凭据（保护首次登录改密状态）
  if (route.path === '/login' || route.path === '/register') {
    ElMessage.error(message || '登录已过期，请重新登录')
    return
  }
  // 业务页面：弹窗提醒，确认后清凭据并回跳登录页
  if (route.matched.some((r) => r.meta.requiresAuth)) {
    if (await promptLogin()) {
      await forceLogout()
    }
    return
  }
  // 公共页面：浏览类 GET 静默降级游客态；业务动作仍弹窗提醒
  const method = (config?.method ?? 'get').toLowerCase()
  if (method === 'get') {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().resetLocal()
    return
  }
  if (await promptLogin()) {
    await forceLogout()
  }
}

/** 判断给定响应是否为 401（兼容 code!=200 与 HTTP 401） */
function isUnauthorized(res?: AxiosResponse | null, err?: AxiosError): boolean {
  if (err?.response?.status === 401) return true
  const body = (res?.data ?? err?.response?.data) as R | undefined
  return body?.code === 401
}

/* ================= 瞬态错误自动重试（仅幂等 GET） ================= */

/** 带重试上下文的请求配置（扩展字段不参与序列化） */
type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean; _retryCount?: number }

/** 最大重试次数（GET 幂等请求，避免写操作重复提交） */
const MAX_RETRIES = 2
/** 可重试的 HTTP 状态码：网关超时/坏网关/服务不可用 */
const RETRYABLE_STATUS = new Set([502, 503, 504])
/** 可重试的 R 业务码：请求超时/网关错误（后端以 HTTP 200 + R 信封返回） */
const RETRYABLE_BODY_CODE = new Set([408, 502, 503, 504])

/**
 * 瞬态错误判定：网络中断、请求超时、网关类错误。
 * 网络层抖动与上游瞬时不可用重试通常可自愈，业务 4xx 不重试。
 */
function isTransientError(error: AxiosError): boolean {
  if (!error.response) return true // 无响应：网络中断 / DNS 失败 / ECONNABORTED 超时
  return RETRYABLE_STATUS.has(error.response.status)
}

/**
 * 瞬态错误指数退避重试：仅对幂等 GET 生效，最多 MAX_RETRIES 次。
 * @throws 原始 error（不可重试或重试耗尽时原样抛出，交由后续处理）
 */
async function retryIfTransient(error: AxiosError): Promise<unknown> {
  const config = error.config as RetryConfig | undefined
  const method = (config?.method ?? 'get').toLowerCase()
  if (!config || method !== 'get' || !isTransientError(error)) {
    throw error
  }
  const retryCount = (config._retryCount ?? 0) + 1
  if (retryCount > MAX_RETRIES) {
    throw error
  }
  config._retryCount = retryCount
  // 指数退避：300ms、600ms，叠加抖动避免惊群
  const delay = 300 * 2 ** (retryCount - 1) + Math.random() * 100
  await new Promise((resolve) => setTimeout(resolve, delay))
  return service.request(config)
}

/** 403：跳转无权限页（已在该页则不重复跳转） */
async function gotoForbidden() {
  const { default: router } = await import('@/router')
  if (router.currentRoute.value.path !== '/403') {
    await router.replace({ path: '/403' })
  }
}

/* ================= 响应拦截器：统一解包 R<T> ================= */
service.interceptors.response.use(
  async (response: AxiosResponse) => {
    const payload = response.data
    if (!isRWrapper(payload)) {
      return payload // 非 R 结构（如裸数据）原样返回
    }
    if (payload.code === 200) {
      return payload.data
    }
    if (payload.code === 401) {
      const msg = rMessage(payload)
      // 认证类接口的 401 是业务失败（如密码错误），不做续期重放（否则有效 refresh cookie 会造成 login↔refresh 死循环）
      const url = response.config.url ?? ''
      if (url.includes('/accounts/refresh') || url.includes('/accounts/login')) {
        await handleUnauthorized(response.config, msg)
        return Promise.reject(new Error(msg))
      }
      // 业务请求 401：静默续期后重放（_retry 标记防止重放仍 401 时陷入刷新↔重放死循环）
      const config = response.config as RetryConfig
      if (!config._retry) {
        config._retry = true
        const newToken = await refreshTokenRequest()
        if (newToken) {
          config.headers.Authorization = `Bearer ${newToken}`
          return service.request(config)
        }
      }
      await handleUnauthorized(response.config, msg)
      return Promise.reject(new Error(msg))
    }
    if (payload.code === 403) {
      // 403：后端接口级鉴权未通过，全局跳转无权限页
      await gotoForbidden()
      return Promise.reject(new Error(rMessage(payload)))
    }
    // 后端以 HTTP 200 + R 信封返回的超时/网关类错误：GET 幂等请求自动重试；不重试时保留全局提示
    if (RETRYABLE_BODY_CODE.has(payload.code)) {
      const synthetic = new AxiosError(rMessage(payload), String(payload.code))
      synthetic.config = response.config
      try {
        return await retryIfTransient(synthetic)
      } catch {
        ElMessage.error(rMessage(payload))
        return Promise.reject(new Error(rMessage(payload)))
      }
    }
    ElMessage.error(rMessage(payload))
    return Promise.reject(new Error(rMessage(payload)))
  },
  async (error: AxiosError<R>) => {
    if (isUnauthorized(null, error)) {
      const msg =
        error.response?.data?.message || error.response?.data?.msg || '登录已过期，请重新登录'
      // 认证类接口的 401 是业务失败，不做续期重放
      const url = error.config?.url ?? ''
      if (url.includes('/accounts/refresh') || url.includes('/accounts/login')) {
        await handleUnauthorized(error.config, msg)
        return Promise.reject(error)
      }
      // _retry 标记：同一请求最多续期重放一次，防止刷新↔重放死循环
      const config = error.config as RetryConfig | undefined
      if (config && !config._retry) {
        config._retry = true
        const newToken = await refreshTokenRequest()
        if (newToken) {
          config.headers.Authorization = `Bearer ${newToken}`
          return service.request(config)
        }
      }
      await handleUnauthorized(error.config, msg)
      return Promise.reject(new Error(msg))
    }
    const bodyCode = error.response?.data?.code
    if (bodyCode === 403 || error.response?.status === 403) {
      await gotoForbidden()
      return Promise.reject(error)
    }
    // 网络中断/超时/网关类错误的 GET 请求：指数退避自动重试；重试耗尽后全局提示
    try {
      return await retryIfTransient(error)
    } catch (e) {
      const err = e as AxiosError<R>
      const msg =
        err.response?.data?.message || err.response?.data?.msg || err.message || '网络异常，请稍后重试'
      ElMessage.error(msg)
      return Promise.reject(err)
    }
  }
)

/** 类型安全请求方法：直接返回业务数据 T */
export const request = {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    return service.get(url, { params }) as Promise<T>
  },
  post<T>(url: string, data?: unknown): Promise<T> {
    return service.post(url, data) as Promise<T>
  },
  put<T>(url: string, data?: unknown): Promise<T> {
    return service.put(url, data) as Promise<T>
  },
  delete<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    return service.delete(url, { params }) as Promise<T>
  },
}

export default service
