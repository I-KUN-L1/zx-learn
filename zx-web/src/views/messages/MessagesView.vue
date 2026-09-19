<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { inboxList, readAllInbox, readInbox } from '@/api/message'
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { InboxVO } from '@/types/api'

const appStore = useAppStore()
const messages = ref<InboxVO[]>([])
const loading = ref(true)

const unreadCount = computed(() => messages.value.filter((m) => !m.read).length)

async function fetchMessages() {
  loading.value = true
  try {
    messages.value = await inboxList()
    appStore.unreadCount = unreadCount.value
  } catch (e) {
    // 不允许静默吞错：拉取失败必须让用户看见，否则页面表现为「暂无消息」，会被误判为「没有消息」
    ElMessage.error('消息加载失败，请稍后重试')
    console.error('[messages] inboxList failed', e)
  } finally {
    loading.value = false
  }
}

async function onRead(m: InboxVO) {
  if (m.read) return
  try {
    await readInbox(m.id)
    m.read = true
    appStore.unreadCount = unreadCount.value
  } catch (e) {
    // 历史缺陷：这里曾是空 catch，而 /inboxes/read 在真实后端并不存在 →
    // 点击无任何反馈、未读角标也不消失，故障被完全掩盖。现在显式提示。
    ElMessage.error('标记已读失败，请稍后重试')
    console.error('[messages] readInbox failed', e)
  }
}

async function onReadAll() {
  try {
    await readAllInbox()
    messages.value.forEach((m) => (m.read = true))
    appStore.unreadCount = 0
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    ElMessage.error('操作失败，请稍后重试')
    console.error('[messages] readAllInbox failed', e)
  }
}

onMounted(fetchMessages)
</script>

<template>
  <div class="zx-page">
    <div class="mb-5 flex flex-wrap items-center gap-4">
      <h1 class="text-2xl font-bold">消息中心</h1>
      <el-badge :value="unreadCount" :hidden="!unreadCount" class="ml-1">
        <el-tag effect="plain" round>未读消息</el-tag>
      </el-badge>
      <el-button class="ml-auto" round :disabled="!unreadCount" @click="onReadAll">一键已读</el-button>
    </div>

    <div v-loading="loading" class="mx-auto max-w-3xl space-y-4">
      <EmptyState v-if="!loading && !messages.length" description="暂无消息" />
      <div
        v-for="m in messages"
        :key="m.id"
        class="zx-card flex cursor-pointer items-start gap-4 p-5"
        :class="{ 'ring-1 ring-primary': !m.read }"
        @click="onRead(m)"
      >
        <div
          class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-lg font-bold"
          :class="m.read ? '' : 'zx-ai-avatar'"
          :style="m.read ? 'background: var(--zx-primary-bg); color: var(--zx-primary)' : ''"
        >
          知
        </div>
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="font-semibold" :class="{ 'opacity-75': m.read }">{{ m.title || '系统消息' }}</span>
            <el-badge v-if="!m.read" is-dot />
          </div>
          <p class="zx-text-secondary mt-1.5 text-sm leading-6">{{ m.content }}</p>
          <p class="zx-text-secondary mt-2 text-xs">{{ formatDate(m.createTime) }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
