import { request } from './request'
import type {
  AdminOrderStatsVO,
  AdminOrderVO,
  AdminRefundVO,
  AdminUserCoursesVO,
  Id,
  PageDTO,
} from '@/types/api'

/**
 * 管理员端订单管理（zx-trade，路径前缀 /orders/admin/**）。
 * 后端类级 @RequireRole(STAFF)，学员/教师调用一律 403。
 */

export interface AdminOrderQuery {
  pageNo?: number
  pageSize?: number
  orderNo?: string
  userId?: Id
  /** 关键词：用户名 / 手机号 / 订单号 / 课程名 */
  keyword?: string
  /** 0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款 */
  status?: number
  courseId?: Id
  /** ISO-8601，如 2026-09-01T00:00:00 */
  beginTime?: string
  endTime?: string
  minAmount?: number
  maxAmount?: number
}

/** 订单分页（多条件筛选） */
export function adminOrderPage(params: AdminOrderQuery) {
  return request.get<PageDTO<AdminOrderVO>>('/orders/admin/page', params as Record<string, unknown>)
}

/** 订单详情 */
export function adminOrderDetail(id: Id) {
  return request.get<AdminOrderVO>(`/orders/admin/${id}`)
}

/** 修改订单状态 */
export function adminUpdateOrderStatus(id: Id, status: number) {
  return request.put<null>(`/orders/admin/${id}/status?status=${status}`)
}

/**
 * 删除订单（清理无用订单）。
 * 仅允许删除不贡献销售数据的订单：待支付(0)、已关闭(2)、已退款(4)；
 * 已支付(1) 与 退款中(3) 会被后端拒绝。逻辑删除，不影响销售额统计口径。
 */
export function adminDeleteOrder(id: Id) {
  return request.delete<null>(`/orders/admin/${id}`)
}

/** 退款审核 */
export function adminAuditRefund(refundId: Id, approved: boolean, remark?: string) {
  return request.put<null>('/orders/admin/refund/audit', { refundId, approved, remark })
}

/** 退款申请分页 */
export function adminRefundPage(params: { pageNo?: number; pageSize?: number; status?: number }) {
  return request.get<PageDTO<AdminRefundVO>>(
    '/orders/admin/refunds',
    params as Record<string, unknown>,
  )
}

/** 订单统计 */
export function adminOrderStats() {
  return request.get<AdminOrderStatsVO>('/orders/admin/statistics')
}

/**
 * 学员相关课程（退款审批辅助）：
 * 返回该学员全部订单课程 + 学习进度/时长 + 消费汇总。
 */
export function adminUserCourses(userId: Id) {
  return request.get<AdminUserCoursesVO>(`/orders/admin/users/${userId}/courses`)
}

/** 订单导出（CSV Blob） */
export function adminExportOrders(params: AdminOrderQuery) {
  return request.download('/orders/admin/export', params as Record<string, unknown>)
}
