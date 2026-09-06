<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Money, ShoppingBag, UserFilled, Notebook } from '@element-plus/icons-vue'
import { insightDashboard } from '@/api/insight'
import { useEcharts } from '@/composables/useEcharts'
import { formatPriceFixed } from '@/utils/format'
import type { EChartsOption } from '@/utils/echarts'
import type { DashboardVO } from '@/types/api'

const dashboard = ref<DashboardVO | null>(null)
const loading = ref(true)

/* ---------- 订单量 & 销售额双轴折线 ---------- */
const orderOption = computed<EChartsOption | undefined>(() => {
  if (!dashboard.value) return undefined
  const dates = dashboard.value.orderTrend.map((t) => t.date)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单量', '销售额(万元)'], top: 0 },
    grid: { left: 50, right: 55, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: '单', splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } } },
      { type: 'value', name: '万', splitLine: { show: false } },
    ],
    series: [
      {
        name: '订单量',
        type: 'bar',
        data: dashboard.value.orderTrend.map((t) => t.count),
        itemStyle: { color: '#4F46E5', borderRadius: [6, 6, 0, 0] },
        barWidth: 18,
      },
      {
        name: '销售额(万元)',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: dashboard.value.orderTrend.map((t) => Number((t.amount / 1000000).toFixed(2))),
        lineStyle: { color: '#f97316', width: 3 },
        itemStyle: { color: '#f97316' },
      },
    ],
  }
})

/* ---------- 活跃趋势柱状图 ---------- */
const activeOption = computed<EChartsOption | undefined>(() => {
  if (!dashboard.value) return undefined
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 55, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: dashboard.value.activeTrend.map((t) => t.date) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } } },
    series: [
      {
        name: '活跃用户',
        type: 'line',
        smooth: true,
        data: dashboard.value.activeTrend.map((t) => t.count),
        lineStyle: { color: '#22c55e', width: 3 },
        itemStyle: { color: '#22c55e' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(34,197,94,0.3)' },
              { offset: 1, color: 'rgba(34,197,94,0.02)' },
            ],
          },
        },
      },
    ],
  }
})

/* ---------- 热门课程横向柱状图 ---------- */
const hotOption = computed<EChartsOption | undefined>(() => {
  if (!dashboard.value) return undefined
  const hot = [...dashboard.value.hotCourses].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 170, right: 30, top: 10, bottom: 30 },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } } },
    yAxis: { type: 'category', data: hot.map((h) => h.name), axisLabel: { width: 150, overflow: 'truncate' } },
    series: [
      {
        type: 'bar',
        data: hot.map((h) => h.count),
        itemStyle: {
          color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [{ offset: 0, color: '#818cf8' }, { offset: 1, color: '#4F46E5' }] },
          borderRadius: [0, 6, 6, 0],
        },
        barWidth: 14,
      },
    ],
  }
})

const orderEl = ref<HTMLElement>()
const activeEl = ref<HTMLElement>()
const hotEl = ref<HTMLElement>()
useEcharts(orderEl, orderOption)
useEcharts(activeEl, activeOption)
useEcharts(hotEl, hotOption)

const metrics = computed(() => [
  { label: '累计用户', value: (dashboard.value?.totalUsers ?? 0).toLocaleString(), icon: UserFilled, bg: '#eef2ff' },
  { label: '订单总量', value: (dashboard.value?.totalOrders ?? 0).toLocaleString(), icon: ShoppingBag, bg: '#fff7ed' },
  { label: '销售额(元)', value: formatPriceFixed(dashboard.value?.totalSales ?? 0), icon: Money, bg: '#f0fdf4' },
  { label: '在售课程', value: String(dashboard.value?.totalCourses ?? 0), icon: Notebook, bg: '#fdf2f8' },
])

onMounted(async () => {
  try {
    dashboard.value = await insightDashboard()
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <h1 class="mb-5 text-xl font-bold">数据看板</h1>

    <!-- 指标卡 -->
    <div class="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-4">
      <div v-for="m in metrics" :key="m.label" class="zx-card flex items-center gap-4 p-5">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl" :style="{ background: m.bg }">
          <el-icon :size="24" color="#4F46E5"><component :is="m.icon" /></el-icon>
        </div>
        <div>
          <div class="zx-text-secondary text-sm">{{ m.label }}</div>
          <div class="text-2xl font-extrabold">{{ m.value }}</div>
        </div>
      </div>
    </div>

    <!-- 图表 -->
    <div class="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
      <div class="zx-card p-5 xl:col-span-2">
        <h2 class="font-bold">订单量与销售额（近 7 日）</h2>
        <div ref="orderEl" class="mt-2 h-[320px] w-full" />
      </div>
      <div class="zx-card p-5">
        <h2 class="font-bold">活跃趋势（近 7 日）</h2>
        <div ref="activeEl" class="mt-2 h-[300px] w-full" />
      </div>
      <div class="zx-card p-5">
        <h2 class="font-bold">热门课程 TOP5</h2>
        <div ref="hotEl" class="mt-2 h-[300px] w-full" />
      </div>
    </div>
  </div>
</template>
