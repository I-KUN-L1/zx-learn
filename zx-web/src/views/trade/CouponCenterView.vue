<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { claimCoupon, pageCoupons, seckillClaim, seckillResult } from '@/api/promotion'
import { useMyCoupons } from '@/composables/useMyCoupons'
import { formatPrice } from '@/utils/format'
import CouponList from '@/components/promotion/CouponList.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { CouponVO } from '@/types/api'

const router = useRouter()

/** 我的优惠券：与「确认下单页选券」共用同一份全局状态，用券后两边实时联动 */
const {
  coupons: mine,
  usableCoupons: usableMine,
  claimedCouponIds: claimedIds,
  refresh: refreshMine,
} = useMyCoupons()

const query = reactive({ pageNo: 1, pageSize: 12, type: '' as number | '' })
const coupons = ref<CouponVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
/** 正在领取中的 couponId（字符串化，兼容雪花 id） */
const claimingId = ref('')

/**
 * 已领取且"未使用"的券 id 集合（后端 status 语义：1 未使用 / 2 已使用 / 3 已过期）。
 * 仅该集合中的券展示"去使用"按钮，已使用/已过期不展示。
 */
const usableIds = computed(
  () => new Set(usableMine.value.map((m) => String(m.couponId))),
)

async function fetchCoupons() {
  loading.value = true
  try {
    const res = await pageCoupons({ ...query })
    // 防御：接口异常结构（list 缺失）时回退空列表，避免 undefined 导致渲染崩溃
    coupons.value = res?.list ?? []
    total.value = res?.total ?? 0
    pages.value = res?.pages ?? 0
  } catch {
    coupons.value = []
  } finally {
    loading.value = false
  }
}

/** 拉取我的优惠券：每次进入页面都重新拉取，保证与后端权威状态一致（如刚下单用掉的券） */
function fetchMine() {
  return refreshMine(true)
}

/** 跳转课程界面使用优惠券 */
function onUse() {
  router.push('/courses')
}

/** 统一领取入口：type=2 走秒杀 + 结果轮询，其余走普通领券 */
async function onClaim(c: CouponVO) {
  if (claimingId.value) return
  claimingId.value = String(c.id)
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
    claimingId.value = ''
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
      :usable-ids="usableIds"
      :claiming-id="claimingId"
      empty-text="暂无可领优惠券，敬请期待"
      @claim="onClaim"
      @use="onUse"
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
      <h2 class="mb-4 flex items-center gap-3 text-xl font-bold">
        我的优惠券
        <span class="zx-text-secondary text-sm font-normal">
          可使用 {{ usableMine.length }} 张
        </span>
      </h2>
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
          <div class="flex shrink-0 flex-col items-end gap-2">
            <el-tag :type="c.status === 1 ? 'primary' : c.status === 2 ? 'success' : 'info'" size="small" round>
              {{ c.status === 1 ? '可使用' : c.status === 2 ? '已使用' : '已过期' }}
            </el-tag>
            <!-- 仅"已领取且未使用"展示去使用 -->
            <el-button v-if="c.status === 1" type="success" size="small" round @click="onUse">
              去使用
            </el-button>
          </div>
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
