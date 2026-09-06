<script setup lang="ts">
// 学员端布局：顶部导航 + 内容 + 页脚
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
</script>

<template>
  <div class="flex min-h-full flex-col">
    <AppHeader />
    <main class="flex-1">
      <RouterView v-slot="{ Component }">
        <Transition name="zx-page-fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
    <AppFooter />
  </div>
</template>

<style scoped>
.zx-page-fade-enter-active,
.zx-page-fade-leave-active {
  transition: opacity 0.18s ease;
}
.zx-page-fade-enter-from,
.zx-page-fade-leave-to {
  opacity: 0;
}
</style>
