<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import { answerOverview } from '@/api/exam'
import { useEcharts } from '@/composables/useEcharts'
import { formatDate } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { EChartsOption } from '@/utils/echarts'
import type { AnswerOverviewVO, StudentAnswerStatVO } from '@/types/api'

/**
 * 教师端答题情况（教与学闭环的"评"）。
 *
 * 数据来源：`GET /question-results/teacher/overview` —— 后端聚合出
 * 整体正确率 + 每题正确率 + 每位学员正确率；本页用卡片 + 柱状图 + 表格三视图呈现。
 */

const data = ref<AnswerOverviewVO | null>(null)
const loading = ref(true)

const EMPTY: AnswerOverviewVO = {
  totalRecords: 0,
  totalStudents: 0,
  totalQuestions: 0,
  accuracy: 0,
  questionStats: [],
  studentStats: [],
}

/** 最需要关注的薄弱题目（正确率最低的前 10 道，横向柱状图更适合长题干） */
const weakest = computed(() =>
  [...(data.value?.questionStats ?? [])].sort((a, b) => a.accuracy - b.accuracy).slice(0, 10),
)

const metrics = computed(() => {
  const d = data.value ?? EMPTY
  return [
    { label: '答题记录', value: String(d.totalRecords), unit: '条' },
    { label: '参与学员', value: String(d.totalStudents), unit: '人' },
    { label: '覆盖题目', value: String(d.totalQuestions), unit: '道' },
    { label: '整体正确率', value: String(d.accuracy), unit: '%' },
  ]
})

/* ---------- 题目正确率横向柱状图 ---------- */
const barEl = ref<HTMLElement>()
const barOption = computed<EChartsOption | undefined>(() => {
  if (!weakest.value.length) return undefined
  // 横向柱状图需自下而上绘制，故反转顺序（正确率最低的排在最上方）
  const rows = [...weakest.value].reverse()
  return {
    animationDuration: 600,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const p = (params as { name: string; value: number }[])[0]
        return `${p.name}<br/>正确率：${p.value}%`
      },
    },
    grid: { left: 12, right: 48, top: 10, bottom: 10, containLabel: true },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { formatter: '{value}%' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } },
    },
    yAxis: {
      type: 'category',
      data: rows.map((q) => (q.questionName.length > 16 ? q.questionName.slice(0, 16) + '…' : q.questionName)),
      axisLabel: { fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: rows.map((q) => q.accuracy),
        label: { show: true, position: 'right', formatter: '{c}%', fontSize: 11 },
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          // 正确率越低越红，越高越绿 —— 一眼定位薄弱题
          color: (p: { value?: unknown }) => {
            const v = Number(p.value ?? 0)
            if (v < 50) return '#ef4444'
            if (v < 75) return '#f59e0b'
            return '#22c55e'
          },
        },
      },
    ],
  }
})
useEcharts(barEl, barOption)

/* ---------- 学员正确率排行（柱状图） ---------- */
const studentBarEl = ref<HTMLElement>()
const studentBarOption = computed<EChartsOption | undefined>(() => {
  const students = [...(data.value?.studentStats ?? [])].slice(0, 10)
  if (!students.length) return undefined
  return {
    animationDuration: 600,
    animationEasing: 'cubicOut',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 20, top: 24, bottom: 12, containLabel: true },
    xAxis: {
      type: 'category',
      data: students.map((s) => s.username ?? `学员 #${s.userId}`),
      axisLabel: { fontSize: 11, interval: 0, rotate: students.length > 6 ? 30 : 0 },
    },
    yAxis: {
      type: 'value',
      max: 100,
      name: '正确率%',
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } },
    },
    series: [
      {
        name: '正确率',
        type: 'bar',
        barMaxWidth: 40,
        data: students.map((s) => s.accuracy),
        label: { show: true, position: 'top', formatter: '{c}%', fontSize: 11 },
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#818cf8' },
              { offset: 1, color: '#4F46E5' },
            ],
          },
        },
      },
    ],
  }
})
useEcharts(studentBarEl, studentBarOption)

function accuracyTagType(v: number): 'success' | 'warning' | 'danger' {
  if (v >= 80) return 'success'
  if (v >= 60) return 'warning'
  return 'danger'
}

async function fetchData() {
  loading.value = true
  try {
    data.value = (await answerOverview()) ?? EMPTY
  } catch {
    data.value = EMPTY
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div v-loading="loading">
    <div class="mb-5 flex flex-wrap items-center gap-3">
      <h1 class="text-xl font-bold">答题情况</h1>
      <span class="zx-text-secondary text-sm">教师发布题目 → 学员作答 → 正确率回流，形成教与学闭环</span>
      <el-button class="ml-auto" :icon="RefreshRight" circle @click="fetchData" />
    </div>

    <!-- 总览指标 -->
    <div class="grid grid-cols-2 gap-5 xl:grid-cols-4">
      <div v-for="m in metrics" :key="m.label" class="zx-card p-5">
        <div class="zx-text-secondary text-sm">{{ m.label }}</div>
        <div class="mt-1 text-2xl font-extrabold">
          {{ m.value }}<span class="zx-text-secondary ml-1 text-sm font-normal">{{ m.unit }}</span>
        </div>
      </div>
    </div>

    <template v-if="data && data.totalRecords > 0">
      <!-- 图表 -->
      <div class="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
        <div class="zx-card overflow-hidden p-5">
          <h2 class="font-bold">薄弱题目 TOP10（正确率升序）</h2>
          <p class="zx-text-secondary mt-1 text-xs">正确率越低越红，用于定位需要重点讲解的题目</p>
          <div ref="barEl" class="zx-chart-box mt-2 h-[340px]" />
        </div>
        <div class="zx-card overflow-hidden p-5">
          <h2 class="font-bold">学员正确率排行 TOP10</h2>
          <p class="zx-text-secondary mt-1 text-xs">按正确率降序，便于关注进步与预警落后</p>
          <div ref="studentBarEl" class="zx-chart-box mt-2 h-[340px]" />
        </div>
      </div>

      <!-- 学员明细 -->
      <div class="zx-card mt-6 p-5">
        <h2 class="mb-4 font-bold">学员答题明细</h2>
        <el-table :data="data.studentStats" row-key="userId">
          <el-table-column label="学员" min-width="180">
            <template #default="{ row }">
              <div class="font-medium">{{ row.username || `学员 #${row.userId}` }}</div>
              <div class="zx-text-secondary text-xs">{{ row.cellPhone }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="totalCount" label="答题数" width="100" align="center" />
          <el-table-column prop="correctCount" label="答对数" width="100" align="center" />
          <el-table-column label="正确率" width="220">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <el-progress
                  :percentage="row.accuracy"
                  :stroke-width="8"
                  :show-text="false"
                  :color="row.accuracy >= 80 ? '#22c55e' : row.accuracy >= 60 ? '#f59e0b' : '#ef4444'"
                  class="flex-1"
                />
                <el-tag :type="accuracyTagType(row.accuracy)" size="small" round>{{ row.accuracy }}%</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近答题" width="170">
            <template #default="{ row }">
              <span class="zx-text-secondary text-xs">
                {{ row.lastAnswerTime ? formatDate(row.lastAnswerTime) : '-' }}
              </span>
            </template>
          </el-table-column>
          <template #empty><EmptyState description="暂无数据" size="small" /></template>
        </el-table>
      </div>

      <!-- 题目明细 -->
      <div class="zx-card mt-6 p-5">
        <h2 class="mb-4 font-bold">题目正确率明细</h2>
        <el-table :data="data.questionStats" row-key="questionId" max-height="420">
          <el-table-column label="题目" min-width="280">
            <template #default="{ row }">
              <div class="line-clamp-2 text-sm">{{ row.questionName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="课程" min-width="160">
            <template #default="{ row }">
              <span class="zx-text-secondary text-xs">{{ row.courseName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="totalCount" label="作答人次" width="100" align="center" />
          <el-table-column label="正确率" width="180" align="center">
            <template #default="{ row }">
              <el-tag :type="accuracyTagType(row.accuracy)" size="small" round>{{ row.accuracy }}%</el-tag>
            </template>
          </el-table-column>
          <template #empty><EmptyState description="暂无数据" size="small" /></template>
        </el-table>
      </div>
    </template>

    <div v-else-if="!loading" class="zx-card mt-6 p-10">
      <EmptyState description="还没有学员作答记录，发布题目后学员练习即可看到正确率" />
    </div>
  </div>
</template>
