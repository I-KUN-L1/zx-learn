import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, setToken, IS_MOCK, clearAuth } from '@/utils/auth'
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

/** 清空凭据并跳转登录页（记录回跳地址） */
export async function forceLogout() {
  clearAuth()
  const { default: router } = await import('@/router')
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    await router.replace({
      path: '/login',
      query: current.path === '/' ? {} : { redirect: current.fullPath },
    })
  }
}

/** 判断给定响应是否为 401（兼容 code!=200 与 HTTP 401） */
function isUnauthorized(res?: AxiosResponse | null, err?: AxiosError): boolean {
  if (err?.response?.status === 401) return true
  const body = (res?.data ?? err?.response?.data) as R | undefined
  return body?.code === 401
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
      // 401：静默续期后重放
      if (response.config.url?.includes('/accounts/refresh')) {
        await forceLogout()
        return Promise.reject(new Error('登录已过期'))
      }
      const newToken = await refreshTokenRequest()
      if (newToken) {
        response.config.headers.Authorization = `Bearer ${newToken}`
        return service.request(response.config)
      }
      await forceLogout()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    if (payload.code === 403) {
      // 403：后端接口级鉴权未通过，全局跳转无权限页
      await gotoForbidden()
      return Promise.reject(new Error(rMessage(payload)))
    }
    ElMessage.error(rMessage(payload))
    return Promise.reject(new Error(rMessage(payload)))
  },
  async (error: AxiosError<R>) => {
    if (isUnauthorized(null, error)) {
      if (error.config?.url?.includes('/accounts/refresh')) {
        await forceLogout()
        return Promise.reject(error)
      }
      const newToken = await refreshTokenRequest()
      if (newToken && error.config) {
        error.config.headers.Authorization = `Bearer ${newToken}`
        return service.request(error.config)
      }
      await forceLogout()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    const bodyCode = error.response?.data?.code
    if (bodyCode === 403 || error.response?.status === 403) {
      await gotoForbidden()
      return Promise.reject(error)
    }
    const msg =
      error.response?.data?.message || error.response?.data?.msg || error.message || '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
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
