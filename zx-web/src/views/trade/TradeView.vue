<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCourse } from '@/api/course'
import { placeOrder } from '@/api/trade'
import { myCoupons } from '@/api/promotion'
import { formatPrice, ORDER_STATUS } from '@/utils/format'
import type { CourseVO, UserCouponVO } from '@/types/api'

const route = useRoute()
const router = useRouter()

const courseIds = computed(() =>
  (route.query.courseIds as string || '').split(',').map(Number).filter(Boolean)
)
const courses = ref<CourseVO[]>([])
const coupons = ref<UserCouponVO[]>([])
const selectedCouponId = ref<number | undefined>()
const submitting = ref(false)
const loading = ref(true)

/** 优惠券只能用于单门课程的订单（后端逐门课程一单、每张券限用一次），多课程时禁用 */
const canUseCoupon = computed(() => courses.value.length === 1)

/** 选中的用户券对单门课程的最大可抵金额（按后端固定面值核销 deduction=price-totalFee） */
function discountOf(course: CourseVO): number {
  const uc = coupons.value.find((c) => c.id === selectedCouponId.value)
  if (!uc) return 0
  const price = course.price ?? 0
  if (price <= 0) return 0
  // 满减券需满足门槛；无门槛券直接立减
  if ((uc.thresholdAmount ?? 0) > price) return 0
  // 兜底封顶不超过课程价，避免实付为负导致后端校验失败
  return Math.min(uc.discountValue ?? 0, price)
}

const totalAmount = computed(() => courses.value.reduce((s, c) => s + (c.price ?? 0), 0))
const discountAmount = computed(() => courses.value.reduce((s, c) => s + discountOf(c), 0))
const realAmount = computed(() => Math.max(0, totalAmount.value - discountAmount.value))

async function init() {
  loading.value = true
  try {
    const details = await Promise.all(courseIds.value.map((id) => getCourse(id)))
    courses.value = details
    coupons.value = (await myCoupons()).filter((c) => c.status === 1)
    // 多课程时不允许用券（后端逐课一单，一张券只能用于一单）
    if (!canUseCoupon.value) selectedCouponId.value = undefined
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

/** 提交订单（后端：每门课程一张订单 + 雪花单号 + 本地消息表 + 15 分钟超时关单） */
async function onSubmit() {
  if (!courses.value.length) return
  submitting.value = true
  try {
    // 选中的用户券：couponId 传“券模板 id”，userCouponId 传“用户券行 id”，与后端下单/核销契约对齐
    const uc = coupons.value.find((c) => c.id === selectedCouponId.value)
    for (const course of courses.value) {
      await placeOrder({
        courseId: course.id,
        // 实付金额：无券 = 课程价（后端默认）；有券 = 课程价 - 抵扣，供后端做金额一致性校验
        totalFee: course.price - discountOf(course),
        couponId: uc?.couponId,
        userCouponId: uc?.id,
      })
    }
    ElMessage.success(`下单成功！订单将在 15 分钟后超时自动关闭`)
    await router.replace(`/trade/orders?pending=1`)
  } catch {
    /* ignore */
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!courseIds.value.length) {
    ElMessage.warning('请先选择要购买的课程')
    router.replace('/courses')
    return
  }
  init()
})

void ORDER_STATUS
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <h1 class="mb-5 text-2xl font-bold">确认下单</h1>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <!-- 课程清单 -->
      <div class="zx-card p-6 lg:col-span-2">
        <h2 class="font-bold">课程清单</h2>
        <div class="mt-4 space-y-4">
          <div
            v-for="c in courses"
            :key="c.id"
            class="flex items-center gap-4 rounded-xl p-3"
            style="background: var(--zx-bg)"
          >
            <img :src="c.coverUrl" :alt="c.name" class="h-16 w-28 rounded-lg object-cover" />
            <div class="min-w-0 flex-1">
              <div class="truncate font-medium">{{ c.name }}</div>
              <div class="zx-text-secondary mt-1 text-xs">讲师 ID：{{ c.teacherId ?? '-' }}</div>
            </div>
            <div class="font-bold text-primary">
              {{ c.free === 1 ? '免费' : `￥${formatPrice(c.price)}` }}
            </div>
          </div>
        </div>

        <!-- 优惠券选择（多课程购买时不支持用券） -->
        <h2 class="mt-8 font-bold">选择优惠券</h2>
        <p v-if="!canUseCoupon" class="zx-text-secondary mt-1 text-xs">
          一次购买多门课程时不支持使用优惠券，请逐门课程单独下单（每张券限用于一单）。
        </p>
        <div class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label
            class="zx-coupon-option flex cursor-pointer items-center justify-between rounded-xl border-2 p-4 transition-colors"
            :class="{ 'zx-coupon-option--active': selectedCouponId === undefined }"
            @click="selectedCouponId = undefined"
          >
            <span class="text-sm">不使用优惠券</span>
            <el-icon v-if="selectedCouponId === undefined" class="text-primary">✔</el-icon>
          </label>
          <label
            v-for="c in coupons"
            :key="c.id"
            class="zx-coupon-option flex cursor-pointer items-center justify-between rounded-xl border-2 p-4 transition-colors"
            :class="{
              'zx-coupon-option--active': selectedCouponId === c.id,
              'zx-coupon-option--disabled': !canUseCoupon,
            }"
            @click="canUseCoupon && (selectedCouponId = c.id)"
          >
            <div class="min-w-0">
              <div class="truncate text-sm font-medium">{{ c.couponName }}</div>
              <div class="zx-text-secondary text-xs">
                {{ (c.thresholdAmount ?? 0) > 0 ? `满 ${formatPrice(c.thresholdAmount)} 可用` : '无门槛' }}
              </div>
            </div>
            <span class="font-bold text-primary">
              {{ (c.thresholdAmount ?? 0) > 0 ? `-${formatPrice(c.discountValue)}` : `立减 ${formatPrice(c.discountValue)}` }}
            </span>
          </label>
        </div>
        <el-empty v-if="!coupons.length" description="暂无可用优惠券" :image-size="70" />
      </div>

      <!-- 结算 -->
      <div class="zx-card h-fit p-6">
        <h2 class="font-bold">订单结算</h2>
        <div class="mt-4 space-y-3 text-sm">
          <div class="flex justify-between">
            <span class="zx-text-secondary">课程金额</span>
            <span>￥{{ formatPrice(totalAmount) }}</span>
          </div>
          <div class="flex justify-between">
            <span class="zx-text-secondary">优惠券抵扣</span>
            <span class="text-red-500">-￥{{ formatPrice(discountAmount) }}</span>
          </div>
          <el-divider class="my-3" />
          <div class="flex items-baseline justify-between">
            <span class="zx-text-secondary">应付金额</span>
            <span class="text-2xl font-extrabold text-primary">￥{{ formatPrice(realAmount) }}</span>
          </div>
        </div>
        <el-button
          type="primary"
          size="large"
          round
          class="mt-6 w-full"
          :loading="submitting"
          @click="onSubmit"
        >
          {{ submitting ? '下单中…' : '提交订单' }}
        </el-button>
        <p class="zx-text-secondary mt-3 text-xs leading-5">
          下单成功后请于 15 分钟内完成支付，超时订单将自动关闭并释放优惠券。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.zx-coupon-option {
  border-color: var(--zx-border);
  background: var(--zx-bg-card);
}
.zx-coupon-option:hover {
  border-color: var(--zx-primary-light, #a5b4fc);
}
.zx-coupon-option--active {
  border-color: var(--zx-primary);
  background: var(--zx-primary-bg);
}
.zx-coupon-option--disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.zx-coupon-option--disabled:hover {
  border-color: var(--zx-border);
}
</style>
