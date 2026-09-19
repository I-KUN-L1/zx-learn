import { request } from './request'
import type { CouponVO, Id, PageDTO, PageQuery, UserCouponVO } from '@/types/api'

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

/** 我的优惠券（分页）。status：1 未使用 2 已使用 3 已过期 */
export function myCouponsPage(params: PageQuery & { status?: number | '' }) {
  return request.get<PageDTO<UserCouponVO>>('/user-coupons/page', params as Record<string, unknown>)
}

/** 优惠券详情 */
export function couponDetail(couponId: Id) {
  return request.get<CouponVO>(`/coupons/${couponId}`)
}

/** 普通领券 */
export function claimCoupon(couponId: Id) {
  return request.post<null>('/user-coupons/claim', { couponId })
}

/** 秒杀领取（立即返回：QUEUING/SOLD_OUT/REPEAT/NOT_READY） */
export function seckillClaim(couponId: Id) {
  return request.post<null>(`/user-coupons/seckill/${couponId}`)
}

/** 秒杀结果轮询。真实后端返回 {status:'SUCCESS'|'QUEUING'|..., couponCode}；Mock 返回 {success,orderId}，二者都兼容 */
export function seckillResult(couponId: Id) {
  return request.get<{
    status?: 'SUCCESS' | 'QUEUING' | 'REPEAT' | 'FAILED' | 'SOLD_OUT' | 'NOT_READY'
    couponCode?: string | null
    success?: boolean
    orderId?: Id | null
  }>(`/user-coupons/seckill/${couponId}/result`)
}
