<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Delete, Edit, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { addQuestion, allQuestions, deleteQuestion, publishQuestion, updateQuestion, type TeacherQuestionForm } from '@/api/exam'
import { pageCourses } from '@/api/course'
import EmptyState from '@/components/common/EmptyState.vue'
import type { CourseVO, QuestionVO } from '@/types/api'

/* ---------- 题库列表 ---------- */
const list = ref<QuestionVO[]>([])
const loading = ref(false)
const keyword = ref('')
const typeFilter = ref<number | undefined>(undefined)

const TYPE_TEXT: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断' }

async function fetchList() {
  loading.value = true
  try {
    const all = (await allQuestions()) ?? []
    list.value = all.filter((q) => {
      const kw = keyword.value?.toLowerCase()
      const matchKeyword = !kw || (q.name ?? '').toLowerCase().includes(kw) || (q.courseName ?? '').toLowerCase().includes(kw)
      const matchType = typeFilter.value === undefined || q.type === typeFilter.value
      return matchKeyword && matchType
    })
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

/* ---------- 新建/编辑题目 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

/** 可选课程（师生联动锚点：学员按课程接收题目） */
const courses = ref<CourseVO[]>([])

async function fetchCourses() {
  try {
    const res = await pageCourses({ pageNo: 1, pageSize: 100 })
    courses.value = res?.list ?? []
  } catch {
    courses.value = []
  }
}

const form = reactive<TeacherQuestionForm>({
  name: '',
  type: 1,
  options: ['', '', '', ''],
  answer: 'A',
  difficulty: 2,
  score: 5,
  analysis: '',
  courseId: undefined,
  status: 1,
})

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: 1,
    options: ['', '', '', ''],
    answer: 'A',
    difficulty: 2,
    score: 5,
    analysis: '',
    courseId: undefined,
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: QuestionVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    name: row.name,
    type: row.type,
    options: row.options?.length ? [...row.options] : ['', ''],
    answer: row.answer ?? 'A',
    difficulty: row.difficulty ?? 2,
    score: row.score ?? 5,
    analysis: row.analysis ?? '',
    courseId: row.courseId,
    status: row.status ?? 1,
  })
  dialogVisible.value = true
}

function onTypeChange() {
  form.options = form.type === 3 ? ['正确', '错误'] : ['', '', '', '']
  form.answer = 'A'
}

async function save() {
  if (!form.name?.trim()) {
    ElMessage.warning('请填写题干')
    return
  }
  saving.value = true
  try {
    const payload: TeacherQuestionForm = {
      name: form.name.trim(),
      type: form.type,
      options: form.type === 3 ? ['正确', '错误'] : (form.options ?? []).map((o) => o.trim()).filter(Boolean),
      answer: form.answer,
      difficulty: form.difficulty,
      score: form.score,
      analysis: form.analysis?.trim() || undefined,
      courseId: form.courseId,
      status: form.status,
    }
    if (editingId.value == null) {
      await addQuestion(payload)
      ElMessage.success('题目已创建')
    } else {
      await updateQuestion(editingId.value, payload)
      ElMessage.success('题目已更新')
    }
    dialogVisible.value = false
    await fetchList()
  } catch {
    /* ignore */
  } finally {
    saving.value = false
  }
}

/** 发布 / 撤回：控制学员端可见性（教师发布 → 学员接收的开关） */
async function togglePublish(row: QuestionVO) {
  const next = (row.status ?? 1) !== 1
  try {
    await publishQuestion(row.id, next)
    ElMessage.success(next ? '已发布，学员端即可练习' : '已撤回，学员端不再展示')
    await fetchList()
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

async function remove(row: QuestionVO) {
  if (!(await confirmAction(`确定删除该题目吗？删除后不可恢复。`, '删除题目', { type: 'warning' }))) return
  try {
    await deleteQuestion(row.id)
    ElMessage.success('已删除')
    await fetchList()
  } catch {
    /* ignore */
  }
}

onMounted(() => {
  fetchList()
  fetchCourses()
})
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <div class="zx-card mb-5 flex flex-wrap items-center gap-3 p-4">
      <el-input
        v-model="keyword"
        placeholder="搜索题干关键词"
        :prefix-icon="Search"
        clearable
        class="!w-64"
        @keyup.enter="fetchList"
        @clear="fetchList"
      />
      <el-select v-model="typeFilter" placeholder="全部题型" clearable class="!w-32" @change="fetchList">
        <el-option v-for="(text, code) in TYPE_TEXT" :key="code" :label="text" :value="Number(code)" />
      </el-select>
      <el-button type="primary" @click="fetchList">搜索</el-button>
      <el-button :icon="RefreshRight" circle @click="fetchList" />
      <el-button type="primary" class="ml-auto" :icon="Plus" round @click="openCreate">新增题目</el-button>
    </div>

    <!-- 列表 -->
    <div class="zx-card p-5">
      <div v-loading="loading">
        <EmptyState v-if="!loading && !list.length" description="题库还是空的，先创建一道题吧" size="small" />
        <el-table v-else :data="list" row-key="id">
          <el-table-column label="题干" min-width="320">
            <template #default="{ row }">
              <div class="line-clamp-2 font-medium">{{ row.name }}</div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.type === 1 ? 'primary' : row.type === 2 ? 'success' : 'info'" size="small" round>
                {{ TYPE_TEXT[row.type] ?? '单选' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="难度" width="140" align="center">
            <template #default="{ row }">
              <el-rate :model-value="Math.ceil((row.difficulty ?? 1) / 1)" :max="5" disabled size="small" />
            </template>
          </el-table-column>
          <el-table-column label="关联课程" min-width="180">
            <template #default="{ row }">
              <span class="zx-text-secondary text-xs">{{ row.courseName || '未关联' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分值" width="70" align="center">
            <template #default="{ row }">{{ row.score ?? 5 }}</template>
          </el-table-column>
          <el-table-column label="答案" width="80" align="center">
            <template #default="{ row }">
              <span class="font-semibold text-primary">{{ row.answer || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="学员可见" width="110" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="(row.status ?? 1) === 1"
                inline-prompt
                active-text="已发布"
                inactive-text="草稿"
                @change="togglePublish(row as QuestionVO)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" round :icon="Edit" @click="openEdit(row as QuestionVO)">编辑</el-button>
              <el-button size="small" type="danger" text :icon="Delete" @click="remove(row as QuestionVO)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><EmptyState description="暂无数据" size="small" /></template>
        </el-table>
      </div>
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑题目' : '新增题目'" width="640px" top="6vh">
      <el-form label-width="80px">
        <el-form-item label="题干" required>
          <el-input v-model="form.name" type="textarea" :rows="2" placeholder="请输入题目内容" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type" @change="onTypeChange">
            <el-radio :value="1">单选</el-radio>
            <el-radio :value="2">多选</el-radio>
            <el-radio :value="3">判断</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="选项">
          <div class="w-full space-y-2">
            <div v-for="(opt, i) in form.options" :key="i" class="flex items-center gap-2">
              <el-tag size="small" round>{{ String.fromCharCode(65 + i) }}</el-tag>
              <el-input v-model="form.options![i]" :placeholder="`选项 ${String.fromCharCode(65 + i)}`" size="small" />
            </div>
            <el-button v-if="form.options!.length < 6" size="small" text type="primary" @click="form.options!.push('')">
              添加选项
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="答案">
          <el-input
            v-if="form.type !== 3"
            v-model="form.answer"
            placeholder="如 A 或 AB（多选用连写字母）"
            class="!w-48"
          />
          <el-select v-else v-model="form.answer" class="!w-48">
            <el-option label="正确" value="A" />
            <el-option label="错误" value="B" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="form.difficulty" :max="5" />
        </el-form-item>
        <el-form-item label="关联课程">
          <el-select v-model="form.courseId" placeholder="选择课程（学员按课程接收题目）" clearable filterable class="w-full">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="form.score" :min="1" :max="100" :step="1" class="!w-40" />
        </el-form-item>
        <el-form-item label="学员可见">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">立即发布（学员可练习）</el-radio>
            <el-radio :value="0">存为草稿（学员不可见）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="选填，答题后展示给学员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
