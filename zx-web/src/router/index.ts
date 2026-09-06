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
        meta: { title: '学习中心', requiresAuth: true },
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
        meta: { title: '学情报告', requiresAuth: true },
      },
      {
        path: 'trade',
        name: 'Trade',
        component: () => import('@/views/trade/TradeView.vue'),
        meta: { title: '确认下单', requiresAuth: true },
      },
      {
        path: 'trade/coupons',
        name: 'Coupons',
        component: () => import('@/views/trade/CouponCenterView.vue'),
        meta: { title: '优惠券中心', requiresAuth: true },
      },
      {
        path: 'trade/orders',
        name: 'Orders',
        component: () => import('@/views/trade/OrderListView.vue'),
        meta: { title: '我的订单', requiresAuth: true },
      },
      {
        path: 'exam',
        name: 'Exam',
        component: () => import('@/views/exam/ExamView.vue'),
        meta: { title: '考试练习', requiresAuth: true },
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/messages/MessagesView.vue'),
        meta: { title: '消息中心', requiresAuth: true },
      },
    ],
  },

  /* ---------- 教师/管理端 ---------- */
  {
    path: '/admin',
    component: () => import('@/components/layout/AdminLayout.vue'),
    meta: { requiresAuth: true },
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
