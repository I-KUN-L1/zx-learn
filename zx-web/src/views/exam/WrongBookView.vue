<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, RefreshRight, VideoPlay } from '@element-plus/icons-vue'
import { myWrongBook, submitOneAnswer } from '@/api/exam'
import { formatDate } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { WrongQuestionVO } from '@/types/api'

/**
 * 错题本（学员端）。
 *
 * 数据来源：`GET /question-results/mine/wrong` —— 后端按题目去重，取最近一次错答，
 * 并回填题干 / 选项 / 正确答案 / 解析，形成"答错 → 复盘 → 重做"的闭环。
 */

const router = useRouter()

const list = ref<WrongQuestionVO[]>([])
const loading = ref(true)
const loadError = ref(false)
const filterType = ref<number | undefined>(undefined)

const filtered = computed(() =>
  filterType.value === undefined ? list.value : list.value.filter((q) => q.type === filterType.value),
)

const TYPE_TEXT: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断' }

/** 选项列表：题库未配置时给默认 ABCD */
function optionsOf(q: WrongQuestionVO): string[] {
  const opts = q.options ?? []
  return Array.isArray(opts) && opts.length ? opts : ['A', 'B', 'C', 'D']
}

const letterOf = (i: number) => String.fromCharCode(65 + i)

function optionLabel(text: string, i: number) {
  const letter = letterOf(i)
  return text && text !== letter ? `${letter}. ${text}` : letter
}

/** 我的作答字母集合 */
function myLetters(q: WrongQuestionVO): string[] {
  return (q.myAnswer ?? '').split('').filter(Boolean)
}

/** 重做状态：questionId → { selected, correct } */
const redo = reactive<Record<number, { selected: string; submitted: boolean; correct: boolean }>>({})

const redoState = (q: WrongQuestionVO) => {
  if (!redo[q.questionId]) {
    redo[q.questionId] = { selected: '', submitted: false, correct: false }
  }
  return redo[q.questionId]
}

function pick(q: WrongQuestionVO, letter: string) {
  const s = redoState(q)
  if (s.submitted) return
  if (q.type === 2) {
    const cur = s.selected.split('').filter(Boolean)
    const idx = cur.indexOf(letter)
    if (idx >= 0) cur.splice(idx, 1)
    else cur.push(letter)
    s.selected = cur.sort().join('')
  } else {
    s.selected = letter
  }
}

async function submitRedo(q: WrongQuestionVO) {
  const s = redoState(q)
  if (!s.selected) {
    ElMessage.warning('请先选择答案')
    return
  }
  try {
    const result = await submitOneAnswer({ questionId: q.questionId, userAnswer: s.selected })
    s.submitted = true
    s.correct = !!result?.correct
    if (s.correct) {
      ElMessage.success('答对了！该题已掌握')
    } else {
      ElMessage.error('还是不对，再想想解析')
    }
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

async function fetchList() {
  loading.value = true
  loadError.value = false
  try {
    list.value = (await myWrongBook()) ?? []
  } catch {
    loadError.value = true
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="zx-page">
    <div class="mb-5 flex flex-wrap items-center gap-3">
      <h1 class="text-2xl font-bold">我的错题本</h1>
      <el-tag type="danger" effect="plain" round>共 {{ list.length }} 道错题</el-tag>
      <div class="ml-auto flex items-center gap-2">
        <el-select v-model="filterType" placeholder="全部题型" clearable class="!w-32">
          <el-option v-for="(text, code) in TYPE_TEXT" :key="code" :label="text" :value="Number(code)" />
        </el-select>
        <el-button :icon="VideoPlay" round @click="router.push('/exam')">去练习</el-button>
        <el-button :icon="RefreshRight" circle @click="fetchList" />
      </div>
    </div>

    <div v-if="loading" class="zx-card p-6">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="!filtered.length" class="zx-card p-10">
      <EmptyState
        :description="loadError ? '错题本加载失败，请稍后重试' : '还没有错题，继续保持！'"
      />
    </div>

    <div v-else class="space-y-5">
      <div v-for="q in filtered" :key="q.questionId" class="zx-card p-5">
        <div class="flex flex-wrap items-center gap-2">
          <el-tag type="primary" effect="plain" size="small" round>{{ TYPE_TEXT[q.type] ?? '单选' }}</el-tag>
          <el-tag v-if="q.courseName" size="small" effect="plain" round>{{ q.courseName }}</el-tag>
          <el-tag type="danger" size="small" round>答错 {{ q.wrongCount }} 次</el-tag>
          <el-tag v-if="redo[q.questionId]?.correct" type="success" size="small" round>本次已答对</el-tag>
          <span class="zx-text-secondary ml-auto text-xs">
            {{ q.lastWrongTime ? `最近答错：${formatDate(q.lastWrongTime)}` : '' }}
          </span>
        </div>

        <h2 class="mt-3 font-semibold leading-7">{{ q.questionName }}</h2>

        <!-- 选项：正确项高亮；重做时可选 -->
        <div class="mt-4 space-y-2">
          <div
            v-for="(opt, i) in optionsOf(q)"
            :key="i"
            class="zx-wrong-option flex items-center gap-3 rounded-lg border p-3 text-sm transition-colors"
            :class="{
              'zx-wrong-option--correct': q.correctAnswer?.includes(letterOf(i)),
              'zx-wrong-option--mine': myLetters(q).includes(letterOf(i)) && !q.correctAnswer?.includes(letterOf(i)),
              'zx-wrong-option--picked': redo[q.questionId]?.selected.includes(letterOf(i)),
              'cursor-pointer': !redo[q.questionId]?.submitted,
            }"
            @click="pick(q, letterOf(i))"
          >
            <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-bold">
              {{ letterOf(i) }}
            </span>
            <span class="flex-1">{{ optionLabel(opt, i) }}</span>
            <el-icon v-if="q.correctAnswer?.includes(letterOf(i))" style="color: #16a34a"><Collection /></el-icon>
          </div>
        </div>

        <!-- 复盘信息 -->
        <div class="zx-review mt-4 rounded-lg p-3 text-sm leading-6">
          <div>
            <span class="zx-text-secondary">我的作答：</span>
            <span style="color: #dc2626">{{ q.myAnswer || '未作答' }}</span>
            <span class="zx-text-secondary ml-4">正确答案：</span>
            <span style="color: #16a34a">{{ q.correctAnswer || '-' }}</span>
          </div>
          <div v-if="q.analysis" class="mt-2"><span class="font-bold">解析：</span>{{ q.analysis }}</div>
        </div>

        <!-- 重做区 -->
        <div class="mt-4 flex items-center gap-3">
          <el-button
            v-if="!redo[q.questionId]?.submitted"
            size="small"
            type="primary"
            round
            :disabled="!redo[q.questionId]?.selected"
            @click="submitRedo(q)"
          >
            提交重做
          </el-button>
          <template v-else>
            <el-tag :type="redo[q.questionId].correct ? 'success' : 'danger'" size="small" round>
              {{ redo[q.questionId].correct ? '重做正确' : '重做仍错误' }}
            </el-tag>
            <el-button
              size="small"
              round
              @click="redo[q.questionId] = { selected: '', submitted: false, correct: false }"
            >
              再试一次
            </el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.zx-wrong-option {
  border-color: var(--zx-border);
  background: var(--zx-bg-card);
}
.zx-wrong-option--correct {
  border-color: #22c55e;
  background: rgba(34, 197, 94, 0.08);
  color: #16a34a;
}
.zx-wrong-option--mine {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
}
.zx-wrong-option--picked {
  border-color: var(--zx-primary);
  box-shadow: 0 0 0 2px var(--zx-primary-bg) inset;
}
.zx-review {
  background: var(--zx-primary-bg);
  color: var(--zx-text);
}
</style>
