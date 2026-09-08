<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Iphone, User } from '@element-plus/icons-vue'
import { registerStudent, registerTeacher, type RegisterFormDTO } from '@/api/auth'
import AgreementDialog from '@/components/auth/AgreementDialog.vue'

const router = useRouter()
const agreementRef = ref<InstanceType<typeof AgreementDialog>>()

/** 查看协议正文（阻止冒泡避免切换勾选状态） */
function openAgreement(tab: 'user' | 'privacy') {
  agreementRef.value?.open(tab)
}

type RoleKey = 'student' | 'teacher'

const formRef = ref<FormInstance>()
const loading = ref(false)
const role = ref<RoleKey>('student')
/** 注册协议勾选：未勾选不允许注册 */
const agreed = ref(false)
const form = reactive<RegisterFormDTO & { confirmPassword: string }>({
  cellPhone: '',
  password: '',
  username: '',
  name: '',
  confirmPassword: '',
})

const rules: FormRules = {
  cellPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  username: [{ max: 30, message: '用户名不能超过 30 个字符', trigger: 'blur' }],
  name: [{ max: 30, message: '姓名不能超过 30 个字符', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  // 注册协议校验：未勾选时提醒并阻止提交
  if (!agreed.value) {
    ElMessage.warning('请先勾选同意《用户协议》与《隐私政策》后再注册')
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    // 管理员账号仅后端创建：自助注册仅支持学员/教师，角色由后端强制指定
    const payload: RegisterFormDTO = {
      cellPhone: form.cellPhone,
      password: form.password,
      username: form.username || undefined,
      name: form.name || undefined,
    }
    if (role.value === 'teacher') {
      await registerTeacher(payload)
    } else {
      await registerStudent(payload)
    }
    ElMessage.success('注册成功，请登录')
    await router.replace('/login')
  } catch {
    /* 拦截器已提示（如手机号已注册） */
  } finally {
    loading.value = false
  }
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
            加入知行智学，<br />开启高效学习
          </h1>
          <p class="mt-4 text-indigo-100">
            注册即享 AI 助教、学情报告与个性化学习路径。管理员账号由系统后台创建。
          </p>
        </div>
        <p class="text-xs text-indigo-200">© 2026 ZhiXing Learn · Spring Cloud 微服务 + Vue 3</p>
      </div>

      <!-- 右侧表单 -->
      <div class="flex-1 bg-white p-8 dark:bg-[#1f2937] sm:p-12">
        <h2 class="text-2xl font-bold">注册新账号</h2>
        <p class="zx-text-secondary mt-2 text-sm">目前支持学员与教师注册</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          class="mt-6"
          @keyup.enter="submit"
        >
          <el-form-item>
            <el-radio-group v-model="role">
              <el-radio-button value="student">学员注册</el-radio-button>
              <el-radio-button value="teacher">教师注册</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item prop="cellPhone">
            <el-input v-model="form.cellPhone" placeholder="手机号" :prefix-icon="Iphone" maxlength="11" />
          </el-form-item>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名（选填）" :prefix-icon="User" maxlength="30" />
          </el-form-item>
          <el-form-item prop="name">
            <el-input v-model="form.name" placeholder="姓名（选填）" :prefix-icon="User" maxlength="30" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码（至少 6 位）"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="agreed">
              <span class="zx-text-secondary text-sm">
                注册即代表同意
                <a class="text-primary" @click.stop.prevent="openAgreement('user')">《用户协议》</a>与<a class="text-primary" @click.stop.prevent="openAgreement('privacy')">《隐私政策》</a>
              </span>
            </el-checkbox>
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
              {{ loading ? '注册中…' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="zx-text-secondary mt-4 flex items-center justify-between text-xs">
          <span>已有账号？</span>
          <a class="cursor-pointer text-primary" @click="router.push('/login')">去登录</a>
        </div>
      </div>
    </div>
    <AgreementDialog ref="agreementRef" />
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
</style>
