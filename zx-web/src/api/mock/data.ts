import type {
  Category,
  CouponVO,
  CourseVO,
  DashboardVO,
  InboxVO,
  InsightProfileVO,
  LearningLessonVO,
  LearningRecordVO,
  LearningPathVO,
  NoteVO,
  OrderVO,
  QuestionVO,
  UserVO,
  ChatSession,
  ChatMessage,
  UserCouponVO,
} from '@/types/api'

/** 封面图（SDXL 文生图） */
export function cover(prompt: string): string {
  return `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(
    prompt
  )}&image_size=landscape_4_3`
}

/** 课程分类（两级） */
export const mockCategories: Category[] = [
  { id: 1, name: '后端开发', parentId: 0, sort: 1, status: 1 },
  { id: 2, name: '前端开发', parentId: 0, sort: 2, status: 1 },
  { id: 3, name: '人工智能', parentId: 0, sort: 3, status: 1 },
  { id: 4, name: '数据库', parentId: 0, sort: 4, status: 1 },
  { id: 5, name: '云计算与运维', parentId: 0, sort: 5, status: 1 },
  { id: 6, name: '软件工程', parentId: 0, sort: 6, status: 1 },
  { id: 101, name: 'Java 语言', parentId: 1, sort: 1, status: 1 },
  { id: 102, name: 'Spring 生态', parentId: 1, sort: 2, status: 1 },
  { id: 103, name: '微服务架构', parentId: 1, sort: 3, status: 1 },
  { id: 201, name: 'Vue3 实战', parentId: 2, sort: 1, status: 1 },
  { id: 202, name: 'TypeScript', parentId: 2, sort: 2, status: 1 },
  { id: 301, name: '大模型应用', parentId: 3, sort: 1, status: 1 },
  { id: 302, name: '机器学习基础', parentId: 3, sort: 2, status: 1 },
  { id: 401, name: 'MySQL', parentId: 4, sort: 1, status: 1 },
  { id: 402, name: 'Redis', parentId: 4, sort: 2, status: 1 },
  { id: 501, name: 'Docker 与 K8s', parentId: 5, sort: 1, status: 1 },
  { id: 601, name: '面试与职业发展', parentId: 6, sort: 1, status: 1 },
]

/** 课程列表 */
export const mockCourses: CourseVO[] = [
  {
    id: 1,
    name: 'Java 21 核心技术：从入门到精通',
    coverUrl: cover('online course cover, Java programming language, dark blue tech style, coffee cup and code, modern flat design'),
    price: 29900,
    categoryIdLv1: 1,
    categoryIdLv2: 101,
    teacherId: 1,
    status: 1,
    free: 0,
    publishTimes: 3,
    enrollNum: 12890,
    score: 4.9,
    description:
      '系统掌握 Java 21 核心语法、集合、并发编程、JVM 调优。课程包含虚拟线程、Record、Sealed Classes 等新特性实战，带你打通 Java 基础到高级进阶之路。',
    catalogues: [
      {
        id: 11,
        name: '第一章 Java 基础强化',
        sections: [
          { id: 111, name: '1.1 开发环境与工具链' },
          { id: 112, name: '1.2 面向对象深度剖析' },
          { id: 113, name: '1.3 集合框架源码解读' },
        ],
      },
      {
        id: 12,
        name: '第二章 并发编程',
        sections: [
          { id: 121, name: '2.1 线程与线程池' },
          { id: 122, name: '2.2 虚拟线程实战' },
          { id: 123, name: '2.3 锁与并发容器' },
        ],
      },
      {
        id: 13,
        name: '第三章 JVM 内存与调优',
        sections: [
          { id: 131, name: '3.1 类加载机制' },
          { id: 132, name: '3.2 GC 算法与收集器' },
        ],
      },
    ],
  },
  {
    id: 2,
    name: 'Spring Boot 3 全栈开发实战',
    coverUrl: cover('online course cover, Spring Boot framework, green leaf logo, enterprise backend development, modern flat illustration'),
    price: 39900,
    categoryIdLv1: 1,
    categoryIdLv2: 102,
    teacherId: 1,
    status: 1,
    free: 0,
    publishTimes: 2,
    enrollNum: 9860,
    score: 4.8,
    description:
      '以企业级项目为驱动，深入 Spring Boot 3 自动装配原理、Web 开发、数据访问、缓存、消息队列与单元测试，一站式掌握主流后端开发技能。',
    catalogues: [
      {
        id: 21,
        name: '第一章 快速起步',
        sections: [
          { id: 211, name: '1.1 Spring Boot 3 新特性' },
          { id: 212, name: '1.2 依赖注入与自动装配' },
        ],
      },
      {
        id: 22,
        name: '第二章 数据访问与缓存',
        sections: [
          { id: 221, name: '2.1 MyBatis-Plus 整合' },
          { id: 222, name: '2.2 Redis 缓存设计' },
        ],
      },
    ],
  },
  {
    id: 3,
    name: 'Spring Cloud Alibaba 微服务架构',
    coverUrl: cover('online course cover, microservices architecture diagram, cloud native, blue purple gradient, isometric illustration'),
    price: 49900,
    categoryIdLv1: 1,
    categoryIdLv2: 103,
    teacherId: 2,
    status: 1,
    free: 0,
    publishTimes: 1,
    enrollNum: 6520,
    score: 4.9,
    description:
      '基于 Nacos / Sentinel / RocketMQ / Seata 构建高可用微服务，涵盖服务注册发现、配置中心、分布式事务、网关鉴权与全链路压测。',
    catalogues: [
      {
        id: 31,
        name: '第一章 微服务全景',
        sections: [
          { id: 311, name: '1.1 单体到微服务演进' },
          { id: 312, name: '1.2 服务拆分方法论' },
        ],
      },
      {
        id: 32,
        name: '第二章 核心组件',
        sections: [
          { id: 321, name: '2.1 Nacos 注册与配置' },
          { id: 322, name: '2.2 RocketMQ 异步解耦' },
        ],
      },
    ],
  },
  {
    id: 4,
    name: 'Vue3 + TypeScript 企业级前端工程',
    coverUrl: cover('online course cover, Vue.js framework, emerald green code editor, frontend development, clean modern design'),
    price: 34900,
    categoryIdLv1: 2,
    categoryIdLv2: 201,
    teacherId: 2,
    status: 1,
    free: 0,
    publishTimes: 2,
    enrollNum: 8320,
    score: 4.7,
    description:
      'Composition API、Pinia 状态管理、Vue Router、Vite 工程化、Element Plus 组件库，从零构建大型 SPA 项目。',
    catalogues: [
      {
        id: 41,
        name: '第一章 Vue3 核心特性',
        sections: [
          { id: 411, name: '1.1 响应式原理' },
          { id: 412, name: '1.2 组合式 API' },
        ],
      },
    ],
  },
  {
    id: 5,
    name: 'TypeScript 从入门到实战',
    coverUrl: cover('online course cover, TypeScript programming, blue white minimal design, code snippets floating'),
    price: 0,
    categoryIdLv1: 2,
    categoryIdLv2: 202,
    teacherId: 3,
    status: 1,
    free: 1,
    publishTimes: 1,
    enrollNum: 20340,
    score: 4.6,
    description: '免费公开课：类型系统、泛型、装饰器、类型体操，结合真实项目掌握 TS 工程实践。',
    catalogues: [
      { id: 51, name: '第一章 类型系统', sections: [{ id: 511, name: '1.1 基础类型与推断' }] },
    ],
  },
  {
    id: 6,
    name: '大模型应用开发：RAG 与 Agent 实战',
    coverUrl: cover('online course cover, AI large language model, neural network, robot assistant, purple blue futuristic style'),
    price: 59900,
    categoryIdLv1: 3,
    categoryIdLv2: 301,
    teacherId: 4,
    status: 1,
    free: 0,
    publishTimes: 1,
    enrollNum: 4210,
    score: 4.9,
    description:
      '从 Prompt 工程到 RAG 检索增强，再到多 Agent 协作与 SSE 流式交互，构建生产级 AI 应用。',
    catalogues: [
      {
        id: 61,
        name: '第一章 LLM 应用基础',
        sections: [
          { id: 611, name: '1.1 大模型原理速览' },
          { id: 612, name: '1.2 Prompt 工程方法论' },
        ],
      },
      {
        id: 62,
        name: '第二章 RAG 检索增强',
        sections: [
          { id: 621, name: '2.1 文本切片与向量化' },
          { id: 622, name: '2.2 向量数据库选型' },
        ],
      },
    ],
  },
  {
    id: 7,
    name: 'MySQL 8 性能优化与索引设计',
    coverUrl: cover('online course cover, MySQL database, dolphin logo, data architecture, indigo blue flat design'),
    price: 19900,
    categoryIdLv1: 4,
    categoryIdLv2: 401,
    teacherId: 5,
    status: 1,
    free: 0,
    publishTimes: 2,
    enrollNum: 11220,
    score: 4.8,
    description: '索引原理、执行计划分析、事务与锁、分库分表，让 SQL 速度起飞。',
    catalogues: [
      { id: 71, name: '第一章 索引 internals', sections: [{ id: 711, name: '1.1 B+ 树索引' }] },
    ],
  },
  {
    id: 8,
    name: 'Redis 高并发缓存实战',
    coverUrl: cover('online course cover, Redis cache database, red crystal structure, high concurrency, dark tech style'),
    price: 24900,
    categoryIdLv1: 4,
    categoryIdLv2: 402,
    teacherId: 5,
    status: 1,
    free: 0,
    publishTimes: 1,
    enrollNum: 9180,
    score: 4.8,
    description: '缓存穿透/击穿/雪崩治理、分布式锁、Lua 原子操作，扛住亿级流量的缓存架构设计。',
    catalogues: [
      { id: 81, name: '第一章 数据结构', sections: [{ id: 811, name: '1.1 五大基础结构' }] },
    ],
  },
  {
    id: 9,
    name: 'Docker 与 Kubernetes 云原生指南',
    coverUrl: cover('online course cover, Docker containers and Kubernetes, blue whale and ship wheel, cloud computing, isometric style'),
    price: 39900,
    categoryIdLv1: 5,
    categoryIdLv2: 501,
    teacherId: 6,
    status: 1,
    free: 0,
    publishTimes: 1,
    enrollNum: 5890,
    score: 4.7,
    description: '容器化部署、编排调度、CI/CD 流水线，打通从开发到上线的最后一公里。',
    catalogues: [
      { id: 91, name: '第一章 容器基础', sections: [{ id: 911, name: '1.1 Docker 核心概念' }] },
    ],
  },
  {
    id: 10,
    name: '大厂面试冲刺：八股文与算法',
    coverUrl: cover('online course cover, job interview preparation, checklist and laptop, professional blue style'),
    price: 0,
    categoryIdLv1: 6,
    categoryIdLv2: 601,
    teacherId: 6,
    status: 1,
    free: 1,
    publishTimes: 1,
    enrollNum: 32600,
    score: 4.9,
    description: '免费公开课：高频面试题精讲 + LeetCode 热题 100，助你拿下心仪 Offer。',
    catalogues: [
      { id: 101, name: '第一章 算法专项', sections: [{ id: 1011, name: '1.1 双指针与滑动窗口' }] },
    ],
  },
  {
    id: 11,
    name: '机器学习数学基础（草稿中）',
    coverUrl: cover('online course cover, machine learning mathematics, formulas and graphs, chalkboard style'),
    price: 19900,
    categoryIdLv1: 3,
    categoryIdLv2: 302,
    teacherId: 4,
    status: 2,
    free: 0,
    publishTimes: 0,
    enrollNum: 0,
    score: 0,
    description: '线性代数、概率统计、微积分在机器学习中的应用（课程筹备中）。',
    catalogues: [],
  },
  {
    id: 12,
    name: 'MyBatis-Plus 高效数据访问',
    coverUrl: cover('online course cover, MyBatis data persistence framework, database and Java code, orange blue design'),
    price: 12900,
    categoryIdLv1: 1,
    categoryIdLv2: 102,
    teacherId: 1,
    status: 1,
    free: 0,
    publishTimes: 1,
    enrollNum: 7440,
    score: 4.5,
    description: '条件构造器、分页插件、代码生成器、多数据源，CRUD 效率倍增。',
    catalogues: [
      { id: 121, name: '第一章 入门', sections: [{ id: 1211, name: '1.1 快速上手' }] },
    ],
  },
]

/** 首页轮播 Banner */
export const mockBanners = [
  {
    id: 1,
    title: 'AI 时代的学习方式：知行智学全新升级',
    tagline: 'AI 助教全天候陪伴，打造「知-学-行-评」学习闭环',
    image: cover('online education platform banner, AI assistant robot teacher with students, indigo blue gradient, modern illustration, wide banner'),
    link: '/assistant',
  },
  {
    id: 2,
    title: 'Java 全栈工程师成长计划',
    tagline: '从 Java 21 到 Spring Cloud 微服务，一站式进阶',
    image: cover('online education banner, java full stack developer roadmap, laptop with code, indigo purple gradient, wide banner'),
    link: '/courses',
  },
  {
    id: 3,
    title: '限量秒杀：热门课程 5 折起',
    tagline: '每天 10:00 开抢，先到先得',
    image: cover('flash sale banner, discount shopping, red and orange festive design, lightning bolt, wide banner'),
    link: '/trade/coupons',
  },
]

/** 优惠券 */
export const mockCoupons: CouponVO[] = [
  {
    id: 1,
    name: '新人立减券',
    discountValue: 1000,
    thresholdAmount: 0,
    type: 1,
    status: 1,
    totalNum: 1000,
    remainNum: 532,
    issueBeginTime: '2026-08-01 00:00:00',
    issueEndTime: '2026-10-01 00:00:00',
    useBeginTime: '2026-08-01 00:00:00',
    useEndTime: '2026-11-01 00:00:00',
  },
  {
    id: 2,
    name: '满 300 减 60 通用券',
    discountValue: 6000,
    thresholdAmount: 30000,
    type: 1,
    status: 1,
    totalNum: 500,
    remainNum: 187,
    issueBeginTime: '2026-08-15 00:00:00',
    issueEndTime: '2026-09-30 00:00:00',
  },
  {
    id: 3,
    name: '爆款课程 5 折秒杀券',
    discountValue: 5000,
    thresholdAmount: 0,
    type: 2,
    status: 1,
    totalNum: 100,
    remainNum: 37,
    issueBeginTime: '2026-09-01 10:00:00',
    issueEndTime: '2026-09-07 22:00:00',
  },
  {
    id: 4,
    name: 'AI 课程专项券（即将开始）',
    discountValue: 3000,
    thresholdAmount: 0,
    type: 2,
    status: 3,
    totalNum: 200,
    remainNum: 200,
    issueBeginTime: '2026-09-10 10:00:00',
    issueEndTime: '2026-09-12 22:00:00',
  },
  {
    id: 5,
    name: '全场满 500 减 120',
    discountValue: 12000,
    thresholdAmount: 50000,
    type: 1,
    status: 2,
    totalNum: 300,
    remainNum: 0,
    issueBeginTime: '2026-07-01 00:00:00',
    issueEndTime: '2026-07-31 00:00:00',
  },
]

/** 我的优惠券 */
export const mockUserCoupons: UserCouponVO[] = [
  { id: 1, userId: 1, couponId: 1, couponName: '新人立减券', discountValue: 1000, thresholdAmount: 0, status: 1, createTime: '2026-08-20 10:12:00' },
  { id: 2, userId: 1, couponId: 2, couponName: '满 300 减 60 通用券', discountValue: 6000, thresholdAmount: 30000, status: 1, createTime: '2026-08-25 15:40:00' },
]

/** 我的订单 */
export const mockOrders: OrderVO[] = [
  {
    id: 1001,
    userId: 1,
    orderNo: '1790000000000000001',
    totalAmount: 39900,
    realAmount: 33900,
    discountAmount: 6000,
    couponId: 2,
    status: 1,
    createTime: '2026-09-05 09:30:00',
    details: [{ id: 1, orderId: 1001, courseId: 2, courseName: 'Spring Boot 3 全栈开发实战', price: 39900 }],
  },
  {
    id: 1002,
    userId: 1,
    orderNo: '1790000000000000002',
    totalAmount: 29900,
    realAmount: 29900,
    discountAmount: 0,
    status: 2,
    createTime: '2026-08-28 14:22:00',
    payTime: '2026-08-28 14:23:10',
    details: [{ id: 2, orderId: 1002, courseId: 1, courseName: 'Java 21 核心技术：从入门到精通', price: 29900 }],
  },
  {
    id: 1003,
    userId: 1,
    orderNo: '1790000000000000003',
    totalAmount: 12900,
    realAmount: 12900,
    discountAmount: 0,
    status: 3,
    createTime: '2026-08-20 21:05:00',
    details: [{ id: 3, orderId: 1003, courseId: 12, courseName: 'MyBatis-Plus 高效数据访问', price: 12900 }],
  },
]

/** 学习课表 */
export const mockLessons: LearningLessonVO[] = [
  { id: 1, userId: 1, courseId: 1, courseName: 'Java 21 核心技术：从入门到精通', coverUrl: mockCourses[0].coverUrl, status: 0, weekFreq: 4, createTime: '2026-08-28 14:30:00', learnProgress: 36 },
  { id: 2, userId: 1, courseId: 5, courseName: 'TypeScript 从入门到实战', coverUrl: mockCourses[4].coverUrl, status: 0, weekFreq: 3, createTime: '2026-09-01 20:10:00', learnProgress: 62 },
  { id: 3, userId: 1, courseId: 10, courseName: '大厂面试冲刺：八股文与算法', coverUrl: mockCourses[9].coverUrl, status: 0, weekFreq: 2, createTime: '2026-09-03 08:45:00', learnProgress: 15 },
  { id: 4, userId: 1, courseId: 8, courseName: 'Redis 高并发缓存实战', coverUrl: mockCourses[7].coverUrl, status: 1, weekFreq: null, createTime: '2026-07-12 19:00:00', learnProgress: 100 },
]

/** 学习记录时间线 */
export const mockLearningRecords: LearningRecordVO[] = [
  { id: 1, userId: 1, lessonId: 1, sectionId: 121, sectionName: '2.2 虚拟线程实战', courseName: 'Java 21 核心技术', moment: 1260, finished: true, updateTime: '2026-09-05 21:30:00' },
  { id: 2, userId: 1, lessonId: 2, sectionId: 511, sectionName: '1.1 基础类型与推断', courseName: 'TypeScript 从入门到实战', moment: 720, finished: false, updateTime: '2026-09-05 20:10:00' },
  { id: 3, userId: 1, lessonId: 3, sectionId: 1011, sectionName: '1.1 双指针与滑动窗口', courseName: '大厂面试冲刺', moment: 1980, finished: true, updateTime: '2026-09-04 22:05:00' },
  { id: 4, userId: 1, lessonId: 1, sectionId: 122, sectionName: '2.3 锁与并发容器', courseName: 'Java 21 核心技术', moment: 540, finished: false, updateTime: '2026-09-04 19:40:00' },
]

/** 笔记 */
export const mockNotes: NoteVO[] = [
  { id: 1, userId: 1, courseId: 1, courseName: 'Java 21 核心技术', content: '虚拟线程由 JVM 管理而非操作系统，创建成本极低，适合 IO 密集型任务。注意 pinned 问题：synchronized 块中会钉住载体线程。', createTime: '2026-09-05 21:40:00' },
  { id: 2, userId: 1, courseId: 5, courseName: 'TypeScript 从入门到实战', content: 'never 类型是所有类型的子类型，常用于穷尽检查（exhaustiveness check）。', createTime: '2026-09-04 20:30:00' },
]

/** 学情画像 */
export const mockProfile: InsightProfileVO = {
  userId: 1,
  totalDuration: 8640,
  completedRate: 72,
  continuousDays: 12,
  abilities: [
    { name: 'Java 基础', value: 82 },
    { name: '框架应用', value: 74 },
    { name: '数据库', value: 65 },
    { name: '中间件', value: 58 },
    { name: '工程素养', value: 70 },
    { name: '算法能力', value: 48 },
  ],
  trends: [
    { date: '08-30', duration: 65 },
    { date: '08-31', duration: 90 },
    { date: '09-01', duration: 45 },
    { date: '09-02', duration: 120 },
    { date: '09-03', duration: 80 },
    { date: '09-04', duration: 105 },
    { date: '09-05', duration: 95 },
  ],
}

/** 学习路径推荐 */
export const mockLearningPath: LearningPathVO = {
  reason: '基于你的能力画像，「数据库」与「算法能力」维度相对薄弱，建议按以下路径补强，预计 6 周完成。',
  steps: [
    { order: 1, courseId: 7, courseName: 'MySQL 8 性能优化与索引设计', reason: '补齐数据库短板，掌握索引与执行计划分析' },
    { order: 2, courseId: 8, courseName: 'Redis 高并发缓存实战', reason: '强化中间件能力，掌握缓存治理套路' },
    { order: 3, courseId: 10, courseName: '大厂面试冲刺：八股文与算法', reason: '专项提升算法，巩固面试高频考点' },
  ],
}

/** 数据看板 */
export const mockDashboard: DashboardVO = {
  totalUsers: 52890,
  totalOrders: 12836,
  totalSales: 382680000,
  totalCourses: 12,
  orderTrend: [
    { date: '08-30', count: 132, amount: 3980000 },
    { date: '08-31', count: 156, amount: 4520000 },
    { date: '09-01', count: 98, amount: 2890000 },
    { date: '09-02', count: 187, amount: 5660000 },
    { date: '09-03', count: 210, amount: 6320000 },
    { date: '09-04', count: 245, amount: 7180000 },
    { date: '09-05', count: 198, amount: 5840000 },
  ],
  activeTrend: [
    { date: '08-30', count: 3200 },
    { date: '08-31', count: 4100 },
    { date: '09-01', count: 2800 },
    { date: '09-02', count: 4600 },
    { date: '09-03', count: 5100 },
    { date: '09-04', count: 5400 },
    { date: '09-05', count: 4900 },
  ],
  hotCourses: [
    { name: '大厂面试冲刺：八股文与算法', count: 3260 },
    { name: 'TypeScript 从入门到实战', count: 2034 },
    { name: 'Java 21 核心技术', count: 1289 },
    { name: 'MySQL 8 性能优化', count: 1122 },
    { name: 'Spring Boot 3 全栈开发', count: 986 },
  ],
}

/** 考题 */
export const mockQuestions: QuestionVO[] = [
  { id: 1, courseId: 1, name: 'Java 21 中，以下哪种线程由 JVM 管理而非操作系统内核？', type: 1, options: ['平台线程', '虚拟线程', '守护线程', '主线程'], answer: 'B', difficulty: 2, analysis: '虚拟线程是用户态线程，由 JVM 调度，挂载在载体线程（ForkJoinPool）上执行。' },
  { id: 2, courseId: 2, name: 'Spring Boot 自动配置的注解是？', type: 1, options: ['@Component', '@EnableAutoConfiguration', '@AutoWired', '@Import'], answer: 'B', difficulty: 1, analysis: '@EnableAutoConfiguration 借助 AutoConfiguration.imports 文件实现自动装配。' },
  { id: 3, courseId: 7, name: 'MySQL InnoDB 默认的索引结构是？', type: 1, options: ['哈希索引', 'B+ 树', '红黑树', 'LSM-Tree'], answer: 'B', difficulty: 1, analysis: 'InnoDB 使用 B+ 树，叶子节点存放数据（聚簇索引）或主键（二级索引）。' },
  { id: 4, courseId: 8, name: 'Redis 缓存穿透的典型解决方案包括？', type: 2, options: ['布隆过滤器', '缓存空对象', '设置随机过期时间', '互斥锁重建'], answer: 'AB', difficulty: 3, analysis: '缓存穿透指查询不存在数据；布隆过滤器与缓存空对象是典型方案；CD 是雪崩/击穿的方案。' },
  { id: 5, courseId: 3, name: 'RocketMQ 事务消息可以保证本地事务与消息发送的最终一致性。', type: 3, options: ['正确', '错误'], answer: 'A', difficulty: 2, analysis: '事务消息通过半消息 + 本地事务 + 回查机制实现最终一致。' },
]

/** 站内信 */
export const mockInboxes: InboxVO[] = [
  { id: 1, title: '秒杀活动提醒', content: '「爆款课程 5 折秒杀券」剩余库存告急，先到先得！', read: false, createTime: '2026-09-05 10:00:00' },
  { id: 2, title: '学习提醒', content: '你已连续打卡 12 天，继续保持！今日课程：Java 虚拟线程实战。', read: false, createTime: '2026-09-05 08:00:00' },
  { id: 3, title: '订单通知', content: '你的订单 1790000000000000001 还有 15 分钟即将超时关闭，请尽快完成支付。', read: true, createTime: '2026-09-05 09:45:00' },
  { id: 4, title: '系统公告', content: '平台已完成 v1.2.4 版本升级，新增 AI 助教断线重连与学情报告能力。', read: true, createTime: '2026-09-01 18:00:00' },
]

/** 用户列表（管理端） */
export const mockUsers: UserVO[] = [
  { id: 1, username: '管理员小知', cellPhone: '13800000001', type: 1, status: 1, createTime: '2026-06-01 09:00:00' },
  { id: 2, username: '张老师', cellPhone: '13800000002', type: 3, status: 1, createTime: '2026-06-15 14:30:00' },
  { id: 3, username: '李老师', cellPhone: '13800000003', type: 3, status: 1, createTime: '2026-07-02 10:00:00' },
  { id: 4, username: '王同学', cellPhone: '13800000004', type: 2, status: 1, createTime: '2026-08-10 16:20:00' },
  { id: 5, username: '赵同学', cellPhone: '13800000005', type: 2, status: 1, createTime: '2026-08-22 11:05:00' },
  { id: 6, username: '孙同学', cellPhone: '13800000006', type: 2, status: 2, createTime: '2026-09-01 09:45:00' },
]

/** AI 会话历史 */
export const mockChatSessions: ChatSession[] = [
  { id: 's1', title: '帮我推荐一门 Java 课程', userId: 1, createTime: '2026-09-04 20:15:00' },
  { id: 's2', title: '虚拟线程和平台线程的区别', userId: 1, createTime: '2026-09-05 10:30:00' },
  { id: 's3', title: '如何制定 30 天学习计划', userId: 1, createTime: '2026-09-05 15:00:00' },
]

/** 会话详情 */
export const mockSessionMessages: Record<string, ChatMessage[]> = {
  s1: [
    { type: 'USER', content: '帮我推荐一门 Java 课程' },
    {
      type: 'AI',
      content:
        '根据你的学习画像（Java 基础扎实、正在进阶并发与框架），为你推荐以下课程：\n\n1. [Java 21 核心技术：从入门到精通](/courses/1) —— 系统学习虚拟线程、JVM 调优\n2. [Spring Boot 3 全栈开发实战](/courses/2) —— 企业级项目驱动\n\n建议先从并发编程章节切入，配合每周 4 次学习计划效果更佳。',
      agent: 'CourseAgent',
    },
  ],
  s2: [
    { type: 'USER', content: '虚拟线程和平台线程的区别' },
    {
      type: 'AI',
      content:
        '主要区别有三点：\n\n- **调度主体**：虚拟线程由 JVM 调度，平台线程由 OS 内核调度\n- **创建成本**：虚拟线程创建成本极低，可轻松创建百万级；平台线程通常只建数千个\n- **适用场景**：虚拟线程适合 IO 密集型任务；CPU 密集型任务收益有限\n\n```java\ntry (var executor = Executors.newVirtualThreadPerTaskExecutor()) {\n    executor.submit(() -> fetchRemoteData());\n}\n```',
      agent: 'TutorAgent',
    },
  ],
  s3: [
    { type: 'USER', content: '如何制定 30 天学习计划' },
    { type: 'AI', content: '推荐使用平台「学习中心」的计划功能：先在课表中选择课程与每周频率，再结合每日签到打卡。我可以按你的目标课程为你生成阶段计划，需要的话告诉我目标课程即可。', agent: 'PlannerAgent' },
  ],
}

/** 热门问题模板 */
export const mockTemplates: Record<string, string> = {
  course: '推荐课程',
  plan: '制定学习计划',
  qa: '技术答疑',
  inspect: '学情分析',
}
