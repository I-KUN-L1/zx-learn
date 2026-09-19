<script setup lang="ts">
import { computed } from 'vue'
import { formatPrice } from '@/utils/format'
import type { CouponVO } from '@/types/api'

/**
 * 优惠券卡片（展示组件，可复用）。
 * 泡泡/票据样式：左侧金额区 + 右侧信息（名称、门槛、有效期、剩余），底部托管给默认插槽（领取按钮等）。
 */
const props = defineProps<{
  coupon: CouponVO
  /** 是否已被当前用户领取（展示"已领取"标记） */
  claimed?: boolean
  /** 已领取且未使用（展示"去使用"入口） */
  usable?: boolean
}>()

/** 是否满减券（有门槛） */
const hasThreshold = computed(() => (props.coupon.thresholdAmount ?? 0) > 0)

/** 金额主文案 */
const amountText = computed(() =>
  hasThreshold.value ? `¥${formatPrice(props.coupon.discountValue)}` : `立减¥${formatPrice(props.coupon.discountValue)}`
)

/** 副标题：满减门槛 / 无门槛 */
const subText = computed(() =>
  hasThreshold.value ? `满${formatPrice(props.coupon.thresholdAmount)}可用` : '无门槛'
)

/** 发放状态文案（对齐后端状态机：0 未开始 / 2 已结束 / 3 已下架） */
const statusText = computed(() => {
  switch (props.coupon.status) {
    case 0:
      return '未开始'
    case 2:
      return '已结束'
    case 3:
      return '已下架'
    default:
      return ''
  }
})

/** 是否还有余量 */
const soldOut = computed(() => (props.coupon.remainNum ?? 0) <= 0)
</script>

<template>
  <article
    class="zx-card zx-coupon-card relative flex min-h-[132px] overflow-hidden"
    :class="{ 'zx-coupon-card--disabled': !usable && (statusText || soldOut || claimed) }"
  >
    <!-- 左侧金额区 -->
    <div class="zx-coupon-amount flex flex-col items-center justify-center text-center text-white">
      <div class="zx-coupon-amount__main">{{ amountText }}</div>
      <div class="zx-coupon-amount__sub">{{ subText }}</div>
    </div>

    <!-- 右侧信息区 -->
    <div class="min-w-0 flex-1 p-4 pl-4">
      <h3 class="truncate text-base font-semibold text-gray-900">{{ coupon.name }}</h3>
      <p class="zx-text-secondary mt-1 text-xs leading-5">
        剩余 {{ coupon.remainNum }}/{{ coupon.totalNum }} 张
      </p>
      <p class="zx-text-secondary mt-1 text-xs">
        发放 {{ (coupon.issueBeginTime || '').slice(0, 10) }} ~ {{ (coupon.issueEndTime || '').slice(0, 10) }}
      </p>
    </div>

    <!-- 右上角状态 -->
    <el-tag
      v-if="usable"
      type="success"
      effect="dark"
      size="small"
      class="absolute right-3 top-3"
      round
    >
      可使用
    </el-tag>
    <el-tag
      v-else-if="claimed"
      type="success"
      effect="dark"
      size="small"
      class="absolute right-3 top-3"
      round
    >
      已领取
    </el-tag>
    <el-tag v-else-if="statusText" type="warning" size="small" class="absolute right-3 top-3" round>
      {{ statusText }}
    </el-tag>
    <el-tag v-else-if="soldOut" type="info" size="small" class="absolute right-3 top-3" round>
      已抢光
    </el-tag>

    <!-- 底部操作区（默认插槽，供调用方放置领取按钮等） -->
    <slot name="footer" />
  </article>
</template>

<style scoped>
.zx-coupon-card--disabled {
  opacity: 0.55;
}
.zx-coupon-amount {
  width: 112px;
  align-self: stretch;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  flex-shrink: 0;
  padding: 12px 6px;
}
.zx-coupon-amount__main {
  font-size: 17px;
  font-weight: 800;
  line-height: 1.2;
}
.zx-coupon-amount__sub {
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.9;
}
</style>