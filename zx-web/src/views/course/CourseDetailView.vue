<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UserFilled, Star, VideoPlay } from '@element-plus/icons-vue'
import { getCourse } from '@/api/course'
import { addToCart } from '@/api/trade'
import { enrollNum } from '@/api/trade'
import { useUserStore } from '@/stores/user'
import { formatPrice } from '@/utils/format'
import type { CourseVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const course = ref<CourseVO | null>(null)
const loading = ref(true)
const enroll = ref<number | null>(null)

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
  if (!course.value) return
  if (course.value.free === 1) {
    // 免费课直接加入学习
    router.push('/learning')
    return
  }
  router.push({ path: '/trade', query: { courseIds: String(course.value.id) } })
}

async function onAddCart() {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!course.value) return
  try {
    await addToCart(course.value.id)
    ElMessage.success('已加入购物车')
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(fetchCourse)
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <template v-if="course">
      <!-- 头部信息 -->
      <div class="zx-card overflow-hidden md:flex">
        <div class="relative md:w-[420px]">
          <img :src="course.coverUrl" :alt="course.name" class="h-56 w-full object-cover md:h-full" />
          <el-tag v-if="course.free === 1" type="success" effect="dark" class="absolute left-4 top-4" round>免费课</el-tag>
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
              <el-button round size="large" @click="onAddCart">加入购物车</el-button>
              <el-button round size="large" @click="router.push('/assistant')">咨询 AI 助教</el-button>
              <el-button type="primary" round size="large" @click="onBuy">
                {{ course.free === 1 ? '加入学习' : '立即购买' }}
              </el-button>
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
