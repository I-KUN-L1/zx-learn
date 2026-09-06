import type { AxiosAdapter, AxiosResponse } from 'axios'
import type { R } from '@/types/api'
import { R_OK, R_ERR } from './helper'
import { createMockSession, mockSessions, mockSessionMessages } from './chat'
import {
  mockCategories,
  mockCourses,
  mockCoupons,
  mockDashboard,
  mockInboxes,
  mockLearningPath,
  mockLearningRecords,
  mockLessons,
  mockNotes,
  mockOrders,
  mockProfile,
  mockQuestions,
  mockTemplates,
  mockUserCoupons,
  mockUsers,
} from './data'
import type { CouponVO, CourseVO, OrderVO, UserCouponVO } from '@/types/api'

const FIRST_CHANGED_KEY = 'zx_mock_first_changed'

function delay(ms = 240 + Math.random() * 260) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

type MockCtx = {
  params: Record<string, unknown>
  data: Record<string, unknown>
  path: string[]
}
type MockHandler = (ctx: MockCtx) => unknown

interface MockRoute {
  method: string
  pattern: RegExp
  handler: MockHandler
}

/* ========================= 内存态数据 ========================= */
const cart: { courseId: number; courseName: string; price: number }[] = []
let orderSeq = 2000
const coupons: CouponVO[] = mockCoupons.map((c) => ({ ...c }))
const userCoupons: UserCouponVO[] = mockUserCoupons.map((c) => ({ ...c }))
const orders: OrderVO[] = mockOrders.map((o) => ({ ...o, details: o.details.map((d) => ({ ...d })) }))
const inboxes = mockInboxes.map((i) => ({ ...i }))
const notes = mockNotes.map((n) => ({ ...n }))
const lessons = mockLessons.map((l) => ({ ...l }))
const users = mockUsers.map((u) => ({ ...u }))

/* ========================= 路由表 ========================= */
const routes: MockRoute[] = [
  /* ---------- 认证 ---------- */
  {
    method: 'post',
    pattern: /^\/accounts\/admin\/login$/,
    handler: ({ data }) => {
      const cellPhone = String(data.cellPhone ?? '')
      const password = String(data.password ?? '')
      if (!cellPhone || !password) return R_ERR(400, '手机号或密码不能为空')
      if (cellPhone !== '13800000001' || password.length < 6) return R_ERR(401, '用户名或密码错误')
      const firstLogin = password === 'admin123' && !localStorage.getItem(FIRST_CHANGED_KEY)
      return {
        accessToken: `mock-admin-token-${Date.now()}`,
        expireTime: Date.now() + 30 * 60 * 1000,
        refreshToken: `mock-admin-refresh-${Date.now()}`,
        userId: 1,
        username: '管理员小知',
        firstLogin,
      }
    },
  },
  {
    method: 'post',
    pattern: /^\/accounts\/login$/,
    handler: ({ data }) => {
      const cellPhone = String(data.cellPhone ?? '')
      const password = String(data.password ?? '')
      if (!cellPhone || !password) return R_ERR(400, '手机号或密码不能为空')
      return {
        accessToken: `mock-student-token-${Date.now()}`,
        expireTime: Date.now() + 30 * 60 * 1000,
        refreshToken: `mock-student-refresh-${Date.now()}`,
        userId: 1,
        username: '知行学员',
      }
    },
  },
  {
    method: 'post',
    pattern: /^\/accounts\/password\/first-change$/,
    handler: ({ data }) => {
      if (String(data.oldPassword ?? '') === String(data.newPassword ?? '')) {
        return R_ERR(400, '新密码不能与旧密码相同')
      }
      localStorage.setItem(FIRST_CHANGED_KEY, '1')
      return null
    },
  },
  { method: 'post', pattern: /^\/accounts\/logout$/, handler: () => null },
  {
    method: 'get',
    pattern: /^\/accounts\/refresh$/,
    handler: () => ({ accessToken: `mock-access-${Date.now()}` }),
  },
  {
    method: 'get',
    pattern: /^\/users\/me$/,
    handler: () => ({ ...users[0] }),
  },
  {
    method: 'get',
    pattern: /^\/users\/page$/,
    handler: ({ params }) => {
      const pageNo = Number(params.pageNo ?? 1)
      const pageSize = Number(params.pageSize ?? 10)
      const list = users.slice((pageNo - 1) * pageSize, pageNo * pageSize)
      return { total: users.length, pages: Math.ceil(users.length / pageSize), list }
    },
  },
  {
    method: 'put',
    pattern: /^\/users\/(\d+)\/password\/default$/,
    handler: () => null,
  },

  /* ---------- 课程 ---------- */
  { method: 'get', pattern: /^\/categorys\/all$/, handler: () => mockCategories },
  {
    method: 'get',
    pattern: /^\/courses\/page$/,
    handler: ({ params }) => {
      const pageNo = Number(params.pageNo ?? 1)
      const pageSize = Number(params.pageSize ?? 10)
      const name = params.name ? String(params.name).toLowerCase() : ''
      const status = params.status != null && params.status !== '' ? Number(params.status) : null
      let list = mockCourses.filter(
        (c) => (!name || c.name.toLowerCase().includes(name)) && (status == null || c.status === status)
      )
      const sortBy = params.sortBy as string | undefined
      if (sortBy === 'enrollNum') {
        list = [...list].sort((a, b) =>
          params.isAsc === 'true' || params.isAsc === true ? a.enrollNum! - b.enrollNum! : b.enrollNum! - a.enrollNum!
        )
      } else if (sortBy === 'price') {
        list = [...list].sort((a, b) => (params.isAsc === 'true' || params.isAsc === true ? a.price - b.price : b.price - a.price))
      }
      return {
        total: list.length,
        pages: Math.ceil(list.length / pageSize),
        list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize),
      }
    },
  },
  {
    method: 'get',
    pattern: /^\/courses\/(\d+)$/,
    handler: ({ path }) => {
      const course = mockCourses.find((c) => c.id === Number(path[0]))
      if (!course) return R_ERR(404, '课程不存在')
      return { ...course, enrollNum: course.enrollNum ?? 0 }
    },
  },
  {
    method: 'get',
    pattern: /^\/courses\/checkName$/,
    handler: ({ params }) => {
      const name = String(params.name ?? '')
      if (mockCourses.some((c) => c.name === name)) return R_ERR(400, '课程名称已存在')
      return null
    },
  },
  {
    method: 'post',
    pattern: /^\/courses\/baseInfo\/save$/,
    handler: ({ data }) => {
      const id = data.id ? Number(data.id) : 1000 + Math.floor(Math.random() * 9000)
      const existing = mockCourses.findIndex((c) => c.id === Number(data.id))
      const course: CourseVO = {
        id,
        name: String(data.name),
        coverUrl:
          (data.coverUrl as string) ||
          'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=online%20course%20default%20cover%2C%20books%20and%20graduation%20cap%2C%20indigo%20flat%20design&image_size=landscape_4_3',
        price: Number(data.price ?? 0),
        categoryIdLv1: Number(data.categoryIdLv1 ?? 1),
        categoryIdLv2: data.categoryIdLv2 ? Number(data.categoryIdLv2) : undefined,
        teacherId: 1,
        status: 2,
        free: Number(data.free ?? 0),
        publishTimes: existing >= 0 ? mockCourses[existing].publishTimes : 0,
        description: (data.description as string) ?? '',
        enrollNum: 0,
        score: 0,
        catalogues: [],
      }
      if (existing >= 0) mockCourses[existing] = course
      else mockCourses.push(course)
      return id
    },
  },
  {
    method: 'post',
    pattern: /^\/courses\/upShelf$/,
    handler: ({ data }) => {
      const c = mockCourses.find((x) => x.id === Number(data.id))
      if (!c) return R_ERR(404, '课程不存在')
      if (!c.name || c.price == null) return R_ERR(400, '课程信息不完整，无法上架')
      if (!c.catalogues?.length) return R_ERR(400, '请先完善章节目录后再上架')
      c.status = 1
      c.publishTimes = (c.publishTimes ?? 0) + 1
      return null
    },
  },
  {
    method: 'post',
    pattern: /^\/courses\/downShelf$/,
    handler: ({ data }) => {
      const c = mockCourses.find((x) => x.id === Number(data.id))
      if (c) c.status = 2
      return null
    },
  },
  {
    method: 'delete',
    pattern: /^\/courses\/delete\/(\d+)$/,
    handler: ({ path }) => {
      const idx = mockCourses.findIndex((c) => c.id === Number(path[0]))
      if (idx >= 0) mockCourses.splice(idx, 1)
      return null
    },
  },
  {
    method: 'get',
    pattern: /^\/courses\/baseInfo\/(\d+)$/,
    handler: ({ path }) => mockCourses.find((c) => c.id === Number(path[0])) ?? R_ERR(404, '课程不存在'),
  },

  /* ---------- AI ---------- */
  { method: 'post', pattern: /^\/chat\/stop$/, handler: () => null },
  {
    method: 'post',
    pattern: /^\/chat\/text$/,
    handler: ({ data }) => {
      const text = mockAnswerText(String(data.question ?? ''))
      const sid = String(data.sessionId ?? 's1')
      ;(mockSessionMessages[sid] ??= []).push({ type: 'USER', content: String(data.question ?? '') })
      mockSessionMessages[sid].push({ type: 'AI', content: text, agent: 'ZxAssistant' })
      return text
    },
  },
  { method: 'get', pattern: /^\/chat\/templates$/, handler: () => mockTemplates },
  {
    method: 'post',
    pattern: /^\/session$/,
    handler: () => createMockSession(),
  },
  { method: 'get', pattern: /^\/session\/hot$/, handler: () => Object.values(mockTemplates) },
  { method: 'get', pattern: /^\/session\/history$/, handler: () => mockSessions },
  {
    method: 'get',
    pattern: /^\/session\/([^/]+)$/,
    handler: ({ path }) => mockSessionMessages[path[0]] ?? [],
  },
  {
    method: 'delete',
    pattern: /^\/session\/history$/,
    handler: ({ data }) => {
      const idx = mockSessions.findIndex((s) => s.id === String(data.sessionId))
      if (idx >= 0) mockSessions.splice(idx, 1)
      delete mockSessionMessages[String(data.sessionId)]
      return null
    },
  },
  {
    method: 'put',
    pattern: /^\/session\/history$/,
    handler: ({ data }) => {
      const s = mockSessions.find((x) => x.id === String(data.sessionId))
      if (s) s.title = String(data.title)
      return null
    },
  },

  /* ---------- 学习中心 ---------- */
  {
    method: 'get',
    pattern: /^\/lessons\/page$/,
    handler: ({ params }) => {
      const pageNo = Number(params.pageNo ?? 1)
      const pageSize = Number(params.pageSize ?? 10)
      const status = params.status != null && params.status !== '' ? Number(params.status) : null
      const list = lessons.filter((l) => status == null || l.status === status)
      return { total: list.length, pages: Math.ceil(list.length / pageSize), list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize) }
    },
  },
  {
    method: 'get',
    pattern: /^\/lessons\/now$/,
    handler: () => lessons.find((l) => l.status === 0) ?? null,
  },
  {
    method: 'post',
    pattern: /^\/learning-records\/progress$/,
    handler: () => null,
  },
  { method: 'get', pattern: /^\/learning-records\/users\/\d+\/all$/, handler: () => mockLearningRecords },
  { method: 'post', pattern: /^\/sign-ins$/, handler: () => null },
  { method: 'get', pattern: /^\/sign-ins\/today$/, handler: () => ({ signed: mockSignedToday() }) },
  { method: 'get', pattern: /^\/sign-ins$/, handler: () => mockSignDates() },
  {
    method: 'post',
    pattern: /^\/notes$/,
    handler: ({ data }) => {
      const n = {
        id: notes.length + 1,
        userId: 1,
        courseId: Number(data.courseId ?? 0),
        courseName: String(data.courseName ?? ''),
        content: String(data.content ?? ''),
        createTime: new Date().toLocaleString('zh-CN', { hour12: false }),
      }
      notes.unshift(n)
      return n
    },
  },
  {
    method: 'get',
    pattern: /^\/notes\/page$/,
    handler: ({ params }) => {
      const pageNo = Number(params.pageNo ?? 1)
      const pageSize = Number(params.pageSize ?? 10)
      return { total: notes.length, pages: Math.ceil(notes.length / pageSize), list: notes.slice((pageNo - 1) * pageSize, pageNo * pageSize) }
    },
  },
  {
    method: 'delete',
    pattern: /^\/notes\/(\d+)$/,
    handler: ({ path }) => {
      const idx = notes.findIndex((n) => n.id === Number(path[0]))
      if (idx >= 0) notes.splice(idx, 1)
      return null
    },
  },

  /* ---------- 学情 ---------- */
  { method: 'get', pattern: /^\/insight\/profiles\/mine$/, handler: () => mockProfile },
  { method: 'get', pattern: /^\/insight\/learning-path$/, handler: () => mockLearningPath },
  {
    method: 'get',
    pattern: /^\/insight\/reports\/latest$/,
    handler: () => ({
      id: 1,
      userId: 1,
      content:
        '本周你共学习 8.6 小时，完成率 72%，连续打卡 12 天。「Java 基础」与「框架应用」表现优秀，但「算法能力」维度偏弱（48 分），建议每天抽出 30 分钟练习算法题。结合学习路径，推荐优先完成《MySQL 8 性能优化与索引设计》。',
      createTime: '2026-09-05 06:00:00',
    }),
  },
  { method: 'get', pattern: /^\/insight\/dashboard$/, handler: () => mockDashboard },

  /* ---------- 交易 ---------- */
  {
    method: 'post',
    pattern: /^\/carts$/,
    handler: ({ data }) => {
      const c = mockCourses.find((x) => x.id === Number(data.courseId))
      if (!c) return R_ERR(404, '课程不存在')
      if (cart.some((x) => x.courseId === c.id)) return R_ERR(400, '该课程已在购物车中')
      cart.push({ courseId: c.id, courseName: c.name, price: c.price })
      return null
    },
  },
  { method: 'get', pattern: /^\/carts$/, handler: () => cart },
  {
    method: 'delete',
    pattern: /^\/carts\/(\d+)$/,
    handler: ({ path }) => {
      const idx = cart.findIndex((x) => x.courseId === Number(path[0]))
      if (idx >= 0) cart.splice(idx, 1)
      return null
    },
  },
  {
    method: 'delete',
    pattern: /^\/carts$/,
    handler: () => {
      cart.length = 0
      return null
    },
  },
  {
    method: 'post',
    pattern: /^\/orders\/placeOrder$/,
    handler: ({ data }) => {
      const ids = (data.courseIds as number[]) ?? []
      if (!ids.length) return R_ERR(400, '请选择要购买的课程')
      const details = ids.map((cid, i) => {
        const c = mockCourses.find((x) => x.id === cid)!
        return { id: orderSeq * 10 + i, orderId: orderSeq, courseId: c.id, courseName: c.name, coverUrl: c.coverUrl, price: c.price }
      })
      const totalAmount = details.reduce((s, d) => s + d.price, 0)
      let discountAmount = 0
      let couponId: number | undefined
      if (data.couponId) {
        couponId = Number(data.couponId)
        const uc = userCoupons.find((x) => x.id === couponId && x.status === 1)
        const cp = coupons.find((x) => x.id === uc?.couponId)
        if (uc && cp && totalAmount >= cp.thresholdAmount) {
          discountAmount = Math.round((totalAmount * cp.discountValue) / 10000)
          if (cp.type === 1) discountAmount = cp.discountValue // 满减券直接减固定金额
          uc.status = 2
        }
      }
      const order: OrderVO = {
        id: orderSeq++,
        userId: 1,
        orderNo: `179${Date.now()}${orderSeq}`,
        totalAmount,
        realAmount: totalAmount - discountAmount,
        discountAmount,
        couponId,
        status: 1,
        createTime: new Date().toLocaleString('zh-CN', { hour12: false }),
        details,
      }
      orders.unshift(order)
      for (const cid of ids) {
        const idx = cart.findIndex((x) => x.courseId === cid)
        if (idx >= 0) cart.splice(idx, 1)
      }
      return order
    },
  },
  {
    method: 'get',
    pattern: /^\/orders\/page$/,
    handler: ({ params }) => {
      const pageNo = Number(params.pageNo ?? 1)
      const pageSize = Number(params.pageSize ?? 10)
      const status = params.status != null && params.status !== '' ? Number(params.status) : null
      const list = orders.filter((o) => status == null || o.status === status)
      return { total: list.length, pages: Math.ceil(list.length / pageSize), list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize) }
    },
  },
  {
    method: 'post',
    pattern: /^\/orders\/(\d+)\/timeout$/,
    handler: ({ path }) => {
      const o = orders.find((x) => x.id === Number(path[0]))
      if (o && o.status === 1) o.status = 3
      return null
    },
  },
  {
    method: 'post',
    pattern: /^\/orders\/pay\/mock\/(\d+)$/,
    handler: ({ path }) => {
      const o = orders.find((x) => x.id === Number(path[0]))
      if (o && o.status === 1) {
        o.status = 2
        o.payTime = new Date().toLocaleString('zh-CN', { hour12: false })
      }
      return null
    },
  },
  {
    method: 'get',
    pattern: /^\/order-details\/enrollNum$/,
    handler: ({ params }) => mockCourses.find((c) => c.id === Number(params.courseId))?.enrollNum ?? 0,
  },

  /* ---------- 优惠 ---------- */
  {
    method: 'get',
    pattern: /^\/coupons\/page$/,
    handler: ({ params }) => {
      const pageNo = Number(params.pageNo ?? 1)
      const pageSize = Number(params.pageSize ?? 10)
      const type = params.type != null && params.type !== '' ? Number(params.type) : null
      const list = coupons.filter((c) => type == null || c.type === type)
      return { total: list.length, pages: Math.ceil(list.length / pageSize), list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize) }
    },
  },
  { method: 'get', pattern: /^\/user-coupons$/, handler: () => userCoupons },
  {
    method: 'post',
    pattern: /^\/user-coupons\/claim$/,
    handler: ({ data }) => {
      const c = coupons.find((x) => x.id === Number(data.couponId))
      if (!c) return R_ERR(404, '优惠券不存在')
      if (c.status === 3) return R_ERR(400, '活动尚未开始')
      if (c.status === 2) return R_ERR(400, '发放已暂停')
      if (c.remainNum <= 0) return R_ERR(400, '已抢光啦')
      c.remainNum -= 1
      userCoupons.push({ id: userCoupons.length + 1, userId: 1, couponId: c.id, couponName: c.name, discountValue: c.discountValue, thresholdAmount: c.thresholdAmount, status: 1, createTime: new Date().toLocaleString('zh-CN', { hour12: false }) })
      return null
    },
  },
  {
    method: 'post',
    pattern: /^\/user-coupons\/seckill\/(\d+)$/,
    handler: ({ path }) => {
      const c = coupons.find((x) => x.id === Number(path[0]))
      if (!c) return R_ERR(404, '优惠券不存在')
      if (c.status === 3) return R_ERR(400, '秒杀尚未开始')
      if (c.remainNum <= 0) return R_ERR(400, '已抢光啦')
      c.remainNum -= 1
      userCoupons.push({ id: userCoupons.length + 1, userId: 1, couponId: c.id, couponName: c.name, discountValue: c.discountValue, thresholdAmount: c.thresholdAmount, status: 1, createTime: new Date().toLocaleString('zh-CN', { hour12: false }) })
      return null
    },
  },
  {
    method: 'get',
    pattern: /^\/user-coupons\/seckill\/(\d+)\/result$/,
    handler: () => ({ success: true, orderId: null }),
  },

  /* ---------- 考试 ---------- */
  { method: 'get', pattern: /^\/questions\/list$/, handler: () => mockQuestions },
  { method: 'post', pattern: /^\/question-results$/, handler: () => null },

  /* ---------- 消息 ---------- */
  {
    method: 'get',
    pattern: /^\/inboxes$/,
    handler: () => inboxes,
  },
  {
    method: 'post',
    pattern: /^\/inboxes\/read$/,
    handler: ({ data }) => {
      const m = inboxes.find((x) => x.id === Number(data.id))
      if (m) m.read = true
      return null
    },
  },
  {
    method: 'post',
    pattern: /^\/inboxes\/read-all$/,
    handler: () => {
      inboxes.forEach((i) => (i.read = true))
      return null
    },
  },
]

/* ========================= 工具 ========================= */
function mockSignedToday(): boolean {
  return localStorage.getItem('zx_mock_signed_today') === new Date().toDateString()
}

export function mockSignToday() {
  localStorage.setItem('zx_mock_signed_today', new Date().toDateString())
}

function mockSignDates(): string[] {
  const stored = localStorage.getItem('zx_mock_sign_dates')
  const dates: string[] = stored ? JSON.parse(stored) : ['2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04', '2026-09-05']
  const today = new Date().toISOString().slice(0, 10)
  if (mockSignedToday() && !dates.includes(today)) dates.push(today)
  return dates
}

export function mockAnswerText(question: string): string {
  const q = question.toLowerCase()
  if (q.includes('推荐') || q.includes('课程') || q.includes('学什么')) {
    return '根据你的学习画像（Java 基础扎实、正在进阶并发与框架），为你推荐以下课程：\n\n1. [Java 21 核心技术：从入门到精通](/courses/1) —— 系统学习虚拟线程、JVM 调优\n2. [Spring Boot 3 全栈开发实战](/courses/2) —— 企业级项目驱动\n\n建议先从并发编程章节切入，配合每周 4 次学习计划效果更佳。'
  }
  if (q.includes('计划') || q.includes('安排')) {
    return '推荐使用「学习中心」的课表与计划功能：\n\n1. 在课表中选择课程并设定**每周频率**\n2. 每日完成**签到打卡**保持连续性\n3. 结合「学情报告」动态调整节奏\n\n告诉我你的目标课程，我可以帮你拆解为周计划。'
  }
  if (q.includes('虚拟线程') || q.includes('java') || q.includes('并发')) {
    return '虚拟线程是 Java 21 的正式特性（JEP 444）：\n\n- **调度主体**：由 JVM 调度，挂载在载体线程（ForkJoinPool）上\n- **成本**：创建成本极低，可支撑百万级并发\n- **适用**：IO 密集型任务收益最大\n\n```java\ntry (var executor = Executors.newVirtualThreadPerTaskExecutor()) {\n    executor.submit(() -> fetchRemoteData());\n}\n```\n\n注意：`synchronized` 块内会钉住（pin）载体线程，热点路径建议改用 `ReentrantLock`。'
  }
  return '好的，我理解你的问题。作为你的 AI 助教，我可以：\n\n- 推荐适合你的课程\n- 解答技术问题（Java / Spring / MySQL / Redis 等）\n- 制定学习计划\n- 分析学情报告\n\n你可以换个更具体的问法，例如："帮我推荐一门 Spring Boot 课程"。'
}

/** Mock Adapter：匹配路由表并返回 R<T> 结构 */
export const mockAdapter: AxiosAdapter = async (config) => {
  await delay()
  const method = (config.method ?? 'get').toLowerCase()
  let url = config.url ?? ''
  if (config.baseURL && url.startsWith(config.baseURL)) {
    url = url.slice(config.baseURL.length)
  }
  for (const route of routes) {
    if (route.method !== method) continue
    const m = route.pattern.exec(url)
    if (m) {
      const data = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const result = route.handler({ params: (config.params ?? {}) as Record<string, unknown>, data, path: m.slice(1) })
      const body =
        typeof result === 'object' && result !== null && 'code' in result
          ? (result as R) // handler 已返回完整 R 结构（错误场景）
          : R_OK(result)
      const response: AxiosResponse = {
        data: body,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
      return response
    }
  }
  const response: AxiosResponse = {
    data: R_ERR(404, `Mock 接口未实现：${method.toUpperCase()} ${url}`),
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
  return response
}
