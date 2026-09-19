<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Clock, Medal, TrendCharts } from '@element-plus/icons-vue'
import { myProfile, learningPath, latestReport } from '@/api/insight'
import { useEcharts } from '@/composables/useEcharts'
import { formatMinutes } from '@/utils/format'
import type { EChartsOption } from '@/utils/echarts'
import type { InsightProfileVO, LearningPathVO } from '@/types/api'

const router = useRouter()

const profile = ref<InsightProfileVO | null>(null)
const path = ref<LearningPathVO | null>(null)
const report = ref<{ content: string; createTime: string } | null>(null)
const loading = ref(true)

/**
 * 空态兜底数据：单个接口失败/返回空时仍有内容可渲染，
 * 杜绝「一直处于加载界面」——即使某个下游接口异常超时也不阻塞整页。
 */
const EMPTY_PROFILE: InsightProfileVO = {
  userId: 0,
  totalDuration: 0,
  completedRate: 0,
  continuousDays: 0,
  abilities: [
    { name: '学习投入度', value: 0 },
    { name: '学习完成度', value: 0 },
    { name: '答题能力', value: 0 },
    { name: '知识广度', value: 0 },
    { name: '综合理解力', value: 0 },
  ],
  trends: Array.from({ length: 7 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - (6 - i))
    const date = `${d.getMonth() + 1}/${d.getDate()}`
    return { date, duration: 0 }
  }),
}

const EMPTY_PATH: LearningPathVO = { reason: '完成几门课程后为你生成个性化学习路径', steps: [] }

/**
 * 加载告警：某个子接口失败时不再静默渲染成全 0（容易被误认为"数据本来就是 0"），
 * 而是显式提示哪一块数据没取到，便于用户刷新重试。
 */
const loadWarnings = ref<string[]>([])

onMounted(async () => {
  // allSettled：任一请求失败不阻塞其余请求，保证页面稳定出图、永不卡在 loading
  const [p, lp, rp] = await Promise.allSettled([myProfile(), learningPath(), latestReport()])
  const warnings: string[] = []
  if (p.status === 'rejected') warnings.push('能力画像')
  if (lp.status === 'rejected') warnings.push('学习路径推荐')
  if (rp.status === 'rejected') warnings.push('最新学情点评')

  profile.value = p.status === 'fulfilled' ? (p.value ?? EMPTY_PROFILE) : EMPTY_PROFILE
  path.value = lp.status === 'fulfilled' ? (lp.value ?? EMPTY_PATH) : EMPTY_PATH
  // 后端 ReportVO 无 content 字段，正文取 summary；时间回退为 reportDate
  let reportVal: { content: string; createTime: string } | null = null
  if (rp.status === 'fulfilled' && rp.value) {
    const v = rp.value
    reportVal = { content: v.summary ?? '暂无AI学情点评', createTime: v.reportDate ?? v.createTime ?? '' }
  }
  report.value = reportVal
  loadWarnings.value = warnings
  loading.value = false
})

/* ---------- 雷达图：能力画像 ---------- */
const radarOption = computed<EChartsOption | undefined>(() => {
  if (!profile.value) return undefined
  return {
    tooltip: {},
    radar: {
      indicator: profile.value.abilities.map((a) => ({ name: a.name, max: 100 })),
      radius: '65%',
      splitNumber: 4,
      axisName: { color: 'var(--zx-text-secondary)', fontSize: 12 },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: profile.value.abilities.map((a) => a.value),
            name: '能力画像',
            areaStyle: { color: 'rgba(79, 70, 229, 0.25)' },
            lineStyle: { color: '#4F46E5', width: 2 },
            itemStyle: { color: '#4F46E5' },
          },
        ],
      },
    ],
  }
})

/* ---------- 折线图：学情趋势 ---------- */
const lineOption = computed<EChartsOption | undefined>(() => {
  if (!profile.value) return undefined
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: profile.value.trends.map((t) => t.date),
      axisLine: { lineStyle: { color: '#9ca3af' } },
    },
    yAxis: {
      type: 'value',
      name: '分钟',
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } },
    },
    series: [
      {
        name: '学习时长',
        type: 'line',
        smooth: true,
        data: profile.value.trends.map((t) => t.duration),
        lineStyle: { color: '#4F46E5', width: 3 },
        itemStyle: { color: '#4F46E5' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(79,70,229,0.35)' },
              { offset: 1, color: 'rgba(79,70,229,0.02)' },
            ],
          },
        },
      },
    ],
  }
})

const radarEl = ref<HTMLElement>()
const lineEl = ref<HTMLElement>()
useEcharts(radarEl, radarOption)
useEcharts(lineEl, lineOption)
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <div class="mb-5 flex items-center gap-3">
      <h1 class="text-2xl font-bold">学情报告</h1>
      <el-tag type="primary" effect="plain" round>知 · 学 · 行 · 评 · 闭环</el-tag>
    </div>

    <el-alert
      v-if="loadWarnings.length"
      class="mb-5"
      type="warning"
      show-icon
      :closable="false"
      title="部分学情数据暂未取到"
      :description="`${loadWarnings.join('、')} 加载失败（已用空数据兜底展示）。可稍后刷新页面重试。`"
    />

    <!-- 总览指标卡 -->
    <div class="grid grid-cols-1 gap-5 sm:grid-cols-3">
      <div class="zx-card flex items-center gap-4 overflow-hidden p-5">
        <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl" style="background: var(--zx-primary-bg); color: var(--zx-primary)">
          <el-icon :size="24"><Clock /></el-icon>
        </div>
        <div class="min-w-0">
          <div class="zx-text-secondary text-sm">累计学习时长</div>
          <div class="truncate text-2xl font-extrabold">{{ profile ? formatMinutes(profile.totalDuration) : '--' }}</div>
        </div>
      </div>
      <div class="zx-card flex items-center gap-4 overflow-hidden p-5">
        <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl" style="background: var(--zx-primary-bg); color: var(--zx-primary)">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div class="min-w-0">
          <div class="zx-text-secondary text-sm">课程完成率</div>
          <div class="truncate text-2xl font-extrabold">{{ profile?.completedRate ?? '--' }}%</div>
        </div>
      </div>
      <div class="zx-card flex items-center gap-4 overflow-hidden p-5">
        <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl" style="background: var(--zx-primary-bg); color: var(--zx-primary)">
          <el-icon :size="24"><Medal /></el-icon>
        </div>
        <div class="min-w-0">
          <div class="zx-text-secondary text-sm">连续打卡</div>
          <div class="truncate text-2xl font-extrabold">{{ profile?.continuousDays ?? '--' }} 天</div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div class="zx-card overflow-hidden p-5">
        <h2 class="font-bold">能力画像</h2>
        <div ref="radarEl" class="zx-chart-box mt-2 h-[300px]" />
      </div>
      <div class="zx-card overflow-hidden p-5">
        <h2 class="font-bold">近 7 日学习趋势</h2>
        <div ref="lineEl" class="zx-chart-box mt-2 h-[300px]" />
      </div>
    </div>

    <!-- 学习路径推荐 + 报告 -->
    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="zx-card min-w-0 overflow-hidden p-5 lg:col-span-2">
        <h2 class="font-bold">学习路径推荐</h2>
        <p class="zx-text-secondary mt-1 break-words text-sm">{{ path?.reason }}</p>
        <!-- 双保险：外层 overflow 隐藏 + 步骤文本强制换行（全局样式已兜底），
             避免长课程名把卡片撑破（原先表现为图表/内容溢出显示区域） -->
        <div v-if="path?.steps?.length" class="mt-5 min-w-0">
          <el-steps direction="vertical" :active="0" finish-status="finish">
            <el-step v-for="s in path.steps" :key="s.order" :title="s.courseName" :description="s.reason" status="wait">
              <template #icon>
                <div class="zx-step-badge">{{ s.order }}</div>
              </template>
            </el-step>
          </el-steps>
        </div>
        <div v-else class="zx-text-secondary mt-4 text-sm">完成更多课程后，这里会给出推荐学习顺序。</div>

        <div class="mt-2 flex flex-wrap gap-3">
          <el-button
            v-for="s in path?.steps ?? []"
            :key="s.courseId"
            class="zx-path-btn"
            round
            @click="router.push(`/courses/${s.courseId}`)"
          >
            <span class="zx-ellipsis">{{ s.courseName }}</span>
            <el-icon class="ml-1 shrink-0"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="zx-card min-w-0 overflow-hidden p-5">
        <h2 class="font-bold">最新学情点评</h2>
        <p v-if="report" class="zx-text-secondary mt-3 break-words text-sm leading-7">{{ report.content }}</p>
        <p v-if="report" class="zx-text-secondary mt-3 text-xs">生成时间：{{ report.createTime }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.zx-step-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}
/* 路径按钮：宽度受容器约束，长课程名省略号截断而非撑破卡片 */
.zx-path-btn {
  max-width: 100%;
}
.zx-path-btn :deep(span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:deep(.el-step__title) {
  font-size: 14px;
  font-weight: 600;
}
:deep(.el-step__description) {
  font-size: 12px;
}
</style>
