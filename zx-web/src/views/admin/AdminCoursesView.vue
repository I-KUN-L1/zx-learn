<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Promotion, RefreshRight, Search, VideoPlay } from '@element-plus/icons-vue'
import {
  checkBeforeUpShelf,
  deleteCourse,
  downShelfCourse,
  getCategoryAll,
  getCourseBaseInfo,
  pageCourses,
  saveCourseBaseInfo,
  upShelfCourse,
} from '@/api/course'
import { formatPrice, COURSE_STATUS_TEXT } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { Category, CourseVO } from '@/types/api'

const router = useRouter()

/* ---------- 草稿 / 已发布 双 Tab（编辑态与发布态解耦） ---------- */
const activeTab = ref<'draft' | 'published'>('draft')

const query = reactive({ pageNo: 1, pageSize: 10, name: '' })
const list = ref<CourseVO[]>([])
const total = ref(0)
const loading = ref(false)

const statusByTab = computed(() => (activeTab.value === 'draft' ? 2 : 1))

async function fetchList() {
  loading.value = true
  try {
    const res = await pageCourses({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      name: query.name || undefined,
      status: statusByTab.value,
    })
    list.value = res.list
    total.value = res.total
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  query.pageNo = 1
  fetchList()
}

/* ---------- 新建/编辑课程：分步表单 ---------- */
const dialogVisible = ref(false)
const activeStep = ref(0)
const saving = ref(false)
const editingId = ref<number | null>(null)

const categories = ref<Category[]>([])
const lv1List = computed(() => categories.value.filter((c) => c.parentId === 0))
const lv2List = computed(() => categories.value.filter((c) => c.parentId === form.categoryIdLv1))

const form = reactive({
  name: '',
  coverUrl: '',
  price: 0,
  free: 0,
  categoryIdLv1: undefined as number | undefined,
  categoryIdLv2: undefined as number | undefined,
  description: '',
  chapters: [{ name: '', sections: [{ name: '' }] }] as { name: string; sections: { name: string }[] }[],
})

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    coverUrl: '',
    price: 0,
    free: 0,
    categoryIdLv1: undefined,
    categoryIdLv2: undefined,
    description: '',
    chapters: [{ name: '', sections: [{ name: '' }] }],
  })
  activeStep.value = 0
  dialogVisible.value = true
}

async function openEdit(row: CourseVO) {
  try {
    const base = await getCourseBaseInfo(row.id)
    editingId.value = row.id
    Object.assign(form, {
      name: base.name,
      coverUrl: base.coverUrl,
      price: base.price,
      free: base.free,
      categoryIdLv1: base.categoryIdLv1,
      categoryIdLv2: base.categoryIdLv2,
      description: base.description ?? '',
      chapters:
        (base.catalogueList as unknown as typeof form.chapters) ??
        row.catalogues?.map((ch) => ({
          name: ch.name,
          sections: (ch.sections ?? []).map((s) => ({ name: s.name })),
        })) ?? [{ name: '', sections: [{ name: '' }] }],
    })
    activeStep.value = 0
    dialogVisible.value = true
  } catch {
    /* ignore */
  }
}

function addChapter() {
  form.chapters.push({ name: '', sections: [{ name: '' }] })
}
function addSection(chIdx: number) {
  form.chapters[chIdx].sections.push({ name: '' })
}
function removeChapter(idx: number) {
  form.chapters.splice(idx, 1)
}

/** 保存基本信息（第 1 步） */
async function saveBase() {
  if (!form.name || form.categoryIdLv1 == null) {
    ElMessage.warning('请填写课程名称与分类')
    return
  }
  saving.value = true
  try {
    const id = await saveCourseBaseInfo({
      id: editingId.value ?? undefined,
      name: form.name,
      coverUrl: form.coverUrl,
      price: form.free === 1 ? 0 : Math.round(form.price * 100),
      categoryIdLv1: form.categoryIdLv1,
      categoryIdLv2: form.categoryIdLv2,
      free: form.free,
      description: form.description,
      catalogueList: form.chapters
        .filter((c) => c.name.trim())
        .map((c) => ({
          name: c.name,
          sections: c.sections.filter((s) => s.name.trim()).map((s) => ({ name: s.name })),
        })),
    })
    editingId.value = id
    ElMessage.success('保存成功')
    dialogVisible.value = false
    activeTab.value = 'draft'
    await fetchList()
  } catch {
    /* ignore */
  } finally {
    saving.value = false
  }
}

/* ---------- 发布 / 下架 ---------- */
const publishingId = ref<number | null>(null)

/** 发布：先 checkBeforeUpShelf（后端原子校验），再 upShelf */
async function onPublish(row: CourseVO) {
  publishingId.value = row.id
  try {
    await checkBeforeUpShelf(row.id)
    await ElMessageBox.confirm(`确定发布课程「${row.name}」吗？发布后将同步到课程中心。`, '发布课程', {
      confirmButtonText: '确认发布',
      type: 'info',
    }).catch(() => Promise.reject(new Error('cancel')))
    await upShelfCourse(row.id)
    ElMessage.success(`已发布（累计第 ${row.publishTimes! + 1} 次发布）`)
    await fetchList()
  } catch {
    /* 用户取消或拦截器已提示校验失败原因 */
  } finally {
    publishingId.value = null
  }
}

async function onDownShelf(row: CourseVO) {
  await ElMessageBox.confirm(`确定下架「${row.name}」吗？`, '下架课程', { type: 'warning' }).catch(() => null)
  try {
    await downShelfCourse(row.id)
    ElMessage.success('已下架')
    await fetchList()
  } catch {
    /* ignore */
  }
}

async function onDelete(row: CourseVO) {
  await ElMessageBox.confirm(`确定删除草稿「${row.name}」吗？`, '删除课程', { type: 'warning' }).catch(() => null)
  try {
    await deleteCourse(row.id)
    ElMessage.success('已删除')
    await fetchList()
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  fetchList()
  try {
    categories.value = await getCategoryAll()
  } catch {
    /* ignore */
  }
})

void COURSE_STATUS_TEXT
void formatPrice
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <div class="zx-card mb-5 flex flex-wrap items-center gap-3 p-4">
      <el-input
        v-model="query.name"
        placeholder="搜索课程名称"
        :prefix-icon="Search"
        clearable
        class="!w-64"
        @keyup.enter="fetchList(); (query.pageNo = 1)"
        @clear="fetchList"
      />
      <el-button type="primary" @click="query.pageNo = 1; fetchList()">搜索</el-button>
      <el-button :icon="RefreshRight" circle @click="fetchList" />
      <el-button type="primary" class="ml-auto" :icon="Plus" round @click="openCreate">新建课程</el-button>
    </div>

    <!-- 草稿 / 已发布 Tab -->
    <div class="zx-card p-5">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="草稿箱（编辑态）" name="draft" />
        <el-tab-pane label="已发布（发布态）" name="published" />
      </el-tabs>

      <div v-loading="loading">
        <EmptyState v-if="!loading && !list.length" description="暂无课程" size="small" />
        <el-table v-else :data="list" row-key="id">
          <el-table-column label="课程" min-width="260">
            <template #default="{ row }">
              <div class="flex items-center gap-3">
                <img :src="row.coverUrl" class="h-12 w-20 rounded-lg object-cover" :alt="row.name" />
                <div class="min-w-0">
                  <div class="truncate font-medium">{{ row.name }}</div>
                  <div class="zx-text-secondary text-xs">
                    {{ row.free === 1 ? '免费' : `￥${formatPrice(row.price)}` }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="activeTab === 'draft' ? 'info' : 'success'" size="small" round>
                {{ activeTab === 'draft' ? '草稿' : COURSE_STATUS_TEXT[row.status] ?? '已上架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布次数" width="100" align="center">
            <template #default="{ row }">
              <span class="font-semibold text-primary">{{ row.publishTimes ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <template v-if="activeTab === 'draft'">
                <el-button size="small" round @click="openEdit(row as CourseVO)">编辑</el-button>
                <el-button
                  size="small"
                  type="primary"
                  round
                  :icon="Promotion"
                  :loading="publishingId === row.id"
                  @click="onPublish(row as CourseVO)"
                >
                  发布
                </el-button>
                <el-button size="small" type="danger" text @click="onDelete(row as CourseVO)">删除</el-button>
              </template>
              <template v-else>
                <el-button size="small" round @click="router.push(`/courses/${row.id}`)">预览</el-button>
                <el-button size="small" round type="warning" plain @click="onDownShelf(row as CourseVO)">下架</el-button>
              </template>
            </template>
          </el-table-column>
          <template #empty><EmptyState description="暂无数据" size="small" /></template>
        </el-table>
      </div>

      <div v-if="total > query.pageSize" class="mt-5 flex justify-center">
        <el-pagination
          v-model:current-page="query.pageNo"
          :page-size="query.pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 分步表单：新建课程 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑课程' : '新建课程'" width="720px" top="5vh">
      <el-steps :active="activeStep" align-center finish-status="success" class="mb-6">
        <el-step title="基本信息" />
        <el-step title="章节目录" />
        <el-step title="完成保存" />
      </el-steps>

      <!-- 第 1 步：基本信息 -->
      <el-form v-if="activeStep === 0" label-width="90px">
        <el-form-item label="课程名称" required>
          <el-input v-model="form.name" placeholder="请输入课程名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="课程分类" required>
          <div class="flex w-full gap-3">
            <el-select v-model="form.categoryIdLv1" placeholder="一级分类" class="flex-1" @change="form.categoryIdLv2 = undefined">
              <el-option v-for="c in lv1List" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-select v-model="form.categoryIdLv2" placeholder="二级分类（可选）" class="flex-1" clearable>
              <el-option v-for="c in lv2List" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="收费模式">
          <el-radio-group v-model="form.free">
            <el-radio :value="1">免费</el-radio>
            <el-radio :value="0">收费</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.free === 0" label="价格（元）">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="封面地址">
          <el-input v-model="form.coverUrl" placeholder="留空则使用默认封面" />
        </el-form-item>
        <el-form-item label="课程介绍">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="一段吸引人的课程介绍" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <!-- 第 2 步：章节目录 -->
      <div v-else-if="activeStep === 1" class="space-y-4">
        <div v-for="(ch, ci) in form.chapters" :key="ci" class="rounded-xl border p-4" style="border-color: var(--zx-border)">
          <div class="flex items-center gap-3">
            <el-tag type="primary" effect="plain" round>章节 {{ ci + 1 }}</el-tag>
            <el-input v-model="ch.name" placeholder="章节名称，如：第一章 Java 基础" class="flex-1" />
            <el-button v-if="form.chapters.length > 1" type="danger" text @click="removeChapter(ci)">删除</el-button>
          </div>
          <div class="mt-3 space-y-2 pl-9">
            <div v-for="(s, si) in ch.sections" :key="si" class="flex items-center gap-2">
              <el-input v-model="s.name" :placeholder="`小节 ${si + 1} 名称`" size="small" />
              <el-button v-if="ch.sections.length > 1" type="danger" text size="small" @click="ch.sections.splice(si, 1)">
                移除
              </el-button>
            </div>
            <el-button size="small" text type="primary" :icon="VideoPlay" @click="addSection(ci)">添加小节</el-button>
          </div>
        </div>
        <el-button class="w-full" dashed :icon="Plus" @click="addChapter">添加章节</el-button>
      </div>

      <!-- 第 3 步：确认保存 -->
      <div v-else class="space-y-4 py-2">
        <el-result icon="success" title="信息已就绪" sub-title="保存后将进入草稿箱，可随时编辑或发布">
          <template #extra>
            <div class="zx-text-secondary text-left text-sm leading-7">
              <div>课程名称：{{ form.name || '-' }}</div>
              <div>收费模式：{{ form.free === 1 ? '免费' : `￥${form.price}` }}</div>
              <div>章节数量：{{ form.chapters.filter((c) => c.name.trim()).length }} 章</div>
            </div>
          </template>
        </el-result>
      </div>

      <template #footer>
        <el-button v-if="activeStep > 0" @click="activeStep--">上一步</el-button>
        <el-button v-if="activeStep < 2" type="primary" @click="activeStep++">下一步</el-button>
        <el-button v-if="activeStep === 2" type="primary" :loading="saving" @click="saveBase">
          {{ saving ? '保存中…' : '保存课程' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
