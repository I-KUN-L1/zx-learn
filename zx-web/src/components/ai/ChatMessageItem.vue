<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { renderMarkdown, extractCourseRefs } from '@/utils/markdown'
import type { ChatMessage } from '@/types/api'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  message: ChatMessage
  /** 是否正在流式输出（AI 最后一条） */
  streaming?: boolean
}>()

const router = useRouter()
const userStore = useUserStore()

const isUser = computed(() => props.message.type === 'USER')

/** Markdown 渲染结果 */
const html = computed(() => (isUser.value ? '' : renderMarkdown(props.message.content)))

/** 推荐课程卡片（识别 markdown 中指向 /courses/:id 的链接） */
const courseRefs = computed(() =>
  isUser.value ? [] : extractCourseRefs(props.message.content)
)
</script>

<template>
  <div class="zx-msg flex gap-3" :class="isUser ? 'flex-row-reverse' : ''">
    <!-- 头像 -->
    <el-avatar v-if="!isUser" :size="36" class="zx-ai-avatar shrink-0">AI</el-avatar>
    <el-avatar v-else :size="36" class="shrink-0" style="background: var(--zx-primary)">
      {{ userStore.username?.slice(0, 1) || '我' }}
    </el-avatar>

    <!-- 气泡 -->
    <div class="max-w-[78%]" :class="isUser ? 'text-right' : ''">
      <div
        v-if="!isUser && message.agent"
        class="zx-text-secondary mb-1 text-xs"
      >
        {{ message.agent }}
      </div>
      <div
        class="zx-bubble inline-block rounded-2xl px-4 py-3 text-left text-sm leading-6"
        :class="isUser ? 'zx-bubble--user' : 'zx-bubble--ai'"
      >
        <!-- 用户消息 -->
        <span v-if="isUser" class="whitespace-pre-wrap">{{ message.content }}</span>
        <!-- AI 等待首字：思考中动画（点循环出现/消失） -->
        <div v-else-if="streaming && !message.content" class="zx-thinking" aria-label="思考中">
          <span class="zx-thinking__text">思考中</span>
          <span class="zx-thinking__dots" aria-hidden="true">
            <i class="zx-dot" /><i class="zx-dot" /><i class="zx-dot" />
          </span>
        </div>
        <!-- AI 消息：Markdown + 流式光标 -->
        <div v-else class="zx-markdown" :class="{ 'zx-cursor': streaming }" v-html="html" />
      </div>

      <!-- 推荐课程卡片 -->
      <div v-if="courseRefs.length && !streaming" class="mt-2 flex flex-wrap gap-2">
        <div
          v-for="c in courseRefs"
          :key="c.id"
          class="zx-card zx-card-hover flex cursor-pointer items-center gap-3 rounded-xl p-3"
          @click="router.push(`/courses/${c.id}`)"
        >
          <div class="zx-ai-avatar flex h-10 w-10 items-center justify-center rounded-lg text-lg font-bold">
            知
          </div>
          <div>
            <div class="text-sm font-medium">{{ c.name }}</div>
            <div class="zx-text-secondary text-xs">点击查看课程详情 →</div>
          </div>
        </div>
      </div>

      <div v-if="!isUser" class="zx-text-secondary mt-1 text-xs">
        内容由 AI 生成，仅供参考
      </div>
    </div>
  </div>
</template>

<style scoped>
.zx-bubble--user {
  background: var(--zx-primary);
  color: #fff;
  border-top-right-radius: 4px;
}
.zx-bubble--ai {
  background: var(--zx-bg-card);
  box-shadow: var(--zx-shadow);
  border-top-left-radius: 4px;
  display: inline-block;
  min-width: 200px;
}

/* 思考中动画：文字 + 三个点依次循环出现/消失 */
.zx-thinking {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  color: var(--zx-text-secondary);
}
.zx-thinking__text {
  font-size: 13px;
}
.zx-thinking__dots {
  display: inline-flex;
  align-items: flex-end;
  gap: 4px;
  margin-left: 2px;
  height: 14px;
}
.zx-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--zx-primary);
  opacity: 0;
  animation: zx-dot-blink 1.4s ease-in-out infinite;
}
.zx-dot:nth-child(2) {
  animation-delay: 0.2s;
}
.zx-dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes zx-dot-blink {
  0%,
  60%,
  100% {
    opacity: 0;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}
</style>
