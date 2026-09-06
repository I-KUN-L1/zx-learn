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

const totalAmount = computed(() => courses.value.reduce((s, c) => s + (c.price ?? 0), 0))

/** 选中优惠券可抵扣金额（简化口径：满减券固定减、折扣券按比例） */
const discountAmount = computed(() => {
  const uc = coupons.value.find((c) => c.id === selectedCouponId.value)
  if (!uc) return 0
  if ((uc.thresholdAmount ?? 0) > totalAmount.value) return 0
  if ((uc.thresholdAmount ?? 0) > 0) return uc.discountValue ?? 0
  // 无门槛折扣券：按折扣比例抵扣
  return Math.round((totalAmount.value * (uc.discountValue ?? 0)) / 100)
})

const realAmount = computed(() => Math.max(0, totalAmount.value - discountAmount.value))

async function init() {
  loading.value = true
  try {
    const details = await Promise.all(courseIds.value.map((id) => getCourse(id)))
    courses.value = details
    coupons.value = (await myCoupons()).filter((c) => c.status === 1)
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

/** 提交订单（后端：雪花单号 + 本地消息表 + 15 分钟超时关单） */
async function onSubmit() {
  if (!courses.value.length) return
  submitting.value = true
  try {
    const order = await placeOrder({
      courseIds: courseIds.value,
      couponId: selectedCouponId.value,
    })
    ElMessage.success(`下单成功！订单 ${order.orderNo} 将在 15 分钟后超时自动关闭`)
    await router.replace(`/trade/orders?orderNo=${order.orderNo}&pending=1`)
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

        <!-- 优惠券选择 -->
        <h2 class="mt-8 font-bold">选择优惠券</h2>
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
            :class="{ 'zx-coupon-option--active': selectedCouponId === c.id }"
            @click="selectedCouponId = c.id"
          >
            <div class="min-w-0">
              <div class="truncate text-sm font-medium">{{ c.couponName }}</div>
              <div class="zx-text-secondary text-xs">
                {{ (c.thresholdAmount ?? 0) > 0 ? `满 ${formatPrice(c.thresholdAmount)} 可用` : '无门槛' }}
              </div>
            </div>
            <span class="font-bold text-primary">
              {{ (c.thresholdAmount ?? 0) > 0 ? `-${formatPrice(c.discountValue)}` : `${Math.round((c.discountValue ?? 0) / 100)} 折` }}
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
</style>
