import type { OrderVO } from '@/types/api'

/** 分 → 元（展示字符串） */
export function formatPrice(price?: number | null): string {
  if (price == null) return '0'
  const yuan = price / 100
  return Number.isInteger(yuan) ? String(yuan) : yuan.toFixed(2)
}

/** 分 → 元（保留两位小数，用于金额展示） */
export function formatPriceFixed(price?: number | null): string {
  if (price == null) return '0.00'
  return (price / 100).toFixed(2)
}

/** 秒 → mm:ss */
export function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** 分钟 → x小时x分钟 */
export function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
}

/** 日期格式化 */
export function formatDate(date: string | number | Date, fmt = 'YYYY-MM-DD HH:mm'): string {
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return '-'
  const map: Record<string, number> = {
    'Y+': d.getFullYear(),
    'M+': d.getMonth() + 1,
    'D+': d.getDate(),
    'H+': d.getHours(),
    'm+': d.getMinutes(),
    's+': d.getSeconds(),
  }
  let result = fmt
  for (const [k, v] of Object.entries(map)) {
    const reg = new RegExp(k)
    if (reg.test(result)) {
      result = result.replace(reg, (match) => String(v).padStart(match.length, '0'))
    }
  }
  return result
}

/** 订单状态映射（对齐后端订单状态机） */
export const ORDER_STATUS = {
  PENDING: 1,
  PAID: 2,
  CLOSED: 3,
  FINISHED: 4,
} as const

export const ORDER_STATUS_TEXT: Record<number, string> = {
  1: '待支付',
  2: '已支付',
  3: '已关闭',
  4: '已完成',
}

export const ORDER_STATUS_TAG: Record<number, 'warning' | 'success' | 'info' | 'primary'> = {
  1: 'warning',
  2: 'success',
  3: 'info',
  4: 'primary',
}

export function orderStatusLabel(order: Pick<OrderVO, 'status'>): string {
  return ORDER_STATUS_TEXT[order.status] ?? '未知'
}

/** 课程状态映射 */
export const COURSE_STATUS_TEXT: Record<number, string> = {
  1: '已上架',
  2: '已下架',
  3: '已完结',
}

/** 用户类型 */
/** user.type 文案（对齐后端 sql/init.sql：类型 1员工/2学员/3教师） */
export const USER_TYPE_TEXT: Record<number, string> = {
  1: '员工',
  2: '学员',
  3: '教师',
}

/** 剩余 x 秒 → 倒计时文本 */
export function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** 防抖 */
export function debounce<T extends (...args: never[]) => void>(fn: T, delay = 300) {
  let timer: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }
}
