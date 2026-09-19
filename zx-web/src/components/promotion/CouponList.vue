<script setup lang="ts">
import CouponCard from './CouponCard.vue'
import SkeletonCards from '@/components/common/SkeletonCards.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { CouponVO } from '@/types/api'

/**
 * 优惠券列表（数据感知组件，可复用）。
 * 负责：加载骨架屏、空态、栅格布局、领取/去使用按钮与领取中状态。
 * 领取动作通过 emit('claim', coupon) 交由外部完成（区分普通领券 / 秒杀轮询）。
 */
const props = withDefaults(
  defineProps<{
    coupons: CouponVO[]
    loading?: boolean
    emptyText?: string
    /** 已领取的 couponId 集合（字符串化比较，兼容雪花 id） */
    claimedIds?: Set<string>
    /** 已领取且"未使用"的 couponId 集合：仅这些券展示"去使用" */
    usableIds?: Set<string>
    /** 正在领取中的 couponId */
    claimingId?: string
    /** 主按钮文案（默认为"立即领取"） */
    claimText?: string
  }>(),
  {
    loading: false,
    emptyText: '暂无可领优惠券，敬请期待',
    claimedIds: () => new Set(),
    usableIds: () => new Set(),
    claimingId: '',
    claimText: '立即领取',
  }
)

const emit = defineEmits<{
  (e: 'claim', coupon: CouponVO): void
  (e: 'use', coupon: CouponVO): void
}>()

/**
 * 券模板状态文案（对齐后端 CouponService 状态机）。
 * 仅 status===1（进行中）才允许领取，其余状态一律不展示领取按钮，
 * 避免用户点击后命中"优惠券未在进行中"之类的失败提示。
 */
function statusText(coupon: CouponVO): string {
  switch (coupon.status) {
    case 0:
      return '未开始'
    case 2:
      return '已结束'
    case 3:
      return '已下架'
    default:
      return ''
  }
}

/** 是否可领取：进行中 + 未领取 + 尚有余额 */
function claimable(coupon: CouponVO): boolean {
  return (
    coupon.status === 1 &&
    !props.claimedIds.has(String(coupon.id)) &&
    (coupon.remainNum ?? 0) > 0
  )
}
</script>

<template>
  <SkeletonCards v-if="loading" :count="6" />
  <EmptyState v-else-if="!coupons.length" :description="emptyText" />
  <div v-else class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
    <CouponCard
      v-for="c in coupons"
      :key="c.id"
      :coupon="c"
      :claimed="claimedIds.has(String(c.id))"
      :usable="usableIds.has(String(c.id))"
    >
      <template #footer>
        <div class="absolute bottom-3 right-3">
          <!-- 已领取且未使用：去使用（跳转课程界面） -->
          <el-button
            v-if="usableIds.has(String(c.id))"
            type="success"
            size="small"
            round
            @click.stop="emit('use', c)"
          >
            去使用
          </el-button>
          <!-- 未领取且进行中：立即领取 -->
          <el-button
            v-else-if="claimable(c)"
            type="primary"
            size="small"
            round
            :loading="claimingId === String(c.id)"
            @click.stop="emit('claim', c)"
          >
            {{ claimText }}
          </el-button>
          <!-- 非进行中且未领取：提示状态，不可领取 -->
          <el-tag v-else-if="statusText(c) && !claimedIds.has(String(c.id))" type="info" size="small" round>
            {{ statusText(c) }}
          </el-tag>
        </div>
      </template>
    </CouponCard>
  </div>
</template>
