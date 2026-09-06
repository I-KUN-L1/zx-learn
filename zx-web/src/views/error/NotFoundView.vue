<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="flex min-h-full flex-col items-center justify-center px-4">
    <div class="text-[120px] font-extrabold leading-none text-primary">404</div>
    <h1 class="mt-4 text-xl font-bold">页面走丢了</h1>
    <p class="zx-text-secondary mt-2 text-sm">你要访问的页面不存在或已被移除。</p>
    <div class="mt-8 flex gap-3">
      <el-button round @click="router.push('/')">返回首页</el-button>
      <el-button type="primary" round @click="router.push('/assistant')">问问 AI 助教</el-button>
    </div>
  </div>
</template>
