<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
const year = new Date().getFullYear()

const links = [
  { label: '课程中心', path: '/courses' },
  { label: 'AI 助教', path: '/assistant' },
  { label: '学习中心', path: '/learning' },
  { label: '学情报告', path: '/insight' },
]
</script>

<template>
  <footer class="zx-footer mt-12">
    <div class="mx-auto max-w-[1280px] px-4 py-10">
      <div class="flex flex-col items-center justify-between gap-6 md:flex-row md:items-start">
        <div class="text-center md:text-left">
          <div class="flex items-center justify-center gap-2 md:justify-start">
            <img src="/favicon.svg" alt="logo" class="h-8 w-8 rounded-lg" />
            <span class="text-base font-bold">知行智学</span>
          </div>
          <p class="zx-text-secondary mt-3 max-w-sm text-sm leading-6">
            围绕「知 - 学 - 行 - 评」闭环构建的 AI 智慧学习平台，
            以 AI 助教、学情画像与个性化路径推荐驱动高效学习。
          </p>
        </div>
        <div class="flex gap-10">
          <div>
            <h4 class="mb-3 text-sm font-semibold">学习</h4>
            <ul class="zx-text-secondary space-y-2 text-sm">
              <li v-for="link in links" :key="link.path">
                <a class="cursor-pointer hover:text-primary" @click="router.push(link.path)">{{ link.label }}</a>
              </li>
            </ul>
          </div>
          <div>
            <h4 class="mb-3 text-sm font-semibold">服务</h4>
            <ul class="zx-text-secondary space-y-2 text-sm">
              <li>微服务架构 · Spring Cloud</li>
              <li>统一网关 · JWT 鉴权</li>
              <li>SSE 流式 AI 助教</li>
            </ul>
          </div>
        </div>
      </div>
      <div class="zx-text-secondary mt-8 border-t pt-6 text-center text-xs" style="border-color: var(--zx-border)">
        © {{ year }} 知行智学 ZhiXing Learn · Vue 3 + Vite + Element Plus
      </div>
    </div>
  </footer>
</template>

<style scoped>
.zx-footer {
  background: var(--zx-bg-card);
  border-top: 1px solid var(--zx-border);
}
</style>
