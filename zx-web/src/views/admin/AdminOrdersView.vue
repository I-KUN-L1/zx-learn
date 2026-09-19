<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Money, RefreshRight, Search, ShoppingBag, TrendCharts, Wallet } from '@element-plus/icons-vue'
import {
  adminAuditRefund,
  adminDeleteOrder,
  adminExportOrders,
  adminOrderDetail,
  adminOrderPage,
  adminOrderStats,
  adminRefundPage,
  adminUpdateOrderStatus,
  adminUserCourses,
  type AdminOrderQuery,
} from '@/api/adminOrder'
import { formatDate, formatPriceFixed } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { AdminOrderStatsVO, AdminOrderVO, AdminRefundVO, AdminUserCoursesVO, Id } from '@/types/api'

/**
 * 管理员端订单管理。
 *
 * 权限：本页所有接口由后端 `@RequireRole(STAFF)` 保护 —— 学员/教师调用一律 403，
 * 前端路由 meta.roles 只是体验层拦截，真正的访问控制在后端。
 *
 * 状态口径：管理端直接使用数据库状态（0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款），
 * 不做学员端那套 1/2/3/5/6 契约映射，便于与财务/运营对齐。
 */

const DB_STATUS_TEXT: Record<number, string> = {
  0: '待支付',
  1: '已支付',
  2: '已关闭',
  3: '退款中',
  4: '已退款',
}

const DB_STATUS_TAG: Record<number, 'warning' | 'success' | 'info' | 'danger' | 'primary'> = {
  0: 'warning',
  1: 'success',
  2: 'info',
  3: 'danger',
  4: 'primary',
}

const PAY_TYPE_TEXT: Record<number, string> = { 1: '支付宝', 2: '微信', 3: '余额' }

/* ---------- 统计卡 ---------- */
const stats = ref<AdminOrderStatsVO | null>(null)

const metrics = computed(() => [
  { label: '订单总量', value: String(stats.value?.totalCount ?? 0), icon: ShoppingBag, color: '#4F46E5' },
  { label: '已支付', value: String(stats.value?.paidCount ?? 0), icon: TrendCharts, color: '#16a34a' },
  {
    label: '销售额(元)',
    value: formatPriceFixed(stats.value?.totalSales ?? 0),
    icon: Money,
    color: '#f97316',
  },
  { label: '待支付', value: String(stats.value?.unpaidCount ?? 0), icon: Wallet, color: '#eab308' },
  { label: '退款中', value: String(stats.value?.refundingCount ?? 0), icon: RefreshRight, color: '#dc2626' },
])

async function fetchStats() {
  try {
    stats.value = await adminOrderStats()
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

/* ---------- 订单列表 ---------- */
const query = reactive<AdminOrderQuery>({
  pageNo: 1,
  pageSize: 10,
  orderNo: '',
  keyword: '',
  status: undefined,
  courseId: undefined,
  beginTime: undefined,
  endTime: undefined,
  minAmount: undefined,
  maxAmount: undefined,
})

/** 时间范围与金额区间使用独立本地态，转换为后端参数后再发请求 */
const dateRange = ref<[string, string] | null>(null)
const minYuan = ref<number | undefined>(undefined)
const maxYuan = ref<number | undefined>(undefined)

const list = ref<AdminOrderVO[]>([])
const total = ref(0)
const loading = ref(false)

function buildParams(): AdminOrderQuery {
  const params: AdminOrderQuery = { ...query }
  if (dateRange.value?.length === 2) {
    params.beginTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  // 输入单位是元，后端按分比较
  if (minYuan.value !== undefined && minYuan.value !== null) {
    params.minAmount = Math.round(minYuan.value * 100)
  }
  if (maxYuan.value !== undefined && maxYuan.value !== null) {
    params.maxAmount = Math.round(maxYuan.value * 100)
  }
  // 空字符串参数不下发，避免后端把 '' 当作有效筛选值
  Object.keys(params).forEach((k) => {
    const v = (params as Record<string, unknown>)[k]
    if (v === '' || v === null || v === undefined) delete (params as Record<string, unknown>)[k]
  })
  return params
}

async function fetchList() {
  loading.value = true
  try {
    const res = await adminOrderPage(buildParams())
    list.value = res?.list ?? []
    total.value = res?.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNo = 1
  fetchList()
}

function onReset() {
  Object.assign(query, {
    pageNo: 1,
    pageSize: 10,
    orderNo: '',
    keyword: '',
    status: undefined,
    courseId: undefined,
    minAmount: undefined,
    maxAmount: undefined,
  })
  dateRange.value = null
  minYuan.value = undefined
  maxYuan.value = undefined
  fetchList()
}

/* ---------- 详情抽屉 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminOrderVO | null>(null)

async function openDetail(row: AdminOrderVO) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await adminOrderDetail(row.id)
  } catch {
    ElMessage.error('订单详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

/* ---------- 修改状态 ---------- */
const statusDialogVisible = ref(false)
const statusSaving = ref(false)
const statusTarget = ref<AdminOrderVO | null>(null)
const newStatus = ref<number>(0)

function openStatusDialog(row: AdminOrderVO) {
  statusTarget.value = row
  newStatus.value = row.status
  statusDialogVisible.value = true
}

async function saveStatus() {
  if (!statusTarget.value) return
  statusSaving.value = true
  try {
    await adminUpdateOrderStatus(statusTarget.value.id, newStatus.value)
    ElMessage.success('订单状态已更新')
    statusDialogVisible.value = false
    await Promise.all([fetchList(), fetchStats()])
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    statusSaving.value = false
  }
}

/* ---------- 删除订单（清理无用订单） ---------- */

/**
 * 可删除状态：待支付(0)、已关闭(2)、已退款(4) —— 均不贡献销售额
 * （看板只统计 已支付 且支付时间非空的订单），属于无效/无用记录。
 * 已支付(1) 影响销售统计与课程归属、退款中(3) 流程进行中，后端会拒绝。
 */
const DELETABLE_DB_STATUS = [0, 2, 4]

function canDelete(row: AdminOrderVO): boolean {
  return DELETABLE_DB_STATUS.includes(row.status)
}

async function onDelete(row: AdminOrderVO) {
  const tip =
    row.status === 0
      ? '该笔待支付订单将被清理（不影响销售额）'
      : row.status === 4
        ? '该笔已退款订单将被清理（不影响销售额）'
        : '该笔已关闭订单将被清理（不影响销售额）'
  try {
    await ElMessageBox.confirm(
      `确定删除订单「${row.orderNo}」（${DB_STATUS_TEXT[row.status] ?? '未知'}）吗？\n${tip}；删除后管理端列表不再展示，数据可追溯。`,
      '删除订单',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await adminDeleteOrder(row.id)
    ElMessage.success('订单已删除')
    await Promise.all([fetchList(), fetchStats()])
  } catch {
    /* 错误由拦截器统一提示（含"已支付订单不允许删除"） */
  }
}

/* ---------- 退款审核 ---------- */
const activeTab = ref<'orders' | 'refunds'>('orders')
const refunds = ref<AdminRefundVO[]>([])
const refundTotal = ref(0)
const refundLoading = ref(false)
const refundQuery = reactive({ pageNo: 1, pageSize: 10, status: undefined as number | undefined })

async function fetchRefunds() {
  refundLoading.value = true
  try {
    const res = await adminRefundPage(refundQuery as Record<string, unknown>)
    refunds.value = res?.list ?? []
    refundTotal.value = res?.total ?? 0
  } catch {
    refunds.value = []
    refundTotal.value = 0
  } finally {
    refundLoading.value = false
  }
}

const REFUND_STATUS_TEXT: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝' }

async function audit(row: AdminRefundVO, approved: boolean) {
  const label = approved ? '通过' : '拒绝'
  try {
    await ElMessageBox.confirm(`确定${label}该退款申请吗？`, '退款审核', {
      type: 'warning',
      confirmButtonText: label,
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await adminAuditRefund(row.id, approved, `管理员${label}`)
    ElMessage.success(`已${label}退款申请`)
    await Promise.all([fetchRefunds(), fetchStats(), fetchList()])
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

/* ---------- 学员相关课程（退款审批辅助） ---------- */
const userCoursesVisible = ref(false)
const userCoursesLoading = ref(false)
const userCourses = ref<AdminUserCoursesVO | null>(null)
const userCoursesLabel = ref('')

/** 学习时长（秒 → 可读文本） */
function durationText(seconds?: number): string {
  const s = seconds ?? 0
  if (s <= 0) return '未学习'
  const minutes = Math.round(s / 60)
  if (minutes < 60) return `${minutes} 分钟`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
}

/** 打开学员课程抽屉：用于判断"该学员是否已实际学习课程"，辅助退款审批 */
async function openUserCourses(userId: Id, label?: string) {
  userCoursesLabel.value = label || `用户 #${userId}`
  userCoursesVisible.value = true
  userCoursesLoading.value = true
  userCourses.value = null
  try {
    userCourses.value = await adminUserCourses(userId)
  } catch {
    ElMessage.error('学员课程加载失败')
  } finally {
    userCoursesLoading.value = false
  }
}

/* ---------- 导出 ---------- */
const exporting = ref(false)

async function onExport() {
  exporting.value = true
  try {
    const blob = await adminExportOrders(buildParams())
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `订单导出_${formatDate(new Date(), 'YYYYMMDD_HHmmss')}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    // 释放 Blob URL，避免内存泄漏
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'refunds' && !refunds.value.length) {
    fetchRefunds()
  }
}

onMounted(async () => {
  await Promise.all([fetchStats(), fetchList()])
})
</script>

<template>
  <div>
    <div class="mb-5 flex flex-wrap items-center gap-2">
      <h1 class="text-lg font-bold">订单管理</h1>
      <span class="zx-text-secondary text-sm">仅管理员可访问 · 数据由后端 403 鉴权兜底</span>
      <el-button class="ml-auto" :icon="RefreshRight" circle @click="onSearch" />
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ============ 订单列表 ============ -->
      <el-tab-pane label="订单列表" name="orders">
        <!-- 统计卡 -->
        <div class="mb-5 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
          <div v-for="m in metrics" :key="m.label" class="zx-card flex items-center gap-3 p-4">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
              :style="{ background: m.color + '1a', color: m.color }"
            >
              <el-icon :size="20"><component :is="m.icon" /></el-icon>
            </div>
            <div class="min-w-0">
              <div class="zx-text-secondary truncate text-xs">{{ m.label }}</div>
              <div class="truncate text-xl font-extrabold">{{ m.value }}</div>
            </div>
          </div>
        </div>

        <!-- 筛选栏 -->
        <div class="zx-card mb-5 p-4">
          <div class="flex flex-wrap items-center gap-3">
            <el-input v-model="query.orderNo" placeholder="订单号" clearable class="!w-48" @keyup.enter="onSearch" />
            <el-input
              v-model="query.keyword"
              placeholder="用户名 / 手机号 / 课程名"
              :prefix-icon="Search"
              clearable
              class="!w-56"
              @keyup.enter="onSearch"
            />
            <el-select v-model="query.status" placeholder="订单状态" clearable class="!w-36">
              <el-option v-for="(text, code) in DB_STATUS_TEXT" :key="code" :label="text" :value="Number(code)" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="~"
              start-placeholder="下单开始"
              end-placeholder="下单结束"
              class="!w-80"
            />
            <el-input-number
              v-model="minYuan"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="实付金额下限(元)"
              class="!w-40"
            />
            <span class="zx-text-secondary text-sm">~</span>
            <el-input-number
              v-model="maxYuan"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="上限(元)"
              class="!w-40"
            />
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
            <el-button
              class="ml-auto"
              type="success"
              plain
              round
              :icon="Download"
              :loading="exporting"
              @click="onExport"
            >
              导出 CSV
            </el-button>
          </div>
        </div>

        <!-- 列表 -->
        <div class="zx-card p-5">
          <div v-loading="loading">
            <EmptyState v-if="!loading && !list.length" description="没有符合条件的订单" size="small" />
            <el-table v-else :data="list" row-key="id">
              <el-table-column label="订单号" min-width="180">
                <template #default="{ row }">
                  <span class="font-mono text-xs">{{ row.orderNo }}</span>
                </template>
              </el-table-column>
              <el-table-column label="下单用户" min-width="160">
                <template #default="{ row }">
                  <div class="font-medium">{{ row.username || `用户 #${row.userId}` }}</div>
                  <div class="zx-text-secondary text-xs">{{ row.cellPhone || '-' }}</div>
                </template>
              </el-table-column>
              <el-table-column label="课程" min-width="200">
                <template #default="{ row }">
                  <div class="line-clamp-2 text-sm">{{ row.courseName }}</div>
                </template>
              </el-table-column>
              <el-table-column label="原价" width="100" align="right">
                <template #default="{ row }">
                  <span class="zx-text-secondary text-sm">{{ formatPriceFixed(row.coursePrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="实付" width="110" align="right">
                <template #default="{ row }">
                  <span class="font-semibold text-primary">{{ formatPriceFixed(row.totalFee) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="优惠" width="100" align="right">
                <template #default="{ row }">
                  <span class="text-sm" :style="{ color: (row.deduction ?? 0) > 0 ? '#16a34a' : 'inherit' }">
                    {{ (row.deduction ?? 0) > 0 ? '-' + formatPriceFixed(row.deduction) : '-' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="150" align="center">
                <template #default="{ row }">
                  <el-tag :type="DB_STATUS_TAG[row.status] ?? 'info'" size="small" round>
                    {{ DB_STATUS_TEXT[row.status] ?? '未知' }}
                  </el-tag>
                  <!-- 学员侧已删除：管理端仍保留记录（需求：用户删除后管理端可查） -->
                  <el-tag
                    v-if="row.userDeleted === 1"
                    class="ml-1"
                    type="info"
                    effect="plain"
                    size="small"
                    round
                    title="学员已从「我的订单」移除该订单，管理端保留用于对账"
                  >
                    学员已删除
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="下单时间" width="160">
                <template #default="{ row }">
                  <span class="zx-text-secondary text-xs">{{ formatDate(row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="330" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" round @click="openDetail(row as AdminOrderVO)">详情</el-button>
                  <el-button
                    size="small"
                    type="success"
                    plain
                    round
                    @click="openUserCourses((row as AdminOrderVO).userId, (row as AdminOrderVO).username)"
                  >
                    查看课程
                  </el-button>
                  <el-button size="small" type="primary" plain round @click="openStatusDialog(row as AdminOrderVO)">
                    改状态
                  </el-button>
                  <!-- 仅无用订单可删：待支付/已关闭/已退款（不影响销售额）；已支付与退款中后端会拒绝 -->
                  <el-button
                    v-if="canDelete(row as AdminOrderVO)"
                    size="small"
                    type="danger"
                    plain
                    round
                    @click="onDelete(row as AdminOrderVO)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
              <template #empty><EmptyState description="暂无数据" size="small" /></template>
            </el-table>
          </div>

          <div v-if="total > (query.pageSize ?? 10)" class="mt-5 flex justify-center">
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
      </el-tab-pane>

      <!-- ============ 退款审核 ============ -->
      <el-tab-pane label="退款审核" name="refunds">
        <div class="zx-card p-5">
          <div class="mb-4 flex items-center gap-3">
            <el-select v-model="refundQuery.status" placeholder="审核状态" clearable class="!w-36" @change="fetchRefunds">
              <el-option v-for="(text, code) in REFUND_STATUS_TEXT" :key="code" :label="text" :value="Number(code)" />
            </el-select>
            <el-button :icon="RefreshRight" circle @click="fetchRefunds" />
          </div>

          <div v-loading="refundLoading">
            <EmptyState v-if="!refundLoading && !refunds.length" description="暂无退款申请" size="small" />
            <el-table v-else :data="refunds" row-key="id">
              <el-table-column label="退款单号" prop="id" width="90" />
              <el-table-column label="订单号" min-width="170">
                <template #default="{ row }">
                  <span class="font-mono text-xs">{{ row.orderNo }}</span>
                </template>
              </el-table-column>
              <el-table-column label="申请人" min-width="140">
                <template #default="{ row }">
                  <div>{{ row.username }}</div>
                  <div class="zx-text-secondary text-xs">{{ row.cellPhone }}</div>
                </template>
              </el-table-column>
              <el-table-column label="课程" min-width="180">
                <template #default="{ row }">
                  <div class="line-clamp-1 text-sm">{{ row.courseName }}</div>
                </template>
              </el-table-column>
              <el-table-column label="金额" width="110" align="right">
                <template #default="{ row }">
                  <span class="font-semibold text-primary">{{ formatPriceFixed(row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="原因" min-width="160">
                <template #default="{ row }">
                  <span class="text-sm">{{ row.reason || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag
                    :type="row.status === 0 ? 'warning' : row.status === 1 ? 'success' : 'info'"
                    size="small"
                    round
                  >
                    {{ REFUND_STATUS_TEXT[row.status] ?? '未知' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="申请时间" width="160">
                <template #default="{ row }">
                  <span class="zx-text-secondary text-xs">{{ formatDate(row.createTime ?? '') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <el-button
                    size="small"
                    type="success"
                    plain
                    round
                    @click="openUserCourses((row as AdminRefundVO).userId, (row as AdminRefundVO).username)"
                  >
                    查看课程
                  </el-button>
                  <template v-if="row.status === 0">
                    <el-button size="small" type="success" round @click="audit(row as AdminRefundVO, true)">通过</el-button>
                    <el-button size="small" type="danger" plain round @click="audit(row as AdminRefundVO, false)">拒绝</el-button>
                  </template>
                  <span v-else class="zx-text-secondary text-xs">{{ row.remark || '已处理' }}</span>
                </template>
              </el-table-column>
              <template #empty><EmptyState description="暂无数据" size="small" /></template>
            </el-table>
          </div>

          <div v-if="refundTotal > refundQuery.pageSize" class="mt-5 flex justify-center">
            <el-pagination
              v-model:current-page="refundQuery.pageNo"
              :page-size="refundQuery.pageSize"
              :total="refundTotal"
              layout="prev, pager, next, total"
              background
              @current-change="fetchRefunds"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 订单详情 ============ -->
    <el-drawer v-model="detailVisible" size="480px" title="订单详情">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="订单号">
              <span class="font-mono text-xs">{{ detail.orderNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="下单用户">
              {{ detail.username || `用户 #${detail.userId}` }}
              <span class="zx-text-secondary ml-2 text-xs">{{ detail.cellPhone }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="课程">
              {{ detail.courseName }}
              <span class="zx-text-secondary ml-2 text-xs">ID: {{ detail.courseId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="课程原价">{{ formatPriceFixed(detail.coursePrice) }}</el-descriptions-item>
            <el-descriptions-item label="优惠抵扣">
              {{ (detail.deduction ?? 0) > 0 ? '-' + formatPriceFixed(detail.deduction) : '无' }}
            </el-descriptions-item>
            <el-descriptions-item label="实付金额">
              <span class="font-bold text-primary">{{ formatPriceFixed(detail.totalFee) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="DB_STATUS_TAG[detail.status] ?? 'info'" size="small" round>
                {{ DB_STATUS_TEXT[detail.status] ?? '未知' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="支付方式">
              {{ detail.payType ? (PAY_TYPE_TEXT[detail.payType] ?? '其他') : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ formatDate(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="支付时间">
              {{ detail.payTime ? formatDate(detail.payTime) : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
        <EmptyState v-else-if="!detailLoading" description="订单不存在" size="small" />
      </div>
    </el-drawer>

    <!-- ============ 修改状态 ============ -->
    <el-dialog v-model="statusDialogVisible" title="修改订单状态" width="420px">
      <div v-if="statusTarget" class="space-y-4">
        <div class="zx-text-secondary text-sm">
          订单号：<span class="font-mono">{{ statusTarget.orderNo }}</span>
        </div>
        <div>
          <div class="mb-2 text-sm">当前状态：{{ DB_STATUS_TEXT[statusTarget.status] ?? '未知' }}</div>
          <el-select v-model="newStatus" class="!w-full">
            <el-option v-for="(text, code) in DB_STATUS_TEXT" :key="code" :label="text" :value="Number(code)" />
          </el-select>
        </div>
        <el-alert
          type="warning"
          :closable="false"
          title="状态修改会直接影响订单后续流程（如销量统计、退券补偿），请谨慎操作。"
        />
      </div>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="statusSaving" @click="saveStatus">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 学员相关课程（退款审批辅助） ============ -->
    <el-drawer v-model="userCoursesVisible" size="620px" :title="`学员课程 · ${userCoursesLabel}`">
      <div v-loading="userCoursesLoading">
        <template v-if="userCourses">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="学员">
              {{ userCourses.username || `用户 #${userCourses.userId}` }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userCourses.cellPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="订单课程数">{{ userCourses.courseCount }}</el-descriptions-item>
            <el-descriptions-item label="已支付">{{ userCourses.paidCount }} 单</el-descriptions-item>
            <el-descriptions-item label="退款中">
              <span :style="{ color: userCourses.refundingCount > 0 ? '#dc2626' : 'inherit' }">
                {{ userCourses.refundingCount }} 单
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="累计实付">
              <span class="font-semibold text-primary">{{ formatPriceFixed(userCourses.totalPaid) }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            class="mt-4"
            type="info"
            :closable="false"
            title="审批参考：课程学习时长/进度为 0 的订单符合「未开始学习」自动退款条件；已有学习记录的建议人工核实。"
          />

          <EmptyState v-if="!userCourses.courses.length" description="该学员暂无订单课程" size="small" />
          <div v-else class="mt-4 space-y-3">
            <div v-for="c in userCourses.courses" :key="c.orderId" class="zx-card p-4">
              <div class="flex flex-wrap items-center gap-2">
                <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ c.courseName }}</span>
                <el-tag
                  :type="DB_STATUS_TAG[c.orderStatus] ?? 'info'"
                  size="small"
                  round
                >
                  {{ c.orderStatusText || DB_STATUS_TEXT[c.orderStatus] || '未知' }}
                </el-tag>
              </div>
              <div class="zx-text-secondary mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
                <span>订单号：<span class="font-mono">{{ c.orderNo }}</span></span>
                <span>实付：￥{{ formatPriceFixed(c.totalFee) }}</span>
                <span>支付时间：{{ c.payTime ? formatDate(c.payTime) : '-' }}</span>
              </div>
              <div class="mt-3">
                <div class="zx-text-secondary mb-1 flex items-center justify-between text-xs">
                  <span>学习进度 {{ c.progress ?? 0 }}%</span>
                  <span>
                    {{ c.finished ? '已完成' : '学习中' }} ·
                    学习时长 {{ durationText(c.learnDuration) }}
                    <template v-if="c.lastLearnTime"> · 最近 {{ formatDate(c.lastLearnTime) }}</template>
                  </span>
                </div>
                <el-progress
                  :percentage="Math.min(100, Math.max(0, c.progress ?? 0))"
                  :stroke-width="8"
                  :show-text="false"
                />
              </div>
            </div>
          </div>
        </template>
        <EmptyState v-else-if="!userCoursesLoading" description="暂无数据" size="small" />
      </div>
    </el-drawer>
  </div>
</template>
