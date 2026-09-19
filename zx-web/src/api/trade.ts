import { request } from './request'
import type { Id, OrderVO, PageDTO, PageQuery, PlaceOrderDTO, RefundApplyResultVO } from '@/types/api'

/**
 * 交易服务（zx-trade）
 * 统一走网关：/carts/**、/orders/**
 *
 * 说明：订单、用户等雪花 id 由后端以字符串下发（见 types/api.ts 的 Id），
 * 本模块所有 id 入参统一使用 Id 类型，避免前端数值运算造成精度丢失。
 */

/* ---------- 购物车 ---------- */
export function addToCart(courseId: Id) {
  return request.post<null>('/carts', { courseId })
}

/** 购物车条目（后端 Cart：courseName/coursePrice 为后端补全的课程快照） */
export interface CartItem {
  id: Id
  courseId: Id
  courseName: string | null
  coursePrice: number | null
  createTime: string
}

export function cartList() {
  return request.get<CartItem[]>('/carts')
}

/** 按课程 id 移除购物车条目（后端 DELETE /carts/course/{courseId}） */
export function removeFromCart(courseId: Id) {
  return request.delete<null>(`/carts/course/${courseId}`)
}

/** 清空购物车（后端兼容空请求体，清空当前用户） */
export function clearCart() {
  return request.delete<null>('/carts')
}

/* ---------- 订单 ---------- */

/**
 * 创建订单（后端雪花单号 + 15 分钟超时关单，返回订单 id）。
 * 返回值为字符串形态的雪花 id（超出 JS 安全整数范围），可直接用于支付/退款接口。
 */
export function placeOrder(data: PlaceOrderDTO) {
  return request.post<Id>('/orders/placeOrder', data)
}

/**
 * 当前学员已拥有课程 id 集合。
 * 口径 = 课程中心（我的课表）∪ 已支付订单：课表存在即拥有（含学习进度 0、
 * 退款后未清课表），已支付订单兜底开课延迟窗口。
 * 课程列表/详情据此展示「已拥有」并禁用购买入口，结算页据此过滤已购课程。
 */
export function boughtCourseIds() {
  return request.get<Id[]>('/orders/bought-course-ids')
}

/**
 * 免费课程 0 元开课（后端直接置已支付并发"支付成功"事件 → 学习中心写课表）。
 * 重复开课会被后端拦截（已购校验 + 唯一索引）。
 */
export function freeCourse(courseId: Id) {
  return request.post<Id>(`/orders/freeCourse/${courseId}`)
}

export interface OrderPageParams extends PageQuery {
  status?: number | ''
}

/** 订单分页 */
export function pageOrders(params: OrderPageParams) {
  return request.get<PageDTO<OrderVO>>('/orders/page', params as Record<string, unknown>)
}

/** 订单详情 */
export function getOrder(id: Id) {
  return request.get<OrderVO>(`/orders/${id}`)
}

/** 取消订单（超时关单） */
export function cancelOrder(id: Id) {
  return request.post<null>(`/orders/${id}/timeout`)
}

/**
 * 删除订单（软删除）。
 * 仅允许删除已终结的订单：已支付、已关闭、已退款；
 * 待支付（请先支付或取消）与退款中（等待审核）会被后端拒绝。
 * 删除只影响本人在「我的订单」的可见性，管理端仍保留该记录。
 */
export function deleteOrder(id: Id) {
  return request.delete<null>(`/orders/${id}`)
}

/**
 * 模拟支付：后端生成合法签名复用真实回调链路（流水幂等 + 支付成功事件）。
 * Mock 模式由前端适配器直接变更订单状态。
 */
export function mockPayOrder(id: Id) {
  return request.post<null>(`/orders/pay/mock/${id}`)
}

/**
 * 申请退款（分级策略）。
 * - mode=INSTANT：满足自动退款条件（未开始学习），已直接退款成功；
 * - mode=AUDIT：已生成退款单，订单转"退款中"，等待管理员审核。
 */
export function applyRefund(orderId: Id, reason?: string) {
  return request.post<RefundApplyResultVO>(`/orders/${orderId}/refund`, { reason })
}

/** 课程学习人数 */
export function enrollNum(courseId: Id) {
  return request.get<number>('/order-details/enrollNum', { courseId })
}
