<script setup lang="ts">
import CouponCard from './CouponCard.vue'
import SkeletonCards from '@/components/common/SkeletonCards.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { CouponVO } from '@/types/api'

/**
 * 优惠券列表（数据感知组件，可复用）。
 * 负责：加载骨架屏、空态、栅格布局、领取按钮与领取中状态。
 * 领取动作通过 emit('claim', coupon) 交由外部完成（区分普通领券 / 秒杀轮询）。
 */
const props = withDefaults(
  defineProps<{
    coupons: CouponVO[]
    loading?: boolean
    emptyText?: string
    /** 已领取的 couponId 集合 */
    claimedIds?: Set<number>
    /** 正在领取中的 couponId */
    claimingId?: number
    /** 主按钮文案（默认为"立即领取"） */
    claimText?: string
  }>(),
  {
    loading: false,
    emptyText: '暂无可领优惠券，敬请期待',
    claimedIds: () => new Set(),
    claimingId: 0,
    claimText: '立即领取',
  }
)

const emit = defineEmits<{
  (e: 'claim', coupon: CouponVO): void
}>()

/** 发放状态文案：非发放中（status!==1）无需显示领取按钮 */
function statusText(coupon: CouponVO): string {
  return coupon.status === 3 ? '未开始' : coupon.status === 2 ? '已暂停' : ''
}
</script>

<template>
  <SkeletonCards v-if="loading" :count="6" />
  <EmptyState v-else-if="!coupons.length" :description="emptyText" />
  <div v-else class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
    <CouponCard v-for="c in coupons" :key="c.id" :coupon="c" :claimed="claimedIds.has(c.id)">
      <template #footer>
        <!-- 底部操作区：未领取且有资格才显示领取按钮 -->
        <div
          v-if="!claimedIds.has(c.id) && !statusText(c)"
          class="absolute bottom-3 right-3"
        >
          <el-button
            type="primary"
            size="small"
            round
            :loading="claimingId === c.id"
            :disabled="(c.remainNum ?? 0) <= 0"
            @click.stop="emit('claim', c)"
          >
            {{ claimText }}
          </el-button>
        </div>
      </template>
    </CouponCard>
  </div>
</template>