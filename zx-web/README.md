# 知行智学 ZhiXing Learn — 前端 Web 应用

基于 **Vue 3 + TypeScript + Vite 5** 的单页 SPA，覆盖「知-学-行-评」闭环：

- **学员端**：首页、课程列表/详情、学习中心、AI 智能助教（SSE 流式）、学情报告（ECharts）、交易（订单 + 秒杀券）、考试练习、消息中心
- **教师/管理端**：课程管理（草稿-正式双表）、数据看板、用户与权限（RBAC）

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 框架 | Vue 3.5（Composition API + `<script setup>`）+ TypeScript 5 |
| 构建 | Vite 5（环境变量 `VITE_` 前缀） |
| 状态 | Pinia（组合式写法） |
| 路由 | Vue Router 4（history 模式 + 全局守卫） |
| UI | Element Plus（unplugin-vue-components 按需自动导入）+ Tailwind CSS 3 |
| 请求 | Axios（统一拦截器 + 401 静默续期） |
| SSE | @microsoft/fetch-event-source（POST + 自定义 Header + 中断/重连） |
| 图表 | ECharts 5（按需引入：雷达/折线/柱状） |
| 规范 | ESLint 9（flat config）+ Prettier |

## 快速开始

```bash
# 1. 安装依赖（Node >= 18，推荐 pnpm 11）
pnpm install

# 2a. Mock 演示模式（无需后端，内置演示数据 + 模拟 SSE 流式输出）
pnpm dev:mock

# 2b. 联调模式（对接本地后端网关）
pnpm dev

# 3. 生产构建 / 预览 / 质量检查
pnpm build       # vite build
pnpm typecheck   # vue-tsc --noEmit
pnpm lint        # eslint --fix
pnpm format      # prettier
```

Mock 模式演示账号：手机号 `13800000001`，密码任意（≥6 位；初始密码 `admin123` 时会触发首次登录强制改密流程演示）。

## 环境变量

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Axios 基地址；开发环境经 Vite proxy 代理到网关 | `/api` |
| `VITE_PROXY_TARGET` | Vite 开发代理目标（后端网关地址） | `http://localhost:8080` |
| `VITE_USE_MOCK` | `true` 时启用内置 Mock Adapter（无需后端） | `false` |

环境文件：`.env.development`（联调）、`.env.mock`（纯演示）、`.env.production`（部署时经 Nginx 反代 `/api` → 网关）。变量说明见 `.env.example`。

## 与后端联调

### 最小启动链路

| 服务 | 端口 | 前端依赖页面 |
| --- | --- | --- |
| zx-gateway | 8080 | 全部（统一入口，Vite proxy 目标） |
| zx-auth | 8081 | 登录 / 首次改密 |
| zx-user | 8082 | 用户信息 / 用户列表 |
| zx-course | 8083 | 课程列表 / 详情 / 课程管理 |

扩展链路：

- **AI 助教**：加启 zx-aigc（8089，SSE 流式 `POST /chat/text`）
- **学情报告**：加启 zx-insight（8095）
- **交易**：加启 zx-trade / zx-promotion（订单、优惠券、秒杀）
- **消息**：加启 zx-message（站内信）

各服务接口文档：`http://localhost:{port}/doc.html`（Knife4j）。

### 全局接口约定

1. 统一响应 `R<T> = { code, message, data }`，`code === 200` 为成功；非 200 全局 ElMessage 报错。
2. JWT 双 Token：登录 `POST /accounts/admin/login`（body: `{ cellPhone, password }`）返回 `accessToken + refreshToken`，自动携带 `Authorization: Bearer <accessToken>`；401 时用 refreshToken 静默换新，失败清空凭据并记录回跳地址。
3. 首次登录强制改密：`POST /accounts/password/first-change`（body: `{ cellPhone, oldPassword, newPassword }`），路由守卫强制跳转改密页。
4. SSE 对话：`POST /chat/text`（body: `{ sessionId, question }`），流式文本以 `END` 事件结束；支持 AbortController 中断与断线重连。
5. 分页示例：`GET /courses/page`（需 Bearer token）。

## 目录结构

```
src/
├── api/            # 按后端服务划分的 API 模块（auth/course/ai/insight/trade/promotion/learning/exam/user/message）
│   ├── request.ts  # Axios 实例：拦截器 + 401 静默续期 + Mock Adapter 挂载
│   └── mock/       # Mock 数据与适配层（对齐真实 R<T> / END 事件格式）
├── components/     # 布局（Header/Footer/Student·Admin Layout）、课程卡片、秒杀券、AI 消息等
├── composables/    # useAuth / useSSE / useCountdown / usePermission / useEcharts
├── directives/     # v-permission 按钮级权限指令
├── router/         # 路由表 + 全局守卫（登录/首改密/RBAC/403）
├── stores/         # Pinia：user（双 Token + 角色）/ app（主题等）
├── styles/         # 全局样式（CSS 变量深色主题切换）
├── types/          # 与后端 DTO 对应的 TS 接口
├── utils/          # auth/format/markdown/echarts/storage 工具
└── views/          # 页面（login/course/learning/assistant/insight/trade/exam/messages/admin…）
```

## 页面路由

| 路径 | 页面 | 权限 |
| --- | --- | --- |
| `/login` | 登录 | 公开 |
| `/password/first-change` | 首次改密 | 登录后强制 |
| `/` `/courses` `/courses/:id` | 首页 / 课程列表 / 详情 | 登录 |
| `/learning` `/assistant` `/insight` `/trade` `/exam` `/messages` | 学习中心 / AI 助教 / 学情报告 / 订单与优惠 / 考试 / 消息 | 登录 |
| `/admin/courses` `/admin/dashboard` `/admin/users` | 课程管理 / 数据看板 / 用户权限 | admin / teacher |
