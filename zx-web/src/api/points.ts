import { request } from './request'
import type { Id, PageDTO, PageQuery, PointsRankVO, PointsRecordVO, PointsSummaryVO } from '@/types/api'

/**
 * 积分服务（zx-learning）
 * 统一走网关：/points/**
 *
 * 隐私边界：所有接口都只返回当前登录用户的积分数据（后端从 JWT 取 userId，
 * 不接受前端传入的 userId），因此前端无越权面。
 */

/** 我的积分概况：总额 / 排名 / 参与人数 / 今日与近 7 日增量 */
export function pointsSummary() {
  return request.get<PointsSummaryVO>('/points/summary')
}

/** 学习积分排行榜：前 N 名 + 本人排名条目 */
export function pointsRank(top = 10) {
  return request.get<{ top: PointsRankVO[]; me: PointsRankVO }>('/points/rank', { top })
}

/** 积分明细分页 */
export function pointsRecords(params: PageQuery) {
  return request.get<PageDTO<PointsRecordVO>>('/points/records/page', params as Record<string, unknown>)
}

export type { Id }
