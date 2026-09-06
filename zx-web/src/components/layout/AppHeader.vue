<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, Moon, Sunny } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const { handleLogout } = useAuth()

const navs = computed(() => [
  { path: '/', label: '首页' },
  { path: '/courses', label: '课程' },
  { path: '/assistant', label: 'AI 助教' },
  { path: '/learning', label: '学习中心' },
  { path: '/insight', label: '学情报告' },
])

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
        <!-- 消息铃铛 -->
        <el-badge :value="appStore.unreadCount" :hidden="!appStore.unreadCount" :max="99">
          <el-button :icon="Bell" circle text @click="router.push('/messages')" />
        </el-badge>

        <!-- 主题切换 -->
        <el-tooltip :content="appStore.dark ? '切换浅色' : '切换深色'" placement="bottom">
          <el-button :icon="appStore.dark ? Sunny : Moon" circle text @click="appStore.toggleTheme()" />
        </el-tooltip>

        <!-- 未登录 -->
        <template v-if="!userStore.isLoggedIn">
          <el-button type="primary" round @click="router.push('/login')">登录</el-button>
        </template>

        <!-- 用户下拉 -->
        <el-dropdown v-else trigger="click" @command="(cmd: string) => cmd === 'logout' ? onLogout() : router.push(cmd)">
          <span class="flex cursor-pointer items-center gap-2">
            <el-avatar :size="34" class="zx-ai-avatar">{{ userStore.username.slice(0, 1) }}</el-avatar>
            <span class="hidden max-w-24 truncate md:inline">{{ userStore.username }}</span>
            <el-icon class="text-secondary"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/learning">学习中心</el-dropdown-item>
              <el-dropdown-item command="/trade/orders">我的订单</el-dropdown-item>
              <el-dropdown-item command="/trade/coupons">优惠券</el-dropdown-item>
              <!-- 管理后台入口对所有人可见，无权限由后端 403 兜底 -->
              <el-dropdown-item command="admin" divided>管理后台</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
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
