import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'
import { useAppStore } from './stores/app'
import { perm } from './directives/permission'
import './styles/index.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 注册按钮级 RBAC 指令：v-perm="'admin'" / v-perm="['admin','teacher']"
app.directive('perm', perm)

// 恢复本地登录状态与主题
useUserStore().restore()
useAppStore().applyTheme()

app.mount('#app')
