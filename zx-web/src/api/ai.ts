import { request } from './request'
import type { ChatMessage, ChatSession } from '@/types/api'

/**
 * AI 服务（zx-aigc）
 * 统一走网关：/chat/**、/session/**
 * SSE 流式接口由 useSSE 组合式函数直连（POST /chat，text/event-stream）
 */

/** 创建会话 */
export function createSession() {
  return request.post<ChatSession>('/session')
}

/** 会话历史列表 */
export function sessionHistory() {
  return request.get<ChatSession[]>('/session/history')
}

/** 会话消息详情 */
export function sessionDetail(sessionId: string) {
  return request.get<ChatMessage[]>(`/session/${sessionId}`)
}

/** 删除会话 */
export function deleteSession(sessionId: string) {
  return request.delete<null>('/session/history', { sessionId } as Record<string, unknown>)
}

/** 修改会话标题 */
export function updateSessionTitle(sessionId: string, title: string) {
  return request.put<null>('/session/history', { sessionId, title })
}

/** 热门问题 */
export function hotQuestions() {
  return request.get<string[]>('/session/hot')
}

/** 停止生成 */
export function stopChat(sessionId: string) {
  return request.post<null>('/chat/stop', { sessionId })
}

/** 非流式对话（降级可用） */
export function chatText(sessionId: string, question: string) {
  return request.post<string>('/chat/text', { sessionId, question })
}

/** SSE 端点：POST /chat（text/event-stream，事件 {type: START|DELTA|END, content, agent}，END 事件标记结束） */
export const CHAT_SSE_URL = `${import.meta.env.VITE_API_BASE_URL || '/api'}/chat`
