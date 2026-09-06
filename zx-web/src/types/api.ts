/**
 * 与后端 DTO 对应的 TypeScript 类型定义
 * 命名以各服务 Knife4j 文档（http://localhost:{port}/doc.html）为准
 */

/** 统一响应结构 R<T>（后端真实字段为 msg，message 为兼容别名） */
export interface R<T = unknown> {
  code: number
  message?: string
  msg?: string
  data: T
}

/** 分页请求参数 PageQuery */
export interface PageQuery {
  pageNo?: number
  pageSize?: number
  sortBy?: string
  isAsc?: boolean
}

/** 分页响应 PageDTO<T> */
export interface PageDTO<T> {
  total: number
  pages: number
  list: T[]
}

/** 登录表单 LoginFormDTO */
export interface LoginFormDTO {
  cellPhone: string
  password: string
}

/** 登录结果 LoginResultVO（权限由后端 JWT role claim 承载，前端不存角色） */
export interface LoginResultVO {
  accessToken: string
  expireTime: number
  refreshToken: string
  userId: number
  username: string
  /** 首次登录标记（后端扩展字段，Mock 模式下使用） */
  firstLogin?: boolean
}

/** 首次改密 FirstChangePasswordDTO */
export interface FirstChangePasswordDTO {
  cellPhone: string
  oldPassword: string
  newPassword: string
}

/** 用户 UserVO（type 对齐后端：1员工/2学员/3教师） */
export interface UserVO {
  id: number
  username: string
  cellPhone: string
  type: number
  status: number
  createTime?: string
}

/** 课程分类 Category */
export interface Category {
  id: number
  name: string
  parentId: number
  sort: number
  status: number
}

/** 章节 CourseCatalogue */
export interface CourseCatalogue {
  id: number
  name: string
  mediaUrl?: string
  sections?: CourseCatalogue[]
}

/** 课程 CourseVO */
export interface CourseVO {
  id: number
  name: string
  coverUrl: string
  /** 价格（分） */
  price: number
  categoryIdLv1: number
  categoryIdLv2?: number
  categoryIdLv3?: number
  teacherId?: number
  /** 1 已上架 2 下架 3 已完结 */
  status: number
  /** 1 免费 0 收费 */
  free: number
  publishTimes?: number
  description?: string
  catalogues?: CourseCatalogue[]
  /** 扩展：学习人数（Mock/聚合字段） */
  enrollNum?: number
  score?: number
}

/** 课程表单 CourseFormDTO */
export interface CourseFormDTO {
  id?: number
  name: string
  coverUrl: string
  price: number
  categoryIdLv1: number
  categoryIdLv2?: number
  categoryIdLv3?: number
  free: number
  description?: string
  /** 草稿章节 */
  catalogueList?: { id?: number; name: string; sections?: { id?: number; name: string }[] }[]
}

/** AI 聊天事件 ChatEventVO（SSE） */
export interface ChatEventVO {
  /** START / DELTA / END */
  type: 'START' | 'DELTA' | 'END'
  /** 增量文本 */
  content: string
  /** 命中的 Agent */
  agent?: string
}

/** AI 会话 ChatSession */
export interface ChatSession {
  id: string
  title?: string
  userId?: number
  createTime?: string
}

/** AI 会话消息 RedisMessage */
export interface ChatMessage {
  type: 'USER' | 'AI'
  content: string
  agent?: string
  createTime?: string
}

/** 学习课表 LearningLessonVO */
export interface LearningLessonVO {
  id: number
  userId: number
  courseId: number
  courseName: string
  coverUrl: string
  /** 0 在学 1 已完成 2 有效期已过期 */
  status: number
  weekFreq: number | null
  createTime: string
  /** 扩展：进度百分比 */
  learnProgress?: number
}

/** 学习记录 LearningRecordVO */
export interface LearningRecordVO {
  id: number
  userId: number
  lessonId: number
  sectionId: number
  sectionName?: string
  courseName?: string
  /** 观看秒数 */
  moment: number
  finished: boolean
  updateTime: string
}

/** 签到记录 SignRecordVO */
export interface SignRecordVO {
  id: number
  userId: number
  signDate: string
}

/** 笔记 NoteVO */
export interface NoteVO {
  id: number
  userId: number
  courseId: number
  courseName?: string
  content: string
  createTime: string
}

/** 学情画像 InsightProfileVO */
export interface InsightProfileVO {
  userId: number
  totalDuration: number // 分钟
  completedRate: number // 百分比
  continuousDays: number
  /** 能力维度雷达图 */
  abilities: { name: string; value: number }[]
  /** 近 7 日学习时长趋势 */
  trends: { date: string; duration: number }[]
}

/** 学习路径推荐 */
export interface LearningPathVO {
  reason: string
  steps: { order: number; courseId: number; courseName: string; reason: string }[]
}

/** 优惠券 CouponVO */
export interface CouponVO {
  id: number
  name: string
  /** 折扣力度，如 85 表示 85 折 */
  discountValue: number
  /** 满减金额（分），0 表示无门槛 */
  thresholdAmount: number
  /** 1 通用券 2 秒杀券 */
  type: number
  /** 1 发放中 2 暂停发放 3 未开始 */
  status: number
  /** 发放总量 */
  totalNum: number
  /** 剩余数量 */
  remainNum: number
  issueBeginTime: string
  issueEndTime: string
  useBeginTime?: string
  useEndTime?: string
}

/** 用户优惠券 UserCouponVO */
export interface UserCouponVO {
  id: number
  userId: number
  couponId: number
  couponName?: string
  discountValue?: number
  thresholdAmount?: number
  /** 1 未使用 2 已使用 3 已过期 */
  status: number
  createTime: string
}

/** 订单明细 OrderDetailVO */
export interface OrderDetailVO {
  id: number
  orderId: number
  courseId: number
  courseName: string
  coverUrl?: string
  price: number
}

/** 订单 OrderVO */
export interface OrderVO {
  id: number
  userId: number
  /** 订单号（雪花） */
  orderNo: string
  totalAmount: number
  realAmount: number
  discountAmount: number
  couponId?: number
  /** 1 待支付 2 已支付 3 已关闭 4 已完成 */
  status: number
  createTime: string
  payTime?: string
  details: OrderDetailVO[]
}

/** 下单请求 */
export interface PlaceOrderDTO {
  courseIds: number[]
  couponId?: number
}

/** 考题 QuestionVO */
export interface QuestionVO {
  id: number
  courseId?: number
  name: string
  /** 1 单选 2 多选 3 判断 */
  type: number
  options?: string[]
  answer?: string
  difficulty?: number
  analysis?: string
}

/** 答题结果 QuestionResultVO */
export interface QuestionResultVO {
  id: number
  userId: number
  questionId: number
  answer: string
  correct: boolean
  createTime: string
}

/** 站内信 InboxVO */
export interface InboxVO {
  id: number
  title?: string
  content: string
  read?: boolean
  createTime: string
}

/** 数据看板 DashboardVO */
export interface DashboardVO {
  totalUsers: number
  totalOrders: number
  totalSales: number // 分
  totalCourses: number
  /** 近 7 日订单量 */
  orderTrend: { date: string; count: number; amount: number }[]
  /** 近 7 日活跃 */
  activeTrend: { date: string; count: number }[]
  /** 热门课程 TOP5 */
  hotCourses: { name: string; count: number }[]
}
