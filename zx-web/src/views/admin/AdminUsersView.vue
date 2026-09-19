<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteUser, pageUsers, resetUserPassword, updateUserStatus } from '@/api/user'
import { formatDate, maskPhone, USER_TYPE_TEXT } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/common/EmptyState.vue'
import type { UserVO } from '@/types/api'

/**
 * 用户管理（管理端）。
 * 权限说明：本页数据接口 /users/page、状态修改 /users/{id}/status/{status}
 * 与删除接口 DELETE /users/{id} 均由后端 @RequireRole(STAFF) 保护，
 * 非员工账号访问时后端返回 403 并全局跳转 403 页。
 *
 * 状态管理：账号状态的修改权限收敛到管理员（本页），
 * 禁用后该账号登录会被网关/认证服务拦截并提示"请联系管理员"。
 */
const userStore = useUserStore()
const query = reactive({ pageNo: 1, pageSize: 10 })
const users = ref<UserVO[]>([])
const total = ref(0)
const loading = ref(false)
/** 正在提交状态变更的用户 id，避免重复点击 */
const statusSubmitting = ref<string>('')

/** type → 权限级别说明 */
const TYPE_AUTHORITY: Record<number, string> = {
  1: '全部权限',
  2: '学员权限',
  3: '课程/题库维护',
}

/** 是否为当前登录账号（后端也会拒绝禁用/删除本人，此处仅做交互层提示） */
function isSelf(user: UserVO): boolean {
  return String(user.id) === String(userStore.userId)
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

/** 启用 / 禁用账号：二次确认后调后端，后端强校验"不能禁用本人与最后一名管理员" */
async function onToggleStatus(user: UserVO) {
  const disabling = user.status === 1
  const action = disabling ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      disabling
        ? `确定禁用「${user.username}」吗？禁用后该账号将无法登录，登录时会被提示"请联系管理员"。`
        : `确定启用「${user.username}」吗？启用后该账号可立即正常登录。`,
      `${action}账号`,
      {
        type: disabling ? 'warning' : 'info',
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消',
        confirmButtonClass: disabling ? 'el-button--danger' : '',
      },
    )
  } catch {
    return
  }
  statusSubmitting.value = String(user.id)
  try {
    await updateUserStatus(user.id, disabling ? 0 : 1)
    ElMessage.success(`已${action}账号`)
    await fetchUsers()
  } catch {
    /* 错误由拦截器统一提示（含"不能禁用当前登录账号/最后一名管理员"） */
  } finally {
    statusSubmitting.value = ''
  }
}

async function onResetPassword(user: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定将用户「${user.username}」的密码重置为 123456 吗？`,
      '重置密码',
      { type: 'warning', confirmButtonText: '重置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await resetUserPassword(user.id)
    ElMessage.success('密码已重置为 123456')
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

/** 删除用户：二次确认；禁止删除本人与系统最后一名管理员（后端强校验） */
async function onDelete(user: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${user.username}」（${USER_TYPE_TEXT[user.type] ?? '未知角色'}）吗？删除后该账号将无法登录，操作不可恢复。`,
      '删除用户',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await deleteUser(user.id)
    ElMessage.success('用户已删除')
    await fetchUsers()
  } catch {
    /* 错误由拦截器统一提示（含"不能删除当前登录账号/最后一名管理员"） */
  }
}

onMounted(fetchUsers)
</script>

<template>
  <div>
    <div class="zx-card mb-5 flex flex-wrap items-center gap-3 p-4">
      <h1 class="text-lg font-bold">用户与权限</h1>
      <span class="zx-text-secondary text-sm">
        账号类型由后端鉴权（1员工/2学员/3教师）；状态修改与重置密码为管理员专属操作
      </span>
      <el-button class="ml-auto" circle @click="fetchUsers" />
    </div>

    <div class="zx-card p-5">
      <div v-loading="loading">
        <EmptyState v-if="!loading && !users.length" description="暂无用户" size="small" />
        <div v-else class="zx-table-scroll">
          <el-table :data="users" row-key="id">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="140" show-overflow-tooltip />
            <!-- 手机号展示层打码：后端仍返回完整号码（客服核身需要），前端仅在列表可见面收敛暴露 -->
            <el-table-column label="手机号" width="140">
              <template #default="{ row }">{{ maskPhone(row.cellPhone) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" round>{{ USER_TYPE_TEXT[row.type] ?? '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="权限范围" min-width="140" show-overflow-tooltip>
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
            <el-table-column label="注册时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" min-width="270" fixed="right">
              <template #default="{ row }">
                <div class="flex flex-wrap items-center gap-2">
                  <el-button
                    size="small"
                    :type="row.status === 1 ? 'danger' : 'success'"
                    plain
                    round
                    :loading="statusSubmitting === String(row.id)"
                    :disabled="isSelf(row as UserVO) && row.status === 1"
                    :title="isSelf(row as UserVO) && row.status === 1 ? '不能禁用当前登录账号' : ''"
                    @click="onToggleStatus(row as UserVO)"
                  >
                    {{ row.status === 1 ? '禁用' : '启用' }}
                  </el-button>
                  <el-button size="small" type="warning" plain round @click="onResetPassword(row as UserVO)">
                    重置密码
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    round
                    :disabled="isSelf(row as UserVO)"
                    :title="isSelf(row as UserVO) ? '不能删除当前登录账号' : '删除用户'"
                    @click="onDelete(row as UserVO)"
                  >
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <template #empty><EmptyState description="暂无数据" size="small" /></template>
          </el-table>
        </div>
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

<style scoped>
/* 横向兜底：窄屏时表格内部可滚动，避免 fixed 列与内容把整页撑出横向滚动条 */
.zx-table-scroll {
  width: 100%;
  overflow-x: auto;
}
</style>
