<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, resetUserPassword } from '@/api/user'
import { formatDate, USER_TYPE_TEXT } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import type { UserVO } from '@/types/api'

/**
 * 用户管理。
 * 权限说明：本页数据接口 /users/page 已由后端 @RequireRole(STAFF) 保护，
 * 非员工账号访问时后端返回 403 并全局跳转 403 页。
 */
const query = reactive({ pageNo: 1, pageSize: 10 })
const users = ref<UserVO[]>([])
const total = ref(0)
const loading = ref(false)

/** type → 权限级别说明 */
const TYPE_AUTHORITY: Record<number, string> = {
  1: '全部权限',
  2: '学员权限',
  3: '课程/题库维护',
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await pageUsers({ ...query })
    users.value = res.list
    total.value = res.total
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

async function onResetPassword(user: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定将用户「${user.username}」的密码重置为默认密码吗？`,
      '重置密码',
      { type: 'warning', confirmButtonText: '重置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await resetUserPassword(user.id)
    ElMessage.success('密码已重置')
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

onMounted(fetchUsers)
</script>

<template>
  <div>
    <div class="zx-card mb-5 flex items-center p-4">
      <h1 class="text-lg font-bold">用户与权限</h1>
      <span class="zx-text-secondary ml-3 text-sm">账号类型由后端鉴权（1员工/2学员/3教师）</span>
      <el-button class="ml-auto" circle @click="fetchUsers" />
    </div>

    <div class="zx-card p-5">
      <div v-loading="loading">
        <EmptyState v-if="!loading && !users.length" description="暂无用户" size="small" />
        <el-table v-else :data="users" row-key="id">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="cellPhone" label="手机号" width="140" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" round>{{ USER_TYPE_TEXT[row.type] ?? '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权限范围" min-width="160">
            <template #default="{ row }">
              <span class="text-sm">{{ TYPE_AUTHORITY[row.type] ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="170">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="warning" plain round @click="onResetPassword(row as UserVO)">
                重置密码
              </el-button>
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
          @current-change="fetchUsers"
        />
      </div>
    </div>
  </div>
</template>
