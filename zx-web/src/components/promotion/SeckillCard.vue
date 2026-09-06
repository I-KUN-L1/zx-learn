<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lightning } from '@element-plus/icons-vue'
import { formatPrice } from '@/utils/format'
import { useCountdown } from '@/composables/useCountdown'
import { claimCoupon, seckillClaim, seckillResult } from '@/api/promotion'
import type { CouponVO } from '@/types/api'

const props = defineProps<{ coupon: CouponVO }>()
const emit = defineEmits<{ claimed: [] }>()

const claiming = ref(false)

/** 秒杀开始倒计时（未开始时展示） */
const issueBegin = new Date(props.coupon.issueBeginTime.replace(/-/g, '/')).getTime()
const now = Date.now()
const startInSeconds = Math.max(0, Math.floor((issueBegin - now) / 1000))
const { formatted, start } = useCountdown(startInSeconds)

onMounted(() => {
  if (startInSeconds > 0) start()
})

const isSeckill = computed(() => props.coupon.type === 2)
const notStarted = computed(() => props.coupon.status === 3 || startInSeconds > 0)
const soldOut = computed(() => props.coupon.remainNum <= 0)
/** 已抢进度百分比 */
const progress = computed(() => {
  const total = props.coupon.totalNum || 1
  return Math.min(100, Math.round(((total - props.coupon.remainNum) / total) * 100))
})
/** 折扣展示文本 */
const discountText = computed(() => {
  if (props.coupon.thresholdAmount > 0) {
    return `满 ${formatPrice(props.coupon.thresholdAmount)} 减 ${formatPrice(props.coupon.discountValue)}`
  }
  const percent = Math.round(props.coupon.discountValue / 100)
  return `${percent} 折无门槛券`
})

/** 领取/抢购 */
async function onClaim() {
  if (notStarted.value) {
    ElMessage.info(`活动尚未开始，${isSeckill.value ? `${formatted.value} 后开抢` : '敬请期待'}`)
    return
  }
  if (claiming.value || soldOut.value) return
  claiming.value = true
  try {
    if (isSeckill.value) {
      // 秒杀：先抢后轮询结果（对齐后端异步秒杀链路）
      await seckillClaim(props.coupon.id)
      const res = await seckillResult(props.coupon.id)
      if (res.success) {
        ElMessageBox.alert(`恭喜你成功抢到「${props.coupon.name}」！`, '秒杀成功', {
          confirmButtonText: '太好了',
          type: 'success',
        })
        emit('claimed')
      } else {
        ElMessage.warning('手慢了，未抢到本次秒杀')
      }
    } else {
      await claimCoupon(props.coupon.id)
      ElMessage.success('领取成功，快去下单使用吧')
      emit('claimed')
    }
  } catch {
    /* 全局拦截器已提示（库存不足 / 限流等） */
  } finally {
    claiming.value = false
  }
}
</script>

<template>
  <div
    class="zx-card relative overflow-hidden p-5"
    :class="isSeckill ? 'zx-seckill-card' : ''"
  >
    <div v-if="isSeckill" class="absolute right-0 top-0 zx-seckill-badge">
      <el-icon><Lightning /></el-icon>
      秒杀
    </div>

    <div class="flex items-start justify-between gap-4">
      <div>
        <h3 class="text-base font-bold">{{ coupon.name }}</h3>
        <p class="zx-text-secondary mt-1 text-xs">
          领取时间：{{ coupon.issueBeginTime }} ~ {{ coupon.issueEndTime }}
        </p>
      </div>
      <div class="text-right">
        <div class="text-xl font-extrabold text-primary">{{ discountText }}</div>
        <div class="zx-text-secondary mt-1 text-xs">
          剩余 <span class="font-bold text-red-500">{{ coupon.remainNum }}</span> / {{ coupon.totalNum }} 张
        </div>
      </div>
    </div>

    <!-- 已抢进度条 -->
    <el-progress
      :percentage="progress"
      :stroke-width="10"
      :show-text="false"
      class="mt-4"
      :color="isSeckill ? '#ef4444' : '#4F46E5'"
    />
    <div class="zx-text-secondary mt-1 flex justify-between text-xs">
      <span>已抢 {{ progress }}%</span>
      <span v-if="isSeckill && notStarted">⏰ 开抢倒计时 {{ formatted }}</span>
      <span v-else-if="soldOut" class="font-bold text-red-500">已抢光</span>
      <span v-else-if="isSeckill" class="text-red-500">手快有，手慢无！</span>
    </div>

    <div class="mt-4 flex justify-end">
      <el-button
        type="primary"
        round
        :loading="claiming"
        :disabled="soldOut || props.coupon.status === 2"
        :class="{ 'zx-seckill-btn': isSeckill && !soldOut }"
        @click="onClaim"
      >
        {{ soldOut ? '已抢光' : notStarted ? '即将开始' : isSeckill ? '立即抢购' : props.coupon.status === 2 ? '暂停发放' : '立即领取' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.zx-seckill-card {
  border: 1px solid rgba(239, 68, 68, 0.35);
  background: linear-gradient(135deg, rgba(254, 242, 242, 0.6), rgba(255, 255, 255, 0.9));
}
html.dark .zx-seckill-card {
  background: linear-gradient(135deg, rgba(127, 29, 29, 0.25), rgba(31, 41, 55, 0.9));
}
.zx-seckill-badge {
  display: flex;
  align-items: center;
  gap: 2px;
  background: linear-gradient(135deg, #ef4444, #f97316);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px 4px 10px;
  border-bottom-left-radius: 12px;
}
.zx-seckill-btn {
  background: linear-gradient(135deg, #f97316, #ef4444);
  border: none;
  font-weight: 700;
  animation: zx-pulse 1.2s ease-in-out infinite;
}
@keyframes zx-pulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}
</style>
