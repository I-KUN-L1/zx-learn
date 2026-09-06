<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Iphone } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'
import { IS_MOCK } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { handleLoginSuccess } = useAuth()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  cellPhone: '',
  password: '',
})

const rules: FormRules = {
  cellPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}

/** 回车提交 */
async function onEnter() {
  if (formRef.value) {
    await formRef.value.validate().catch(() => Promise.reject())
  }
  await submit()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    // 管理端与学员端共用登录页：默认学员端登录；Mock 演示账号见下方提示
    await userStore.login({ cellPhone: form.cellPhone, password: form.password }, false)
    ElMessage.success('登录成功')
    await handleLoginSuccess(form.cellPhone)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function goAdminLogin() {
  router.push('/login?admin=1').then(() => router.go(0))
}

const isAdminPage = route.query.admin === '1'

if (IS_MOCK && isAdminPage) {
  ElMessage.info('Mock 演示：管理端账号 13800000001 / admin123（首次登录 admin123 会触发强制改密）')
}
</script>

<template>
  <div class="zx-login flex min-h-full items-center justify-center px-4 py-10">
    <div class="w-full max-w-[900px] overflow-hidden rounded-3xl zx-login-panel md:flex">
      <!-- 左侧品牌区 -->
      <div class="zx-login-banner hidden flex-1 flex-col justify-between p-10 md:flex">
        <RouterLink to="/" class="flex items-center gap-2">
          <img src="/favicon.svg" alt="logo" class="h-10 w-10 rounded-xl" />
          <span class="text-xl font-bold text-white">知行智学</span>
        </RouterLink>
        <div>
          <h1 class="text-3xl font-extrabold leading-snug text-white">
            知学合一，<br />AI 伴你每一程
          </h1>
          <p class="mt-4 text-indigo-100">
            围绕「知 - 学 - 行 - 评」闭环，AI 助教 / 学情画像 / 个性化路径，高效学习平台。
          </p>
          <div class="mt-8 flex gap-3">
            <span class="zx-login-chip">AI 助教</span>
            <span class="zx-login-chip">学情报告</span>
            <span class="zx-login-chip">个性化路径</span>
          </div>
        </div>
        <p class="text-xs text-indigo-200">© 2026 ZhiXing Learn · Spring Cloud 微服务 + Vue 3</p>
      </div>

      <!-- 右侧表单 -->
      <div class="flex-1 bg-white p-8 dark:bg-[#1f2937] sm:p-12">
        <h2 class="text-2xl font-bold">{{ isAdminPage ? '管理端登录' : '欢迎回来' }}</h2>
        <p class="zx-text-secondary mt-2 text-sm">
          {{ isAdminPage ? '教师 / 管理员账号登录' : '登录后开启你的智慧学习之旅' }}
        </p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          class="mt-8"
          @keyup.enter="onEnter"
        >
          <el-form-item prop="cellPhone">
            <el-input v-model="form.cellPhone" placeholder="手机号" :prefix-icon="Iphone" maxlength="11" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="w-full"
              size="large"
              round
              :loading="loading"
              @click="submit"
            >
              {{ loading ? '登录中…' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="zx-text-secondary mt-4 flex items-center justify-between text-xs">
          <span v-if="IS_MOCK">Mock 账号：13800000001 / admin123</span>
          <span v-else>登录即代表同意《用户协议》与《隐私政策》</span>
          <a class="cursor-pointer text-primary" @click="goAdminLogin">
            {{ isAdminPage ? '学员端登录' : '管理端登录' }}
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.zx-login {
  background:
    radial-gradient(800px 400px at 15% 15%, rgba(99, 102, 241, 0.18), transparent),
    radial-gradient(800px 400px at 85% 85%, rgba(168, 85, 247, 0.14), transparent);
}
.zx-login-panel {
  box-shadow: 0 20px 60px 0 rgba(49, 46, 129, 0.25);
}
.zx-login-banner {
  background: linear-gradient(150deg, #4f46e5 0%, #7c3aed 60%, #9333ea 100%);
}
.zx-login-chip {
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 999px;
  padding: 4px 14px;
  font-size: 12px;
}
</style>
