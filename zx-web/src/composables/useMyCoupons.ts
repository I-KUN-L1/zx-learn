import { computed, ref } from 'vue'
import { myCoupons } from '@/api/promotion'
import { useUserStore } from '@/stores/user'
import type { Id, UserCouponVO } from '@/types/api'

/**
 * 「我的优惠券」状态（全局单例）。
 *
 * 权威口径：**券是否可用只看 user_coupon.status（1 未使用 / 2 已使用 / 3 已过期）**。
 * 优惠券中心列表、确认下单页选券两处共用这一份数据，因此：
 *
 * - 两处判定规则完全一致（不会出现"下单页说券不能用、券中心还显示未使用"）；
 * - 用券成功后调用 `markUsed`，两处立即同步（SPA 内实时联动），随后 `refresh` 与服务端对齐。
 *
 * 后端侧同样做了状态回写：下单核销后由 zx-trade 同步回写 promotion 的 user_coupon.status
 * （MQ 与对账任务兜底），所以任何一次刷新拿到的都是真实状态，而不是靠前端"猜"。
 */

/** 我的全部优惠券 */
const coupons = ref<UserCouponVO[]>([])
/** 是否已成功拉取过一次（用于区分"还没拉到"与"确实没有"） */
const loaded = ref(false)
const loading = ref(false)
/** 并发去重：多个页面同时挂载只发一次请求 */
let inflight: Promise<void> | null = null

/** 状态语义：1 未使用 / 2 已使用 / 3 已过期 */
const STATUS_UNUSED = 1

export function useMyCoupons() {
  const userStore = useUserStore()

  /** 拉取（学员登录态下）；force=true 时忽略进行中的请求重新拉取 */
  async function refresh(force = false): Promise<void> {
    if (!userStore.isLoggedIn || !userStore.isStudent) {
      coupons.value = []
      loaded.value = true
      return
    }
    const current = inflight
    if (current && !force) {
      return current
    }
    loading.value = true
    inflight = (async () => {
      try {
        const list = await myCoupons()
        coupons.value = Array.isArray(list) ? list : []
        loaded.value = true
      } catch {
        // 拉取失败保持既有结果：宁可短暂沿用旧状态，也不要让券列表闪烁消失
      } finally {
        loading.value = false
        inflight = null
      }
    })()
    return inflight
  }

  /** 可用（未使用）的券：券中心"去使用"与下单页选券共用同一判定 */
  const usableCoupons = computed(() => coupons.value.filter((c) => c.status === STATUS_UNUSED))

  /** 可用券数量（券中心"可使用 N 张"） */
  const usableCount = computed(() => usableCoupons.value.length)

  /** 已领取的券模板 id 集合（券中心"已领取"标记用，字符串化以兼容雪花 id） */
  const claimedCouponIds = computed(() => new Set(coupons.value.map((c) => String(c.couponId))))

  function setStatus(userCouponId: Id, status: number) {
    coupons.value = coupons.value.map((c) =>
      String(c.id) === String(userCouponId) ? { ...c, status } : c,
    )
  }

  /** 用券成功后立即置为「已使用」（乐观更新，随后 refresh 与服务端对齐） */
  function markUsed(userCouponId: Id) {
    if (userCouponId == null) return
    setStatus(userCouponId, 2)
  }

  /** 订单关单 / 取消退回后恢复为「未使用」 */
  function markUnused(userCouponId: Id) {
    if (userCouponId == null) return
    setStatus(userCouponId, STATUS_UNUSED)
  }

  return {
    coupons,
    usableCoupons,
    usableCount,
    claimedCouponIds,
    loaded,
    loading,
    refresh,
    markUsed,
    markUnused,
  }
}
