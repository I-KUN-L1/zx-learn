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
  // 后端统一下发 'yyyy-MM-dd HH:mm:ss'（空格分隔）。Safari / 旧内核把这种非 ISO 串
  // 解析为 Invalid Date（页面显示 '-'），这里统一换成 'T' 分隔再交给 Date 解析。
  const normalized = typeof date === 'string' ? date.replace(' ', 'T') : date
  const d = new Date(normalized)
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

/** 订单状态映射（对齐后端订单状态机：前端契约 1/2/3/5/6） */
export const ORDER_STATUS = {
  PENDING: 1,
  PAID: 2,
  CLOSED: 3,
  FINISHED: 4,
  REFUNDING: 5,
  REFUNDED: 6,
} as const

export const ORDER_STATUS_TEXT: Record<number, string> = {
  1: '待支付',
  2: '已支付',
  3: '已关闭',
  4: '已完成',
  5: '退款中',
  6: '已退款',
}

export const ORDER_STATUS_TAG: Record<number, 'warning' | 'success' | 'info' | 'primary' | 'danger'> = {
  1: 'warning',
  2: 'success',
  3: 'info',
  4: 'primary',
  5: 'danger',
  6: 'danger',
}

export function orderStatusLabel(order: Pick<OrderVO, 'status'>): string {
  return ORDER_STATUS_TEXT[order.status] ?? '未知'
}

/** 课程状态映射 */
export const COURSE_STATUS_TEXT: Record<number, string> = {
  // ⚠ 库里的真实取值是 1-上架 / 0-下架（见 sql/init.sql 的 course.status 注释）。
  // 这里的 2 是历史遗留别名（部分旧数据/Mock 用 2 表示下架），保留以免旧数据变空白。
  0: '已下架',
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

/**
 * 手机号打码（仅展示层，后端返回的仍是完整号码，便于客服核身）。
 * 11 位手机号保留前 3 + 后 4：`13900000001` → `139****0001`。
 * 运营后台列表属于"可见面较大的页面"，最小化手机号明文暴露面。
 */
export function maskPhone(phone?: string | number | null): string {
  if (phone == null) return '-'
  const s = String(phone).trim()
  if (!s) return '-'
  if (s.length >= 7) return `${s.slice(0, 3)}****${s.slice(-4)}`
  if (s.length <= 2) return '*'.repeat(s.length)
  return `${'*'.repeat(s.length - 2)}${s.slice(-2)}`
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
