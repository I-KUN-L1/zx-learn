import { createRouter, createWebHistory } from 'vue-router'
import { setupRouterGuard } from './guard'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  /* ---------- 公共 ---------- */
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/RegisterView.vue'),
    meta: { title: '注册' },
  },
  {
    path: '/password/first-change',
    name: 'FirstChangePassword',
    component: () => import('@/views/password/FirstChangePasswordView.vue'),
    meta: { title: '首次登录修改密码' },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '无权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },

  /* ---------- 学员端 ---------- */
  {
    path: '/',
    component: () => import('@/components/layout/StudentLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'courses',
        name: 'CourseList',
        component: () => import('@/views/course/CourseListView.vue'),
        meta: { title: '课程中心' },
      },
      {
        path: 'courses/:id',
        name: 'CourseDetail',
        component: () => import('@/views/course/CourseDetailView.vue'),
        meta: { title: '课程详情' },
      },
      {
        path: 'learning',
        name: 'Learning',
        component: () => import('@/views/learning/LearningView.vue'),
        meta: { title: '学习中心', requiresAuth: true, roles: ['student'] },
      },
      {
        // 学习通式课程内容页：左侧章节目录 + 右侧章节内容/讨论/测验
        path: 'learning/course/:courseId',
        name: 'CourseContent',
        component: () => import('@/views/learning/CourseContentView.vue'),
        meta: { title: '课程内容', requiresAuth: true, roles: ['student'] },
      },
      {
        // 个人中心：我的积分 / 排行榜 / 积分明细（全角色可用）
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心', requiresAuth: true },
      },
      {
        path: 'assistant',
        name: 'Assistant',
        component: () => import('@/views/assistant/AssistantView.vue'),
        meta: { title: 'AI 智能助教', requiresAuth: true },
      },
      {
        path: 'insight',
        name: 'Insight',
        component: () => import('@/views/insight/InsightView.vue'),
        meta: { title: '学情报告', requiresAuth: true, roles: ['student'] },
      },
      {
        path: 'trade',
        name: 'Trade',
        component: () => import('@/views/trade/TradeView.vue'),
        meta: { title: '确认下单', requiresAuth: true, roles: ['student'] },
      },
      {
        path: 'trade/coupons',
        name: 'Coupons',
        component: () => import('@/views/trade/CouponCenterView.vue'),
        meta: { title: '优惠券中心', requiresAuth: true, roles: ['student'] },
      },
      {
        path: 'trade/cart',
        name: 'Cart',
        component: () => import('@/views/trade/CartView.vue'),
        meta: { title: '我的购物车', requiresAuth: true, roles: ['student'] },
      },
      {
        path: 'trade/orders',
        name: 'Orders',
        component: () => import('@/views/trade/OrderListView.vue'),
        meta: { title: '我的订单', requiresAuth: true, roles: ['student'] },
      },
      {
        path: 'exam',
        name: 'Exam',
        component: () => import('@/views/exam/ExamView.vue'),
        meta: { title: '在线答题', requiresAuth: true, roles: ['student'] },
      },
      {
        path: 'exam/wrong-book',
        name: 'WrongBook',
        component: () => import('@/views/exam/WrongBookView.vue'),
        meta: { title: '我的错题本', requiresAuth: true, roles: ['student'] },
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/messages/MessagesView.vue'),
        meta: { title: '消息中心', requiresAuth: true },
      },
    ],
  },

  /* ---------- 管理端 ---------- */
  {
    path: '/admin',
    component: () => import('@/components/layout/AdminLayout.vue'),
    meta: { requiresAuth: true, roles: ['admin'] },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/AdminDashboardView.vue'),
        meta: { title: '数据看板', requiresAuth: true },
      },
      {
        path: 'courses',
        name: 'AdminCourses',
        component: () => import('@/views/admin/AdminCoursesView.vue'),
        meta: { title: '课程管理', requiresAuth: true },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/AdminUsersView.vue'),
        meta: { title: '用户与权限', requiresAuth: true },
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('@/views/admin/AdminOrdersView.vue'),
        meta: { title: '订单管理', requiresAuth: true },
      },
      {
        /**
         * AI 助教（管理端入口）。
         * 后端 /chat、/session、/audio 仅要求登录、无角色限制，管理员本就能用；
         * 这里补一份控制台内入口，避免管理员必须绕回门户顶部导航才能进。
         */
        path: 'assistant',
        name: 'AdminAssistant',
        component: () => import('@/views/assistant/AssistantView.vue'),
        meta: { title: 'AI 助教', requiresAuth: true },
      },
    ],
  },

  /* ---------- 教师端 ---------- */
  {
    path: '/teacher',
    component: () => import('@/components/layout/TeacherLayout.vue'),
    meta: { requiresAuth: true, roles: ['teacher'] },
    children: [
      {
        path: 'courses',
        name: 'TeacherCourses',
        component: () => import('@/views/admin/AdminCoursesView.vue'),
        meta: { title: '课程管理', requiresAuth: true },
      },
      {
        path: 'questions',
        name: 'TeacherQuestions',
        component: () => import('@/views/teacher/TeacherQuestionsView.vue'),
        meta: { title: '题库管理', requiresAuth: true },
      },
      {
        path: 'students',
        name: 'TeacherStudents',
        component: () => import('@/views/teacher/TeacherStudentsView.vue'),
        meta: { title: '学员学情', requiresAuth: true },
      },
      {
        path: 'answers',
        name: 'TeacherAnswers',
        component: () => import('@/views/teacher/TeacherAnswersView.vue'),
        meta: { title: '答题情况', requiresAuth: true },
      },
      {
        /**
         * AI 助教（教师端入口）—— 修复「教师端无法使用 AI 助教」。
         *
         * 根因不在鉴权：后端 /chat、/session、/audio 只要求登录、无 @RequireRole，
         * 网关也只校验 JWT；真正缺的是入口 —— 全站唯一的 AI 助教导航在 AppHeader，
         * 而 AppHeader 只被学员端 StudentLayout 引用，教师登录后默认落地教师工作台，
         * 侧边栏没有该入口，因此表现为"教师端用不了"。
         *
         * 注：本路由挂在 /teacher 之下，继承父级 meta.roles: ['teacher']，
         * 管理员走 /admin/assistant，学员走门户 /assistant，三者共用同一页面组件。
         */
        path: 'assistant',
        name: 'TeacherAssistant',
        component: () => import('@/views/assistant/AssistantView.vue'),
        meta: { title: 'AI 助教', requiresAuth: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

setupRouterGuard(router)

export default router
