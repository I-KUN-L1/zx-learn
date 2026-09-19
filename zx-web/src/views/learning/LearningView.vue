<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import {
  CircleCheckFilled,
  Delete,
  Reading,
  Refresh,
  Right as ArrowRight,
  Search,
  ShoppingCart,
  Timer,
} from '@element-plus/icons-vue'
import {
  pageLessons,
  myLearningRecords,
  pageNotes,
  addNote,
  deleteNote,
  signInDates,
  signInToday,
  doSignIn,
} from '@/api/learning'
import { pageCourses } from '@/api/course'
import { addToCart, freeCourse } from '@/api/trade'
import { useOwnedCourses } from '@/composables/useOwnedCourses'
import { useUserStore } from '@/stores/user'
import { formatMinutes } from '@/utils/format'
import { renderMarkdown } from '@/utils/markdown'
import CourseCover from '@/components/course/CourseCover.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { CourseVO, LearningLessonVO, LearningRecordVO, NoteVO } from '@/types/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('lessons')

/**
 * 已拥有课程集合：以「我的课表」为唯一权威口径，与课程列表 / 课程详情共用同一份数据。
 * 课表里有 → 显示「已拥有」并可继续学习；课表里没有 → 显示可购买状态。
 */
const { isOwned, refresh: refreshOwned, markOwned } = useOwnedCourses()

/* ---------- 我的课表 ---------- */
/** 一次性拉取（学员课表规模小），前端完成筛选 / 排序 / 分页，三者语义一致 */
const lessons = ref<LearningLessonVO[]>([])
const lessonsTotal = ref(0)
const lessonsFetching = ref(false)
const LESSON_FETCH_SIZE = 100

async function fetchLessons() {
  lessonsFetching.value = true
  try {
    const res = await pageLessons({ pageNo: 1, pageSize: LESSON_FETCH_SIZE })
    lessons.value = res.list ?? []
    lessonsTotal.value = res.total ?? 0
  } catch {
    /* ignore */
  } finally {
    lessonsFetching.value = false
  }
}

const lessonStatusText: Record<number, { text: string; type: 'primary' | 'success' | 'info' }> = {
  0: { text: '在学', type: 'primary' },
  1: { text: '已完成', type: 'success' },
  2: { text: '已过期', type: 'info' },
}

/* ---------- 课表筛选 / 排序 / 分页（前端完成，保证与统计口径一致） ---------- */
const lessonFilter = ref<'all' | 0 | 1>('all')
const lessonSort = ref<'recent' | 'progress'>('recent')
/**
 * 每页 9 门：卡片网格在 xl 断点是 3 列，9 门正好 3×3 铺满，不留空白单元格。
 * 之前是 8 —— 9 门课会把第 9 门挤到第 2 页，看起来像"少了一门"。
 */
const LESSON_PAGE_SIZE = 9
/** 课表不超过 5 行（3 列 × 5 = 15 门）时整屏展示，避免多一两门就被拆到下一页 */
const LESSON_SHOW_ALL_MAX = 15
const lessonPage = reactive({ pageNo: 1, pageSize: LESSON_PAGE_SIZE })

const filteredLessons = computed(() => {
  const list =
    lessonFilter.value === 'all'
      ? [...lessons.value]
      : lessons.value.filter((l) => l.status === lessonFilter.value)
  return list.sort((a, b) =>
    lessonSort.value === 'progress'
      ? (b.learnProgress ?? 0) - (a.learnProgress ?? 0)
      : String(b.createTime ?? '').localeCompare(String(a.createTime ?? ''))
  )
})

/** 小规模课表不分页：一屏能放下就直接全部渲染，保证"九门课在同一界面完整展示" */
const lessonShowAll = computed(() => filteredLessons.value.length <= LESSON_SHOW_ALL_MAX)

const pagedLessons = computed(() => {
  if (lessonShowAll.value) return filteredLessons.value
  const start = (lessonPage.pageNo - 1) * lessonPage.pageSize
  return filteredLessons.value.slice(start, start + lessonPage.pageSize)
})

watch([lessonFilter, lessonSort], () => {
  lessonPage.pageNo = 1
})

/* ---------- 课表统计（避免顶部大面积留白，同时给出有价值的信息） ---------- */
const learningRecords = ref<LearningRecordVO[]>([])

const stats = computed(() => {
  const active = lessons.value.filter((l) => l.status === 0).length
  const done = lessons.value.filter((l) => l.status === 1).length
  const avgProgress = lessons.value.length
    ? Math.round(
        lessons.value.reduce((sum, l) => sum + (l.learnProgress ?? 0), 0) / lessons.value.length
      )
    : 0
  const totalSeconds = learningRecords.value.reduce((sum, r) => sum + (r.moment ?? 0), 0)
  const finishedSections = learningRecords.value.filter((r) => r.finished).length
  return { active, done, avgProgress, totalSeconds, finishedSections }
})

/* ---------- 课程检索（需求：检索结果按「我的课表」判定已拥有 / 可购买） ---------- */
const searchKeyword = ref('')
const searchLoading = ref(false)
/** 是否已执行过检索（未检索时不渲染结果区，避免空白块） */
const searched = ref(false)
const searchResults = ref<CourseVO[]>([])
const actionBusyId = ref('')

async function onSearchCourses() {
  const keyword = searchKeyword.value.trim()
  searchLoading.value = true
  actionBusyId.value = ''
  try {
    // 先刷新「已拥有」状态，保证检索结果的持有状态与我的课表完全同步
    await refreshOwned(true)
    const res = await pageCourses({ pageNo: 1, pageSize: 12, name: keyword || undefined })
    searchResults.value = res.list ?? []
    searched.value = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    searchLoading.value = false
  }
}

function clearSearch() {
  searchKeyword.value = ''
  searched.value = false
  searchResults.value = []
}

/** 检索结果里的操作：已拥有 → 继续学习；免费课 → 加入学习；付费课 → 购买 / 加购物车 */
function gotoLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

function onContinue(course: CourseVO) {
  router.push(`/learning/course/${course.id}`)
}

function onBuy(course: CourseVO) {
  if (!userStore.isLoggedIn) return gotoLogin()
  router.push({ path: '/trade', query: { courseIds: String(course.id) } })
}

async function onAddCart(course: CourseVO) {
  if (!userStore.isLoggedIn) return gotoLogin()
  if (actionBusyId.value) return
  actionBusyId.value = String(course.id)
  try {
    await addToCart(course.id)
    ElMessage.success('已加入购物车')
  } catch {
    /* 拦截器已提示 */
  } finally {
    actionBusyId.value = ''
  }
}

async function onEnrollFree(course: CourseVO) {
  if (!userStore.isLoggedIn) return gotoLogin()
  if (isOwned(course.id)) return onContinue(course)
  if (actionBusyId.value) return
  actionBusyId.value = String(course.id)
  try {
    await freeCourse(course.id)
    // 后端支付成功时会同步写入课表；这里乐观置位并重拉，做到两端立即一致
    markOwned(course.id)
    ElMessage.success(`《${course.name}》已加入学习`)
    await Promise.all([refreshOwned(true), fetchLessons()])
  } catch {
    /* 拦截器已提示（如已拥有） */
  } finally {
    actionBusyId.value = ''
  }
}

/* ---------- 学习记录时间线 ---------- */
async function fetchRecords() {
  try {
    learningRecords.value = (await myLearningRecords()) ?? []
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
    notes.value = res.list ?? []
    notesTotal.value = res.total ?? 0
  } catch {
    /* ignore */
  }
}

async function removeNote(id: number) {
  if (!(await confirmAction('确定删除这条笔记吗？', '提示', { type: 'warning' }))) return
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
    signDates.value = dates ?? []
    signedToday.value = todayRes
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

/** 登录态变化后强制重拉，避免沿用上一个账号的课表与已拥有状态 */
watch(
  () => userStore.userId,
  () => {
    refreshOwned(true)
    fetchLessons()
  }
)

onMounted(() => {
  refreshOwned()
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
        <!-- 统计条：让页面顶部有内容、有信息量，不留空白 -->
        <div class="zx-stat-grid">
          <div class="zx-card zx-stat">
            <span class="zx-stat__label">在学课程</span>
            <span class="zx-stat__value text-primary">{{ stats.active }}</span>
            <span class="zx-stat__hint">共 {{ lessonsTotal }} 门已开通</span>
          </div>
          <div class="zx-card zx-stat">
            <span class="zx-stat__label">已完成</span>
            <span class="zx-stat__value text-emerald-500">{{ stats.done }}</span>
            <span class="zx-stat__hint">已学完 {{ stats.finishedSections }} 节</span>
          </div>
          <div class="zx-card zx-stat">
            <span class="zx-stat__label">平均进度</span>
            <span class="zx-stat__value text-primary">{{ stats.avgProgress }}%</span>
            <el-progress
              :percentage="stats.avgProgress"
              :stroke-width="6"
              :show-text="false"
              class="mt-1"
              :color="stats.avgProgress === 100 ? '#22c55e' : '#4F46E5'"
            />
          </div>
          <div class="zx-card zx-stat">
            <span class="zx-stat__label">累计学习时长</span>
            <span class="zx-stat__value text-primary">
              {{ formatMinutes(Math.floor(stats.totalSeconds / 60)) }}
            </span>
            <span class="zx-stat__hint">来自全部学习记录</span>
          </div>
        </div>

        <!-- 课程检索：按「我的课表」判定已拥有 / 可购买 -->
        <div class="zx-card mt-5 p-4">
          <div class="flex flex-wrap items-center gap-3">
            <div class="min-w-0">
              <h2 class="font-bold">课程检索</h2>
              <p class="zx-text-secondary mt-1 text-xs">
                在课表外检索课程：<b>已在课表中</b>的显示「已拥有」并可直接进入学习，
                未拥有的显示可购买状态
              </p>
            </div>
            <div class="ml-auto flex flex-wrap items-center gap-2">
              <el-input
                v-model="searchKeyword"
                placeholder="输入课程名称检索"
                :prefix-icon="Search"
                clearable
                class="!w-64"
                @keyup.enter="onSearchCourses"
                @clear="clearSearch"
              />
              <el-button type="primary" round :loading="searchLoading" @click="onSearchCourses">
                检索
              </el-button>
              <el-button v-if="searched" round @click="clearSearch">收起结果</el-button>
            </div>
          </div>

          <!-- 检索结果 -->
          <div v-if="searched" v-loading="searchLoading" class="mt-4">
            <EmptyState
              v-if="!searchResults.length"
              description="没有检索到相关课程，换个关键词试试"
              size="small"
            />
            <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
              <div v-for="c in searchResults" :key="c.id" class="zx-pick-card">
                <div class="zx-pick-card__cover">
                  <CourseCover :src="c.coverUrl" :name="c.name" :seed="c.id" />
                  <el-tag
                    v-if="isOwned(c.id)"
                    type="primary"
                    effect="dark"
                    size="small"
                    class="absolute left-3 top-3"
                    round
                  >
                    已拥有
                  </el-tag>
                  <el-tag
                    v-else-if="c.free === 1"
                    type="success"
                    effect="dark"
                    size="small"
                    class="absolute left-3 top-3"
                    round
                  >
                    免费
                  </el-tag>
                </div>
                <div class="flex min-w-0 flex-1 flex-col p-3">
                  <h3 class="line-clamp-2 text-sm font-semibold leading-5">{{ c.name }}</h3>
                  <div class="zx-text-secondary mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                    <span v-if="c.enrollNum != null">{{ c.enrollNum.toLocaleString() }} 人在学</span>
                    <span v-if="c.score" class="text-amber-500">★ {{ c.score.toFixed(1) }}</span>
                    <span v-if="c.free !== 1" class="font-semibold text-primary">
                      ￥{{ (c.price / 100).toFixed(2) }}
                    </span>
                    <span v-else class="font-semibold text-green-500">免费</span>
                  </div>

                  <div class="mt-auto flex flex-wrap items-center gap-2 pt-3">
                    <template v-if="isOwned(c.id)">
                      <el-button type="primary" size="small" round @click="onContinue(c)">
                        进入学习
                        <el-icon class="ml-1"><ArrowRight /></el-icon>
                      </el-button>
                      <span class="zx-text-secondary text-xs">已在你的课表中</span>
                    </template>
                    <template v-else-if="c.free === 1">
                      <el-button
                        type="primary"
                        size="small"
                        round
                        :loading="actionBusyId === String(c.id)"
                        @click="onEnrollFree(c)"
                      >
                        加入学习
                      </el-button>
                      <span class="zx-text-secondary text-xs">可购买 · 0 元开课</span>
                    </template>
                    <template v-else>
                      <el-button type="primary" size="small" round @click="onBuy(c)">
                        立即购买
                      </el-button>
                      <el-button
                        size="small"
                        round
                        :icon="ShoppingCart"
                        :loading="actionBusyId === String(c.id)"
                        @click="onAddCart(c)"
                      >
                        加购物车
                      </el-button>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 我的课表列表 -->
        <div class="mt-5">
          <div class="zx-card flex flex-wrap items-center gap-3 p-4">
            <h2 class="flex items-center gap-2 font-bold">
              <el-icon class="text-primary"><Reading /></el-icon>
              我的课表
              <span class="zx-text-secondary text-sm font-normal">({{ filteredLessons.length }} 门)</span>
            </h2>
            <div class="ml-auto flex flex-wrap items-center gap-2">
              <el-radio-group v-model="lessonFilter" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button :value="0">在学</el-radio-button>
                <el-radio-button :value="1">已完成</el-radio-button>
              </el-radio-group>
              <el-select v-model="lessonSort" size="small" class="!w-32">
                <el-option label="最近加入" value="recent" />
                <el-option label="进度优先" value="progress" />
              </el-select>
              <el-button size="small" round @click="fetchLessons">
                <el-icon class="mr-1"><Refresh /></el-icon>刷新
              </el-button>
            </div>
          </div>

          <div v-loading="lessonsFetching" class="mt-4">
            <EmptyState
              v-if="!lessons.length && !lessonsFetching"
              description="还没有在学课程，去挑选一门吧"
            >
              <el-button type="primary" round @click="router.push('/courses')">浏览课程</el-button>
            </EmptyState>
            <EmptyState
              v-else-if="!filteredLessons.length"
              :description="lessonFilter === 1 ? '还没有已完成的课程，继续加油' : '当前筛选下没有课程'"
              size="small"
            >
              <el-button round @click="lessonFilter = 'all'">查看全部</el-button>
            </EmptyState>
            <div v-else class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
              <!-- 整卡可点击进入「课程内容页」（学习通式：目录 + 内容 + 讨论 + 测验） -->
              <div
                v-for="l in pagedLessons"
                :key="l.id"
                class="zx-card zx-card-hover zx-lesson-card overflow-hidden"
                role="button"
                tabindex="0"
                :title="`查看《${l.courseName}》课程内容`"
                @click="router.push(`/learning/course/${l.courseId}`)"
                @keyup.enter="router.push(`/learning/course/${l.courseId}`)"
              >
                <div class="relative h-36">
                  <CourseCover :src="l.coverUrl" :name="l.courseName" :seed="l.courseId" />
                  <el-tag
                    :type="lessonStatusText[l.status]?.type"
                    effect="dark"
                    size="small"
                    class="absolute right-3 top-3"
                    round
                  >
                    {{ lessonStatusText[l.status]?.text ?? '在学' }}
                  </el-tag>
                </div>
                <div class="p-4">
                  <h3 class="line-clamp-1 font-semibold">{{ l.courseName }}</h3>
                  <div class="mt-3 flex items-center gap-2">
                    <el-progress
                      :percentage="l.learnProgress ?? 0"
                      :stroke-width="8"
                      class="min-w-0 flex-1"
                      :color="l.learnProgress === 100 ? '#22c55e' : '#4F46E5'"
                    />
                    <span class="zx-text-secondary shrink-0 text-xs">{{ l.learnProgress ?? 0 }}%</span>
                  </div>
                  <div class="zx-text-secondary mt-2 flex items-center justify-between gap-2 text-xs">
                    <span class="min-w-0 truncate">周学习 {{ l.weekFreq ?? '-' }} 次</span>
                    <span class="shrink-0">加入于 {{ l.createTime }}</span>
                  </div>
                  <el-button class="mt-3 w-full" type="primary" plain round size="small">
                    查看课程内容
                    <el-icon class="ml-1"><ArrowRight /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
            <!-- 课表规模小时整屏展示（不分页），超出时才出现分页；并给出总数/范围提示，
                 让"还有多少门课"始终可见，避免出现"看不见的课程" -->
            <div v-if="!lessonShowAll" class="mt-6 flex flex-wrap items-center justify-center gap-3">
              <span class="zx-text-secondary text-sm">
                共 {{ filteredLessons.length }} 门，本页显示
                {{ (lessonPage.pageNo - 1) * lessonPage.pageSize + 1 }}–{{
                  Math.min(lessonPage.pageNo * lessonPage.pageSize, filteredLessons.length)
                }} 门
              </span>
              <el-pagination
                v-model:current-page="lessonPage.pageNo"
                :page-size="lessonPage.pageSize"
                :total="filteredLessons.length"
                layout="prev, pager, next"
                background
              />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 学习记录时间线 -->
      <el-tab-pane label="学习记录" name="records">
        <EmptyState v-if="!learningRecords.length" description="暂无学习记录" />
        <el-timeline v-else class="mx-auto max-w-2xl pt-2">
          <el-timeline-item
            v-for="r in learningRecords"
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
              <!-- 笔记内容为用户输入：经 renderMarkdown(DOMPurify) 净化后渲染，防存储型 XSS -->
              <div class="zx-markdown zx-text-secondary mt-2 text-sm" v-html="renderMarkdown(n.content)" />
            </div>
            <div v-if="notesTotal > notesQuery.pageSize" class="flex justify-center">
              <el-pagination
                v-model:current-page="notesQuery.pageNo"
                :page-size="notesQuery.pageSize"
                :total="notesTotal"
                layout="prev, pager, next"
                background
                small
                @current-change="fetchNotes"
              />
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
.zx-lesson-card {
  cursor: pointer;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.zx-lesson-card:focus-visible {
  outline: 2px solid var(--zx-primary);
  outline-offset: 2px;
}
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

/* 统计条：自适应列数，窄屏单列，避免出现空白单元格 */
.zx-stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
@media (min-width: 1024px) {
  .zx-stat-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
.zx-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 16px;
}
.zx-stat__label {
  font-size: 13px;
  color: var(--zx-text-secondary);
}
.zx-stat__value {
  font-size: 24px;
  font-weight: 800;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.zx-stat__hint {
  font-size: 12px;
  color: var(--zx-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 检索结果卡片：横向布局，信息密度高，不出现大片留白 */
.zx-pick-card {
  display: flex;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--zx-border);
  border-radius: 12px;
  background: var(--zx-bg-card);
  transition: box-shadow 0.2s, transform 0.2s;
}
.zx-pick-card:hover {
  box-shadow: 0 8px 24px rgba(79, 70, 229, 0.12);
  transform: translateY(-2px);
}
.zx-pick-card__cover {
  position: relative;
  flex-shrink: 0;
  width: 108px;
  min-height: 108px;
  overflow: hidden;
}
</style>
