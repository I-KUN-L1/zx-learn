<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, RefreshRight } from '@element-plus/icons-vue'
import { myAnswerStats, practiceList, submitAnswers } from '@/api/exam'
import EmptyState from '@/components/common/EmptyState.vue'
import type { QuestionVO, SubmitResultVO } from '@/types/api'

/**
 * 在线答题（学员端）。
 *
 * 数据来源：`GET /questions/list` —— 后端按角色过滤，学员只拿到教师已发布的题目。
 * 判分规则：**服务端判分**（只提交作答，正误与得分由后端比对题库答案计算），
 * 交卷后回传逐题结果，前端据此展示对错与解析。
 */

const router = useRouter()

const questions = ref<QuestionVO[]>([])
const loading = ref(true)
const loadError = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const activeIndex = ref(0)

/** questionId → 选中的选项字母（多选为字母连写、升序） */
const answers = reactive<Record<number, string>>({})

const TYPE_TEXT: Record<number, string> = { 1: '单选题', 2: '多选题', 3: '判断题' }

const current = computed(() => questions.value[activeIndex.value])

/** 选项列表：题库未配置时给默认 ABCD，保证永远有可作答项 */
const currentOptions = computed(() => {
  const opts = current.value?.options ?? []
  if (Array.isArray(opts) && opts.length) return opts
  return ['A', 'B', 'C', 'D']
})

/** 选项序号 → 字母（第 i 个选项对应 A/B/C/...） */
const letterOf = (i: number) => String.fromCharCode(65 + i)

/** 展示文案：选项内容本身就是字母时只显示字母，避免 "A. A" 重复 */
function optionLabel(text: string, i: number) {
  const letter = letterOf(i)
  return text && text !== letter ? `${letter}. ${text}` : letter
}

function cardState(q: QuestionVO): 'answered' | 'current' | 'none' {
  if (activeIndex.value === questions.value.indexOf(q)) return 'current'
  return answers[q.id] ? 'answered' : 'none'
}

function selectOption(q: QuestionVO, letter: string) {
  if (submitted.value) return
  if (q.type === 2) {
    const cur = (answers[q.id] ?? '').split('').filter(Boolean)
    const idx = cur.indexOf(letter)
    if (idx >= 0) cur.splice(idx, 1)
    else cur.push(letter)
    answers[q.id] = cur.sort().join('')
  } else {
    answers[q.id] = letter
  }
}

const isSelected = (q: QuestionVO, letter: string) => (answers[q.id] ?? '').includes(letter)

const answeredCount = computed(() => questions.value.filter((q) => answers[q.id]).length)

/* ---------- 交卷与成绩 ---------- */
const results = ref<SubmitResultVO[]>([])
const score = ref(0)
const correctCount = ref(0)
const totalScore = ref(0)

/** questionId → 判分结果，便于当前题即时展示对错与解析 */
const resultMap = computed(() => {
  const m: Record<number, SubmitResultVO> = {}
  results.value.forEach((r) => {
    m[r.questionId] = r
  })
  return m
})

const currentResult = computed(() => (current.value ? resultMap.value[current.value.id] : undefined))

/** 我的历史答题统计（错题本入口的辅助信息） */
const stats = ref<{ count: number; correct: number; accuracy: number } | null>(null)

async function fetchStats() {
  try {
    stats.value = await myAnswerStats()
  } catch {
    stats.value = null
  }
}

async function onSubmit() {
  if (answeredCount.value < questions.value.length) {
    await ElMessage.warning(`还有 ${questions.value.length - answeredCount.value} 题未作答`)
    return
  }
  submitting.value = true
  try {
    const payload = questions.value.map((q) => ({ questionId: q.id, userAnswer: answers[q.id] ?? '' }))
    // 服务端判分：后端返回逐题对错 / 正确答案 / 解析
    const graded = (await submitAnswers(payload)) ?? []
    results.value = graded
    correctCount.value = graded.filter((r) => r.correct).length
    totalScore.value = questions.value.reduce((sum, q) => sum + (q.score ?? 5), 0)
    score.value = graded.reduce((sum, r) => sum + (r.score ?? 0), 0)
    submitted.value = true
    ElMessage.success(`交卷成功！得分 ${score.value} / ${totalScore.value}`)
    await fetchStats()
  } catch {
    /* 错误由拦截器统一提示 */
  } finally {
    submitting.value = false
  }
}

async function onReload() {
  loading.value = true
  loadError.value = false
  submitted.value = false
  results.value = []
  activeIndex.value = 0
  Object.keys(answers).forEach((k) => delete answers[Number(k)])
  try {
    questions.value = (await practiceList()) ?? []
    if (!questions.value.length) {
      ElMessage.info('题库暂无已发布题目，请等待教师发布')
    }
  } catch {
    loadError.value = true
    questions.value = []
  } finally {
    loading.value = false
  }
  await fetchStats()
}

onMounted(onReload)
</script>

<template>
  <div class="zx-page">
    <!-- 页头 -->
    <div class="mb-5 flex flex-wrap items-center gap-3">
      <h1 class="text-2xl font-bold">在线答题</h1>
      <el-tag type="primary" effect="plain" round>教师发布 · 学员练习 · 服务端判分</el-tag>
      <div class="ml-auto flex items-center gap-2">
        <el-button :icon="Collection" round @click="router.push('/exam/wrong-book')">
          我的错题本
          <span v-if="stats && stats.count - stats.correct > 0" class="ml-1">
            ({{ stats.count - stats.correct }})
          </span>
        </el-button>
        <el-button :icon="RefreshRight" circle @click="onReload" />
      </div>
    </div>

    <!-- 加载态：明确骨架，避免"空白框" -->
    <div v-if="loading" class="zx-card p-6">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 空态 -->
    <div v-else-if="!questions.length" class="zx-card p-10">
      <EmptyState
        :description="loadError ? '题库加载失败，请稍后重试' : '题库暂无已发布题目，等待教师发布后即可练习'"
      />
      <div class="mt-4 flex justify-center">
        <el-button round @click="onReload">重新加载</el-button>
      </div>
    </div>

    <div v-else class="grid grid-cols-1 gap-6 lg:grid-cols-4">
      <!-- 题目区 -->
      <div class="lg:col-span-3">
        <div v-if="current" class="zx-card p-6">
          <div class="flex flex-wrap items-center gap-3">
            <el-tag type="primary" effect="plain" round>{{ TYPE_TEXT[current.type] ?? '单选题' }}</el-tag>
            <span class="zx-text-secondary text-sm">
              第 {{ activeIndex + 1 }} / {{ questions.length }} 题 · {{ current.score ?? 5 }} 分
            </span>
            <el-tag v-if="current.courseName" size="small" effect="plain" round>{{ current.courseName }}</el-tag>
            <el-tag
              v-if="submitted && currentResult"
              :type="currentResult.correct ? 'success' : 'danger'"
              size="small"
              round
            >
              {{ currentResult.correct ? `回答正确 +${currentResult.score ?? 0} 分` : '回答错误' }}
            </el-tag>
          </div>

          <h2 class="mt-4 text-lg font-semibold leading-8">{{ current.name }}</h2>
          <p v-if="current.content" class="zx-text-secondary mt-2 text-sm leading-6">{{ current.content }}</p>

          <div class="mt-5 space-y-3">
            <div
              v-for="(opt, i) in currentOptions"
              :key="i"
              class="zx-option flex cursor-pointer items-center gap-3 rounded-xl border-2 p-4 transition-colors"
              :class="{
                'zx-option--selected': isSelected(current, letterOf(i)),
                'zx-option--correct': submitted && currentResult?.correctAnswer?.includes(letterOf(i)),
                'zx-option--wrong':
                  submitted && isSelected(current, letterOf(i)) && !currentResult?.correctAnswer?.includes(letterOf(i)),
              }"
              @click="selectOption(current, letterOf(i))"
            >
              <span
                class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-sm font-bold"
                style="border-color: currentColor"
              >
                {{ letterOf(i) }}
              </span>
              <span class="flex-1 text-sm">{{ optionLabel(opt, i) }}</span>
            </div>
          </div>

          <!-- 解析 -->
          <div v-if="submitted && currentResult" class="zx-analysis mt-5 rounded-xl p-4 text-sm leading-6">
            <div class="font-bold">
              我的作答：{{ currentResult.userAnswer || '未作答' }} ｜ 正确答案：{{ currentResult.correctAnswer }}
            </div>
            <div v-if="currentResult.analysis" class="mt-2">
              <span class="font-bold">解析：</span>{{ currentResult.analysis }}
            </div>
          </div>

          <div class="mt-6 flex justify-between">
            <el-button round :disabled="activeIndex === 0" @click="activeIndex--">上一题</el-button>
            <el-button round :disabled="activeIndex >= questions.length - 1" @click="activeIndex++">下一题</el-button>
          </div>
        </div>
      </div>

      <!-- 答题卡 -->
      <div class="space-y-5">
        <div class="zx-card p-5">
          <h3 class="font-bold">答题卡</h3>
          <div class="mt-4 grid grid-cols-5 gap-2">
            <div
              v-for="(q, i) in questions"
              :key="q.id"
              class="zx-card-cell"
              :class="[
                cardState(q),
                submitted && resultMap[q.id] && !resultMap[q.id].correct ? 'zx-card-cell--wrong' : '',
              ]"
              @click="activeIndex = i"
            >
              {{ i + 1 }}
            </div>
          </div>
          <div class="zx-text-secondary mt-4 text-xs">已答 {{ answeredCount }} / {{ questions.length }} 题</div>
        </div>

        <!-- 成绩 -->
        <div v-if="submitted" class="zx-card p-5 text-center">
          <div class="zx-text-secondary text-sm">本次得分</div>
          <div class="mt-1 text-4xl font-extrabold text-primary">{{ score }}</div>
          <div class="zx-text-secondary mt-2 text-sm">
            正确 {{ correctCount }} / {{ questions.length }} 题（满分 {{ totalScore }}）
          </div>
          <el-button class="mt-3" type="danger" plain round size="small" @click="router.push('/exam/wrong-book')">
            查看错题本
          </el-button>
        </div>

        <el-button
          type="primary"
          size="large"
          round
          class="w-full"
          :loading="submitting"
          :disabled="submitted"
          @click="onSubmit"
        >
          {{ submitted ? '已交卷' : '交卷' }}
        </el-button>
        <el-button v-if="submitted" class="w-full" round @click="onReload">再练一遍</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.zx-option {
  border-color: var(--zx-border);
  background: var(--zx-bg-card);
}
.zx-option:hover {
  border-color: var(--zx-primary-light, #a5b4fc);
}
.zx-option--selected {
  border-color: var(--zx-primary);
  background: var(--zx-primary-bg);
  color: var(--zx-primary);
}
.zx-option--correct {
  border-color: #22c55e !important;
  background: rgba(34, 197, 94, 0.08) !important;
  color: #16a34a;
}
.zx-option--wrong {
  border-color: #ef4444 !important;
  background: rgba(239, 68, 68, 0.08) !important;
  color: #dc2626;
}
.zx-analysis {
  background: var(--zx-primary-bg);
  color: var(--zx-text);
}
.zx-card-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  border-radius: 8px;
  border: 1px solid var(--zx-border);
  font-size: 13px;
  cursor: pointer;
  color: var(--zx-text-secondary);
  transition: all 0.15s;
}
.zx-card-cell.current {
  border-color: var(--zx-primary);
  color: var(--zx-primary);
  font-weight: 700;
}
.zx-card-cell.answered {
  background: var(--zx-primary-bg);
  border-color: transparent;
  color: var(--zx-primary);
}
.zx-card-cell--wrong {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}
</style>
