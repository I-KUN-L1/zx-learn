<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, ChatDotRound, DataAnalysis, Promotion, Reading } from '@element-plus/icons-vue'
import { pageCourses, getCategoryAll } from '@/api/course'
import { pageCoupons } from '@/api/promotion'
import CourseCard from '@/components/course/CourseCard.vue'
import SkeletonCards from '@/components/common/SkeletonCards.vue'
import { useUserStore } from '@/stores/user'
import type { Category, CouponVO, CourseVO } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

/* ---------- 轮播 ---------- */
/**
 * 首页横幅图片：本地静态 SVG（zx-web/public/banners/）。
 *
 * ⚠ 这里原先用的是文生图接口 `trae-api-cn.mchost.guru/api/ide/v1/text_to_image?...`
 * —— 那是「按需生成」的服务端点，**不是静态图片资源**，现已返回 HTTP 404，
 * 三个轮播位全部裂图。改用本地静态资源：不依赖外网、离线可跑、永不失效。
 */
const banners = ref([
  {
    id: 1,
    title: 'AI 时代的学习方式：知行智学全新升级',
    tagline: 'AI 助教全天候陪伴，打造「知-学-行-评」学习闭环',
    image: '/banners/banner-01.svg',
    link: '/assistant',
  },
  {
    id: 2,
    title: 'Java 全栈工程师成长计划',
    tagline: '从 Java 21 到 Spring Cloud 微服务，一站式进阶',
    image: '/banners/banner-02.svg',
    link: '/courses',
  },
  {
    id: 3,
    title: '限量秒杀：热门课程 5 折起',
    tagline: '每天 10:00 开抢，先到先得',
    image: '/banners/banner-03.svg',
    link: '/trade/coupons',
  },
])

/* ---------- 分类 ---------- */
const categories = ref<Category[]>([])
const categoryTree = computed(() => categories.value.filter((c) => c.parentId === 0))

/* ---------- 热门课程 ---------- */
const hotCourses = ref<CourseVO[]>([])
const loading = ref(true)

/* ---------- 优惠券活动提示 ---------- */
const activityCoupons = ref<CouponVO[]>([])

const features = [
  { icon: ChatDotRound, title: 'AI 智能助教', desc: 'SSE 流式对话，课程推荐 / 答疑 / 计划制定', path: '/assistant' },
  { icon: Reading, title: '学习中心', desc: '课表规划、笔记沉淀、每日签到打卡', path: '/learning' },
  { icon: DataAnalysis, title: '学情报告', desc: '能力雷达画像与个性化学习路径推荐', path: '/insight' },
  { icon: Promotion, title: '优惠秒杀', desc: '限时秒杀券，热门课程 5 折起', path: '/trade/coupons' },
]

/** 学员专属入口（游客可见引导注册；教师/管理员无权限不渲染） */
const STUDENT_ONLY_PATHS = ['/learning', '/insight', '/trade/coupons']

/** 按钮级权限过滤：教师/管理员仅保留 AI 助教等公共入口 */
const visibleFeatures = computed(() =>
  features.filter(
    (f) => !(userStore.isLoggedIn && !userStore.isStudent && STUDENT_ONLY_PATHS.includes(f.path)),
  ),
)

/** 秒杀类 Banner 仅游客与学员可见 */
const visibleBanners = computed(() =>
  banners.value.filter(
    (b) => !(userStore.isLoggedIn && !userStore.isStudent && b.link === '/trade/coupons'),
  ),
)

onMounted(async () => {
  try {
    const [coursePage, cats, couponPage] = await Promise.all([
      pageCourses({ pageNo: 1, pageSize: 8, sortBy: 'enrollNum', isAsc: false }),
      getCategoryAll(),
      pageCoupons({ pageNo: 1, pageSize: 3, type: 2 }),
    ])
    hotCourses.value = coursePage.list
    categories.value = cats
    activityCoupons.value = couponPage.list.filter((c) => c.status !== 2)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="zx-page">
    <!-- 轮播 Banner -->
    <el-carousel height="360px" class="zx-card overflow-hidden" :interval="5000" arrow="hover">
      <el-carousel-item v-for="b in visibleBanners" :key="b.id">
        <div class="relative h-full cursor-pointer" @click="router.push(b.link)">
          <img :src="b.image" :alt="b.title" class="h-full w-full object-cover" />
          <div class="zx-banner-mask absolute inset-0 flex flex-col justify-center px-10 md:px-16">
            <h2 class="max-w-lg text-3xl font-extrabold text-white drop-shadow md:text-4xl">
              {{ b.title }}
            </h2>
            <p class="mt-4 max-w-md text-indigo-100">{{ b.tagline }}</p>
            <el-button type="primary" round class="mt-6 w-fit" size="large" @click.stop="router.push(b.link)">
              立即体验
              <el-icon class="ml-1"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 特色功能入口 -->
    <div class="mt-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div
        v-for="f in visibleFeatures"
        :key="f.title"
        class="zx-card zx-card-hover flex cursor-pointer items-center gap-4 p-5"
        @click="router.push(f.path)"
      >
        <div class="zx-ai-avatar flex h-12 w-12 shrink-0 items-center justify-center rounded-xl">
          <el-icon :size="24"><component :is="f.icon" /></el-icon>
        </div>
        <div class="min-w-0">
          <div class="font-semibold">{{ f.title }}</div>
          <div class="zx-text-secondary truncate text-xs">{{ f.desc }}</div>
        </div>
      </div>
    </div>

    <!-- 课程分类入口 -->
    <section class="mt-10">
      <h2 class="text-xl font-bold">课程分类</h2>
      <div class="mt-4 flex flex-wrap gap-3">
        <el-tag
          v-for="cat in categoryTree"
          :key="cat.id"
          size="large"
          effect="plain"
          round
          class="cursor-pointer"
          @click="router.push({ path: '/courses', query: { categoryId: cat.id } })"
        >
          {{ cat.name }}
        </el-tag>
      </div>
    </section>

    <!-- 优惠券活动提示 -->
    <section v-if="activityCoupons.length" class="mt-10">
      <div class="zx-card flex flex-wrap items-center gap-4 border-l-4 border-l-primary p-5">
        <span class="zx-ai-avatar flex h-10 w-10 items-center justify-center rounded-xl font-bold">券</span>
        <div class="min-w-0 flex-1">
          <div class="font-semibold">限时活动进行中</div>
          <div class="zx-text-secondary truncate text-sm">
            {{ activityCoupons.map((c) => `${c.name}（剩 ${c.remainNum} 张）`).join(' · ') }}
          </div>
        </div>
        <!-- 抢券入口：教师/管理员无交易权限，不渲染（RBAC） -->
        <el-button
          v-if="!userStore.isLoggedIn || userStore.isStudent"
          type="danger"
          round
          plain
          @click="router.push('/trade/coupons')"
        >
          去抢券
        </el-button>
      </div>
    </section>

    <!-- 热门课程 -->
    <section class="mt-10">
      <div class="flex items-center justify-between">
        <h2 class="text-xl font-bold">热门课程</h2>
        <el-button text type="primary" @click="router.push('/courses')">
          查看全部
          <el-icon class="ml-0.5"><ArrowRight /></el-icon>
        </el-button>
      </div>
      <SkeletonCards v-if="loading" :count="8" />
      <div v-else class="mt-5 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <CourseCard v-for="c in hotCourses" :key="c.id" :course="c" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.zx-banner-mask {
  background: linear-gradient(90deg, rgba(30, 27, 75, 0.78) 0%, rgba(49, 46, 129, 0.35) 55%, transparent 100%);
}
</style>
