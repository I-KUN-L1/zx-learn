<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Back, ChatDotRound, DataLine, List, Notebook, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { handleLogout } = useAuth()

/** 头像下拉：个人中心（积分/排行榜）对全角色开放 */
async function onDropdownCommand(cmd: string) {
  if (cmd === 'logout') {
    await handleLogout()
    ElMessage.success('已退出登录')
    return
  }
  router.push(cmd)
}

/**
 * 管理端菜单对所有人可见：真正的权限由后端接口鉴权，
 * 无权限用户访问后接口返回 403 并跳转 403 页。
 * AI 助教：后端 /chat 等接口仅要求登录、不限角色，管理员本就可用，
 * 此前只有门户顶部导航有入口，故在控制台内补齐。
 */
const menus = [
  { path: '/admin/dashboard', label: '数据看板', icon: DataLine },
  { path: '/admin/courses', label: '课程管理', icon: Notebook },
  { path: '/admin/orders', label: '订单管理', icon: List },
  { path: '/admin/users', label: '用户与权限', icon: User },
  { path: '/admin/assistant', label: 'AI 助教', icon: ChatDotRound },
]

const activeMenu = computed(() => route.path)

const breadcrumb = computed(() => {
  const matched = route.matched.filter((r) => r.meta?.title)
  return matched.map((r) => ({ title: r.meta.title as string, path: r.path }))
})
</script>

<template>
  <div class="flex min-h-full">
    <!-- 侧边栏 -->
    <aside class="zx-sidebar fixed inset-y-0 left-0 z-40 flex w-56 flex-col pt-4">
      <div class="flex items-center gap-2 px-5 pb-6">
        <img src="/favicon.svg" alt="logo" class="h-9 w-9 rounded-xl" />
        <div>
          <div class="text-sm font-bold text-white">知行智学</div>
          <div class="text-xs text-indigo-200">管理控制台</div>
        </div>
      </div>

      <nav class="flex-1 space-y-1 px-3">
        <RouterLink
          v-for="menu in menus"
          :key="menu.path"
          :to="menu.path"
          class="zx-side-item"
          :class="{ 'zx-side-item--active': activeMenu === menu.path }"
        >
          <el-icon :size="16"><component :is="menu.icon" /></el-icon>
          <span>{{ menu.label }}</span>
        </RouterLink>
      </nav>

      <div class="p-3">
        <RouterLink to="/" class="zx-side-item">
          <el-icon :size="16"><Back /></el-icon>
          <span>返回学员端</span>
        </RouterLink>
      </div>
    </aside>

    <!-- 主体 -->
    <div class="ml-56 flex min-h-full flex-1 flex-col">
      <header class="zx-topbar sticky top-0 z-30 flex h-14 items-center gap-4 px-6">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">管理端</el-breadcrumb-item>
          <el-breadcrumb-item v-for="bc in breadcrumb" :key="bc.path" :to="bc.path">
            {{ bc.title }}
          </el-breadcrumb-item>
        </el-breadcrumb>
        <div class="ml-auto flex items-center gap-3">
          <el-dropdown trigger="click" @command="onDropdownCommand">
            <span class="flex cursor-pointer items-center gap-2">
              <el-avatar :size="30" class="zx-ai-avatar">{{ userStore.username?.slice(0, 1) || '知' }}</el-avatar>
              <span class="text-sm">{{ userStore.username || '知行用户' }}</span>
              <el-icon class="text-secondary"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="flex-1 p-6">
        <RouterView v-slot="{ Component }">
          <Transition name="zx-admin-fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.zx-sidebar {
  background: linear-gradient(180deg, #312e81 0%, #1e1b4b 100%);
}
.zx-side-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border-radius: 8px;
  color: #c7d2fe;
  font-size: 14px;
  text-decoration: none;
  transition: all 0.2s;
}
.zx-side-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}
.zx-side-item--active {
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  font-weight: 600;
}
.zx-topbar {
  background: var(--zx-bg-card);
  box-shadow: var(--zx-shadow);
}
.zx-admin-fade-enter-active,
.zx-admin-fade-leave-active {
  transition: opacity 0.18s ease;
}
.zx-admin-fade-enter-from,
.zx-admin-fade-leave-to {
  opacity: 0;
}
</style>
