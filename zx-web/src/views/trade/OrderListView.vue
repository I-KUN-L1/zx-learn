<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageOrders, cancelOrder, mockPayOrder } from '@/api/trade'
import { formatDate, formatPrice, ORDER_STATUS, ORDER_STATUS_TAG, ORDER_STATUS_TEXT } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { OrderVO } from '@/types/api'

const route = useRoute()

const query = reactive({ pageNo: 1, pageSize: 8, status: '' as number | '' })
const orders = ref<OrderVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const highlightOrderNo = (route.query.orderNo as string) || ''

async function fetchOrders() {
  loading.value = true
  try {
    const res = await pageOrders({ ...query })
    orders.value = res.list
    total.value = res.total
    pages.value = res.pages
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function onPay(order: OrderVO) {
  try {
    await mockPayOrder(order.id)
    ElMessage.success('支付成功！')
    await fetchOrders()
  } catch (e) {
    ElMessage.info(e instanceof Error ? e.message : '支付失败')
  }
}

async function onCancel(order: OrderVO) {
  await ElMessageBox.confirm(
    '关闭后订单将不可恢复，优惠券随订单释放，确定关闭吗？',
    '取消订单',
    { type: 'warning' }
  ).catch(() => null)
  try {
    await cancelOrder(order.id)
    ElMessage.success('订单已关闭')
    await fetchOrders()
  } catch {
    /* ignore */
  }
}

/** 待支付 15 分钟倒计时（对齐后端 RocketMQ 延迟消息超时关单） */
const PAY_TIMEOUT = 15 * 60

function remainingSeconds(order: OrderVO): number {
  const created = new Date(order.createTime.replace(/-/g, '/')).getTime()
  const left = PAY_TIMEOUT - Math.floor((Date.now() - created) / 1000)
  return Math.max(0, left)
}

interface CountdownItem {
  id: number
  seconds: number
  timer: ReturnType<typeof setInterval>
}
const countdowns = ref<Map<number, CountdownItem>>(new Map())
const countdownMap = ref<Record<number, number>>({})

function setupCountdowns(list: OrderVO[]) {
  // 清理旧计时器
  countdowns.value.forEach((c) => clearInterval(c.timer))
  countdowns.value.clear()
  countdownMap.value = {}
  for (const o of list) {
    if (o.status !== ORDER_STATUS.PENDING) continue
    const left = remainingSeconds(o)
    countdownMap.value[o.id] = left
    const item: CountdownItem = { id: o.id, seconds: left, timer: setInterval(() => {
      countdownMap.value[o.id] = Math.max(0, (countdownMap.value[o.id] ?? 0) - 1)
      if ((countdownMap.value[o.id] ?? 0) <= 0) {
        clearInterval(item.timer)
        // 超时刷新状态（后端延迟消息会关闭订单）
        fetchOrders()
      }
    }, 1000) }
    countdowns.value.set(o.id, item)
  }
}

watch(orders, setupCountdowns)

function countdownText(order: OrderVO): string {
  const s = countdownMap.value[order.id]
  if (s == null) return ''
  const m = Math.floor(s / 60)
  const ss = s % 60
  return `${String(m).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
}

onBeforeUnmount(() => {
  countdowns.value.forEach((c) => clearInterval(c.timer))
  countdowns.value.clear()
})

onMounted(fetchOrders)
</script>

<template>
  <div class="zx-page">
    <div class="mb-5 flex flex-wrap items-center gap-4">
      <h1 class="text-2xl font-bold">我的订单</h1>
      <el-radio-group v-model="query.status" round class="ml-auto" @change="query.pageNo = 1; fetchOrders()">
        <el-radio-button :value="''">全部</el-radio-button>
        <el-radio-button :value="ORDER_STATUS.PENDING">待支付</el-radio-button>
        <el-radio-button :value="ORDER_STATUS.PAID">已支付</el-radio-button>
        <el-radio-button :value="ORDER_STATUS.CLOSED">已关闭</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="highlightOrderNo" class="zx-card mb-5 flex items-center gap-3 border-l-4 border-l-amber-500 p-4">
      <el-icon class="text-amber-500" :size="20">⏰</el-icon>
      <span class="text-sm">订单 {{ highlightOrderNo }} 已创建，请尽快完成支付。</span>
    </div>

    <div v-loading="loading">
      <EmptyState v-if="!loading && !orders.length" description="暂无相关订单">
        <el-button type="primary" round @click="$router.push('/courses')">去逛逛</el-button>
      </EmptyState>

      <div class="space-y-4">
        <div
          v-for="o in orders"
          :key="o.id"
          class="zx-card overflow-hidden"
          :class="{ 'ring-2 ring-amber-400': highlightOrderNo && o.orderNo === highlightOrderNo }"
        >
          <!-- 头部：单号与状态 -->
          <div class="flex flex-wrap items-center gap-3 border-b px-5 py-3" style="border-color: var(--zx-border); background: var(--zx-bg)">
            <span class="zx-text-secondary text-xs">订单号：{{ o.orderNo }}</span>
            <span class="zx-text-secondary text-xs">{{ formatDate(o.createTime) }}</span>
            <el-tag :type="ORDER_STATUS_TAG[o.status]" size="small" round>{{ ORDER_STATUS_TEXT[o.status] }}</el-tag>
            <div class="ml-auto flex items-center gap-3">
              <!-- 待支付 15 分钟倒计时 -->
              <span v-if="o.status === ORDER_STATUS.PENDING && countdownText(o)" class="text-xs font-bold text-red-500">
                剩余 {{ countdownText(o) }} 自动关闭
              </span>
              <template v-if="o.status === ORDER_STATUS.PENDING">
                <el-button type="primary" size="small" round @click="onPay(o)">去支付</el-button>
                <el-button size="small" round @click="onCancel(o)">取消订单</el-button>
              </template>
            </div>
          </div>

          <!-- 明细 -->
          <div
            v-for="d in o.details"
            :key="d.id"
            class="flex cursor-pointer items-center gap-4 px-5 py-4 transition-colors hover:bg-[var(--zx-primary-bg)]"
            @click="$router.push(`/courses/${d.courseId}`)"
          >
            <img v-if="d.coverUrl" :src="d.coverUrl" :alt="d.courseName" class="h-14 w-24 rounded-lg object-cover" />
            <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ d.courseName }}</span>
            <span class="text-sm">￥{{ formatPrice(d.price) }}</span>
          </div>

          <div class="flex flex-wrap items-center justify-end gap-2 px-5 py-3 text-sm">
            <span class="zx-text-secondary text-xs">共 {{ o.details.length }} 门课程</span>
            <span v-if="o.discountAmount > 0" class="zx-text-secondary text-xs">优惠 -￥{{ formatPrice(o.discountAmount) }}</span>
            <span class="zx-text-secondary">实付：</span>
            <span class="text-lg font-extrabold text-primary">￥{{ formatPrice(o.realAmount) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="pages > 1" class="mt-8 flex justify-center">
      <el-pagination
        v-model:current-page="query.pageNo"
        :page-size="query.pageSize"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="fetchOrders"
      />
    </div>
  </div>
</template>
