<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Delete, ShoppingCart } from '@element-plus/icons-vue'
import { cartList, clearCart, removeFromCart, type CartItem } from '@/api/trade'
import { formatPrice } from '@/utils/format'
import type { Id } from '@/types/api'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonCards from '@/components/common/SkeletonCards.vue'

const router = useRouter()

const items = ref<CartItem[]>([])
const loading = ref(true)

/**
 * 已勾选的课程 id。
 * 注意：课程 id 可能是雪花 id（后端以字符串下发，见 types/api.ts 的 Id），
 * 因此统一用 String(id) 做集合比较，避免 number/string 混用导致勾选失效。
 */
const selectedIds = ref<string[]>([])
const selectedKey = computed(() => new Set(selectedIds.value.map(String)))

const allSelected = computed(
  () => items.value.length > 0 && selectedIds.value.length === items.value.length,
)
const indeterminate = computed(
  () => selectedIds.value.length > 0 && selectedIds.value.length < items.value.length,
)

/** 选中的购物车条目 */
const selectedItems = computed(() =>
  items.value.filter((it) => selectedKey.value.has(String(it.courseId))),
)

/** 合计（仅统计已勾选条目） */
const totalFee = computed(() =>
  selectedItems.value.reduce((sum, it) => sum + (it.coursePrice ?? 0), 0),
)

async function fetchCart() {
  loading.value = true
  try {
    const res = await cartList()
    // 防御：接口异常结构时回退空列表
    items.value = Array.isArray(res) ? res : []
    // 清理已不在购物车中的勾选项
    const valid = new Set(items.value.map((it) => String(it.courseId)))
    selectedIds.value = selectedIds.value.filter((id) => valid.has(id))
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function toggleOne(courseId: Id, checked: boolean) {
  const key = String(courseId)
  const next = new Set(selectedIds.value)
  if (checked) next.add(key)
  else next.delete(key)
  selectedIds.value = [...next]
}

function toggleAll(checked: boolean) {
  selectedIds.value = checked ? items.value.map((it) => String(it.courseId)) : []
}

async function onRemove(item: CartItem) {
  if (!(await confirmAction(`确定将「${item.courseName || '该课程'}」移出购物车吗？`, '移出购物车', { type: 'warning' }))) return
  try {
    await removeFromCart(item.courseId)
    items.value = items.value.filter((it) => String(it.courseId) !== String(item.courseId))
    selectedIds.value = selectedIds.value.filter((id) => id !== String(item.courseId))
    ElMessage.success('已移出购物车')
  } catch {
    /* 全局拦截器已提示 */
  }
}

async function onClear() {
  if (!(await confirmAction('确定清空购物车吗？该操作不可恢复。', '清空购物车', { type: 'warning' }))) return
  try {
    await clearCart()
    items.value = []
    selectedIds.value = []
    ElMessage.success('购物车已清空')
  } catch {
    /* 全局拦截器已提示 */
  }
}

/**
 * 进入确认下单页。
 * 后端下单模型为"每门课程一张订单"，因此单独购买传 1 个课程 id，
 * 批量结算用逗号拼接多个课程 id；参数名必须为 courseIds（与 TradeView 契约一致）。
 */
function goCheckout(courseIds: Id[]) {
  if (!courseIds.length) {
    ElMessage.warning('请先勾选要购买的课程')
    return
  }
  router.push({ path: '/trade', query: { courseIds: courseIds.map(String).join(',') } })
}

/** 单独购买某一门课程 */
function onBuyNow(item: CartItem) {
  goCheckout([item.courseId])
}

/** 多门课程一起下单支付 */
function onCheckoutSelected() {
  goCheckout(selectedItems.value.map((it) => it.courseId))
}

onMounted(fetchCart)
</script>

<template>
  <div class="zx-page">
    <div class="mb-5 flex flex-wrap items-center gap-4">
      <h1 class="flex items-center gap-2 text-2xl font-bold">
        <el-icon :size="26" class="text-primary"><ShoppingCart /></el-icon>
        我的购物车
      </h1>
      <span class="zx-text-secondary text-sm">共 {{ items.length }} 门课程</span>
      <div class="ml-auto flex items-center gap-2">
        <el-button round :icon="Delete" :disabled="!items.length" @click="onClear">清空购物车</el-button>
      </div>
    </div>

    <SkeletonCards v-if="loading" :count="3" />
    <EmptyState
      v-else-if="!items.length"
      description="购物车还是空的，去挑几门好课吧"
    >
      <el-button type="primary" round @click="router.push('/courses')">去逛课程</el-button>
    </EmptyState>

    <div v-else class="space-y-4">
      <!-- 全选 -->
      <div class="zx-card flex items-center gap-3 px-4 py-3">
        <el-checkbox
          :model-value="allSelected"
          :indeterminate="indeterminate"
          @change="(v: boolean | string | number) => toggleAll(Boolean(v))"
        >
          全选
        </el-checkbox>
        <span class="zx-text-secondary text-xs">已选 {{ selectedItems.length }} / {{ items.length }} 门</span>
      </div>

      <div
        v-for="it in items"
        :key="it.id"
        class="zx-card flex cursor-pointer items-center gap-4 p-4 transition-colors hover:shadow-md"
        @click="router.push(`/courses/${it.courseId}`)"
      >
        <el-checkbox
          class="shrink-0"
          :model-value="selectedKey.has(String(it.courseId))"
          @click.stop
          @change="(v: boolean | string | number) => toggleOne(it.courseId, Boolean(v))"
        />

        <div class="zx-ai-avatar flex h-14 w-24 shrink-0 items-center justify-center rounded-lg text-lg font-bold">
          知
        </div>
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm font-medium">
            {{ it.courseName || `课程 #${it.courseId}` }}
          </div>
          <div class="zx-text-secondary mt-1 text-xs">加入时间：{{ (it.createTime || '').slice(0, 10) }}</div>
        </div>
        <span class="text-sm font-semibold">￥{{ formatPrice(it.coursePrice ?? 0) }}</span>
        <div class="flex shrink-0 items-center gap-2" @click.stop>
          <el-button type="primary" size="small" round @click="onBuyNow(it)">立即购买</el-button>
          <el-button size="small" round :icon="Delete" @click="onRemove(it)">移出</el-button>
        </div>
      </div>

      <!-- 结算栏 -->
      <div class="zx-card sticky bottom-4 flex flex-wrap items-center gap-4 p-4">
        <el-checkbox
          :model-value="allSelected"
          :indeterminate="indeterminate"
          @change="(v: boolean | string | number) => toggleAll(Boolean(v))"
        >
          全选
        </el-checkbox>
        <span class="zx-text-secondary text-sm">已选 {{ selectedItems.length }} 门</span>
        <div class="ml-auto flex items-center gap-3">
          <span class="zx-text-secondary text-sm">合计：</span>
          <span class="text-xl font-extrabold text-primary">￥{{ formatPrice(totalFee) }}</span>
          <el-button
            type="primary"
            size="large"
            round
            :disabled="!selectedItems.length"
            @click="onCheckoutSelected"
          >
            结算（{{ selectedItems.length }}）
          </el-button>
        </div>
        <p class="zx-text-secondary w-full text-right text-xs">
          每门课程单独生成一张订单，可一起下单支付
        </p>
      </div>
    </div>
  </div>
</template>
