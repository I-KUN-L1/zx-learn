import { request } from './request'
import { IS_MOCK } from '@/utils/auth'
import type { OrderVO, PageDTO, PageQuery, PlaceOrderDTO } from '@/types/api'

/**
 * 交易服务（zx-trade）
 * 统一走网关：/carts/**、/orders/**
 */

/* ---------- 购物车 ---------- */
export function addToCart(courseId: number) {
  return request.post<null>('/carts', { courseId })
}

export function cartList() {
  return request.get<{ courseId: number; courseName: string; price: number }[]>('/carts')
}

export function removeFromCart(courseId: number) {
  return request.delete<null>(`/carts/${courseId}`)
}

export function clearCart() {
  return request.delete<null>('/carts')
}

/* ---------- 订单 ---------- */

/** 创建订单（后端雪花单号 + 15 分钟超时关单） */
export function placeOrder(data: PlaceOrderDTO) {
  return request.post<OrderVO>('/orders/placeOrder', data)
}

export interface OrderPageParams extends PageQuery {
  status?: number | ''
}

/** 订单分页 */
export function pageOrders(params: OrderPageParams) {
  return request.get<PageDTO<OrderVO>>('/orders/page', params as Record<string, unknown>)
}

/** 订单详情 */
export function getOrder(id: number) {
  return request.get<OrderVO>(`/orders/${id}`)
}

/** 取消订单（超时关单） */
export function cancelOrder(id: number) {
  return request.post<null>(`/orders/${id}/timeout`)
}

/**
 * 模拟支付：Mock 模式直接变更订单状态
 * 真实模式走收银台（GET /orders/pay/sign 获取支付签名后跳转第三方）
 */
export function mockPayOrder(id: number) {
  if (!IS_MOCK) {
    return Promise.reject(new Error('真实环境请通过收银台完成支付（GET /orders/pay/sign）'))
  }
  return request.post<null>(`/orders/pay/mock/${id}`)
}

/** 课程学习人数 */
export function enrollNum(courseId: number) {
  return request.get<number>('/order-details/enrollNum', { courseId })
}
