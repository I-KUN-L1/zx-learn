<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourse } from '@/api/course'
import { mockPayOrder, placeOrder, removeFromCart, boughtCourseIds } from '@/api/trade'
import { useMyCoupons } from '@/composables/useMyCoupons'
import { formatPrice, ORDER_STATUS } from '@/utils/format'
import type { CourseVO, Id } from '@/types/api'

const route = useRoute()
const router = useRouter()

/** 可用（未使用）优惠券：与「优惠券中心」共用同一份全局状态，用券后两边实时联动 */
const { usableCoupons: coupons, refresh: refreshCoupons, markUsed } = useMyCoupons()

const courseIds = computed(() =>
  (route.query.courseIds as string || '').split(',').map(Number).filter(Boolean)
)
const courses = ref<CourseVO[]>([])
/** 选中的用户券行 id（雪花 id 为字符串，见 types/api.ts 的 Id） */
const selectedCouponId = ref<Id | undefined>()
const submitting = ref(false)
const loading = ref(true)

/** 优惠券只能用于单门课程的订单（后端逐门课程一单、每张券限用一次），多课程时禁用 */
const canUseCoupon = computed(() => courses.value.length === 1)

/** 选中的用户券对单门课程的最大可抵金额（按后端固定面值核销 deduction=price-totalFee） */
function discountOf(course: CourseVO): number {
  const uc = coupons.value.find((c) => String(c.id) === String(selectedCouponId.value))
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
    // 防误购：过滤当前学员已拥有的课程（从旧购物车/历史链接进入结算页的场景）
    const ownedIds = await boughtCourseIds()
      .then((ids) => new Set(ids.map(String)))
      .catch(() => new Set<string>())
    const fresh = details.filter((c) => !ownedIds.has(String(c.id)))
    if (fresh.length < details.length) {
      ElMessage.warning(
        details.length - fresh.length === 1
          ? '「' + details.find((c) => ownedIds.has(String(c.id)))?.name + '」已在你的课程库中，已自动移出'
          : `所选课程中 ${details.length - fresh.length} 门已拥有，已自动移出`,
      )
    }
    if (!fresh.length) {
      ElMessage.info('所选课程均已拥有，可直接前往学习中心开始学习')
      router.replace('/learning')
      return
    }
    courses.value = fresh
    // 可用券取自全局单例（券中心用掉后会立即同步，不会出现"券已用还列在可选里"）
    await refreshCoupons()
    // 多课程时不允许用券（后端逐课一单，一张券只能用于一单）
    if (!canUseCoupon.value) selectedCouponId.value = undefined
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

/** 支付成功提示；用户点「开始学习」返回 true（跳转学习中心），关闭/取消返回 false */
function alertPaidSuccess(count = 1) {
  return ElMessageBox.alert(
    count > 1
      ? `已成功支付 ${count} 笔订单，课程已开通，可在「学习中心」开始学习。`
      : '支付成功，课程已开通，可在「学习中心」开始学习。',
    '支付成功',
    { type: 'success', confirmButtonText: '开始学习' },
  )
    .then(() => true)
    .catch(() => false)
}

/** 立即支付：逐笔走 Mock 支付通道（后端复用真实回调链路，流水幂等）。导航在内部完成： */
/** 全部支付成功 → 弹窗确认后跳学习中心或订单列表；全部失败返回 false（留在待支付单） */
async function payNow(orderIds: Id[]) {
  let paid = 0
  for (const id of orderIds) {
    try {
      await mockPayOrder(id)
      paid++
    } catch {
      /* 单笔失败继续，最后统一提示 */
    }
  }
  if (paid === 0) {
    ElMessage.error('支付失败，可在「我的订单」中点击继续支付重试')
    return false
  }
  if (paid < orderIds.length) {
    ElMessage.warning(`已支付 ${paid}/${orderIds.length} 笔，其余可在「我的订单」继续支付`)
  }
  const goLearn = await alertPaidSuccess(paid)
  // 支付成功事件已异步开课（zx-learning 消费），进入学习中心即拉取最新课表
  await router.replace(goLearn ? '/learning' : '/trade/orders?status=2')
  return true
}

/**
 * 提交订单：后端每门课程生成一张订单（雪花单号 + 15 分钟超时关单）。
 * 下单成功后弹出「是否立即支付」：立即支付直接完成支付，稍后支付保持待支付态。
 */
async function onSubmit() {
  // 防重入：按钮 loading 态之外再兜一层（键盘回车/快速连点）
  if (!courses.value.length || submitting.value) return
  submitting.value = true
  try {
    // 选中的用户券：couponId 传"券模板 id"，userCouponId 传"用户券行 id"，与后端下单/核销契约对齐
    const uc = coupons.value.find((c) => String(c.id) === String(selectedCouponId.value))
    const orderIds: Id[] = []
    for (const course of courses.value) {
      const orderId = await placeOrder({
        courseId: course.id,
        // 实付金额：无券 = 课程价（后端默认）；有券 = 课程价 - 抵扣，供后端做金额一致性校验
        totalFee: course.price - discountOf(course),
        couponId: uc?.couponId,
        userCouponId: uc?.id,
      })
      orderIds.push(orderId)
    }

    // 用券成功：立即把该券置为"已使用"，券中心/统计同步更新（随后刷新与服务端对齐）。
    // 后端也在下单核销后同步回写了券状态，此处是同一事实的前端即时呈现。
    if (uc) {
      markUsed(uc.id)
      selectedCouponId.value = undefined
    }

    // 下单成功即从购物车移除（不在购物车中时为幂等空操作）
    await Promise.all(courses.value.map((c) => removeFromCart(c.id).catch(() => null)))

    // 是否立即支付
    let payNowChosen = false
    try {
      await ElMessageBox.confirm(
        `订单已创建（共 ${orderIds.length} 笔）。立即支付即可开通课程；也可稍后支付，订单将在 15 分钟后超时自动关闭。`,
        '是否立即支付？',
        {
          type: 'success',
          confirmButtonText: '立即支付',
          cancelButtonText: '稍后支付',
          distinguishCancelAndClose: true,
        },
      )
      payNowChosen = true
    } catch {
      payNowChosen = false
    }

    if (payNowChosen) {
      const ok = await payNow(orderIds)
      if (ok) return // 成功路径的导航已在 payNow 内完成（学习中心 / 订单列表）
      await router.replace('/trade/orders?pending=1')
      return
    }
    ElMessage.info('已保留待支付订单，可在「我的订单」中继续支付')
    await router.replace('/trade/orders?pending=1')
  } catch {
    /* 全局拦截器已提示 */
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
              'zx-coupon-option--active': String(selectedCouponId) === String(c.id),
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
          提交后会询问是否立即支付；选择稍后支付则订单保持待支付状态，请在 15 分钟内完成支付，
          超时订单将自动关闭并释放优惠券。
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
