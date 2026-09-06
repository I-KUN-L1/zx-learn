<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { pageCoupons, myCoupons } from '@/api/promotion'
import { formatPrice } from '@/utils/format'
import SeckillCard from '@/components/promotion/SeckillCard.vue'
import SkeletonCards from '@/components/common/SkeletonCards.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { CouponVO, UserCouponVO } from '@/types/api'

const query = reactive({ pageNo: 1, pageSize: 12, type: '' as number | '' })
const coupons = ref<CouponVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const mine = ref<UserCouponVO[]>([])

/** 领取状态（本地映射：couponId -> 已领取） */
const claimedIds = computed(() => new Set(mine.value.map((m) => m.couponId)))

async function fetchCoupons() {
  loading.value = true
  try {
    const res = await pageCoupons({ ...query })
    coupons.value = res.list
    total.value = res.total
    pages.value = res.pages
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function fetchMine() {
  try {
    mine.value = await myCoupons()
  } catch {
    /* ignore */
  }
}

async function onClaimed() {
  await fetchMine()
}

onMounted(() => {
  fetchCoupons()
  fetchMine()
})
</script>

<template>
  <div class="zx-page">
    <div class="mb-5 flex flex-wrap items-center gap-4">
      <h1 class="text-2xl font-bold">优惠券中心</h1>
      <div class="ml-auto flex items-center gap-2">
        <el-radio-group v-model="query.type" round @change="query.pageNo = 1; fetchCoupons()">
          <el-radio-button :value="''">全部</el-radio-button>
          <el-radio-button :value="2">秒杀专区</el-radio-button>
          <el-radio-button :value="1">普通券</el-radio-button>
        </el-radio-group>
        <el-button round @click="fetchMine(); fetchCoupons()">刷新</el-button>
      </div>
    </div>

    <SkeletonCards v-if="loading" :count="6" />
    <EmptyState v-else-if="!coupons.length" description="暂无可领优惠券，敬请期待" />
    <div v-else class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="c in coupons" :key="c.id" class="relative">
        <SeckillCard :coupon="c" @claimed="onClaimed" />
        <el-tag
          v-if="claimedIds.has(c.id)"
          type="success"
          effect="dark"
          size="small"
          class="absolute left-4 top-4 z-10"
          round
        >
          已领取
        </el-tag>
      </div>
    </div>

    <div v-if="pages > 1" class="mt-8 flex justify-center">
      <el-pagination
        v-model:current-page="query.pageNo"
        :page-size="query.pageSize"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="fetchCoupons"
      />
    </div>

    <!-- 我的优惠券 -->
    <section class="mt-12">
      <h2 class="mb-4 text-xl font-bold">我的优惠券</h2>
      <EmptyState v-if="!mine.length" description="还没有优惠券，去上面领取吧" size="small" />
      <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="c in mine"
          :key="c.id"
          class="zx-card flex items-center gap-4 p-4"
          :style="c.status !== 1 ? 'opacity:0.55' : ''"
        >
          <div class="zx-coupon-left text-center text-white">
            <div class="text-lg font-extrabold">
              {{ (c.thresholdAmount ?? 0) > 0 ? `¥${formatPrice(c.discountValue)}` : `${Math.round((c.discountValue ?? 0) / 100)}折` }}
            </div>
            <div class="text-[10px]">{{ (c.thresholdAmount ?? 0) > 0 ? `满${formatPrice(c.thresholdAmount)}可用` : '无门槛' }}</div>
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-medium">{{ c.couponName }}</div>
            <div class="zx-text-secondary mt-1 text-xs">{{ c.createTime }}</div>
          </div>
          <el-tag :type="c.status === 1 ? 'primary' : c.status === 2 ? 'success' : 'info'" size="small" round>
            {{ c.status === 1 ? '可使用' : c.status === 2 ? '已使用' : '已过期' }}
          </el-tag>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.zx-coupon-left {
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 96px;
  align-self: stretch;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  padding: 10px 6px;
}
</style>
