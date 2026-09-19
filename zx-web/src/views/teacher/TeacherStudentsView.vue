<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, RefreshRight, Search } from '@element-plus/icons-vue'
import { pageStudents, studentProfile } from '@/api/teacher'
import { useEcharts } from '@/composables/useEcharts'
import { formatMinutes } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { EChartsOption } from '@/utils/echarts'
import type { InsightProfileVO, UserVO } from '@/types/api'

/* ---------- 学员列表 ---------- */
const list = ref<UserVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNo: 1, pageSize: 10, keyword: '' })

async function fetchList() {
  loading.value = true
  try {
    const res = await pageStudents({ pageNo: query.pageNo, pageSize: query.pageSize })
    list.value = res.list
    total.value = res.total
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNo = 1
  fetchList()
}

/* ---------- 学情抽屉 ---------- */
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const currentStudent = ref<UserVO | null>(null)
const profile = ref<InsightProfileVO | null>(null)

async function openInsight(row: UserVO) {
  currentStudent.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  profile.value = null
  try {
    profile.value = await studentProfile(row.id)
  } catch {
    ElMessage.error('学情数据加载失败')
  } finally {
    drawerLoading.value = false
  }
}

/* ---------- 雷达图 ---------- */
const radarEl = ref<HTMLElement>()
const radarOption = computed<EChartsOption | undefined>(() => {
  if (!profile.value?.abilities) return undefined
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
            areaStyle: { color: 'rgba(16, 185, 129, 0.25)' },
            lineStyle: { color: '#059669', width: 2 },
            itemStyle: { color: '#059669' },
          },
        ],
      },
    ],
  }
})
useEcharts(radarEl, radarOption)

/* ---------- 能力维度柱状图（雷达图的补充视图，便于精确比较各维度分值） ---------- */
const abilityBarEl = ref<HTMLElement>()
const abilityBarOption = computed<EChartsOption | undefined>(() => {
  const abilities = profile.value?.abilities
  if (!abilities?.length) return undefined
  return {
    animationDuration: 600,
    animationEasing: 'cubicOut',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 20, top: 24, bottom: 12, containLabel: true },
    xAxis: { type: 'category', data: abilities.map((a) => a.name), axisLabel: { fontSize: 11, interval: 0 } },
    yAxis: {
      type: 'value',
      max: 100,
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } },
    },
    series: [
      {
        name: '能力分值',
        type: 'bar',
        barMaxWidth: 36,
        data: abilities.map((a) => a.value),
        label: { show: true, position: 'top', fontSize: 11 },
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#34d399' },
              { offset: 1, color: '#059669' },
            ],
          },
        },
      },
    ],
  }
})
useEcharts(abilityBarEl, abilityBarOption)

/* ---------- 趋势折线图 ---------- */
const trendEl = ref<HTMLElement>()
const trendOption = computed<EChartsOption | undefined>(() => {
  if (!profile.value?.trends) return undefined
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: profile.value.trends.map((t) => t.date),
      axisLine: { lineStyle: { color: '#9ca3af' } },
    },
    yAxis: { type: 'value', name: '分钟', splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } } },
    series: [
      {
        name: '学习时长',
        type: 'line',
        smooth: true,
        data: profile.value.trends.map((t) => t.duration),
        lineStyle: { color: '#059669', width: 3 },
        itemStyle: { color: '#059669' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(16,185,129,0.35)' },
              { offset: 1, color: 'rgba(16,185,129,0.02)' },
            ],
          },
        },
      },
    ],
  }
})
useEcharts(trendEl, trendOption)

onMounted(fetchList)
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <div class="zx-card mb-5 flex flex-wrap items-center gap-3 p-4">
      <el-input
        v-model="query.keyword"
        placeholder="搜索学员（本地过滤当前页）"
        :prefix-icon="Search"
        clearable
        class="!w-64"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button :icon="RefreshRight" circle @click="fetchList" />
    </div>

    <!-- 学员列表 -->
    <div class="zx-card p-5">
      <div v-loading="loading">
        <EmptyState v-if="!loading && !list.length" description="暂无学员" size="small" />
        <el-table v-else :data="list.filter((u) => !query.keyword || (u.username ?? '').includes(query.keyword) || (u.cellPhone ?? '').includes(query.keyword))" row-key="id">
          <el-table-column label="学员" min-width="200">
            <template #default="{ row }">
              <div class="flex items-center gap-3">
                <el-avatar :size="36" class="zx-ai-avatar">{{ (row.username ?? '学').slice(0, 1) }}</el-avatar>
                <div>
                  <div class="font-medium">{{ row.username || `学员 #${row.id}` }}</div>
                  <div class="zx-text-secondary text-xs">{{ row.cellPhone }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" round>
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="180">
            <template #default="{ row }">
              <span class="zx-text-secondary text-xs">{{ row.createTime ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" round :icon="DataAnalysis" @click="openInsight(row as UserVO)">
                查看学情
              </el-button>
            </template>
          </el-table-column>
          <template #empty><EmptyState description="暂无数据" size="small" /></template>
        </el-table>
      </div>

      <div v-if="total > query.pageSize" class="mt-5 flex justify-center">
        <el-pagination
          v-model:current-page="query.pageNo"
          :page-size="query.pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 学情抽屉 -->
    <el-drawer v-model="drawerVisible" size="560px" :title="currentStudent ? `${currentStudent.username} 的学情画像` : '学情画像'">
      <div v-loading="drawerLoading" class="space-y-5">
        <template v-if="profile">
          <!-- 总览指标 -->
          <div class="grid grid-cols-3 gap-3">
            <div class="rounded-xl p-3 text-center" style="background: var(--zx-primary-bg)">
              <div class="zx-text-secondary text-xs">累计学习</div>
              <div class="text-lg font-extrabold text-primary">{{ formatMinutes(profile.totalDuration) }}</div>
            </div>
            <div class="rounded-xl p-3 text-center" style="background: var(--zx-primary-bg)">
              <div class="zx-text-secondary text-xs">完成率</div>
              <div class="text-lg font-extrabold text-primary">{{ profile.completedRate }}%</div>
            </div>
            <div class="rounded-xl p-3 text-center" style="background: var(--zx-primary-bg)">
              <div class="zx-text-secondary text-xs">连续打卡</div>
              <div class="text-lg font-extrabold text-primary">{{ profile.continuousDays }} 天</div>
            </div>
          </div>

          <!-- 能力雷达 -->
          <div class="zx-card min-w-0 overflow-hidden p-4">
            <h3 class="mb-1 font-bold">能力画像</h3>
            <div ref="radarEl" class="zx-chart-box h-[280px]" />
          </div>

          <!-- 能力维度柱状图 -->
          <div class="zx-card min-w-0 overflow-hidden p-4">
            <h3 class="mb-1 font-bold">各维度能力分布</h3>
            <div ref="abilityBarEl" class="zx-chart-box h-[240px]" />
          </div>

          <!-- 趋势 -->
          <div class="zx-card min-w-0 overflow-hidden p-4">
            <h3 class="mb-1 font-bold">近 7 日学习趋势</h3>
            <div ref="trendEl" class="zx-chart-box h-[240px]" />
          </div>
        </template>
        <EmptyState v-else-if="!drawerLoading" description="暂无学情数据" size="small" />
      </div>
    </el-drawer>
  </div>
</template>
