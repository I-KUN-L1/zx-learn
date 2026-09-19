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

/**
 * 主键 / 外键 id 类型。
 * <p>
 * 后端雪花算法生成的 id（订单、部分用户、优惠券等，约 19 位）超出 JavaScript
 * `Number.MAX_SAFE_INTEGER`，若按 number 传输会被浏览器浮点舍入，回传后端即"记录不存在"。
 * 后端（zx-common SafeLongSerializer）对超出安全范围的 Long 统一以字符串下发，
 * 自增 id 仍为数字，因此前端 id 统一按 `number | string` 处理，且不得参与数值运算。
 */
export type Id = number | string

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
  /** 角色（admin/teacher/student，Mock 或后端显式返回时使用，否则从前端解析 JWT） */
  role?: 'admin' | 'teacher' | 'student'
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
  /** 用户 id（部分账号为雪花 id，字符串下发，见 {@link Id}） */
  id: Id
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

/** 章节 CourseCatalogue（后端下发两级结构：章章节点带 sections） */
export interface CourseCatalogue {
  id: number
  name: string
  /** 媒资 id */
  mediaId?: number
  /** 视频地址（有媒资时由后端/媒资服务补全） */
  mediaUrl?: string
  /** 兼容字段：部分接口以 videoUrl 下发 */
  videoUrl?: string
  /** 时长（秒） */
  duration?: number
  /** 是否可试看 */
  trailer?: number
  /** 类型：1-章 2-小节 */
  chapterType?: number
  /** 父章 id */
  parentId?: number
  /** 顺序 */
  index?: number
  /** 讲义正文（Markdown） */
  content?: string
  /** 本节要点，"|" 分隔 */
  keyPoints?: string
  /** 学习资料地址 */
  attachmentUrl?: string
  /** 学习资料名称 */
  attachmentName?: string
  /** 子小节（仅章节节点持有） */
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
  /**
   * 课程状态：1 = 已上架，0 = 已下架（与 sql/init.sql 的 course.status 一致）。
   * 下架课程对学生/访客侧展示位一律不可见，仅管理端/教师端工作台可见。
   */
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
  /** 编辑步骤：1-基础信息 2-目录 3-视频 4-题目 */
  step?: number
  /** 草稿章节 */
  catalogueList?: { id?: number; name: string; sections?: { id?: number; name: string }[] }[]
}

/**
 * 课程草稿 CourseDraftVO（草稿箱列表项）。
 *
 * 注意：草稿在 **course_draft** 表，正式课程在 **course** 表，
 * 二者不是同一张表的不同 status。草稿箱必须调 `/courses/draft/page`。
 */
export interface CourseDraftVO {
  id: number
  /** 关联的正式课程 id（从未上架过则为 null） */
  courseId?: number | null
  name: string
  coverUrl?: string
  /** 价格（分） */
  price?: number
  categoryIdLv1?: number
  categoryIdLv2?: number
  categoryIdLv3?: number
  teacherId?: number | null
  /** 1 免费 0 收费 */
  free?: number
  description?: string
  /** 编辑步骤：1-基础信息 2-目录 3-视频 4-题目 */
  step?: number
  /** 0 草稿箱 / 1 已发布 */
  submitted?: number
  updateTime?: string
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
  courseId: number
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
  /** 所属小节（课程目录小节 id）；0 表示未绑定小节 */
  lessonId?: number
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
  /** 券模板 id（雪花 id 为字符串，见 {@link Id}） */
  id: Id
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
  /** 用户券行 id（下单时作为 userCouponId 回传） */
  id: Id
  userId: Id
  /** 券模板 id */
  couponId: Id
  couponName?: string
  discountValue?: number
  thresholdAmount?: number
  /** 1 未使用 2 已使用 3 已过期 */
  status: number
  createTime: string
}

/** 订单明细 OrderDetailVO */
export interface OrderDetailVO {
  id: Id
  orderId: Id
  courseId: Id
  courseName: string
  coverUrl?: string
  price: number
}

/** 订单 OrderVO */
export interface OrderVO {
  /** 订单 id（雪花，字符串下发，见 {@link Id}） */
  id: Id
  userId: Id
  /** 订单号（雪花） */
  orderNo: string
  totalAmount: number
  realAmount: number
  discountAmount: number
  couponId?: Id
  /** 1 待支付 2 已支付 3 已关闭 5 退款中 6 已退款 */
  status: number
  createTime: string
  payTime?: string
  details: OrderDetailVO[]
}

/** 下单请求（对齐后端 OrderFormDTO：单课程 + 可选优惠券） */
export interface PlaceOrderDTO {
  /** 课程 id（后端逐门课程生成一张订单） */
  courseId: Id
  /** 实付金额（分）；无券可省略（后端默认课程价），有券必须传优惠后金额 */
  totalFee?: number
  /** 优惠券模板 id */
  couponId?: Id
  /** 用户券 id（用户领取到的具体一张券） */
  userCouponId?: Id
}

/** 退款申请结果（POST /orders/{id}/refund） */
export interface RefundApplyResultVO {
  /** INSTANT 直接退款成功；AUDIT 转管理员审核 */
  mode: 'INSTANT' | 'AUDIT'
  refundId?: Id
  message: string
}

/** 管理端-学员相关课程（退款审批辅助） */
export interface AdminUserCourseVO {
  orderId: Id
  orderNo?: string
  /** 数据库状态：0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款 */
  orderStatus: number
  orderStatusText?: string
  courseId?: Id
  courseName?: string
  coursePrice?: number
  totalFee?: number
  deduction?: number
  createTime?: string
  payTime?: string
  /** 学习进度百分比 0~100 */
  progress?: number
  finished?: boolean
  /** 累计学习时长（秒） */
  learnDuration?: number
  lastLearnTime?: string
}

/** 管理端-学员课程概览 */
export interface AdminUserCoursesVO {
  userId: Id
  username?: string
  cellPhone?: string
  courseCount: number
  paidCount: number
  refundingCount: number
  refundedCount: number
  /** 累计实付金额（分） */
  totalPaid: number
  courses: AdminUserCourseVO[]
}

/** 考题 QuestionVO */
export interface QuestionVO {
  id: number
  courseId?: number
  /** 关联课程名称（后端 Feign 补全，失败时为「课程 #id」） */
  courseName?: string
  /** 归属教师 id */
  teacherId?: number
  name: string
  /** 1 单选 2 多选 3 判断 */
  type: number
  options?: string[]
  answer?: string
  difficulty?: number
  score?: number
  content?: string
  analysis?: string
  /** 0 草稿（学员不可见） 1 已发布（学员可见） */
  status?: number
  createTime?: string
}

/** 错题本条目 WrongQuestionVO */
export interface WrongQuestionVO {
  questionId: number
  questionName: string
  type: number
  difficulty?: number
  options?: string[]
  /** 正确答案 */
  correctAnswer?: string
  /** 我的作答 */
  myAnswer?: string
  analysis?: string
  courseId?: number
  courseName?: string
  /** 累计答错次数 */
  wrongCount: number
  lastWrongTime?: string
}

/** 题目维度答题统计（教师端） */
export interface QuestionStatVO {
  questionId: number
  questionName: string
  courseId?: number
  courseName?: string
  totalCount: number
  correctCount: number
  accuracy: number
}

/** 学员维度答题统计（教师端） */
export interface StudentAnswerStatVO {
  userId: number
  username?: string
  cellPhone?: string
  totalCount: number
  correctCount: number
  accuracy: number
  lastAnswerTime?: string
}

/** 教师端答题情况总览 */
export interface AnswerOverviewVO {
  totalRecords: number
  totalStudents: number
  totalQuestions: number
  accuracy: number
  questionStats: QuestionStatVO[]
  studentStats: StudentAnswerStatVO[]
}

/** 交卷后的单题判分结果（服务端判分回传） */
export interface SubmitResultVO {
  questionId: number
  questionName?: string
  userAnswer?: string
  correctAnswer?: string
  correct: boolean
  score?: number
  analysis?: string
}

/** 答题结果 QuestionResultVO */
export interface QuestionResultVO {
  id: number
  userId: number
  questionId: number
  questionName?: string
  /** 学员作答 */
  userAnswer?: string
  courseId?: number
  correct: boolean
  score?: number
  createTime: string
}

/** 管理员端订单行（含下单用户信息） */
export interface AdminOrderVO {
  /** 订单 id（雪花，字符串下发，见 {@link Id}） */
  id: Id
  orderNo: string
  userId: Id
  username?: string
  cellPhone?: string
  courseId: Id
  courseName: string
  coursePrice: number
  totalFee: number
  couponId?: Id
  deduction: number
  /** 数据库状态：0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款 */
  status: number
  /** 学员侧是否已删除该订单（1=学员已从"我的订单"移除，管理端仍保留记录） */
  userDeleted?: number
  payType?: number
  payTime?: string
  createTime: string
  updateTime?: string
}

/** 管理员端订单统计 */
export interface AdminOrderStatsVO {
  totalCount: number
  unpaidCount: number
  paidCount: number
  closedCount: number
  refundingCount: number
  refundedCount: number
  totalSales: number
}

/** 管理员端退款申请（审核工作台） */
export interface AdminRefundVO {
  /** 退款单 id */
  id: Id
  orderId: Id
  orderNo?: string
  courseName?: string
  userId: Id
  username?: string
  cellPhone?: string
  amount: number
  reason?: string
  /** 0 待审核 1 已通过 2 已拒绝 */
  status: number
  remark?: string
  createTime?: string
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

/* ==================== 个人中心 · 积分体系 ==================== */

/** 我的积分概况（GET /points/summary） */
export interface PointsSummaryVO {
  /** 当前积分总额 */
  points: number
  /** 本人排名（无积分时为 0） */
  rank: number
  /** 有积分记录的用户总数（排行榜分母） */
  totalUsers: number
  /** 今日新增积分 */
  todayPoints: number
  /** 近 7 日新增积分 */
  weekPoints: number
  /** 超越比例（0~100） */
  beatRate: number
  /** 积分明细条数 */
  recordCount: number
}

/** 排行榜条目（GET /points/rank） */
export interface PointsRankVO {
  rank: number
  userId: Id
  name: string
  points: number
  /** 是否为当前登录用户 */
  me: boolean
}

/** 积分明细条目（GET /points/records/page） */
export interface PointsRecordVO {
  id: Id
  /** 积分变动（正数=获得） */
  points: number
  /** 来源标识：LESSON/COURSE/QUIZ/SIGN/DISCUSSION/REPLY */
  source: string
  /** 来源中文文案 */
  sourceText: string
  description?: string
  refId?: string
  createTime?: string
}

/* ==================== 课程讨论 ==================== */

/** 讨论话题（GET /boards/page、GET /boards/{id}） */
export interface BoardVO {
  id: Id
  courseId: Id
  userId: Id
  userName?: string
  title: string
  content?: string
  replyCount: number
  /** 0 普通 / 1 置顶 */
  top: number
  createTime?: string
  /** 话题详情附带全部回复 */
  replies?: BoardReplyVO[]
}

/** 讨论回复 */
export interface BoardReplyVO {
  id: Id
  boardId: Id
  userId: Id
  userName?: string
  /** 被回复的楼层 id（0 = 直接回复话题） */
  parentId: number
  content: string
  createTime?: string
}

/** 小节学习进度（课程内容页左侧目录状态） */
export interface SectionProgress {
  lessonId: number
  progress: number
  finished: boolean
}
