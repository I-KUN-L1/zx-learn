import { ref, type Ref } from 'vue'
import { fetchEventSource, EventStreamContentType } from '@microsoft/fetch-event-source'
import { getToken, IS_MOCK } from '@/utils/auth'
import { CHAT_SSE_URL } from '@/api/ai'
import type { ChatEventVO } from '@/types/api'
import { mockStreamChat } from '@/api/mock/chat'

export interface SSEStartOptions {
  /** 会话 ID */
  sessionId: string
  /** 用户问题 */
  question: string
  /** 事件回调 */
  onEvent: (ev: ChatEventVO) => void
  /** 正常结束（收到 END 或连接关闭） */
  onDone?: () => void
  /** 异常结束（重连耗尽等） */
  onError?: (err: unknown) => void
  /** 断线最大重连次数（对齐后端 Last-Event-ID 增量续传） */
  maxRetries?: number
}

/**
 * SSE 流式对话（@microsoft/fetch-event-source）
 * - POST + JSON body + Bearer Token
 * - 断线自动重连并携带 Last-Event-ID
 * - AbortController 支持手动中断
 * - Mock 模式下本地模拟流式输出（对齐真实 END 事件格式）
 */
export function useSSE() {
  const streaming: Ref<boolean> = ref(false)
  let ctrl: AbortController | null = null
  let retries = 0

  async function start(opts: SSEStartOptions) {
    const { sessionId, question, onEvent, onDone, onError, maxRetries = 3 } = opts
    streaming.value = true
    retries = 0
    ctrl = new AbortController()
    const signal = ctrl.signal

    /* ---------- Mock：本地模拟流式输出 ---------- */
    if (IS_MOCK) {
      try {
        await mockStreamChat(sessionId, question, onEvent, signal)
        onDone?.()
      } catch (err) {
        onError?.(err)
      } finally {
        streaming.value = false
        ctrl = null
      }
      return
    }

    /* ---------- 真实：fetch-event-source ---------- */
    const token = getToken()
    /**
     * 致命错误（401/403 等认证、权限、来源被拒）：重连多少次都不会成功，
     * 必须直接终止并把真实原因透出，否则用户只看到「连接中断，请重试」，
     * 无法判断是登录过期、无权限，还是后端不可用（排查盲区）。
     */
    let fatal: Error | null = null
    try {
      await fetchEventSource(CHAT_SSE_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ sessionId, question }),
        signal,
        openWhenHidden: true, // 页面隐藏时保持连接
        async onopen(response) {
          if (response.ok && response.headers.get('content-type')?.includes(EventStreamContentType)) {
            return
          }
          if (response.status === 401) {
            const { forceLogout } = await import('@/utils/loginPrompt')
            await forceLogout()
            fatal = new Error('登录已过期，请重新登录')
          } else if (response.status === 403) {
            // 注意：403 也可能是网关 CORS 拒绝了当前页面来源（allowedOriginPatterns
            // 未覆盖你访问所用的域名，如用 127.0.0.1 打开而白名单只有 localhost）。
            fatal = new Error('无权限访问该资源（若页面地址非 localhost，请改用 http://localhost:5173 访问）')
          } else if (response.status === 0 || response.type === 'opaque') {
            fatal = new Error('网络异常或请求被跨域策略拦截，请稍后重试')
          } else {
            fatal = new Error(`对话连接失败（HTTP ${response.status}），请稍后重试`)
          }
          throw fatal
        },
        onmessage(ev) {
          if (!ev.data) return // 心跳注释 / 空事件
          try {
            const parsed = JSON.parse(ev.data) as ChatEventVO
            onEvent(parsed)
            if (parsed.type === 'END') {
              ctrl?.abort() // 正常结束
            }
          } catch {
            /* 非 JSON 事件忽略 */
          }
        },
        onerror(err) {
          // 正常中断（收到 END 后主动 abort）
          if (signal.aborted) {
            throw err
          }
          // 认证/权限/来源类错误不可重试，直接终止并把原因交给 onError
          if (fatal) {
            throw fatal
          }
          // 断线重连：fetch-event-source 自动携带 Last-Event-ID 增量续传，
          // 返回值为下次重连间隔（ms）
          if (retries < maxRetries) {
            retries += 1
            return 800 * retries
          }
          throw err
        },
        onclose() {
          onDone?.()
        },
      })
      if (!signal.aborted) onDone?.()
    } catch (err) {
      if (signal.aborted) {
        onDone?.() // 用户主动中断视为正常结束
      } else {
        onError?.(err)
      }
    } finally {
      streaming.value = false
      ctrl = null
    }
  }

  /** 手动中断（同时通知后端停止生成） */
  function stop() {
    ctrl?.abort()
    ctrl = null
    streaming.value = false
  }

  return { streaming, start, stop }
}
