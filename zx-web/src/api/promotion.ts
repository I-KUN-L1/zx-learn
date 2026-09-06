import { request } from './request'
import type { CouponVO, PageDTO, PageQuery, UserCouponVO } from '@/types/api'

/**
 * 优惠服务（zx-promotion）
 * 统一走网关：/coupons/**、/user-coupons/**
 */

export interface CouponPageParams extends PageQuery {
  type?: number | ''
}

/** 可领券列表 */
export function pageCoupons(params: CouponPageParams) {
  return request.get<PageDTO<CouponVO>>('/coupons/page', params as Record<string, unknown>)
}

/** 我的优惠券 */
export function myCoupons() {
  return request.get<UserCouponVO[]>('/user-coupons')
}

/** 普通领券 */
export function claimCoupon(couponId: number) {
  return request.post<null>('/user-coupons/claim', { couponId })
}

/** 秒杀抢券（网关 5 QPS 限流） */
export function seckillClaim(couponId: number) {
  return request.post<null>(`/user-coupons/seckill/${couponId}`)
}

/** 秒杀结果轮询 */
export function seckillResult(couponId: number) {
  return request.get<{ success: boolean; orderId?: number | null }>(`/user-coupons/seckill/${couponId}/result`)
}
