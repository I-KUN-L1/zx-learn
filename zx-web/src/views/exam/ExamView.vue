<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { questionList, submitAnswers } from '@/api/user'
import type { QuestionVO } from '@/types/api'

const questions = ref<QuestionVO[]>([])
const loading = ref(true)
const submitted = ref(false)
const submitting = ref(false)
const activeIndex = ref(0)

const answers = reactive<Record<number, string>>({})

const typeText: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断' }

const current = computed(() => questions.value[activeIndex.value])

/** 答题卡状态 */
function cardState(q: QuestionVO): 'answered' | 'current' | 'none' {
  if (activeIndex.value === questions.value.indexOf(q)) return 'current'
  return answers[q.id] ? 'answered' : 'none'
}

function selectOption(q: QuestionVO, option: string) {
  if (q.type === 2) {
    // 多选
    const cur = (answers[q.id] ?? '').split('')
    const idx = cur.indexOf(option)
    if (idx >= 0) cur.splice(idx, 1)
    else cur.push(option)
    answers[q.id] = cur.sort().join('')
  } else {
    answers[q.id] = option
  }
}

function isSelected(q: QuestionVO, option: string): boolean {
  return (answers[q.id] ?? '').includes(option)
}

const answeredCount = computed(() => questions.value.filter((q) => answers[q.id]).length)

/** 交卷与成绩 */
const score = ref(0)
const correctCount = ref(0)

async function onSubmit() {
  if (answeredCount.value < questions.value.length) {
    await ElMessage.warning(`还有 ${questions.value.length - answeredCount.value} 题未作答`)
    return
  }
  submitting.value = true
  try {
    let correct = 0
    const results = questions.value.map((q) => {
      const isCorrect = answers[q.id] === q.answer
      if (isCorrect) correct += 1
      return { questionId: q.id, answer: answers[q.id], correct: isCorrect }
    })
    await submitAnswers(results)
    correctCount.value = correct
    score.value = Math.round((correct / questions.value.length) * 100)
    submitted.value = true
    ElMessage.success(`交卷成功！得分 ${score.value} 分`)
  } catch {
    /* ignore */
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    questions.value = await questionList()
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="zx-page">
    <div class="mb-5 flex items-center gap-3">
      <h1 class="text-2xl font-bold">考试练习</h1>
      <el-tag effect="plain" round>扩展模块 · 基础框架</el-tag>
    </div>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-4">
      <!-- 题目区 -->
      <div class="lg:col-span-3">
        <div v-if="current" class="zx-card p-6">
          <div class="flex items-center gap-3">
            <el-tag type="primary" effect="plain" round>{{ typeText[current.type] }}</el-tag>
            <span class="zx-text-secondary text-sm">第 {{ activeIndex + 1 }} / {{ questions.length }} 题</span>
            <el-tag
              v-if="submitted"
              :type="answers[current.id] === current.answer ? 'success' : 'danger'"
              size="small"
              round
            >
              {{ answers[current.id] === current.answer ? '回答正确' : '回答错误' }}
            </el-tag>
          </div>

          <h2 class="mt-4 text-lg font-semibold leading-8">{{ current.name }}</h2>

          <div class="mt-5 space-y-3">
            <div
              v-for="opt in current.options ?? []"
              :key="opt"
              class="zx-option flex cursor-pointer items-center gap-3 rounded-xl border-2 p-4 transition-colors"
              :class="{
                'zx-option--selected': isSelected(current, opt),
                'zx-option--correct': submitted && opt === current.answer,
                'zx-option--wrong': submitted && isSelected(current, opt) && opt !== current.answer,
              }"
              @click="selectOption(current, opt)"
            >
              <span class="flex h-7 w-7 items-center justify-center rounded-full border text-sm font-bold" style="border-color: currentColor">
                {{ opt }}
              </span>
              <span class="flex-1 text-sm">选项 {{ opt }}</span>
            </div>
          </div>

          <div v-if="submitted && current.analysis" class="zx-analysis mt-5 rounded-xl p-4 text-sm leading-6">
            <span class="font-bold">解析：</span>{{ current.analysis }}
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
              :class="cardState(q)"
              @click="activeIndex = i"
            >
              {{ i + 1 }}
            </div>
          </div>
          <div class="zx-text-secondary mt-4 text-xs">已答 {{ answeredCount }} / {{ questions.length }} 题</div>
        </div>

        <!-- 成绩 -->
        <div v-if="submitted" class="zx-card p-5 text-center">
          <div class="zx-text-secondary text-sm">最终得分</div>
          <div class="mt-1 text-4xl font-extrabold text-primary">{{ score }}</div>
          <div class="zx-text-secondary mt-2 text-sm">正确 {{ correctCount }} / {{ questions.length }} 题</div>
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
</style>
