<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applyRefund, cancelOrder, deleteOrder, mockPayOrder, pageOrders } from '@/api/trade'
import { formatDate, formatPrice, ORDER_STATUS, ORDER_STATUS_TAG, ORDER_STATUS_TEXT } from '@/utils/format'
import { confirmAction } from '@/utils/confirm'
import EmptyState from '@/components/common/EmptyState.vue'
import type { OrderVO } from '@/types/api'

const route = useRoute()

const query = reactive({ pageNo: 1, pageSize: 8, status: '' as number | '' })
const orders = ref<OrderVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const highlightOrderNo = (route.query.orderNo as string) || ''

/**
 * 支持深链筛选：/trade/orders?status=2（已支付）/ ?pending=1（待支付）
 * 下单流程跳转后可直接落到对应状态页。
 */
function statusFromRoute(): number | '' {
  if (route.query.pending != null) return ORDER_STATUS.PENDING
  const raw = route.query.status
  if (raw == null || raw === '') return ''
  const n = Number(raw)
  return Number.isNaN(n) ? '' : n
}

async function fetchOrders() {
  loading.value = true
  try {
    const res = await pageOrders({ ...query })
    // 防御：接口异常结构（list 缺失）时回退空列表，避免 undefined 导致渲染崩溃
    orders.value = res?.list ?? []
    total.value = res?.total ?? 0
    pages.value = res?.pages ?? 0
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

/** 继续支付：后端复用真实支付回调链路（流水幂等），成功后弹支付成功提示 */
async function onPay(order: OrderVO) {
  try {
    await mockPayOrder(order.id)
    await ElMessageBox.alert('支付成功，课程已开通，可在「学习中心」开始学习。', '支付成功', {
      type: 'success',
      confirmButtonText: '好的',
    }).catch(() => null)
    await fetchOrders()
  } catch (e) {
    ElMessage.info(e instanceof Error ? e.message : '支付失败，请稍后重试')
  }
}

async function onCancel(order: OrderVO) {
  if (!(await confirmAction('关闭后订单将不可恢复，优惠券随订单释放，确定关闭吗？', '取消订单', { type: 'warning' }))) return
  try {
    await cancelOrder(order.id)
    ElMessage.success('订单已关闭')
    await fetchOrders()
  } catch {
    /* ignore */
  }
}

/**
 * 可删除的订单状态：已终结、不再需要用户操作的订单。
 * - 已支付(2)/已完成(4)：交易已完成，允许整理掉
 * - 已关闭(3)/已退款(6)：流程已终结
 * 待支付(1) 需先支付或取消；退款中(5) 需等待审核 —— 后端同样强校验。
 */
const DELETABLE_STATUS: number[] = [
  ORDER_STATUS.PAID,
  ORDER_STATUS.FINISHED,
  ORDER_STATUS.CLOSED,
  ORDER_STATUS.REFUNDED,
]

function canDelete(order: OrderVO): boolean {
  return DELETABLE_STATUS.includes(order.status)
}

/**
 * 删除订单：软删除，仅从"我的订单"移除，管理端仍保留该记录用于对账。
 */
async function onDelete(order: OrderVO) {
  const name = order.details?.[0]?.courseName || '该订单'
  if (
    !(await confirmAction(
      `确定从「我的订单」中移除「${name}」吗？\n移除后不影响课程与交易记录，管理端仍保留该订单。`,
      '删除订单',
      { type: 'warning', confirmButtonText: '确认移除' },
    ))
  ) {
    return
  }
  try {
    await deleteOrder(order.id)
    ElMessage.success('订单已移除')
    await fetchOrders()
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

/**
 * 申请退款：满足基础条件后提交。
 * - 后端判定满足"进一步条件"（课程未开始学习）时直接退款成功；
 * - 否则生成待审核退款单，订单转"退款中"，由管理员审批。
 */
async function onRefund(order: OrderVO) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      `将对「${order.details?.[0]?.courseName || '该课程'}」申请退款（实付 ￥${formatPrice(order.realAmount)}），可填写退款原因：`,
      '申请退款',
      {
        type: 'warning',
        confirmButtonText: '提交申请',
        cancelButtonText: '再想想',
        inputPlaceholder: '选填，例如：课程内容与预期不符',
        inputValidator: () => true,
      },
    )
    reason = res.value ?? ''
  } catch {
    return
  }
  try {
    const res = await applyRefund(order.id, reason)
    if (res?.mode === 'INSTANT') {
      await ElMessageBox.alert(
        res.message || '退款成功，款项将原路退回。',
        '退款成功',
        { type: 'success', confirmButtonText: '好的' },
      ).catch(() => null)
    } else {
      await ElMessageBox.alert(
        res?.message || '退款申请已提交，将由管理员审核。',
        '已提交审核',
        { type: 'info', confirmButtonText: '知道了' },
      ).catch(() => null)
    }
    await fetchOrders()
  } catch {
    /* 全局拦截器已提示 */
  }
}

/** 待支付 15 分钟倒计时（对齐后端 RocketMQ 延迟消息超时关单） */
const PAY_TIMEOUT = 15 * 60

/**
 * 剩余秒数。
 * 后端 createTime 为 "yyyy-MM-dd HH:mm:ss"（Jackson 默认）或 ISO "yyyy-MM-ddTHH:mm:ss"。
 * 旧实现 `createTime.replace(/-/g, '/')` 会把 ISO 串变成 "2026/09/12T17:47:42"——
 * 斜杠与 T 混用是 Invalid Date，getTime() 返回 NaN，最终渲染成「剩余 NaN:NaN」。
 * 这里统一归一化为 "yyyy/MM/dd HH:mm:ss"（按本地时区解析），并在任何一步失败时返回 0 兜底。
 */
function remainingSeconds(order: OrderVO): number {
  const raw = (order.createTime ?? '').trim()
  if (!raw) return 0
  const normalized = raw.replace('T', ' ').replace(/-/g, '/')
  const created = new Date(normalized).getTime()
  if (!Number.isFinite(created)) return 0
  const left = PAY_TIMEOUT - Math.floor((Date.now() - created) / 1000)
  return Math.max(0, Math.floor(left))
}

interface CountdownItem {
  id: string
  seconds: number
  timer: ReturnType<typeof setInterval>
}
const countdowns = ref<Map<string, CountdownItem>>(new Map())
const countdownMap = ref<Record<string, number>>({})

/**
 * 归零触发的刷新节流表：orderId → 上次因倒计时归零刷新列表的时间戳。
 * <p>
 * 必要性：订单归零后我们调用 fetchOrders 重新拉取；若此刻后端尚未完成关单
 * （例如事务提交、定时任务未到点），列表仍返回"待支付 + 剩余 0"，watch 会重建计时器
 * 并在 1 秒后再次归零 → 再次刷新，形成「每秒一次请求」的风暴。
 * 这里对同一订单的归零刷新做 5 秒节流，杜绝该循环。
 */
const zeroRefetchAt = new Map<string, number>()
/** 归零刷新的最小间隔（毫秒） */
const ZERO_REFETCH_COOLDOWN = 5000

function setupCountdowns(list: OrderVO[]) {
  // 清理旧计时器
  countdowns.value.forEach((c) => clearInterval(c.timer))
  countdowns.value.clear()
  countdownMap.value = {}
  for (const o of list) {
    if (o.status !== ORDER_STATUS.PENDING) continue
    const key = String(o.id)
    const left = remainingSeconds(o)
    countdownMap.value[key] = left
    const item: CountdownItem = { id: key, seconds: left, timer: setInterval(() => {
      countdownMap.value[key] = Math.max(0, (countdownMap.value[key] ?? 0) - 1)
      if ((countdownMap.value[key] ?? 0) <= 0) {
        clearInterval(item.timer)
        // 超时刷新状态（后端惰性对账 + 定时任务会关闭订单），同一订单 5 秒内只刷新一次
        const last = zeroRefetchAt.get(key) ?? 0
        if (Date.now() - last >= ZERO_REFETCH_COOLDOWN) {
          zeroRefetchAt.set(key, Date.now())
          fetchOrders()
        }
      }
    }, 1000) }
    countdowns.value.set(key, item)
  }
}

watch(orders, (list) => {
  if (Array.isArray(list)) {
    setupCountdowns(list)
  }
})

function countdownText(order: OrderVO): string {
  const s = countdownMap.value[String(order.id)]
  // 防御：非法值（NaN/undefined）一律不渲染，避免出现「剩余 NaN:NaN」
  if (s == null || !Number.isFinite(s)) return ''
  const m = Math.floor(s / 60)
  const ss = s % 60
  return `${String(m).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
}

onBeforeUnmount(() => {
  countdowns.value.forEach((c) => clearInterval(c.timer))
  countdowns.value.clear()
})

onMounted(() => {
  query.status = statusFromRoute()
  fetchOrders()
})
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
                <el-button type="primary" size="small" round @click="onPay(o)">继续支付</el-button>
                <el-button size="small" round @click="onCancel(o)">取消订单</el-button>
              </template>
              <!-- 已支付：可申请退款 -->
              <template v-else-if="o.status === ORDER_STATUS.PAID">
                <el-button type="warning" size="small" plain round @click="onRefund(o)">申请退款</el-button>
              </template>
              <span v-else-if="o.status === ORDER_STATUS.REFUNDING" class="zx-text-secondary text-xs">退款审核中，请耐心等待</span>
              <!-- 已终结订单（已支付/已完成/已关闭/已退款）可删除；软删除，管理端仍保留记录 -->
              <el-button
                v-if="canDelete(o)"
                type="danger"
                size="small"
                plain
                round
                @click="onDelete(o)"
              >
                删除
              </el-button>
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
