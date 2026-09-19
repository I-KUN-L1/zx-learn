<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Plus, Promotion, RefreshRight, Search, VideoPlay } from '@element-plus/icons-vue'
import {
  checkBeforeUpShelf,
  deleteCourse,
  deleteDraft,
  downShelfCourse,
  getCategoryAll,
  getCourseBaseInfo,
  pageCourses,
  pageDrafts,
  saveCourseBaseInfo,
  upShelfCourse,
} from '@/api/course'
import { formatPrice, COURSE_STATUS_TEXT } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/common/EmptyState.vue'
import CourseCover from '@/components/course/CourseCover.vue'
import type { Category, CourseDraftVO, CourseVO } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

/**
 * 草稿 / 已发布 双 Tab。
 *
 * ⚠ 这两个 Tab 背后是**两张不同的表**，不是同一张表的两个 status：
 *   - 草稿箱   → course_draft（submitted = 0），走 /courses/draft/page
 *   - 已发布   → course（正式课程），走 /courses/page
 * 早期这里两个 Tab 都打 /courses/page，用 status=2 冒充"草稿"，
 * 而 course.status 只有 0/1 —— 于是新建课程「保存成功」但草稿箱永远空白。
 */
const activeTab = ref<'draft' | 'published'>('draft')

const query = reactive({ pageNo: 1, pageSize: 10, name: '' })
const draftList = ref<CourseDraftVO[]>([])
const courseList = ref<CourseVO[]>([])
const total = ref(0)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      name: query.name || undefined,
    }
    if (activeTab.value === 'draft') {
      const res = await pageDrafts(params)
      draftList.value = res.list
      total.value = res.total
    } else {
      // 不带 status 过滤：下架（status=0）的课程也要留在列表里可见，
      // 否则「下架」一按课程就从管理端彻底消失，只能翻数据库才找得到。
      const res = await pageCourses(params)
      courseList.value = res.list
      total.value = res.total
    }
  } catch {
    /* 业务错误已由请求拦截器统一提示 */
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  query.pageNo = 1
  fetchList()
}

function onSearch() {
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

/** 步骤文案：1-基础信息 2-目录（与后端 course_draft.step 对齐） */
const STEP_TEXT: Record<number, string> = { 1: '基础信息', 2: '章节目录', 3: '视频', 4: '题目' }

function resetForm() {
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
}

function openCreate() {
  editingId.value = null
  resetForm()
  activeStep.value = 0
  dialogVisible.value = true
}

async function openEdit(row: CourseDraftVO) {
  try {
    // 草稿 id：草稿表的主键，与正式课程 id 不是一回事
    const base = await getCourseBaseInfo(row.id)
    editingId.value = row.id
    Object.assign(form, {
      name: base.name ?? '',
      coverUrl: base.coverUrl ?? '',
      // 库里价格是「分」，表单是「元」
      price: (base.price ?? 0) / 100,
      free: base.free ?? 0,
      categoryIdLv1: base.categoryIdLv1,
      categoryIdLv2: base.categoryIdLv2,
      description: base.description ?? '',
      chapters: base.catalogueList?.length
        ? base.catalogueList.map((ch) => ({
            name: ch.name,
            sections: ch.sections?.length ? ch.sections.map((s) => ({ name: s.name })) : [{ name: '' }],
          }))
        : [{ name: '', sections: [{ name: '' }] }],
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

/** 是否填了有意义的章节（只有名称非空的行才算） */
const filledChapters = computed(() =>
  form.chapters
    .filter((c) => c.name.trim())
    .map((c) => ({
      name: c.name.trim(),
      sections: c.sections.filter((s) => s.name.trim()).map((s) => ({ name: s.name.trim() })),
    })),
)
const filledChapterCount = computed(() => filledChapters.value.length)

function nextStep() {
  if (activeStep.value === 0 && (!form.name.trim() || form.categoryIdLv1 == null)) {
    ElMessage.warning('请填写课程名称与一级分类')
    return
  }
  if (activeStep.value < 2) activeStep.value += 1
}

/**
 * 保存到草稿箱（新增或更新草稿）。
 *
 * 保存成功后**不关弹窗**：用户可以继续往下填/改，草稿箱里已经有这条记录了。
 * keepOpen=false 时（例如点"完成"）保存后返回列表。
 */
async function saveBase(keepOpen = true) {
  if (!form.name.trim() || form.categoryIdLv1 == null) {
    ElMessage.warning('请填写课程名称与一级分类')
    return
  }
  saving.value = true
  try {
    const id = await saveCourseBaseInfo({
      id: editingId.value ?? undefined,
      name: form.name.trim(),
      coverUrl: form.coverUrl.trim(),
      // 免费课价格强制为 0；表单是元，接口要分
      price: form.free === 1 ? 0 : Math.round(form.price * 100),
      categoryIdLv1: form.categoryIdLv1,
      categoryIdLv2: form.categoryIdLv2,
      free: form.free,
      description: form.description,
      // 目录随基础信息一起存进 course_draft.catalogue_json：
      // 早期后端没有这个字段，第 2 步填的章节会被静默丢弃，看起来像"没保存上"
      catalogueList: filledChapters.value,
      step: Math.max(1, activeStep.value + 1),
    })
    editingId.value = id
    ElMessage.success(keepOpen ? '已存入草稿箱，可继续编辑' : '已存入草稿箱')
    if (!keepOpen) {
      dialogVisible.value = false
    }
    activeTab.value = 'draft'
    query.pageNo = 1
    await fetchList()
  } catch {
    /* ignore */
  } finally {
    saving.value = false
  }
}

/* ---------- 发布 / 下架 ---------- */
const publishingId = ref<number | null>(null)

/**
 * 发布：以**草稿 id** 为入参（先 checkBeforeUpShelf 原子校验，再 upShelf）。
 * 后端 upShelf 会把草稿同步到正式表、把目录合并进 course_catalogue，
 * 并把草稿标记为已发布（于是它离开草稿箱、出现在「已发布」Tab）。
 */
async function onPublish(row: CourseDraftVO) {
  publishingId.value = row.id
  try {
    await checkBeforeUpShelf(row.id)
    const ok = await confirmAction(`确定发布课程「${row.name}」吗？发布后将同步到课程中心。`, '发布课程', {
      confirmButtonText: '确认发布',
      type: 'info',
    })
    if (!ok) return
    await upShelfCourse(row.id)
    ElMessage.success('已发布')
    await fetchList()
  } catch {
    /* 用户取消，或校验失败原因已由拦截器提示 */
  } finally {
    publishingId.value = null
  }
}

/** 下架正式课程（员工任意课程；教师仅本人名下课程，后端按归属校验） */
async function onDownShelf(row: CourseVO) {
  if (!(await confirmAction(`确定下架「${row.name}」吗？`, '下架课程', { type: 'warning' }))) return
  try {
    await downShelfCourse(row.id)
    ElMessage.success('已下架')
    await fetchList()
  } catch {
    /* ignore */
  }
}

/** 删除草稿（走草稿表接口；正式课程删除是另一个接口，不要混用） */
async function onDeleteDraft(row: CourseDraftVO) {
  if (!(await confirmAction(`确定删除草稿「${row.name}」吗？`, '删除草稿', { type: 'warning' }))) return
  try {
    await deleteDraft(row.id)
    ElMessage.success('已删除')
    await fetchList()
  } catch {
    /* ignore */
  }
}

/**
 * 删除正式课程（员工与教师均可，后端双重校验）。
 * 教师只能删本人名下课程，必然 403 的入口不在前端暴露，真正的拦截在后端。
 */
async function onDeleteCourse(row: CourseVO) {
  if (!(await confirmAction(`确定删除课程「${row.name}」吗？该操作不可撤销。`, '删除课程', { type: 'warning' }))) return
  try {
    await deleteCourse(row.id)
    ElMessage.success('已删除')
    await fetchList()
  } catch {
    /* ignore */
  }
}

/**
 * 课程归属判定：本页所有管理操作（下架 / 删除）的可见性共用同一口径，
 * 逐条对齐后端 CourseService#assertCourseManageable 的权限矩阵：
 * - 员工(1)：不受归属限制，任意课程可管理；
 * - 教师(3)：仅本人名下课程（course.teacherId === 当前用户 id）可管理；
 * - 其他角色：无课程管理能力。
 *
 * 前端只保证"不给必然失败的入口"，权限判定始终以后端为准（前端不可信）。
 * 改这里必须同步后端 CourseService 的归属校验，反之亦然。
 */
function canManageCourse(row: CourseVO) {
  if (userStore.isAdmin) return true
  if (userStore.isTeacher) {
    return userStore.userId > 0 && Number(row.teacherId) === Number(userStore.userId)
  }
  return false
}

function stepText(step?: number) {
  return step ? STEP_TEXT[step] ?? `第 ${step} 步` : '基础信息'
}

function statusText(status?: number) {
  return status == null ? '-' : COURSE_STATUS_TEXT[status] ?? '未知'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  fetchList()
  try {
    categories.value = await getCategoryAll()
  } catch {
    /* ignore */
  }
})

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
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" @click="onSearch">搜索</el-button>
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
        <!-- 草稿箱：来自 course_draft -->
        <template v-if="activeTab === 'draft'">
          <EmptyState
            v-if="!loading && !draftList.length"
            description="草稿箱是空的，点右上角「新建课程」开始创建"
            size="small"
          />
          <el-table v-else :data="draftList" row-key="id">
            <el-table-column label="课程" min-width="280">
              <template #default="{ row }">
                <div class="flex items-center gap-3">
                  <div class="h-12 w-20 shrink-0 overflow-hidden rounded-lg">
                    <CourseCover :src="row.coverUrl" :name="row.name" :seed="row.id" />
                  </div>
                  <div class="min-w-0">
                    <div class="truncate font-medium">{{ row.name }}</div>
                    <div class="zx-text-secondary text-xs">
                      {{ row.free === 1 ? '免费' : `￥${formatPrice(row.price ?? 0)}` }}
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="编辑进度" width="120">
              <template #default="{ row }">
                <el-tag type="info" size="small" round>{{ stepText(row.step) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近编辑" width="160">
              <template #default="{ row }">
                <span class="zx-text-secondary text-xs">{{ formatTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button size="small" round @click="openEdit(row as CourseDraftVO)">编辑</el-button>
                <el-button
                  size="small"
                  type="primary"
                  round
                  :icon="Promotion"
                  :loading="publishingId === row.id"
                  @click="onPublish(row as CourseDraftVO)"
                >
                  发布
                </el-button>
                <el-button size="small" type="danger" text @click="onDeleteDraft(row as CourseDraftVO)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty><EmptyState description="暂无数据" size="small" /></template>
          </el-table>
        </template>

        <!-- 已发布：来自 course（正式表） -->
        <template v-else>
          <EmptyState v-if="!loading && !courseList.length" description="暂无已发布课程" size="small" />
          <el-table v-else :data="courseList" row-key="id">
            <el-table-column label="课程" min-width="280">
              <template #default="{ row }">
                <div class="flex items-center gap-3">
                  <div class="h-12 w-20 shrink-0 overflow-hidden rounded-lg">
                    <CourseCover :src="row.coverUrl" :name="row.name" :seed="row.id" />
                  </div>
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
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
                  {{ statusText(row.status) }}
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
                <el-button size="small" round @click="router.push(`/courses/${row.id}`)">预览</el-button>
                <!-- 下架与删除共用同一归属口径：教师对非本人课程不显示入口（后端同样会 403） -->
                <el-button
                  v-if="row.status === 1 && canManageCourse(row as CourseVO)"
                  size="small"
                  round
                  type="warning"
                  plain
                  @click="onDownShelf(row as CourseVO)"
                >
                  下架
                </el-button>
                <el-button
                  v-if="canManageCourse(row as CourseVO)"
                  size="small"
                  type="danger"
                  text
                  @click="onDeleteCourse(row as CourseVO)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
            <template #empty><EmptyState description="暂无数据" size="small" /></template>
          </el-table>
        </template>
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

    <!-- 分步表单：新建/编辑课程（保存即入草稿箱） -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑课程草稿' : '新建课程'"
      width="720px"
      top="5vh"
    >
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
          <div class="w-full">
            <el-input v-model="form.coverUrl" placeholder="留空或填错都会自动使用渐变兜底封面" />
            <div class="mt-2 flex items-center gap-3">
              <div class="h-14 w-24 shrink-0 overflow-hidden rounded-lg border" style="border-color: var(--zx-border)">
                <CourseCover :src="form.coverUrl" :name="form.name || '课程封面'" />
              </div>
              <span class="zx-text-secondary text-xs">
                {{ form.coverUrl ? '封面预览（加载失败会自动降级为渐变占位）' : '未填封面，将显示渐变占位封面' }}
              </span>
            </div>
          </div>
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
        <el-result icon="success" title="信息已就绪" sub-title="保存后进入草稿箱，可随时编辑或发布">
          <template #extra>
            <div class="zx-text-secondary text-left text-sm leading-7">
              <div>课程名称：{{ form.name || '-' }}</div>
              <div>收费模式：{{ form.free === 1 ? '免费' : `￥${form.price}` }}</div>
              <div>章节数量：{{ filledChapterCount }} 章</div>
            </div>
          </template>
        </el-result>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="activeStep > 0" @click="activeStep--">上一步</el-button>
        <el-button v-if="activeStep < 2" @click="nextStep">下一步</el-button>
        <el-button :loading="saving" :type="activeStep === 2 ? 'primary' : 'default'" @click="saveBase(true)">
          {{ activeStep === 2 ? '保存到草稿箱' : '存草稿箱' }}
        </el-button>
        <el-button v-if="activeStep === 2" type="primary" :loading="saving" @click="saveBase(false)">
          保存并返回列表
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
