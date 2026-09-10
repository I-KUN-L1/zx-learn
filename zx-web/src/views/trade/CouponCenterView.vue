<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { claimCoupon, myCoupons, pageCoupons, seckillClaim, seckillResult } from '@/api/promotion'
import { formatPrice } from '@/utils/format'
import CouponList from '@/components/promotion/CouponList.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { CouponVO, UserCouponVO } from '@/types/api'

const query = reactive({ pageNo: 1, pageSize: 12, type: '' as number | '' })
const coupons = ref<CouponVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const mine = ref<UserCouponVO[]>([])
/** 正在领取中的 couponId */
const claimingId = ref<number>(0)

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

/** 统一领取入口：type=2 走秒杀 + 结果轮询，其余走普通领券 */
async function onClaim(c: CouponVO) {
  if (claimingId.value) return
  claimingId.value = c.id
  try {
    if (c.type === 2) {
      // 秒杀：Lua 原子预扣 → MQ 异步落库 → 轮询结果（对齐后端真实链路）
      await seckillClaim(c.id)
      for (let i = 0; i < 6; i++) {
        await new Promise((resolve) => setTimeout(resolve, 2000))
        const res = await seckillResult(c.id)
        if (res.success || res.status === 'SUCCESS') {
          ElMessageBox.alert(`恭喜你成功抢到「${c.name}」！`, '秒杀成功', {
            confirmButtonText: '太好了',
            type: 'success',
          })
          break
        } else if (res.status === 'QUEUING') {
          continue
        } else {
          const msg: Record<string, string> = {
            SOLD_OUT: '手慢了，库存已抢光',
            REPEAT: '你已经领取过该秒杀券了',
            FAILED: '领取失败，请稍后再试',
            NOT_READY: '秒杀活动尚未开始',
          }
          ElMessage.warning(msg[res.status as string] || '未抢到本次秒杀')
          break
        }
      }
    } else {
      await claimCoupon(c.id)
      ElMessage.success('领取成功，快去下单使用吧')
    }
  } catch {
    /* 全局拦截器已提示 */
  } finally {
    claimingId.value = 0
    await fetchMine()
  }
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

    <CouponList
      :coupons="coupons"
      :loading="loading"
      :claimed-ids="claimedIds"
      :claiming-id="claimingId"
      empty-text="暂无可领优惠券，敬请期待"
      @claim="onClaim"
    />

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
