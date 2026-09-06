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

onMounted(async () => {
  try {
    const [p, lp, rp] = await Promise.all([myProfile(), learningPath(), latestReport()])
    profile.value = p
    path.value = lp
    report.value = rp
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <div class="mb-5 flex items-center gap-3">
      <h1 class="text-2xl font-bold">学情报告</h1>
      <el-tag type="primary" effect="plain" round>知 · 学 · 行 · 评 · 闭环</el-tag>
    </div>

    <!-- 总览指标卡 -->
    <div class="grid grid-cols-1 gap-5 sm:grid-cols-3">
      <div class="zx-card flex items-center gap-4 p-5">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl" style="background: var(--zx-primary-bg); color: var(--zx-primary)">
          <el-icon :size="24"><Clock /></el-icon>
        </div>
        <div>
          <div class="zx-text-secondary text-sm">累计学习时长</div>
          <div class="text-2xl font-extrabold">{{ profile ? formatMinutes(profile.totalDuration) : '--' }}</div>
        </div>
      </div>
      <div class="zx-card flex items-center gap-4 p-5">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl" style="background: var(--zx-primary-bg); color: var(--zx-primary)">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div>
          <div class="zx-text-secondary text-sm">课程完成率</div>
          <div class="text-2xl font-extrabold">{{ profile?.completedRate ?? '--' }}%</div>
        </div>
      </div>
      <div class="zx-card flex items-center gap-4 p-5">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl" style="background: var(--zx-primary-bg); color: var(--zx-primary)">
          <el-icon :size="24"><Medal /></el-icon>
        </div>
        <div>
          <div class="zx-text-secondary text-sm">连续打卡</div>
          <div class="text-2xl font-extrabold">{{ profile?.continuousDays ?? '--' }} 天</div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div class="zx-card p-5">
        <h2 class="font-bold">能力画像</h2>
        <div ref="radarEl" class="mt-2 h-[300px] w-full" />
      </div>
      <div class="zx-card p-5">
        <h2 class="font-bold">近 7 日学习趋势</h2>
        <div ref="lineEl" class="mt-2 h-[300px] w-full" />
      </div>
    </div>

    <!-- 学习路径推荐 + 报告 -->
    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="zx-card p-5 lg:col-span-2">
        <h2 class="font-bold">学习路径推荐</h2>
        <p class="zx-text-secondary mt-1 text-sm">{{ path?.reason }}</p>
        <el-steps v-if="path" direction="vertical" class="mt-5" :active="0" finish-status="finish">
          <el-step v-for="s in path.steps" :key="s.order" :title="s.courseName" :description="s.reason" status="wait">
            <template #icon>
              <div class="zx-step-badge">{{ s.order }}</div>
            </template>
          </el-step>
        </el-steps>
        <div class="mt-2 flex flex-wrap gap-3">
          <el-button
            v-for="s in path?.steps ?? []"
            :key="s.courseId"
            round
            @click="router.push(`/courses/${s.courseId}`)"
          >
            {{ s.courseName }}
            <el-icon class="ml-1"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="zx-card p-5">
        <h2 class="font-bold">最新学情点评</h2>
        <p v-if="report" class="zx-text-secondary mt-3 text-sm leading-7">{{ report.content }}</p>
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
:deep(.el-step__title) {
  font-size: 14px;
  font-weight: 600;
}
:deep(.el-step__description) {
  font-size: 12px;
}
</style>
