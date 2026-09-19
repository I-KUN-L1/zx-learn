<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * 课程封面（带兜底）。
 *
 * 为什么要兜底：课程封面历史上指向外部对象存储，一旦资源不可达（域名失效、
 * 未上传、离线环境）`<img>` 会渲染成裂图/空白块，整页出现大量"空洞"。
 * 这里在图片加载失败时切换到**本地生成的渐变占位块**（课程名首字 + 名称），
 * 保证任何情况下都有确定的视觉内容，不会出现空白占位。
 */
const props = defineProps<{
  src?: string | null
  name?: string | null
  /** 占位块配色序号（同一课程稳定同色） */
  seed?: number | string
}>()

const failed = ref(false)
watch(
  () => props.src,
  () => {
    failed.value = false
  }
)

const showImage = computed(() => !!props.src && !failed.value)

/** 课程名首字（中英文都取第一个字符），空则用「课」 */
const initial = computed(() => {
  const name = (props.name ?? '').trim()
  return name ? Array.from(name)[0] : '课'
})

/** 6 套渐变，按课程 id 取模，保证同一门课颜色稳定、列表整体色彩丰富 */
const gradientIndex = computed(() => {
  const seed = String(props.seed ?? props.name ?? '0')
  let hash = 0
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) % 9973
  }
  return hash % 6
})
</script>

<template>
  <div class="zx-cover">
    <img
      v-if="showImage"
      :src="src as string"
      :alt="name ?? '课程封面'"
      class="zx-cover__img"
      loading="lazy"
      @error="failed = true"
    />
    <div v-else class="zx-cover__fallback" :class="`zx-cover__fallback--${gradientIndex}`">
      <span class="zx-cover__initial">{{ initial }}</span>
      <span class="zx-cover__name">{{ name || '课程封面' }}</span>
    </div>
  </div>
</template>

<style scoped>
.zx-cover {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--zx-primary-bg);
}
.zx-cover__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.zx-cover__fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 100%;
  min-height: 96px;
  padding: 12px;
  text-align: center;
  color: #fff;
}
.zx-cover__initial {
  font-size: 34px;
  font-weight: 800;
  line-height: 1;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.25);
}
.zx-cover__name {
  max-width: 100%;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  opacity: 0.92;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.zx-cover__fallback--0 {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
}
.zx-cover__fallback--1 {
  background: linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%);
}
.zx-cover__fallback--2 {
  background: linear-gradient(135deg, #059669 0%, #0d9488 100%);
}
.zx-cover__fallback--3 {
  background: linear-gradient(135deg, #d97706 0%, #ea580c 100%);
}
.zx-cover__fallback--4 {
  background: linear-gradient(135deg, #db2777 0%, #9333ea 100%);
}
.zx-cover__fallback--5 {
  background: linear-gradient(135deg, #475569 0%, #1e293b 100%);
}
</style>
