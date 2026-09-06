<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled } from '@element-plus/icons-vue'
import { formatPrice } from '@/utils/format'
import type { CourseVO } from '@/types/api'

const props = defineProps<{
  course: CourseVO
  /** 是否展示发布次数（管理端） */
  showPublish?: boolean
}>()

const router = useRouter()

const priceText = computed(() =>
  props.course.free === 1 ? '免费' : `￥${formatPrice(props.course.price)}`
)

const statusText: Record<number, { text: string; type: 'success' | 'info' | 'warning' }> = {
  1: { text: '已上架', type: 'success' },
  2: { text: '已下架', type: 'info' },
  3: { text: '已完结', type: 'warning' },
}

function goDetail() {
  router.push(`/courses/${props.course.id}`)
}
</script>

<template>
  <div class="zx-card zx-card-hover group cursor-pointer overflow-hidden" @click="goDetail">
    <div class="relative">
      <img
        :src="course.coverUrl"
        :alt="course.name"
        class="h-[150px] w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
        loading="lazy"
      />
      <el-tag
        v-if="course.free === 1"
        type="success"
        effect="dark"
        size="small"
        class="absolute left-3 top-3"
        round
      >
        免费
      </el-tag>
      <el-tag
        v-else-if="showPublish && statusText[course.status]"
        :type="statusText[course.status].type"
        size="small"
        class="absolute left-3 top-3"
        round
      >
        {{ statusText[course.status].text }}
      </el-tag>
    </div>
    <div class="p-4">
      <h3 class="line-clamp-2 min-h-[44px] text-[15px] font-semibold leading-[22px]">
        {{ course.name }}
      </h3>
      <div class="mt-3 flex items-center justify-between">
        <div class="zx-text-secondary flex items-center gap-3 text-xs">
          <span v-if="course.enrollNum != null" class="flex items-center gap-1">
            <el-icon><UserFilled /></el-icon>
            {{ course.enrollNum.toLocaleString() }} 人在学
          </span>
          <span v-if="course.score" class="text-amber-500">★ {{ course.score.toFixed(1) }}</span>
          <span v-if="showPublish && course.publishTimes != null" class="zx-text-secondary">
            发布 {{ course.publishTimes }} 次
          </span>
        </div>
        <span
          class="text-base font-bold"
          :class="course.free === 1 ? 'text-green-500' : 'text-primary'"
        >
          {{ priceText }}
        </span>
      </div>
    </div>
  </div>
</template>
