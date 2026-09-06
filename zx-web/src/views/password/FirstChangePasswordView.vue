<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Iphone } from '@element-plus/icons-vue'
import { firstChangePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  cellPhone: userStore.pendingCellPhone,
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

/** 密码强度：≥8 位且同时包含字母与数字 */
const validateStrongPassword = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) return callback(new Error('请输入新密码'))
  if (value.length < 8) return callback(new Error('密码长度至少 8 位'))
  if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) {
    return callback(new Error('密码需同时包含字母和数字'))
  }
  callback()
}

const rules: FormRules = {
  cellPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  oldPassword: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  newPassword: [{ required: true, validator: validateStrongPassword, trigger: 'blur' }],
  confirmPassword: [
    {
      required: true,
      validator: (_r: unknown, v: string, cb: (err?: Error) => void) => {
        if (!v) return cb(new Error('请再次输入新密码'))
        if (v !== form.newPassword) return cb(new Error('两次输入的密码不一致'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

/** 密码强度条 */
const strength = computed(() => {
  const v = form.newPassword
  if (!v) return 0
  let score = 0
  if (v.length >= 8) score += 34
  if (/[a-zA-Z]/.test(v) && /\d/.test(v)) score += 33
  if (/[^\w]/.test(v) || v.length >= 12) score += 33
  return score
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await firstChangePassword({
      cellPhone: form.cellPhone,
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    userStore.finishFirstChange()
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    await userStore.logout()
    await router.replace('/login')
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="zx-first flex min-h-full items-center justify-center px-4 py-10">
    <div class="zx-card w-full max-w-md p-8 sm:p-10">
      <div class="mb-2 flex items-center gap-2">
        <el-icon :size="22" color="var(--zx-primary)"><Lock /></el-icon>
        <h1 class="text-xl font-bold">首次登录，请修改初始密码</h1>
      </div>
      <p class="zx-text-secondary text-sm">
        为保障账号安全，系统检测到当前为初始密码，修改成功前无法访问其他页面。
      </p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" class="mt-8" @keyup.enter="submit">
        <el-form-item prop="cellPhone">
          <el-input v-model="form.cellPhone" placeholder="手机号" :prefix-icon="Iphone" maxlength="11" />
        </el-form-item>
        <el-form-item prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" placeholder="初始密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="新密码（≥8 位，含字母和数字）" :prefix-icon="Lock" show-password />
          <el-progress
            v-if="form.newPassword"
            :percentage="strength"
            :stroke-width="6"
            :show-text="false"
            :color="strength < 40 ? '#ef4444' : strength < 80 ? '#f59e0b' : '#22c55e'"
            class="w-full"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认新密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button
          type="primary"
          class="mt-2 w-full"
          size="large"
          round
          :loading="submitting"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '确认修改' }}
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.zx-first {
  background:
    radial-gradient(700px 350px at 20% 20%, rgba(99, 102, 241, 0.15), transparent),
    radial-gradient(700px 350px at 80% 80%, rgba(168, 85, 247, 0.12), transparent);
}
</style>
