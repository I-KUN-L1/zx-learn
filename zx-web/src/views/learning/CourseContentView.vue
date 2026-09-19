<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  ChatDotRound,
  CircleCheckFilled,
  Clock,
  DataAnalysis,
  Delete,
  Document,
  Download,
  EditPen,
  Notebook,
  VideoPlay,
} from '@element-plus/icons-vue'
import { getCourse } from '@/api/course'
import { reportProgress, myLearningRecords, pageNotes, addNote, deleteNote } from '@/api/learning'
import { pageBoards, boardDetail, createBoard, createReply, deleteBoard, deleteReply } from '@/api/board'
import { practicePage } from '@/api/exam'
import { useUserStore } from '@/stores/user'
import { formatMinutes } from '@/utils/format'
import { renderMarkdown } from '@/utils/markdown'
import { confirmAction } from '@/utils/confirm'
import EmptyState from '@/components/common/EmptyState.vue'
import type {
  BoardVO,
  CourseCatalogue,
  CourseVO,
  LearningRecordVO,
  NoteVO,
  QuestionVO,
} from '@/types/api'

/**
 * 课程内容页（学习通风格）。
 *
 * 版式：左侧章节目录树（章节可折叠、已完成打勾、当前小节高亮），
 * 右侧内容区按「章节内容 / 课程讨论 / 课程测验」三 Tab 切换，
 * 与学习通"课程 → 目录 + 内容"的心智模型对齐。
 *
 * 与积分的联动：标记小节完成后调 /learning-records/progress，
 * 由服务端在进度首次达到 100% 时自动发放积分（完成小节 +10，整门课程 +50），
 * 前端只负责提示，不参与计算。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const courseId = computed(() => Number(route.params.courseId))

const loading = ref(true)
const course = ref<CourseVO | null>(null)

/* ==================== 章节目录 ==================== */

/** 章节（顶层节点）。后端已组装两级结构，孤儿小节也会被提升到顶层，不会丢内容 */
const chapters = computed<CourseCatalogue[]>(() => course.value?.catalogues ?? [])

interface FlatSection extends CourseCatalogue {
  chapterId: number
  chapterName: string
}

/** 拍平后的小节序列：用于上一节/下一节与进度统计 */
const sections = computed<FlatSection[]>(() =>
  chapters.value.flatMap((ch) =>
    (ch.sections ?? []).map((s) => ({ ...s, chapterId: ch.id, chapterName: ch.name })),
  ),
)

const activeSectionId = ref<number | null>(null)
const activeChapterId = ref<number | null>(null)

const activeSection = computed(
  () => sections.value.find((s) => s.id === activeSectionId.value) ?? null,
)
const activeIndex = computed(() => sections.value.findIndex((s) => s.id === activeSectionId.value))

/** 默认展开全部章节：学习通默认展开，减少一次点击 */
const expandedChapters = ref<number[]>([])
watch(chapters, (list) => {
  expandedChapters.value = list.map((c) => c.id)
})

/* ==================== 学习进度 ==================== */

/** lessonId(小节 id) → 进度 */
const progressMap = ref<Record<number, { progress: number; finished: boolean }>>({})
/** 本课程的学习记录（用于「我的学习记录」区展示观看时长/完成时间） */
const courseRecords = ref<LearningRecordVO[]>([])

const finishedCount = computed(
  () => sections.value.filter((s) => progressMap.value[s.id]?.finished).length,
)
const coursePercent = computed(() =>
  sections.value.length === 0
    ? 0
    : Math.round((finishedCount.value * 100) / sections.value.length),
)

function sectionState(sectionId: number) {
  return progressMap.value[sectionId] ?? { progress: 0, finished: false }
}

/** 当前小节的学习记录（可能不存在：还没开始学） */
const activeRecord = computed<LearningRecordVO | null>(() => {
  const id = activeSectionId.value
  if (id == null) return null
  return courseRecords.value.find((r) => Number(r.lessonId ?? r.sectionId) === Number(id)) ?? null
})

/** 当前小节在该课程中的序号（第 N 节 / 共 M 节） */
const activeOrdinal = computed(() =>
  activeIndex.value >= 0 ? activeIndex.value + 1 : 0
)

/**
 * 拉取本课程的学习进度。
 * <p>
 * 后端学习记录以"小节"为单位（sectionId = 目录小节 id），只区分 finished/未完成，
 * 因此这里按「已学完 = 100%，有记录未完成 = 50%」映射为进度条语义；
 * 同一小节存在多条记录时取"已完成优先"。
 */
async function fetchProgress() {
  try {
    const records = await myLearningRecords()
    courseRecords.value = (records ?? []).filter((r) => Number(r.courseId) === courseId.value)
    const map: Record<number, { progress: number; finished: boolean }> = {}
    for (const r of courseRecords.value) {
      const key = Number(r.lessonId ?? r.sectionId)
      const prev = map[key]
      const finished = Boolean(r.finished) || Boolean(prev?.finished)
      map[key] = { finished, progress: finished ? 100 : Math.max(prev?.progress ?? 0, 50) }
    }
    progressMap.value = map
  } catch {
    /* 进度获取失败仅影响打勾展示，不阻塞页面 */
  }
}

/* ==================== 章节内容：要点 / 讲义 / 资料 / 本节笔记 ==================== */

/** 本节要点（后端以 "|" 分隔） */
const activeKeyPoints = computed<string[]>(() => {
  const raw = activeSection.value?.keyPoints
  if (!raw) return []
  return raw
    .split('|')
    .map((s) => s.trim())
    .filter(Boolean)
})

/** 讲义正文渲染为 HTML（XSS 过滤在 renderMarkdown 内完成） */
const activeHandoutHtml = computed(() =>
  activeSection.value?.content ? renderMarkdown(activeSection.value.content) : ''
)

/** 本节笔记（按 courseId + 小节 id 精确过滤，与「我的笔记」互不干扰） */
const sectionNotes = ref<NoteVO[]>([])
const noteDraft = ref('')
const noteLoading = ref(false)
const noteSubmitting = ref(false)

async function fetchSectionNotes() {
  const id = activeSectionId.value
  if (id == null || !userStore.isStudent) {
    sectionNotes.value = []
    return
  }
  noteLoading.value = true
  try {
    const res = await pageNotes({
      pageNo: 1,
      pageSize: 50,
      courseId: courseId.value,
      lessonId: id,
    })
    sectionNotes.value = res.list ?? []
  } catch {
    /* 笔记获取失败不影响讲义阅读 */
  } finally {
    noteLoading.value = false
  }
}

async function submitSectionNote() {
  const section = activeSection.value
  const content = noteDraft.value.trim()
  if (!section) return
  if (!content) {
    ElMessage.warning('请输入笔记内容')
    return
  }
  noteSubmitting.value = true
  try {
    await addNote({
      courseId: courseId.value,
      lessonId: section.id,
      courseName: course.value?.name,
      content,
    })
    noteDraft.value = ''
    ElMessage.success('笔记已保存到本节')
    await fetchSectionNotes()
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    noteSubmitting.value = false
  }
}

async function removeSectionNote(id: number) {
  if (!(await confirmAction('确定删除这条笔记吗？', '提示', { type: 'warning' }))) return
  try {
    await deleteNote(id)
    ElMessage.success('已删除')
    await fetchSectionNotes()
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

// 切换小节时重新拉取"本节笔记"
watch(activeSectionId, () => {
  noteDraft.value = ''
  fetchSectionNotes()
})

const marking = ref(false)

/** 标记当前小节为已学完（服务端据此自动加分） */
async function markFinished() {
  const section = activeSection.value
  if (!section || marking.value) return
  if (sectionState(section.id).finished) {
    ElMessage.info('该小节已完成学习')
    return
  }
  marking.value = true
  try {
    await reportProgress({
      courseId: courseId.value,
      lessonId: section.id,
      progress: 100,
      // 上报本次学习时长（演示：按小节标称时长折算，真实播放器应上报实际观看秒数）
      learnDuration: section.duration ?? 0,
    })
    progressMap.value = {
      ...progressMap.value,
      [section.id]: { progress: 100, finished: true },
    }
    ElMessage.success('已完成本节学习，积分 +10 已到账')
    // 重拉学习记录，让「我的学习记录」的观看时长/完成时间立即同步
    await fetchProgress()
    goNext()
  } catch {
    /* 错误由拦截器统一提示（如进度倒退） */
  } finally {
    marking.value = false
  }
}

function goPrev() {
  if (activeIndex.value > 0) selectSection(sections.value[activeIndex.value - 1])
}

function goNext() {
  if (activeIndex.value >= 0 && activeIndex.value < sections.value.length - 1) {
    selectSection(sections.value[activeIndex.value + 1])
  }
}

function selectSection(section: FlatSection) {
  activeSectionId.value = section.id
  activeChapterId.value = section.chapterId
  if (!expandedChapters.value.includes(section.chapterId)) {
    expandedChapters.value = [...expandedChapters.value, section.chapterId]
  }
}

/* ==================== 课程讨论 ==================== */

const activeTab = ref('content')

const boards = ref<BoardVO[]>([])
const boardTotal = ref(0)
const boardLoading = ref(false)
const boardQuery = reactive({ pageNo: 1, pageSize: 5 })

const topicForm = reactive({ title: '', content: '' })
const topicSubmitting = ref(false)
const showTopicForm = ref(false)

/** 展开中的话题详情（含回复） */
const expandedBoard = ref<BoardVO | null>(null)
const replyDraft = ref('')
const replySubmitting = ref(false)

async function fetchBoards() {
  boardLoading.value = true
  try {
    const res = await pageBoards({ courseId: courseId.value, ...boardQuery })
    boards.value = res.list ?? []
    boardTotal.value = res.total ?? 0
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    boardLoading.value = false
  }
}

async function toggleBoard(board: BoardVO) {
  if (expandedBoard.value && String(expandedBoard.value.id) === String(board.id)) {
    expandedBoard.value = null
    return
  }
  try {
    expandedBoard.value = await boardDetail(board.id)
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

async function submitTopic() {
  if (!topicForm.title.trim()) {
    ElMessage.warning('请填写话题标题')
    return
  }
  topicSubmitting.value = true
  try {
    await createBoard({
      courseId: courseId.value,
      title: topicForm.title.trim(),
      content: topicForm.content.trim() || undefined,
    })
    ElMessage.success('话题已发布，积分 +5 已到账')
    topicForm.title = ''
    topicForm.content = ''
    showTopicForm.value = false
    boardQuery.pageNo = 1
    await fetchBoards()
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    topicSubmitting.value = false
  }
}

async function submitReply() {
  const board = expandedBoard.value
  if (!board) return
  if (!replyDraft.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replySubmitting.value = true
  try {
    await createReply({ boardId: board.id, content: replyDraft.value.trim() })
    ElMessage.success('回复成功，积分 +2 已到账')
    replyDraft.value = ''
    expandedBoard.value = await boardDetail(board.id)
    await fetchBoards()
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    replySubmitting.value = false
  }
}

/** 删除自己的话题/回复：作者本人或管理员 */
function canManage(ownerId: number | string) {
  return userStore.isAdmin || String(ownerId) === String(userStore.userId)
}

async function removeTopic(board: BoardVO) {
  if (!(await confirmAction('确定删除该话题及其全部回复吗？', '删除话题', { type: 'warning' }))) return
  try {
    await deleteBoard(board.id)
    ElMessage.success('话题已删除')
    if (expandedBoard.value && String(expandedBoard.value.id) === String(board.id)) {
      expandedBoard.value = null
    }
    await fetchBoards()
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

async function removeReply(replyId: number | string) {
  if (!(await confirmAction('确定删除这条回复吗？', '删除回复', { type: 'warning' }))) return
  try {
    await deleteReply(replyId)
    ElMessage.success('回复已删除')
    if (expandedBoard.value) expandedBoard.value = await boardDetail(expandedBoard.value.id)
    await fetchBoards()
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

/* ==================== 课程测验 ==================== */

const questions = ref<QuestionVO[]>([])
const questionTotal = ref(0)
const questionLoading = ref(false)

async function fetchQuestions() {
  questionLoading.value = true
  try {
    const res = await practicePage({ courseId: courseId.value, pageNo: 1, pageSize: 10 })
    questions.value = res.list ?? []
    questionTotal.value = res.total ?? 0
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    questionLoading.value = false
  }
}

const QUESTION_TYPE_TEXT: Record<number, string> = { 1: '单选题', 2: '多选题', 3: '判断题' }

/* ==================== 初始化 ==================== */

async function fetchCourse() {
  loading.value = true
  try {
    course.value = await getCourse(courseId.value)
    const first = sections.value[0]
    if (first) {
      // 默认定位到第一个未完成的小节，避免每次进来都从头翻
      const pending = sections.value.find((s) => !sectionState(s.id).finished)
      selectSection(pending ?? first)
    }
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchCourse()
  await fetchProgress()
  const pending = sections.value.find((s) => !sectionState(s.id).finished)
  if (pending) selectSection(pending)
  await fetchSectionNotes()
  await Promise.allSettled([fetchBoards(), fetchQuestions()])
})
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <!-- 课程头部 -->
    <div class="zx-card p-5">
      <div class="flex flex-wrap items-center gap-3">
        <el-button :icon="ArrowLeft" circle text @click="router.push('/learning')" />
        <h1 class="min-w-0 truncate text-xl font-bold">
          {{ course?.name ?? `课程 #${courseId}` }}
        </h1>
        <el-tag v-if="course?.free === 1" type="success" effect="plain" round size="small">免费课</el-tag>
        <div class="ml-auto flex shrink-0 items-center gap-2">
          <el-tag effect="plain" round>{{ finishedCount }} / {{ sections.length }} 节已完成</el-tag>
          <el-button round @click="router.push(`/courses/${courseId}`)">课程详情</el-button>
        </div>
      </div>
      <div class="mt-4 flex items-center gap-3">
        <el-progress
          :percentage="coursePercent"
          :stroke-width="10"
          :color="coursePercent === 100 ? '#22c55e' : '#4F46E5'"
          class="min-w-0 flex-1"
        />
        <span class="zx-text-secondary shrink-0 text-xs">{{ coursePercent }}%</span>
      </div>
    </div>

    <div class="zx-course-layout mt-6">
      <!-- 左：章节目录 -->
      <aside class="zx-card zx-catalog p-4">
        <div class="flex items-center gap-2">
          <el-icon class="text-primary"><Document /></el-icon>
          <h2 class="font-bold">课程目录</h2>
        </div>

        <EmptyState v-if="!sections.length" description="章节目录筹备中" size="small" />

        <div v-else class="mt-3 space-y-3">
          <div v-for="ch in chapters" :key="ch.id" class="zx-chapter">
            <!-- 章标题（可折叠） -->
            <button
              class="zx-chapter-head"
              :class="{ 'is-active': activeChapterId === ch.id }"
              @click="
                expandedChapters.includes(ch.id)
                  ? (expandedChapters = expandedChapters.filter((id) => id !== ch.id))
                  : (expandedChapters = [...expandedChapters, ch.id])
              "
            >
              <el-icon class="zx-chapter-arrow" :class="{ 'is-open': expandedChapters.includes(ch.id) }">
                <ArrowRight />
              </el-icon>
              <span class="min-w-0 flex-1 truncate text-left">{{ ch.name }}</span>
              <span class="zx-text-secondary shrink-0 text-xs">{{ ch.sections?.length ?? 0 }} 节</span>
            </button>

            <!-- 小节列表 -->
            <ul v-show="expandedChapters.includes(ch.id)" class="mt-1 space-y-1">
              <li v-for="s in ch.sections ?? []" :key="s.id">
                <button
                  class="zx-section-btn"
                  :class="{ 'is-active': activeSectionId === s.id }"
                  @click="selectSection({ ...s, chapterId: ch.id, chapterName: ch.name })"
                >
                  <span class="zx-section-idx">
                    <el-icon v-if="sectionState(s.id).finished" class="text-emerald-500">
                      <CircleCheckFilled />
                    </el-icon>
                    <template v-else>
                      {{ (sections.findIndex((x) => x.id === s.id) ?? 0) + 1 }}
                    </template>
                  </span>
                  <span class="min-w-0 flex-1 truncate text-left">{{ s.name }}</span>
                  <el-tag v-if="s.trailer === 1" size="small" effect="plain" round>试看</el-tag>
                </button>
              </li>
            </ul>
          </div>
        </div>
      </aside>

      <!-- 右：内容区 -->
      <section class="min-w-0">
        <el-tabs v-model="activeTab" class="zx-tabs">
          <!-- 章节内容 -->
          <el-tab-pane label="章节内容" name="content">
            <div v-if="activeSection" class="space-y-5">
              <!-- 小节头 + 视频 -->
              <div class="zx-card overflow-hidden">
                <div class="flex flex-wrap items-center gap-2 border-b px-5 py-3" style="border-color: var(--zx-border)">
                  <span class="zx-text-secondary shrink-0 text-xs">{{ activeSection.chapterName }}</span>
                  <h2 class="min-w-0 truncate font-bold">{{ activeSection.name }}</h2>
                  <el-tag v-if="activeSection.trailer === 1" type="warning" size="small" effect="plain" round>
                    可试看
                  </el-tag>
                  <el-tag v-if="sectionState(activeSection.id).finished" type="success" size="small" effect="plain" round>
                    已完成
                  </el-tag>
                  <span class="zx-text-secondary ml-auto flex shrink-0 items-center gap-1 text-xs">
                    <el-icon><Clock /></el-icon>
                    {{ activeSection.duration ? formatMinutes(Math.round(activeSection.duration / 60)) : '时长待补充' }}
                    <span>· 第 {{ activeOrdinal }} / {{ sections.length }} 节</span>
                  </span>
                </div>

                <!-- 视频区：有视频播视频，没有则给明确的"可用动作"而不是空白块 -->
                <div class="zx-media-box">
                  <video
                    v-if="activeSection.mediaUrl || activeSection.videoUrl"
                    :src="activeSection.mediaUrl || activeSection.videoUrl"
                    controls
                    class="h-full w-full object-contain"
                  />
                  <div v-else class="zx-media-empty">
                    <el-icon :size="46" class="text-primary"><VideoPlay /></el-icon>
                    <p class="mt-3 font-medium">本节以图文讲义为主，暂无视频</p>
                    <p class="zx-text-secondary mt-1 text-xs">
                      请阅读下方「讲义」与「本节要点」，完成后在页面底部标记为已学完
                    </p>
                  </div>
                </div>
              </div>

              <!-- 本节要点 + 讲义 + 学习资料 -->
              <div class="zx-card p-5">
                <h3 class="flex items-center gap-2 font-semibold">
                  <el-icon class="text-primary"><Notebook /></el-icon>本节要点
                </h3>
                <div v-if="activeKeyPoints.length" class="mt-3 flex flex-wrap gap-2">
                  <el-tag v-for="(p, i) in activeKeyPoints" :key="i" effect="plain" round>{{ p }}</el-tag>
                </div>
                <p v-else class="zx-text-secondary mt-2 text-sm">本节暂未提炼要点，可直接阅读下方讲义。</p>

                <h3 class="mt-6 flex items-center gap-2 font-semibold">
                  <el-icon class="text-primary"><Document /></el-icon>讲义
                </h3>
                <div
                  v-if="activeHandoutHtml"
                  class="zx-markdown zx-handout mt-3 text-sm"
                  v-html="activeHandoutHtml"
                />
                <p v-else class="zx-text-secondary mt-2 text-sm leading-7">
                  本节讲义仍在整理中。你可以先观看视频、参与课程讨论，或直接标记为已学完以累积学习进度与积分。
                </p>

                <div v-if="activeSection.attachmentUrl" class="zx-attach mt-5">
                  <el-icon><Download /></el-icon>
                  <span class="min-w-0 flex-1 truncate">
                    {{ activeSection.attachmentName || '本节学习资料' }}
                  </span>
                  <el-link :href="activeSection.attachmentUrl" target="_blank" type="primary" :underline="false">
                    下载
                  </el-link>
                </div>
              </div>

              <!-- 我的学习记录 + 小节导航 -->
              <div class="zx-card p-5">
                <h3 class="font-semibold">我的学习记录</h3>
                <div class="zx-record-grid mt-3">
                  <div class="zx-record-item">
                    <span class="zx-record-item__label">本节状态</span>
                    <span class="zx-record-item__value" :class="sectionState(activeSection.id).finished ? 'text-emerald-500' : 'text-primary'">
                      {{ sectionState(activeSection.id).finished ? '已学完' : '未完成' }}
                    </span>
                  </div>
                  <div class="zx-record-item">
                    <span class="zx-record-item__label">观看时长</span>
                    <span class="zx-record-item__value">
                      {{ activeRecord ? formatMinutes(Math.floor((activeRecord.moment ?? 0) / 60)) : '暂无记录' }}
                    </span>
                  </div>
                  <div class="zx-record-item">
                    <span class="zx-record-item__label">最近学习</span>
                    <span class="zx-record-item__value zx-record-item__value--sm">
                      {{ activeRecord?.updateTime ?? '尚未开始本节' }}
                    </span>
                  </div>
                  <div class="zx-record-item">
                    <span class="zx-record-item__label">课程进度</span>
                    <span class="zx-record-item__value">
                      {{ finishedCount }} / {{ sections.length }} 节（{{ coursePercent }}%）
                    </span>
                  </div>
                </div>

                <div class="mt-5 flex flex-wrap items-center gap-3">
                  <el-button round :disabled="activeIndex <= 0" @click="goPrev">上一节</el-button>
                  <el-button
                    type="primary"
                    round
                    :icon="CircleCheckFilled"
                    :loading="marking"
                    :disabled="sectionState(activeSection.id).finished"
                    @click="markFinished"
                  >
                    {{ sectionState(activeSection.id).finished ? '本节已完成' : '标记为已学完' }}
                  </el-button>
                  <el-button
                    round
                    :disabled="activeIndex < 0 || activeIndex >= sections.length - 1"
                    @click="goNext"
                  >
                    下一节
                  </el-button>
                  <el-button round class="ml-auto" @click="activeTab = 'quiz'">
                    去做本节测验
                    <el-icon class="ml-1"><DataAnalysis /></el-icon>
                  </el-button>
                </div>
              </div>

              <!-- 本节笔记 -->
              <div class="zx-card p-5">
                <h3 class="flex items-center gap-2 font-semibold">
                  <el-icon class="text-primary"><EditPen /></el-icon>本节笔记
                  <span class="zx-text-secondary text-xs font-normal">（仅自己可见，随小节归档）</span>
                </h3>

                <div v-if="userStore.isStudent" class="mt-3 flex flex-wrap items-start gap-2">
                  <el-input
                    v-model="noteDraft"
                    type="textarea"
                    :rows="2"
                    maxlength="500"
                    show-word-limit
                    placeholder="记录本节的重点、疑问或代码片段…"
                    class="min-w-0 flex-1"
                  />
                  <el-button type="primary" round :loading="noteSubmitting" @click="submitSectionNote">
                    保存笔记
                  </el-button>
                </div>

                <div v-loading="noteLoading" class="mt-3 space-y-2">
                  <EmptyState
                    v-if="!sectionNotes.length && !noteLoading"
                    description="本节还没有笔记，随手记一条试试"
                    size="small"
                  />
                  <div v-for="n in sectionNotes" :key="n.id" class="zx-note-item">
                    <div class="zx-text-secondary flex items-center gap-2 text-xs">
                      <span>{{ n.createTime }}</span>
                      <el-button
                        class="ml-auto"
                        :icon="Delete"
                        text
                        size="small"
                        type="danger"
                        @click="removeSectionNote(n.id)"
                      />
                    </div>
                    <!-- 笔记内容为用户输入：必须经 renderMarkdown(DOMPurify) 净化后再 v-html，
                         否则存储型 XSS 会随笔记渲染执行（与讲义、AI 消息保持同一净化口径） -->
                    <div class="zx-markdown zx-text-secondary mt-1 text-sm" v-html="renderMarkdown(n.content)" />
                  </div>
                </div>
              </div>
            </div>

            <EmptyState v-else description="该课程暂无课程内容" />
          </el-tab-pane>

          <!-- 课程讨论 -->
          <el-tab-pane name="discuss">
            <template #label>
              <span class="flex items-center gap-1">
                <el-icon><ChatDotRound /></el-icon>课程讨论
              </span>
            </template>

            <div class="zx-card p-5">
              <div class="flex flex-wrap items-center gap-3">
                <div>
                  <h2 class="font-bold">课程讨论区</h2>
                  <p class="zx-text-secondary mt-1 text-xs">发布话题 +5 积分，回复讨论 +2 积分</p>
                </div>
                <el-button
                  v-if="userStore.isStudent"
                  class="ml-auto"
                  type="primary"
                  round
                  :icon="EditPen"
                  @click="showTopicForm = !showTopicForm"
                >
                  {{ showTopicForm ? '收起' : '发布话题' }}
                </el-button>
              </div>

              <!-- 发帖表单 -->
              <div v-if="showTopicForm && userStore.isStudent" class="zx-topic-form mt-4">
                <el-input v-model="topicForm.title" placeholder="话题标题（必填）" maxlength="100" show-word-limit />
                <el-input
                  v-model="topicForm.content"
                  type="textarea"
                  :rows="3"
                  maxlength="500"
                  show-word-limit
                  placeholder="补充你的问题或想法，便于同学与老师回复…"
                  class="mt-3"
                />
                <div class="mt-3 flex justify-end gap-2">
                  <el-button round @click="showTopicForm = false">取消</el-button>
                  <el-button type="primary" round :loading="topicSubmitting" @click="submitTopic">
                    发布
                  </el-button>
                </div>
              </div>

              <!-- 话题列表 -->
              <div v-loading="boardLoading" class="mt-5">
                <EmptyState v-if="!boards.length && !boardLoading" description="还没有讨论，来发布第一个话题吧" size="small" />

                <ul v-else class="space-y-3">
                  <li v-for="b in boards" :key="String(b.id)" class="zx-topic">
                    <button class="zx-topic-head" @click="toggleBoard(b)">
                      <el-tag v-if="b.top === 1" size="small" type="danger" effect="plain" round>置顶</el-tag>
                      <span class="min-w-0 flex-1 truncate text-left font-semibold">{{ b.title }}</span>
                      <span class="zx-text-secondary shrink-0 text-xs">{{ b.replyCount }} 回复</span>
                    </button>

                    <!-- 展开：正文 + 回复 -->
                    <div
                      v-if="expandedBoard && String(expandedBoard.id) === String(b.id)"
                      class="zx-topic-body"
                    >
                      <div class="flex items-center gap-2 text-xs">
                        <span class="font-medium text-primary">{{ b.userName }}</span>
                        <span class="zx-text-secondary">{{ b.createTime }}</span>
                        <el-button
                          v-if="canManage(b.userId)"
                          class="ml-auto"
                          size="small"
                          type="danger"
                          text
                          round
                          @click="removeTopic(b)"
                        >
                          删除
                        </el-button>
                      </div>
                      <p class="mt-2 whitespace-pre-wrap text-sm leading-7">{{ b.content }}</p>

                      <div class="mt-4 space-y-3">
                        <div
                          v-for="rp in expandedBoard.replies ?? []"
                          :key="String(rp.id)"
                          class="zx-reply"
                        >
                          <div class="flex items-center gap-2 text-xs">
                            <span class="font-medium text-primary">{{ rp.userName }}</span>
                            <span class="zx-text-secondary">{{ rp.createTime }}</span>
                            <el-button
                              v-if="canManage(rp.userId)"
                              class="ml-auto"
                              size="small"
                              type="danger"
                              text
                              round
                              @click="removeReply(rp.id)"
                            >
                              删除
                            </el-button>
                          </div>
                          <p class="mt-1 whitespace-pre-wrap text-sm leading-6">{{ rp.content }}</p>
                        </div>
                        <p v-if="!expandedBoard.replies?.length" class="zx-text-secondary text-xs">
                          还没有回复，来说两句吧
                        </p>
                      </div>

                      <div v-if="userStore.isStudent" class="mt-4 flex flex-wrap items-start gap-2">
                        <el-input
                          v-model="replyDraft"
                          type="textarea"
                          :rows="2"
                          maxlength="500"
                          show-word-limit
                          placeholder="写下你的回复…"
                          class="min-w-0 flex-1"
                        />
                        <el-button type="primary" round :loading="replySubmitting" @click="submitReply">
                          回复
                        </el-button>
                      </div>
                    </div>
                  </li>
                </ul>

                <div v-if="boardTotal > boardQuery.pageSize" class="mt-4 flex justify-center">
                  <el-pagination
                    v-model:current-page="boardQuery.pageNo"
                    :page-size="boardQuery.pageSize"
                    :total="boardTotal"
                    layout="prev, pager, next"
                    background
                    small
                    @current-change="fetchBoards"
                  />
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- 课程测验 -->
          <el-tab-pane name="quiz">
            <template #label>
              <span class="flex items-center gap-1">
                <el-icon><DataAnalysis /></el-icon>课程测验
              </span>
            </template>

            <div class="zx-card p-5">
              <div class="flex flex-wrap items-center gap-3">
                <div>
                  <h2 class="font-bold">本课程测验题</h2>
                  <p class="zx-text-secondary mt-1 text-xs">答对一题 +5 积分，判分由服务端完成</p>
                </div>
                <el-button v-if="questionTotal" class="ml-auto" type="primary" round @click="router.push('/exam')">
                  进入答题
                  <el-icon class="ml-1"><ArrowRight /></el-icon>
                </el-button>
              </div>

              <div v-loading="questionLoading" class="mt-4">
                <EmptyState
                  v-if="!questions.length && !questionLoading"
                  description="本课程暂无已发布的测验题"
                  size="small"
                />
                <ul v-else class="space-y-3">
                  <li v-for="(q, i) in questions" :key="q.id" class="zx-question">
                    <div class="flex items-center gap-2">
                      <span class="zx-question-idx">{{ i + 1 }}</span>
                      <el-tag size="small" effect="plain" round>{{ QUESTION_TYPE_TEXT[q.type] ?? '题目' }}</el-tag>
                      <el-tag v-if="q.score" size="small" type="warning" effect="plain" round>
                        {{ q.score }} 分
                      </el-tag>
                    </div>
                    <p class="mt-2 line-clamp-2 text-sm leading-6">{{ q.name }}</p>
                  </li>
                </ul>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 两栏自适应：移动端单列，桌面端固定目录栏宽 + 内容区自适应剩余宽度 */
.zx-course-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}
@media (min-width: 1024px) {
  .zx-course-layout {
    /* 关键：内容列使用 minmax(0,1fr)，避免长标题/表格把栅格撑破导致横向溢出 */
    grid-template-columns: 300px minmax(0, 1fr);
  }
}
.zx-catalog {
  min-width: 0;
}
@media (min-width: 1024px) {
  .zx-catalog {
    position: sticky;
    top: 84px;
    max-height: calc(100vh - 108px);
    overflow-y: auto;
  }
}
.zx-chapter-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: var(--zx-primary-bg);
  color: var(--zx-text);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.zx-chapter-head.is-active {
  color: var(--zx-primary);
}
.zx-chapter-arrow {
  flex-shrink: 0;
  transition: transform 0.2s;
}
.zx-chapter-arrow.is-open {
  transform: rotate(90deg);
}
.zx-section-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--zx-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.zx-section-btn:hover {
  background: var(--zx-primary-bg);
  color: var(--zx-primary);
}
.zx-section-btn.is-active {
  background: var(--zx-primary);
  color: #fff;
  font-weight: 600;
}
.zx-section-idx {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  font-size: 12px;
}
.zx-media-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-width: 0;
  aspect-ratio: 16 / 9;
  max-height: 460px;
  overflow: hidden;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 100%);
}
.zx-media-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  text-align: center;
  color: #e0e7ff;
}
.zx-topic-form {
  padding: 14px;
  border-radius: 12px;
  background: var(--zx-primary-bg);
}
.zx-topic {
  min-width: 0;
  border: 1px solid var(--zx-border);
  border-radius: 12px;
  overflow: hidden;
}
.zx-topic-head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  padding: 12px 14px;
  border: none;
  background: transparent;
  color: var(--zx-text);
  font-size: 14px;
  cursor: pointer;
}
.zx-topic-head:hover {
  background: var(--zx-primary-bg);
}
.zx-topic-body {
  min-width: 0;
  padding: 14px;
  border-top: 1px solid var(--zx-border);
}
.zx-reply {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--zx-bg);
}
.zx-question {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--zx-border);
  border-radius: 12px;
}
.zx-question-idx {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--zx-primary-bg);
  color: var(--zx-primary);
  font-size: 12px;
  font-weight: 700;
}

/* ---------- 讲义 / 资料 / 学习记录 / 本节笔记 ---------- */
/* 讲义正文：限制行宽与行高，长内容可读性优先；标题与列表间距收紧避免松散留白 */
.zx-handout {
  max-width: 100%;
  min-width: 0;
  line-height: 1.85;
  word-break: break-word;
}
.zx-handout :deep(h2) {
  margin: 18px 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--zx-text);
}
.zx-handout :deep(h2:first-child) {
  margin-top: 0;
}
.zx-handout :deep(h3) {
  margin: 14px 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--zx-text);
}
.zx-handout :deep(p) {
  margin: 8px 0;
}
.zx-handout :deep(ul),
.zx-handout :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}
.zx-handout :deep(ul) {
  list-style: disc;
}
.zx-handout :deep(ol) {
  list-style: decimal;
}
.zx-handout :deep(li) {
  margin: 4px 0;
}
.zx-handout :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--zx-primary-bg);
  color: var(--zx-primary);
  font-size: 12px;
}
.zx-handout :deep(pre) {
  margin: 10px 0;
  overflow-x: auto;
}
.zx-handout :deep(.code-block) {
  margin: 10px 0;
  border-radius: 10px;
  overflow: hidden;
}
.zx-handout :deep(blockquote) {
  margin: 10px 0;
  padding: 6px 12px;
  border-left: 3px solid var(--zx-primary);
  background: var(--zx-primary-bg);
  border-radius: 0 8px 8px 0;
}

/* 学习资料下载条 */
.zx-attach {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 10px 14px;
  border: 1px dashed var(--zx-border);
  border-radius: 10px;
  background: var(--zx-primary-bg);
  font-size: 13px;
  color: var(--zx-text);
}

/* 学习记录：自适应四列，窄屏两列，不留空白格 */
.zx-record-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (min-width: 768px) {
  .zx-record-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
.zx-record-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 12px;
  border-radius: 10px;
  background: var(--zx-bg);
}
.zx-record-item__label {
  font-size: 12px;
  color: var(--zx-text-secondary);
}
.zx-record-item__value {
  font-size: 15px;
  font-weight: 700;
  color: var(--zx-text);
}
.zx-record-item__value--sm {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  word-break: break-all;
}

/* 本节笔记条目 */
.zx-note-item {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--zx-bg);
}
</style>
