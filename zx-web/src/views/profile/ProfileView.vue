<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Calendar, Coin, Document, Medal, Tickets, TrendCharts } from '@element-plus/icons-vue'
import { pointsRank, pointsRecords, pointsSummary } from '@/api/points'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/common/EmptyState.vue'
import type { PointsRankVO, PointsRecordVO, PointsSummaryVO } from '@/types/api'

/**
 * 个人中心。
 *
 * 数据来源全部为「当前登录用户」视角（后端从 JWT 取 userId），前端不传 userId，
 * 不存在越权读取他人积分数据的可能。
 *
 * 「我的数据」= 我的积分 + 我的排名 + 今日/近 7 日增量；
 * 「学习积分排行榜」= 前 10 名 + 本人排名（已在前 10 内则高亮同一行，否则单独置顶展示）；
 * 「积分明细」= 抽屉内的分页流水，来源包含课程学习/测验/签到/讨论。
 */
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const summary = ref<PointsSummaryVO | null>(null)
const top = ref<PointsRankVO[]>([])
const me = ref<PointsRankVO | null>(null)

/** 本人是否已出现在前 10 名里（决定是否需要单独展示"我的排名"条） */
const inTop = computed(() => top.value.some((item) => item.me))

const roleText = computed(() => {
  if (userStore.isAdmin) return '管理员'
  if (userStore.isTeacher) return '教师'
  return '学员'
})

const displayName = computed(() => userStore.username || '知行用户')

/* ---------- 拉取数据 ---------- */
async function fetchData() {
  loading.value = true
  // allSettled：任一接口失败不阻塞其余数据渲染（个人中心首屏不能卡在 loading）
  const [s, r] = await Promise.allSettled([pointsSummary(), pointsRank(10)])
  summary.value = s.status === 'fulfilled' ? (s.value ?? null) : null
  if (r.status === 'fulfilled' && r.value) {
    top.value = r.value.top ?? []
    me.value = r.value.me ?? null
  } else {
    top.value = []
    me.value = null
  }
  loading.value = false
}

/* ---------- 积分明细抽屉 ---------- */
const recordsOpen = ref(false)
const recordsLoading = ref(false)
const records = ref<PointsRecordVO[]>([])
const recordsTotal = ref(0)
const recordsQuery = reactive({ pageNo: 1, pageSize: 10 })

async function fetchRecords() {
  recordsLoading.value = true
  try {
    const res = await pointsRecords({ ...recordsQuery })
    records.value = res.list ?? []
    recordsTotal.value = res.total ?? 0
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    recordsLoading.value = false
  }
}

function openRecords() {
  recordsOpen.value = true
  recordsQuery.pageNo = 1
  fetchRecords()
}

/** 来源 → 标签配色（与后端 source 取值一致） */
const SOURCE_TAG: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  LESSON: 'primary',
  COURSE: 'success',
  QUIZ: 'warning',
  SIGN: 'info',
  DISCUSSION: 'danger',
  REPLY: 'info',
}

/** 前三名奖牌配色 */
const MEDAL_STYLE: Record<number, { bg: string; color: string }> = {
  1: { bg: 'linear-gradient(135deg,#fbbf24,#f59e0b)', color: '#fff' },
  2: { bg: 'linear-gradient(135deg,#cbd5e1,#94a3b8)', color: '#fff' },
  3: { bg: 'linear-gradient(135deg,#fdba74,#ea580c)', color: '#fff' },
}

/** 积分获取规则（与后端 PointsService 常量保持一致，仅作说明展示） */
const RULES = [
  { label: '完成一个小节学习', points: '+10' },
  { label: '完成整门课程', points: '+50' },
  { label: '测验答对一题', points: '+5' },
  { label: '每日签到（按连续天数）', points: '+5 ~ +50' },
  { label: '发布讨论话题', points: '+5' },
  { label: '回复课程讨论', points: '+2' },
]

onMounted(fetchData)
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <div class="mb-5 flex flex-wrap items-center gap-3">
      <h1 class="text-2xl font-bold">个人中心</h1>
      <el-tag type="primary" effect="plain" round>我的积分 · 学习排行 · 积分明细</el-tag>
    </div>

    <!-- 账号信息 -->
    <div class="zx-card overflow-hidden">
      <div class="zx-profile-banner h-24" />
      <div class="flex flex-wrap items-end gap-4 px-5 pb-5">
        <el-avatar :size="76" class="zx-ai-avatar zx-profile-avatar">
          {{ displayName.slice(0, 1) }}
        </el-avatar>
        <div class="min-w-0 pb-1">
          <div class="truncate text-xl font-bold">{{ displayName }}</div>
          <div class="zx-text-secondary mt-1 text-sm">
            {{ roleText }} · 已加入知行智学
          </div>
        </div>
        <div class="ml-auto flex flex-wrap gap-2 pb-1">
          <el-button round @click="router.push('/learning')">学习中心</el-button>
          <el-button v-if="userStore.isStudent" type="primary" round @click="router.push('/insight')">
            我的学情报告
          </el-button>
        </div>
      </div>
    </div>

    <!-- 我的数据 -->
    <div class="zx-card mt-6 p-5">
      <div class="flex flex-wrap items-center gap-3">
        <h2 class="text-lg font-bold">我的数据</h2>
        <span class="zx-text-secondary text-xs">完成课程、参与讨论、完成测验后自动累计</span>
        <el-button class="ml-auto" type="primary" round :icon="Tickets" @click="openRecords">
          积分明细
        </el-button>
      </div>

      <div class="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div class="zx-stat-card">
          <div class="zx-stat-icon" style="background: linear-gradient(135deg, #6366f1, #4f46e5)">
            <el-icon :size="20"><Coin /></el-icon>
          </div>
          <div class="min-w-0">
            <div class="zx-text-secondary text-sm">当前积分</div>
            <div class="truncate text-2xl font-extrabold text-primary">
              {{ summary?.points ?? 0 }}
            </div>
          </div>
        </div>

        <div class="zx-stat-card">
          <div class="zx-stat-icon" style="background: linear-gradient(135deg, #fbbf24, #f59e0b)">
            <el-icon :size="20"><Medal /></el-icon>
          </div>
          <div class="min-w-0">
            <div class="zx-text-secondary text-sm">我的排名</div>
            <div class="truncate text-2xl font-extrabold">
              <template v-if="summary && summary.rank > 0">
                第 {{ summary.rank }} 名
                <span class="zx-text-secondary text-sm font-normal">/ {{ summary.totalUsers }} 人</span>
              </template>
              <template v-else>
                <span class="zx-text-secondary text-base font-normal">暂无排名</span>
              </template>
            </div>
          </div>
        </div>

        <div class="zx-stat-card">
          <div class="zx-stat-icon" style="background: linear-gradient(135deg, #34d399, #10b981)">
            <el-icon :size="20"><Calendar /></el-icon>
          </div>
          <div class="min-w-0">
            <div class="zx-text-secondary text-sm">今日新增</div>
            <div class="truncate text-2xl font-extrabold">
              +{{ summary?.todayPoints ?? 0 }}
            </div>
          </div>
        </div>

        <div class="zx-stat-card">
          <div class="zx-stat-icon" style="background: linear-gradient(135deg, #60a5fa, #3b82f6)">
            <el-icon :size="20"><TrendCharts /></el-icon>
          </div>
          <div class="min-w-0">
            <div class="zx-text-secondary text-sm">近 7 日新增</div>
            <div class="truncate text-2xl font-extrabold">
              +{{ summary?.weekPoints ?? 0 }}
            </div>
          </div>
        </div>
      </div>

      <p v-if="summary && summary.totalUsers > 0 && summary.points > 0" class="zx-text-secondary mt-4 text-xs">
        当前已超越 {{ summary.beatRate }}% 的学员，继续学习可以继续提升排名。
      </p>
    </div>

    <!-- 排行榜 + 规则 -->
    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="zx-card min-w-0 p-5 lg:col-span-2">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="text-lg font-bold">学习积分排行榜</h2>
          <el-tag size="small" effect="plain" round>前 10 名</el-tag>
          <span class="zx-text-secondary text-xs">随学习行为实时更新</span>
        </div>

        <!-- 本人排名：未进前 10 时单独置顶展示，避免用户"找不到自己" -->
        <div v-if="me && !inTop" class="zx-rank-row zx-rank-me mt-4">
          <div class="zx-rank-badge">我的排名</div>
          <div class="min-w-0 flex-1">
            <div class="truncate font-semibold">{{ me.name }}（我）</div>
            <div class="zx-text-secondary text-xs">
              {{ me.points > 0 ? `第 ${me.rank} 名` : '还没有积分记录，去学习中心打卡吧' }}
            </div>
          </div>
          <div class="shrink-0 text-lg font-extrabold text-primary">{{ me.points }}</div>
        </div>

        <EmptyState v-if="!top.length" description="还没有学员获得积分，快去成为第一个吧" size="small" />

        <ul v-else class="mt-4 space-y-2">
          <li
            v-for="item in top"
            :key="String(item.userId)"
            class="zx-rank-row"
            :class="{ 'zx-rank-row--me': item.me }"
          >
            <div
              class="zx-rank-badge"
              :style="MEDAL_STYLE[item.rank] ? { background: MEDAL_STYLE[item.rank].bg, color: MEDAL_STYLE[item.rank].color } : undefined"
            >
              <el-icon v-if="item.rank <= 3" :size="14"><Medal /></el-icon>
              <span v-else>{{ item.rank }}</span>
            </div>
            <div class="min-w-0 flex-1">
              <div class="truncate font-semibold">
                {{ item.name }}
                <span v-if="item.me" class="zx-me-tag">我</span>
              </div>
              <div class="zx-text-secondary text-xs">第 {{ item.rank }} 名</div>
            </div>
            <div class="shrink-0 text-lg font-extrabold" :class="item.me ? 'text-primary' : ''">
              {{ item.points }}
            </div>
          </li>
        </ul>
      </div>

      <div class="zx-card min-w-0 p-5">
        <h2 class="text-lg font-bold">积分怎么获得</h2>
        <p class="zx-text-secondary mt-1 text-xs">积分由服务端自动发放，无需手动领取</p>
        <ul class="mt-4 space-y-3">
          <li v-for="r in RULES" :key="r.label" class="flex items-center justify-between gap-3 text-sm">
            <span class="min-w-0 truncate">{{ r.label }}</span>
            <span class="shrink-0 font-semibold text-primary">{{ r.points }}</span>
          </li>
        </ul>
        <el-button class="mt-5 w-full" round @click="router.push('/learning')">
          去学习赚积分
          <el-icon class="ml-1"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 积分明细抽屉 -->
    <el-drawer v-model="recordsOpen" title="积分明细" size="480px" :destroy-on-close="false">
      <div v-loading="recordsLoading">
        <EmptyState v-if="!records.length && !recordsLoading" description="暂无积分记录" size="small" />
        <ul v-else class="space-y-3">
          <li v-for="rec in records" :key="String(rec.id)" class="zx-record-row">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <el-tag size="small" effect="plain" round :type="SOURCE_TAG[rec.source] ?? 'info'">
                  {{ rec.sourceText }}
                </el-tag>
                <span class="zx-text-secondary text-xs">{{ rec.createTime }}</span>
              </div>
              <div class="mt-1 text-sm leading-6">{{ rec.description || rec.sourceText }}</div>
            </div>
            <div class="shrink-0 text-base font-extrabold text-primary">+{{ rec.points }}</div>
          </li>
        </ul>

        <div v-if="recordsTotal > recordsQuery.pageSize" class="mt-5 flex justify-center">
          <el-pagination
            v-model:current-page="recordsQuery.pageNo"
            :page-size="recordsQuery.pageSize"
            :total="recordsTotal"
            layout="prev, pager, next"
            background
            small
            @current-change="fetchRecords"
          />
        </div>
      </div>
      <template #footer>
        <div class="zx-text-secondary flex items-center gap-2 text-xs">
          <el-icon><Document /></el-icon>
          共 {{ recordsTotal }} 条积分流水
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.zx-profile-banner {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 60%, #a855f7 100%);
}
.zx-profile-avatar {
  margin-top: -38px;
  border: 4px solid var(--zx-bg-card);
  flex-shrink: 0;
}
.zx-stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border-radius: 12px;
  background: var(--zx-primary-bg);
}
.zx-stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: #fff;
}
.zx-rank-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--zx-border);
  transition: all 0.2s;
}
.zx-rank-row:hover {
  background: var(--zx-primary-bg);
}
.zx-rank-row--me {
  border-color: var(--zx-primary);
  background: var(--zx-primary-bg);
}
.zx-rank-me {
  border: 1px dashed var(--zx-primary);
  background: var(--zx-primary-bg);
}
.zx-rank-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-width: 30px;
  height: 30px;
  padding: 0 6px;
  border-radius: 9px;
  background: var(--zx-border);
  color: var(--zx-text-secondary);
  font-size: 13px;
  font-weight: 700;
}
.zx-me-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--zx-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  vertical-align: middle;
}
.zx-record-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--zx-border);
}
</style>
