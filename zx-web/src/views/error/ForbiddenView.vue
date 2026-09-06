<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="flex min-h-full flex-col items-center justify-center px-4">
    <div class="text-[120px] font-extrabold leading-none text-primary">403</div>
    <h1 class="mt-4 text-xl font-bold">抱歉，你没有访问该页面的权限</h1>
    <p class="zx-text-secondary mt-2 text-sm">如需开通权限，请联系管理员分配对应角色。</p>
    <div class="mt-8 flex gap-3">
      <el-button round @click="router.push('/')">返回首页</el-button>
      <el-button type="primary" round @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>
