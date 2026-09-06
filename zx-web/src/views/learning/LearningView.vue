<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheckFilled, Delete, Timer } from '@element-plus/icons-vue'
import { pageLessons, myLearningRecords, pageNotes, addNote, deleteNote, signInDates, signInToday, doSignIn } from '@/api/learning'
import { useUserStore } from '@/stores/user'
import { formatMinutes } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { LearningLessonVO, LearningRecordVO, NoteVO } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('lessons')

/* ---------- 我的课表 ---------- */
const lessons = ref<LearningLessonVO[]>([])
const lessonsTotal = ref(0)
const lessonsQuery = reactive({ pageNo: 1, pageSize: 8 })

async function fetchLessons() {
  try {
    const res = await pageLessons({ ...lessonsQuery })
    lessons.value = res.list
    lessonsTotal.value = res.total
  } catch {
    /* ignore */
  }
}

const lessonStatusText: Record<number, { text: string; type: 'primary' | 'success' | 'info' }> = {
  0: { text: '在学', type: 'primary' },
  1: { text: '已完成', type: 'success' },
  2: { text: '已过期', type: 'info' },
}

/* ---------- 学习记录时间线 ---------- */
const records = ref<LearningRecordVO[]>([])

async function fetchRecords() {
  try {
    records.value = await myLearningRecords(userStore.userId || 1)
  } catch {
    /* ignore */
  }
}

/* ---------- 我的笔记（轻量富文本） ---------- */
const notes = ref<NoteVO[]>([])
const notesTotal = ref(0)
const notesQuery = reactive({ pageNo: 1, pageSize: 10 })
const editorRef = ref<HTMLDivElement>()
const noteSaving = ref(false)

const editorToolbar = [
  { cmd: 'bold', label: 'B', title: '加粗', style: 'font-weight:700' },
  { cmd: 'italic', label: 'I', title: '斜体', style: 'font-style:italic' },
  { cmd: 'underline', label: 'U', title: '下划线', style: 'text-decoration:underline' },
  { cmd: 'insertUnorderedList', label: '•', title: '无序列表', style: '' },
  { cmd: 'insertOrderedList', label: '1.', title: '有序列表', style: '' },
]

function execCmd(cmd: string) {
  editorRef.value?.focus()
  document.execCommand(cmd)
}

async function saveNote() {
  const content = editorRef.value?.innerHTML?.trim()
  if (!content || content === '<br>') {
    ElMessage.warning('笔记内容不能为空')
    return
  }
  noteSaving.value = true
  try {
    await addNote({ courseId: 0, content, courseName: '随手记' })
    editorRef.value!.innerHTML = ''
    ElMessage.success('笔记已保存')
    await fetchNotes()
  } catch {
    /* ignore */
  } finally {
    noteSaving.value = false
  }
}

async function fetchNotes() {
  try {
    const res = await pageNotes({ ...notesQuery })
    notes.value = res.list
    notesTotal.value = res.total
  } catch {
    /* ignore */
  }
}

async function removeNote(id: number) {
  await ElMessageBox.confirm('确定删除这条笔记吗？', '提示', { type: 'warning' }).catch(() => null)
  try {
    await deleteNote(id)
    ElMessage.success('已删除')
    await fetchNotes()
  } catch {
    /* ignore */
  }
}

/* ---------- 签到打卡 ---------- */
const signDates = ref<string[]>([])
const signedToday = ref(false)
const signing = ref(false)
const today = new Date()

/** el-calendar 单元格对应日期是否已签到 */
function isSigned(date: Date): boolean {
  const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return signDates.value.includes(key)
}

async function fetchSign() {
  try {
    const [dates, todayRes] = await Promise.all([signInDates(), signInToday()])
    signDates.value = dates
    signedToday.value = todayRes.signed
  } catch {
    /* ignore */
  }
}

async function onSignIn() {
  if (signedToday.value) return
  signing.value = true
  try {
    await doSignIn()
    signedToday.value = true
    ElMessage.success('签到成功，+1 连续打卡！')
    await fetchSign()
  } catch {
    /* ignore */
  } finally {
    signing.value = false
  }
}

/** 连续打卡天数 */
const continuousDays = computed(() => {
  const set = new Set(signDates.value)
  let count = 0
  const cursor = new Date(today)
  // 今日未签也从昨天开始数
  if (!set.has(fmt(cursor))) cursor.setDate(cursor.getDate() - 1)
  while (set.has(fmt(cursor))) {
    count += 1
    cursor.setDate(cursor.getDate() - 1)
  }
  return count
})

function fmt(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  fetchLessons()
  fetchRecords()
  fetchNotes()
  fetchSign()
})
</script>

<template>
  <div class="zx-page">
    <div class="mb-5 flex flex-wrap items-center gap-4">
      <h1 class="text-2xl font-bold">学习中心</h1>
      <div class="ml-auto flex items-center gap-3">
        <el-tag effect="plain" round size="large">
          <el-icon class="mr-1 align-middle"><Timer /></el-icon>
          连续打卡 {{ continuousDays }} 天
        </el-tag>
        <el-button
          type="primary"
          round
          :loading="signing"
          :disabled="signedToday"
          @click="onSignIn"
        >
          <el-icon v-if="signedToday" class="mr-1"><CircleCheckFilled /></el-icon>
          {{ signedToday ? '今日已签到' : '每日签到' }}
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="zx-tabs">
      <!-- 我的课表 -->
      <el-tab-pane label="我的课表" name="lessons">
        <EmptyState v-if="!lessons.length && lessonsTotal === 0" description="还没有在学课程，去挑选一门吧">
          <el-button type="primary" round @click="router.push('/courses')">浏览课程</el-button>
        </EmptyState>
        <div v-else class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
          <div v-for="l in lessons" :key="l.id" class="zx-card zx-card-hover overflow-hidden">
            <div class="relative">
              <img :src="l.coverUrl" :alt="l.courseName" class="h-36 w-full object-cover" />
              <el-tag :type="lessonStatusText[l.status]?.type" effect="dark" size="small" class="absolute right-3 top-3" round>
                {{ lessonStatusText[l.status]?.text }}
              </el-tag>
            </div>
            <div class="p-4">
              <h3 class="line-clamp-1 font-semibold">{{ l.courseName }}</h3>
              <el-progress
                :percentage="l.learnProgress ?? 0"
                :stroke-width="8"
                class="mt-3"
                :color="l.learnProgress === 100 ? '#22c55e' : '#4F46E5'"
              />
              <div class="zx-text-secondary mt-2 flex justify-between text-xs">
                <span>周学习 {{ l.weekFreq ?? '-' }} 次</span>
                <span>{{ l.createTime }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="lessonsTotal > lessonsQuery.pageSize" class="mt-6 flex justify-center">
          <el-pagination
            v-model:current-page="lessonsQuery.pageNo"
            :page-size="lessonsQuery.pageSize"
            :total="lessonsTotal"
            layout="prev, pager, next"
            background
            @current-change="fetchLessons"
          />
        </div>
      </el-tab-pane>

      <!-- 学习记录时间线 -->
      <el-tab-pane label="学习记录" name="records">
        <EmptyState v-if="!records.length" description="暂无学习记录" />
        <el-timeline v-else class="mx-auto max-w-2xl pt-2">
          <el-timeline-item
            v-for="r in records"
            :key="r.id"
            :timestamp="r.updateTime"
            :type="r.finished ? 'success' : 'primary'"
            placement="top"
          >
            <div class="zx-card p-4">
              <div class="flex items-center justify-between gap-3">
                <span class="font-medium">{{ r.sectionName }}</span>
                <el-tag :type="r.finished ? 'success' : 'warning'" size="small" round>
                  {{ r.finished ? '已完成' : '学习中' }}
                </el-tag>
              </div>
              <div class="zx-text-secondary mt-1 text-xs">
                {{ r.courseName }} · 观看 {{ formatMinutes(Math.floor(r.moment / 60)) }}
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-tab-pane>

      <!-- 我的笔记 -->
      <el-tab-pane label="我的笔记" name="notes">
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <!-- 富文本编辑器（contenteditable 轻量实现） -->
          <div class="zx-card overflow-hidden">
            <div class="zx-editor-toolbar flex items-center gap-1 border-b px-3 py-2">
              <button
                v-for="t in editorToolbar"
                :key="t.cmd"
                class="zx-editor-btn"
                :style="t.style"
                :title="t.title"
                @mousedown.prevent
                @click="execCmd(t.cmd)"
              >
                {{ t.label }}
              </button>
              <el-button type="primary" size="small" round class="ml-auto" :loading="noteSaving" @click="saveNote">
                保存笔记
              </el-button>
            </div>
            <div
              ref="editorRef"
              contenteditable="true"
              data-placeholder="记录你的学习心得…支持加粗、斜体、列表等格式"
              class="zx-editor min-h-[220px] px-4 py-3 text-sm leading-7 focus:outline-none"
            />
          </div>

          <!-- 笔记列表 -->
          <div class="space-y-4">
            <EmptyState v-if="!notes.length" description="还没有笔记" size="small" />
            <div v-for="n in notes" :key="n.id" class="zx-card p-4">
              <div class="flex items-center justify-between">
                <span class="text-xs font-medium text-primary">{{ n.courseName }}</span>
                <div class="flex items-center gap-2">
                  <span class="zx-text-secondary text-xs">{{ n.createTime }}</span>
                  <el-button :icon="Delete" text size="small" type="danger" @click="removeNote(n.id)" />
                </div>
              </div>
              <div class="zx-markdown zx-text-secondary mt-2 text-sm" v-html="n.content" />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 签到日历 -->
      <el-tab-pane label="签到打卡" name="sign">
        <div class="zx-card p-4">
          <el-calendar>
            <template #header>
              <div class="flex items-center gap-3">
                <span class="font-semibold">每日签到</span>
                <el-tag type="success" effect="plain" round>已连续 {{ continuousDays }} 天</el-tag>
              </div>
            </template>
            <template #date-cell="{ data }">
              <div class="zx-sign-cell" :class="{ 'is-signed': isSigned(data.date) }">
                <span>{{ data.date.getDate() }}</span>
                <span v-if="isSigned(data.date)" class="zx-sign-dot">✓</span>
              </div>
            </template>
          </el-calendar>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.zx-editor-toolbar {
  background: var(--zx-primary-bg);
  border-color: var(--zx-border);
}
.zx-editor-btn {
  min-width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--zx-primary);
  cursor: pointer;
  font-size: 13px;
  transition: background 0.2s;
}
.zx-editor-btn:hover {
  background: rgba(79, 70, 229, 0.15);
}
.zx-editor:empty::before,
.zx-editor[data-placeholder]:not(:focus):empty::before {
  content: attr(data-placeholder);
  color: var(--zx-text-secondary);
  pointer-events: none;
}
.zx-sign-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 48px;
}
.zx-sign-cell.is-signed {
  background: var(--zx-primary-bg);
  border-radius: 8px;
  color: var(--zx-primary);
  font-weight: 700;
}
.zx-sign-dot {
  position: absolute;
  right: 6px;
  top: 4px;
  color: #22c55e;
  font-size: 12px;
}
</style>
