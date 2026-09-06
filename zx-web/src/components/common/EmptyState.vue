<script setup lang="ts">
// 空状态占位（插画风格，不使用外链占位图）
defineProps<{
  description?: string
  size?: 'default' | 'small'
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-12">
    <svg viewBox="0 0 200 140" class="w-48" :class="size === 'small' ? 'w-32' : 'w-48'">
      <ellipse cx="100" cy="120" rx="70" ry="10" fill="var(--zx-primary-bg)" />
      <rect x="55" y="30" width="90" height="66" rx="10" fill="var(--zx-bg-card)" stroke="var(--zx-border)" stroke-width="2" />
      <line x1="68" y1="50" x2="132" y2="50" stroke="var(--zx-border)" stroke-width="6" stroke-linecap="round" />
      <line x1="68" y1="66" x2="118" y2="66" stroke="var(--zx-primary-bg)" stroke-width="6" stroke-linecap="round" />
      <line x1="68" y1="80" x2="126" y2="80" stroke="var(--zx-primary-bg)" stroke-width="6" stroke-linecap="round" />
      <circle cx="148" cy="34" r="16" fill="var(--zx-primary)" opacity="0.9" />
      <path d="M142 34l5 5 9-10" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
    <p class="zx-text-secondary mt-4 text-sm">{{ description || '暂无数据' }}</p>
    <div v-if="$slots.default" class="mt-4">
      <slot />
    </div>
  </div>
</template>
