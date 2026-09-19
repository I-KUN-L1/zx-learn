import { computed, ref } from 'vue'
import { myLessonCourseIds } from '@/api/learning'
import { useUserStore } from '@/stores/user'
import type { Id } from '@/types/api'

/**
 * 「已拥有」课程状态（全局单例）。
 *
 * 权威口径：**我的课表（lesson）里有 = 已拥有；没有 = 可购买**。
 * 课程列表、课程详情、「我的课表」检索三处共用这一份数据，因此：
 *
 * - 三端判定规则完全一致（不会出现"课程界面显示已拥有、我的课表里却没有"）；
 * - 任一入口完成开课后调用 `markOwned` / `refresh`，其余入口立即同步（SPA 内实时联动）。
 *
 * 数据来源是学员端接口 `/lessons/mine/course-ids`（只返回本人在课表中的课程 id），
 * 不做"已支付订单 ∪ 课表"的并集——那个并集会在支付成功而课表尚未开通的窗口期里
 * 让课程界面比我的课表"多出"已拥有状态，正是两端不一致的根源。
 */

/** 已拥有课程 id 集合（统一转字符串比较，规避后端 Long → string 的精度序列化） */
const ownedIds = ref<Set<string>>(new Set())
/** 是否已成功拉取过一次（用于区分"还没拉到"与"确实没有"） */
const loaded = ref(false)
const loading = ref(false)
/** 并发去重：多个页面同时挂载只发一次请求 */
let inflight: Promise<void> | null = null

export function useOwnedCourses() {
  const userStore = useUserStore()

  /** 拉取（学员登录态下）；force=true 时忽略进行中的请求重新拉取 */
  async function refresh(force = false): Promise<void> {
    if (!userStore.isLoggedIn || !userStore.isStudent) {
      ownedIds.value = new Set()
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
        const ids = await myLessonCourseIds()
        ownedIds.value = new Set((ids ?? []).map((id) => String(id)))
        loaded.value = true
      } catch {
        // 拉取失败保持既有结果：宁可短暂沿用旧状态，也不要让角标闪烁消失
      } finally {
        loading.value = false
        inflight = null
      }
    })()
    return inflight
  }

  function isOwned(courseId: Id | null | undefined): boolean {
    if (courseId == null) return false
    return ownedIds.value.has(String(courseId))
  }

  /** 开课成功后立即置为已拥有（乐观更新，随后 refresh 与服务端对齐） */
  function markOwned(courseId: Id) {
    const next = new Set(ownedIds.value)
    next.add(String(courseId))
    ownedIds.value = next
    loaded.value = true
  }

  /** 已拥有课程数（用于「我的课表」统计条） */
  const ownedCount = computed(() => ownedIds.value.size)

  return { ownedIds, ownedCount, loaded, loading, refresh, isOwned, markOwned }
}
