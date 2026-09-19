<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UserFilled, Star, VideoPlay } from '@element-plus/icons-vue'
import { getCourse } from '@/api/course'
import { addToCart, freeCourse } from '@/api/trade'
import { enrollNum } from '@/api/trade'
import { useOwnedCourses } from '@/composables/useOwnedCourses'
import { useUserStore } from '@/stores/user'
import { formatPrice } from '@/utils/format'
import CourseCover from '@/components/course/CourseCover.vue'
import type { CourseVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const course = ref<CourseVO | null>(null)
const loading = ref(true)
const enroll = ref<number | null>(null)
/**
 * 是否已拥有该课程：以「我的课表」为权威口径（useOwnedCourses 全局单例），
 * 与「我的课表」共用同一份数据，保证两端状态实时一致。
 */
const { isOwned, refresh: refreshOwned, markOwned } = useOwnedCourses()
const owned = computed(() => isOwned(course.value?.id))
/** 免费课开课中：防重复提交（并发由后端唯一索引兜底） */
const enrolling = ref(false)

/**
 * 课程是否已下架（status=0）。
 * 下架后课程不再作为「可购买商品」对外展示，但**不影响已购学员的学习资产**。
 */
const offShelf = computed(() => course.value?.status === 0)
/** 管理端/教师端视角：工作台「预览」下架课程是正常需求，不参与下架收口 */
const isStaffView = computed(() => userStore.isAdmin || userStore.isTeacher)
/**
 * 已下架 + 未拥有 + 非管理/教师：整页只呈现"已下架"空态。
 * 不渲染价格、章节内容与购买/加购入口 —— 这是"课程下架后不再对用户可见"在详情页的落点。
 * 判定依赖「是否已拥有」（useOwnedCourses 以我的课表为权威口径），
 * 因此已购学员不会被误挡在学习之外。
 */
const offShelfBlocked = computed(() => offShelf.value && !owned.value && !isStaffView.value)

const priceText = computed(() =>
  course.value?.free === 1 ? '免费' : `￥${formatPrice(course.value?.price)}`
)

/** 点评区（前端演示数据） */
const remarks = ref([
  { id: 1, user: '王同学', score: 5, content: '讲解非常细致，虚拟线程章节直接解决了我项目里的性能问题！', time: '2026-09-01' },
  { id: 2, user: '李同学', score: 4.5, content: '配套资料齐全，就是章节练习再多点就更好了。', time: '2026-08-25' },
])

async function fetchCourse() {
  loading.value = true
  try {
    const id = route.params.id as string
    const [res, num] = await Promise.allSettled([getCourse(id), enrollNum(Number(id))])
    if (res.status === 'fulfilled') {
      course.value = res.value
      enroll.value = res.value.enrollNum ?? null
    }
    if (num.status === 'fulfilled') enroll.value = num.value
    // 已拥有状态以「我的课表」为准；进页面刷新一次，确保与课表实时一致
    await refreshOwned(true)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

/** 立即购买 / 加入学习 */
async function onBuy() {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!course.value || owned.value || enrolling.value) return
  // 兜底：下架课程不再允许发起购买（后端 CoursePurchaseGuard 会再拦一次）
  if (offShelf.value) {
    ElMessage.warning('课程已下架，无法购买')
    return
  }
  if (course.value.free === 1) {
    // 免费课：调用 0 元开课接口（后端同步写入课表）→ 前端立即置为已拥有，与我的课表一致
    enrolling.value = true
    try {
      await freeCourse(course.value.id)
      markOwned(course.value.id)
      ElMessage.success('已加入学习，可在学习中心开始学习')
    } catch {
      return /* 拦截器已提示（如已拥有） */
    } finally {
      enrolling.value = false
    }
    await refreshOwned(true)
    router.push(`/learning/course/${course.value.id}`)
    return
  }
  router.push({ path: '/trade', query: { courseIds: String(course.value.id) } })
}

async function onAddCart() {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!course.value || owned.value) return
  // 兜底：下架课程不再允许加入购物车（后端 CartService#add 会再拦一次）
  if (offShelf.value) {
    ElMessage.warning('课程已下架，无法加入购物车')
    return
  }
  try {
    await addToCart(course.value.id)
    ElMessage.success('已加入购物车')
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(fetchCourse)

// 同一路由内切换课程 id（如从"课程点评"跳另一门课）时重新加载，避免展示上一门课的状态
watch(
  () => route.params.id,
  (v) => {
    if (v) fetchCourse()
  }
)

// 登录态变化后强制重拉已拥有状态
watch(
  () => userStore.userId,
  () => refreshOwned(true)
)
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <template v-if="course">
      <!-- 已下架且未拥有：整页空态。下架课程不再作为可购买/可学习资源对外展示 -->
      <div v-if="offShelfBlocked" class="zx-card p-10">
        <el-empty description="该课程已下架">
          <p class="zx-text-secondary max-w-md text-sm leading-6">
            该课程已下架，无法购买或学习。你可以浏览其他课程，或咨询 AI 助教获取推荐。
          </p>
          <div class="mt-5 flex justify-center gap-3">
            <el-button type="primary" round @click="router.push('/courses')">浏览其他课程</el-button>
            <el-button round @click="router.push('/assistant')">咨询 AI 助教</el-button>
          </div>
        </el-empty>
      </div>

      <template v-else>
      <!-- 已购学员 / 管理教师端：保留完整详情，但明确标注已下架 -->
      <el-alert
        v-if="offShelf"
        class="mb-4"
        type="warning"
        effect="light"
        :closable="false"
        show-icon
        title="该课程已下架"
        :description="isStaffView ? '下架课程仅管理端/教师端可见，可在此预览或前往课程管理重新上架。' : '课程已下架，已购学员可继续学习，但无法再次购买。'"
      />
      <!-- 头部信息 -->
      <div class="zx-card overflow-hidden md:flex">
        <div class="relative h-56 shrink-0 md:h-auto md:w-[420px]">
          <CourseCover :src="course.coverUrl" :name="course.name" :seed="course.id" />
          <el-tag v-if="course.free === 1" type="success" effect="dark" class="absolute left-4 top-4" round>免费课</el-tag>
          <el-tag v-if="owned" type="primary" effect="dark" class="absolute right-4 top-4" round>已拥有</el-tag>
        </div>
        <div class="flex flex-1 flex-col p-6 md:p-8">
          <h1 class="text-2xl font-bold leading-snug">{{ course.name }}</h1>
          <div class="zx-text-secondary mt-3 flex flex-wrap items-center gap-5 text-sm">
            <span class="flex items-center gap-1"><el-icon><UserFilled /></el-icon>{{ (enroll ?? course.enrollNum ?? 0).toLocaleString() }} 人在学</span>
            <span class="flex items-center gap-1 text-amber-500">
              <el-icon><Star /></el-icon>{{ course.score ? course.score.toFixed(1) : '暂无评分' }}
            </span>
            <span v-if="course.publishTimes">已发布 {{ course.publishTimes }} 次</span>
          </div>

          <p class="zx-text-secondary mt-4 line-clamp-3 text-sm leading-6">
            {{ course.description }}
          </p>

          <div class="mt-auto flex flex-wrap items-end justify-between gap-4 pt-6">
            <div>
              <span class="text-3xl font-extrabold text-primary">{{ priceText }}</span>
              <span v-if="course.free !== 1" class="zx-text-secondary ml-2 text-sm">支持优惠券抵扣</span>
            </div>
            <div class="flex gap-3">
              <!-- 购买/购物车入口仅游客（引导注册）与学员可见；教师/管理员无交易权限，不渲染（RBAC） -->
              <template v-if="!userStore.isLoggedIn || userStore.isStudent">
                <!-- 已拥有：禁用购买/加购，直接引导去学习 -->
                <template v-if="owned">
                  <el-tag type="primary" effect="light" size="large" round>已拥有</el-tag>
                  <el-button
                    type="primary"
                    round
                    size="large"
                    @click="router.push(`/learning/course/${course.id}`)"
                  >
                    继续学习
                  </el-button>
                </template>
                <template v-else>
                  <el-button round size="large" @click="onAddCart">加入购物车</el-button>
                  <el-button type="primary" round size="large" @click="onBuy">
                    {{ course.free === 1 ? '加入学习' : '立即购买' }}
                  </el-button>
                </template>
              </template>
              <el-button round size="large" @click="router.push('/assistant')">咨询 AI 助教</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <!-- 课程介绍 + 章节目录 -->
        <div class="space-y-6 lg:col-span-2">
          <div class="zx-card p-6">
            <h2 class="text-lg font-bold">课程介绍</h2>
            <p class="zx-text-secondary mt-3 whitespace-pre-wrap text-sm leading-7">{{ course.description }}</p>
          </div>

          <!-- 手风琴章节目录 -->
          <div class="zx-card p-6">
            <h2 class="text-lg font-bold">章节目录</h2>
            <el-collapse class="mt-4 zx-collapse">
              <el-collapse-item v-for="chapter in course.catalogues ?? []" :key="chapter.id" :name="chapter.id">
                <template #title>
                  <div class="flex items-center gap-2 font-medium">
                    <el-icon class="text-primary"><VideoPlay /></el-icon>
                    {{ chapter.name }}
                    <span class="zx-text-secondary ml-2 text-xs">{{ chapter.sections?.length ?? 0 }} 小节</span>
                  </div>
                </template>
                <div
                  v-for="s in chapter.sections ?? []"
                  :key="s.id"
                  class="zx-section-item"
                >
                  <span>{{ s.name }}</span>
                  <el-tag size="small" type="info" effect="plain" round>视频</el-tag>
                </div>
              </el-collapse-item>
            </el-collapse>
            <el-empty v-if="!course.catalogues?.length" description="章节筹备中" :image-size="80" />
          </div>
        </div>

        <!-- 课程点评 -->
        <div class="space-y-6">
          <div class="zx-card p-6">
            <h2 class="text-lg font-bold">课程点评</h2>
            <div class="mt-4 space-y-5">
              <div v-for="r in remarks" :key="r.id" class="border-b pb-4 last:border-none" style="border-color: var(--zx-border)">
                <div class="flex items-center justify-between">
                  <span class="font-medium">{{ r.user }}</span>
                  <el-rate :model-value="r.score" disabled size="small" />
                </div>
                <p class="zx-text-secondary mt-2 text-sm leading-6">{{ r.content }}</p>
                <p class="zx-text-secondary mt-1 text-xs">{{ r.time }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
      </template>
    </template>

    <el-skeleton v-else-if="loading" class="mt-4" animated :rows="12" />
  </div>
</template>

<style scoped>
.zx-section-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--zx-text-secondary);
  cursor: pointer;
  transition: background 0.2s;
}
.zx-section-item:hover {
  background: var(--zx-primary-bg);
  color: var(--zx-primary);
}
</style>
