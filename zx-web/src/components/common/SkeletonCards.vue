<script setup lang="ts">
// 课程卡片骨架屏
defineProps<{ count?: number }>()
</script>

<template>
  <div class="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
    <div v-for="i in (count ?? 8)" :key="i" class="zx-card overflow-hidden">
      <el-skeleton animated :rows="0">
        <template #template>
          <el-skeleton-item variant="image" style="width: 100%; height: 150px" />
          <div class="p-4">
            <el-skeleton-item variant="text" style="width: 85%" />
            <div class="mt-3 flex items-center justify-between">
              <el-skeleton-item variant="text" style="width: 40%" />
              <el-skeleton-item variant="circle" style="width: 56px; height: 22px" />
            </div>
          </div>
        </template>
      </el-skeleton>
    </div>
  </div>
</template>
