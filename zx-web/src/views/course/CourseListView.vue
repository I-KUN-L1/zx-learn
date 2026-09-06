<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { getCategoryAll, pageCourses } from '@/api/course'
import CourseCard from '@/components/course/CourseCard.vue'
import SkeletonCards from '@/components/common/SkeletonCards.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { Category, CourseVO } from '@/types/api'

const route = useRoute()

/* ---------- 分类树 ---------- */
const categories = ref<Category[]>([])
const categoryTree = computed(() => {
  const roots = categories.value.filter((c) => c.parentId === 0)
  return roots.map((root) => ({
    ...root,
    children: categories.value.filter((c) => c.parentId === root.id),
  }))
})

/* ---------- 查询条件 ---------- */
const query = reactive({
  pageNo: 1,
  pageSize: 12,
  name: '',
  categoryId: (route.query.categoryId as string) || '',
  sortBy: '',
  isAsc: false,
})

const courses = ref<CourseVO[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)

async function fetchCourses() {
  loading.value = true
  try {
    const res = await pageCourses({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      name: query.name || undefined,
      sortBy: query.sortBy || undefined,
      isAsc: query.sortBy ? query.isAsc : undefined,
    })
    // 分类过滤：按一级/二级分类匹配
    let list = res.list
    if (query.categoryId) {
      const cid = Number(query.categoryId)
      list = list.filter(
        (c) => c.categoryIdLv1 === cid || c.categoryIdLv2 === cid
      )
    }
    courses.value = list
    total.value = res.total
    pages.value = res.pages
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function onSelectCategory(id: number | '') {
  query.categoryId = id === '' ? '' : String(id)
  query.pageNo = 1
  fetchCourses()
}

function onSearch() {
  query.pageNo = 1
  fetchCourses()
}

function onSort(sortBy: string) {
  if (query.sortBy === sortBy) {
    query.isAsc = !query.isAsc
  } else {
    query.sortBy = sortBy
    query.isAsc = false
  }
  query.pageNo = 1
  fetchCourses()
}

watch(
  () => route.query.categoryId,
  (v) => {
    query.categoryId = (v as string) || ''
    query.pageNo = 1
    fetchCourses()
  }
)

onMounted(async () => {
  getCategoryAll()
    .then((res) => (categories.value = res))
    .catch(() => undefined)
  fetchCourses()
})
</script>

<template>
  <div class="zx-page">
    <div class="flex gap-6">
      <!-- 左侧分类树 -->
      <aside class="zx-card hidden w-56 shrink-0 self-start p-4 lg:block">
        <h3 class="mb-3 px-2 font-semibold">课程分类</h3>
        <div
          class="zx-cat-item"
          :class="{ 'zx-cat-item--active': query.categoryId === '' }"
          @click="onSelectCategory('')"
        >
          全部课程
        </div>
        <template v-for="root in categoryTree" :key="root.id">
          <div
            class="zx-cat-item"
            :class="{ 'zx-cat-item--active': query.categoryId === String(root.id) }"
            @click="onSelectCategory(root.id)"
          >
            {{ root.name }}
          </div>
          <div
            v-for="child in root.children"
            :key="child.id"
            class="zx-cat-item zx-cat-item--child"
            :class="{ 'zx-cat-item--active': query.categoryId === String(child.id) }"
            @click="onSelectCategory(child.id)"
          >
            {{ child.name }}
          </div>
        </template>
      </aside>

      <!-- 右侧列表 -->
      <div class="min-w-0 flex-1">
        <!-- 搜索与排序 -->
        <div class="zx-card flex flex-wrap items-center gap-3 p-4">
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

          <div class="ml-auto flex items-center gap-2">
            <el-button
              size="small"
              round
              :type="query.sortBy === '' ? 'primary' : ''"
              @click="onSort('')"
            >
              默认
            </el-button>
            <el-button size="small" round :type="query.sortBy === 'enrollNum' ? 'primary' : ''" @click="onSort('enrollNum')">
              学习人数
              <span v-if="query.sortBy === 'enrollNum'">{{ query.isAsc ? '↑' : '↓' }}</span>
            </el-button>
            <el-button size="small" round :type="query.sortBy === 'price' ? 'primary' : ''" @click="onSort('price')">
              价格
              <span v-if="query.sortBy === 'price'">{{ query.isAsc ? '↑' : '↓' }}</span>
            </el-button>
          </div>
        </div>

        <!-- 课程卡片 -->
        <div class="mt-5">
          <SkeletonCards v-if="loading" :count="8" />
          <EmptyState v-else-if="!courses.length" description="没有找到相关课程，换个关键词试试吧">
            <el-button type="primary" round @click="onSelectCategory('')">查看全部课程</el-button>
          </EmptyState>
          <div v-else class="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
            <CourseCard v-for="c in courses" :key="c.id" :course="c" />
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="pages > 1" class="mt-8 flex justify-center">
          <el-pagination
            v-model:current-page="query.pageNo"
            :page-size="query.pageSize"
            :total="total"
            layout="prev, pager, next, jumper, total"
            background
            @current-change="fetchCourses"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.zx-cat-item {
  padding: 9px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--zx-text-secondary);
  font-size: 14px;
  transition: all 0.2s;
  margin-bottom: 2px;
}
.zx-cat-item:hover {
  color: var(--zx-primary);
  background: var(--zx-primary-bg);
}
.zx-cat-item--active {
  color: var(--zx-primary);
  background: var(--zx-primary-bg);
  font-weight: 600;
}
.zx-cat-item--child {
  padding-left: 26px;
  font-size: 13px;
}
</style>
