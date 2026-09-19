<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, Moon, ShoppingCart, Sunny } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useAuth } from '@/composables/useAuth'
import { cartList } from '@/api/trade'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const { handleLogout } = useAuth()

/** 购物车条目数（学员徽标展示；失败静默为 0，不阻塞导航渲染） */
const cartCount = ref(0)

async function fetchCartCount() {
  if (!userStore.isStudent) {
    cartCount.value = 0
    return
  }
  try {
    const res = await cartList()
    cartCount.value = Array.isArray(res) ? res.length : 0
  } catch {
    cartCount.value = 0
  }
}

onMounted(fetchCartCount)
// 进入购物车/下单相关页面后刷新数量（增删动作影响徽标）
watch(
  () => route.path,
  (path) => {
    if (userStore.isStudent && (path.includes('/trade') || path.includes('/courses'))) {
      fetchCartCount()
    }
  },
)

/**
 * 差异化导航（按钮级权限过滤）：
 * - 首页/课程：公共，所有人可见；
 * - AI 助教：登录用户可用（接口仅要求登录，无角色限制）；
 * - 学习中心/学情报告：学员专属（后端 @RequireRole(STUDENT)，教师/管理员不可见）；
 * - 教师工作台/管理后台：对应角色专属入口。
 */
const navs = computed(() => {
  const list: Array<{ path: string; label: string }> = [
    { path: '/', label: '首页' },
    { path: '/courses', label: '课程' },
  ]
  if (userStore.isLoggedIn) {
    list.push({ path: '/assistant', label: 'AI 助教' })
  }
  if (userStore.isStudent) {
    list.push({ path: '/learning', label: '学习中心' })
    list.push({ path: '/exam', label: '在线答题' })
    list.push({ path: '/insight', label: '学情报告' })
  }
  if (userStore.isTeacher) {
    list.push({ path: '/teacher/questions', label: '教师工作台' })
  }
  if (userStore.isAdmin) {
    list.push({ path: '/admin/dashboard', label: '管理后台' })
  }
  return list
})

/** 下拉菜单：按角色过滤无权限入口（学习/交易均为学员专属） */
const dropdownItems = computed(() => {
  // 个人中心对所有登录角色开放（我的积分 / 排行榜 / 积分明细），固定置顶
  const items: Array<{ command: string; label: string; divided?: boolean }> = [
    { command: '/profile', label: '个人中心' },
  ]
  if (userStore.isStudent) {
    items.push({ command: '/learning', label: '学习中心', divided: true })
    items.push({ command: '/exam', label: '在线答题' })
    items.push({ command: '/exam/wrong-book', label: '我的错题本' })
    items.push({ command: '/trade/cart', label: '我的购物车' })
    items.push({ command: '/trade/orders', label: '我的订单' })
    items.push({ command: '/trade/coupons', label: '优惠券' })
  }
  if (userStore.isTeacher) {
    items.push({ command: '/teacher/questions', label: '教师工作台', divided: true })
  }
  if (userStore.isAdmin) {
    items.push({ command: '/admin/dashboard', label: '管理后台', divided: true })
  }
  items.push({ command: 'logout', label: '退出登录', divided: true })
  return items
})

const isActive = (path: string) =>
  path === '/' ? route.path === '/' : route.path.startsWith(path)

async function onLogout() {
  await handleLogout()
  ElMessage.success('已退出登录')
}
</script>

<template>
  <header class="zx-header sticky top-0 z-50">
    <div class="mx-auto flex h-16 max-w-[1280px] items-center gap-6 px-4">
      <!-- Logo -->
      <RouterLink to="/" class="flex shrink-0 items-center gap-2">
        <img src="/favicon.svg" alt="logo" class="h-9 w-9 rounded-xl" />
        <span class="hidden text-lg font-bold text-primary sm:inline">知行智学</span>
      </RouterLink>

      <!-- 主导航（≥lg 展示） -->
      <nav class="hidden flex-1 items-center gap-1 lg:flex">
        <RouterLink
          v-for="nav in navs"
          :key="nav.path"
          :to="nav.path"
          class="zx-nav-link"
          :class="{ 'zx-nav-link--active': isActive(nav.path) }"
        >
          {{ nav.label }}
        </RouterLink>
      </nav>

      <div class="ml-auto flex items-center gap-2">
        <!-- 购物车入口（学员专属：教师/管理员无交易权限不渲染） -->
        <el-tooltip v-if="userStore.isStudent" content="我的购物车" placement="bottom">
          <el-badge :value="cartCount" :hidden="!cartCount" :max="99">
            <el-button :icon="ShoppingCart" circle text @click="router.push('/trade/cart')" />
          </el-badge>
        </el-tooltip>

        <!-- 消息铃铛 -->
        <el-badge :value="appStore.unreadCount" :hidden="!appStore.unreadCount" :max="99">
          <el-button :icon="Bell" circle text @click="router.push('/messages')" />
        </el-badge>

        <!-- 主题切换 -->
        <el-tooltip :content="appStore.dark ? '切换浅色' : '切换深色'" placement="bottom">
          <el-button :icon="appStore.dark ? Sunny : Moon" circle text @click="appStore.toggleTheme()" />
        </el-tooltip>

        <!-- 未登录：自由浏览，登录/注册入口常驻 -->
        <template v-if="!userStore.isLoggedIn">
          <el-button round @click="router.push('/register')">注册</el-button>
          <el-button type="primary" round @click="router.push('/login')">登录</el-button>
        </template>

        <!-- 用户下拉 -->
        <el-dropdown v-else trigger="click" @command="(cmd: string) => cmd === 'logout' ? onLogout() : router.push(cmd)">
          <span class="flex cursor-pointer items-center gap-2">
            <el-avatar :size="34" class="zx-ai-avatar">{{ userStore.username?.slice(0, 1) || '知' }}</el-avatar>
            <span class="hidden max-w-24 truncate md:inline">{{ userStore.username || '知行用户' }}</span>
            <el-icon class="text-secondary"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="item in dropdownItems"
                :key="item.command"
                :command="item.command"
                :divided="item.divided"
              >
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 平板/移动端导航 -->
    <nav class="flex items-center gap-1 overflow-x-auto border-t px-3 py-2 lg:hidden" style="border-color: var(--zx-border)">
      <RouterLink
        v-for="nav in navs"
        :key="nav.path"
        :to="nav.path"
        class="zx-nav-link whitespace-nowrap"
        :class="{ 'zx-nav-link--active': isActive(nav.path) }"
      >
        {{ nav.label }}
      </RouterLink>
    </nav>
  </header>
</template>

<style scoped>
.zx-header {
  background: var(--zx-bg-card);
  box-shadow: var(--zx-shadow);
}
.zx-nav-link {
  padding: 8px 14px;
  border-radius: 8px;
  color: var(--zx-text-secondary);
  font-size: 15px;
  text-decoration: none;
  transition: all 0.2s;
}
.zx-nav-link:hover {
  color: var(--zx-primary);
  background: var(--zx-primary-bg);
}
.zx-nav-link--active {
  color: var(--zx-primary);
  background: var(--zx-primary-bg);
  font-weight: 600;
}
.text-secondary {
  color: var(--zx-text-secondary);
}
</style>
