import { request } from './request'
import type { InsightProfileVO, LearningPathVO } from '@/types/api'

/**
 * 学情服务（zx-insight）
 * 统一走网关：/insight/**
 */

/** 我的学情画像（含能力雷达 / 趋势数据） */
export function myProfile() {
  return request.get<InsightProfileVO>('/insight/profiles/mine')
}

/** 学习路径推荐 */
export function learningPath() {
  return request.get<LearningPathVO>('/insight/learning-path')
}

/** 最新学情报告文本 */
export function latestReport() {
  return request.get<{ id: number; userId: number; content: string; createTime: string }>('/insight/reports/latest')
}

/** 管理端看板数据 */
export function insightDashboard() {
  return request.get<import('@/types/api').DashboardVO>('/insight/dashboard')
}
