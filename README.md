# 知行智学 · ZhiXing Learn（zx-learn）

<!--
  Badges 说明：
  1. CI 徽章指向 github.com/I-KUN-L1/zx-learn，若仓库迁移请同步更新。
  2. Coverage 为静态数值徽章。CI 每次运行会将各模块 JaCoCo 报告上传为 artifact
     （jacoco-report-java-21），下载后可查看 HTML 报告；将最新总覆盖率手动更新到
     下方 badge 的数字即可（或接入 Codecov 后替换为 codecov 动态徽章，见 docs/ROADMAP.md）。
-->
[![CI](https://github.com/I-KUN-L1/zx-learn/actions/workflows/ci.yml/badge.svg)](https://github.com/I-KUN-L1/zx-learn/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?logo=springboot&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Coverage](https://img.shields.io/badge/coverage-35%25-green)

> **AI 驱动的智慧学习平台，围绕"知-学-行-评"闭环构建** · AI-Powered Smart Learning Platform
>
> Java 21 · Spring Boot 3.3.5 · Spring Cloud Alibaba · MyBatis-Plus · Redis · JWT · OpenAI 兼容 LLM

<p align="center">
  <b>课程 · 学习 · 实践 · 评估 · AI 智能助教</b><br/>
  16 个可运行服务 · 2 个公共底座 · 多 Agent 智能问答 · SSE 流式输出 · 会话记忆 · 链路追踪（requestId 透传）
</p>

## 📸 项目预览

> 🚧 素材录制中，当前为占位区。录制完成后取消注释并提交图片至 `docs/images/`。

<!--
<p align="center">
  <img src="docs/images/preview-courses.png"  alt="课程列表"            width="420"/>
  <img src="docs/images/preview-sse-chat.gif" alt="AI 助教 SSE 流式对话" width="420"/>
  <img src="docs/images/preview-insight.png"  alt="学情报告 / 能力画像"  width="420"/>
</p>
-->

<details>
<summary>🎬 素材录制方法（点击展开）</summary>

**① 终端动图（SSE 对话演示）—— asciinema 录制 + agg 转 GIF**

```bash
# 安装（Windows）
pip install asciinema                          # 录制工具
scoop install agg                              # 转 GIF 工具（或到 asciinema/agg releases 下载 exe）

# 录制（进入录屏后，把下方"演示脚本"逐条粘贴执行，Ctrl+D 结束录制）
asciinema rec -o docs/images/sse-chat.cast

# 回放检查效果
asciinema play docs/images/sse-chat.cast

# 转 GIF（cols 控制宽度，speed 控制播放速度）
agg --cols 100 --font-size 14 --speed 1 --theme monokai \
    docs/images/sse-chat.cast docs/images/preview-sse-chat.gif
```

演示脚本（录制期间执行，展示流式输出最直观）：

```bash
curl -N -X POST http://localhost:8080/chat/text \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"demo","question":"帮我推荐一门 Java 课程"}'
```

**② 页面截图（课程列表 / 学情报告）**

- Windows：`Win + Shift + S` 框选截图，保存为 `docs/images/preview-courses.png`、`preview-insight.png`（建议统一宽度 1200px）
- 数据来自 `sql/init.sql` 初始化的示例课程，启动 zx-course / zx-insight 后访问 `http://localhost:{port}/doc.html` 调试或页面截取

</details>

---

## ✨ 项目亮点

| 亮点 | 说明 |
|---|---|
| 🧠 **AI 多 Agent 助教** | 意图路由（RouteAgent）分发到 4 个业务 Agent，SSE 流式输出 + Redis 会话记忆，OpenAI 兼容协议可插拔 |
| 🏗️ **微服务公共底座** | 统一响应 `R<T>`、统一异常、requestId 链路追踪抽到 zx-common，16 服务零重复 |
| 🔐 **JWT 网关统一鉴权** | 双 Token（access + refresh），网关全局过滤器校验 + user-info 透传，RBAC 权限模型 |
| 📦 **草稿-正式双表** | 课程编辑态与发布态解耦，上架原子校验，发布次数统计 |
| 🔄 **"知-学-行-评"闭环** | 知（课程内容）→ 学（学习记录/课表）→ 行（练习/笔记/交易）→ 评（学情报告/能力画像/学习路径） |
| 📚 **文档体系完整** | 架构图 / 接口参考 / 数据库设计 / 部署指南 / 零基础指南 / 面试题库 / 演进路线 一应俱全 |
| 🔒 **凭据外部化** | 数据库/Redis 密码、JWT 密钥全部通过环境变量注入（见 `.env.example`），仓库零硬编码密钥 |

## 📖 文档导航

| 文档 | 内容 |
|---|---|
| [📐 架构设计](docs/ARCHITECTURE.md) | 架构图、模块依赖、核心设计、技术选型 |
| [🔌 接口参考](docs/API-REFERENCE.md) | 各服务全部接口清单与统一响应约定 |
| [🗄️ 数据库设计](docs/DATABASE.md) | 按服务分库设计、表结构、公共字段约定 |
| [🚀 部署指南](docs/DEPLOYMENT.md) | 本地启动、Docker 容器化、生产配置切换 |
| [🎓 零基础学习指南](docs/LEARNING-GUIDE.md) | 从环境搭建到看懂核心代码的完整路线 + 动手练习 |
| [💬 面试题库](docs/INTERVIEW-QUESTIONS.md) | 高频面试题与深度解答、权衡取舍、踩坑复盘 |
| [📝 版本记录](docs/CHANGELOG.md) | 里程碑、问题修复、技术要点 |
| [🗺️ 演进路线（Roadmap）](docs/ROADMAP.md) | 扩展位服务落地计划与基础设施规划 |
| [🤝 贡献指南](CONTRIBUTING.md) | 分支命名、提交规范、PR 流程 |

---

## 🏛️ 架构一览

```
客户端 (Web / 小程序 / App)
        │
        ▼
┌─────────────────────────────────────────────┐
│  zx-gateway (8080)  统一入口 · JWT 双 Token 鉴权 │
└──────────────────────┬──────────────────────┘
                       │  路由分发
                       ▼
   ┌─────────────────────────────────────────┐
   │               🎯 核心服务组                │
   │  zx-auth (8081)      zx-user (8082)      │
   │  登录 / JWT / RBAC      用户 / 学员 / 教师    │
   │ ─────────────────────────────────────── │
   │  zx-course (8083)    zx-aigc (8089)      │
   │  课程 / 草稿-正式      多 Agent 助教 / SSE    │
   │ ─────────────────────────────────────── │
   │  zx-insight (8095)                       │
   │  学情报告 / 能力画像 / 路径推荐                │
   └─────────────────────────────────────────┘
        │                │                 │
        ▼                ▼                 ▼
   MySQL(分库)     Redis(缓存/会话记忆)    PostgreSQL+pgvector
                                          (向量知识库)
```

> 完整架构图与模块依赖见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 🧩 模块清单

| 模块 | 职责 | 状态 | 端口 |
|---|---|---|---|
| zx-common | 统一响应/异常/分页/工具/链路追踪（公共底座） | ✅ 完整 | - |
| zx-api | Feign 契约层 / DTO（公共底座） | ✅ 完整 | - |
| zx-gateway | 路由 / JWT 鉴权 / user-info 透传 | ✅ 完整 | 8080 |
| zx-auth | 登录 / JWT 双 Token / RBAC | ✅ 完整 | 8081 |
| zx-user | 用户 / 学员 / 教师 / 员工 | ✅ 完整 | 8082 |
| zx-course | 课程草稿-正式 / 分类 / 目录 | ✅ 完整 | 8083 |
| zx-aigc | AI 多 Agent 助教 / SSE 流式 / 会话记忆 / RAG | ✅ 完整 | 8089 |
| zx-insight | 学情报告 / 能力画像 / 学习路径 | ✅ 完整 | 8095 |
| zx-learning | 课表 / 学习记录 / 笔记 / 签到 | ✅ 完整 | 8086 |
| zx-trade | 订单（雪花 ID/状态机） / 支付回调 / 超时关单 | ✅ 完整 | 8087 |
| zx-promotion | 优惠券（券状态机/兑换码核销） / 领券 | ✅ 完整 | 8088 |

> ✅ 完整实现：含数据库持久化与完整业务校验。
> 另有 7 个契约先行的扩展位（exam/media/pay/search/remark/message/data），规划见 [ROADMAP.md](docs/ROADMAP.md)。

---

## 🚀 快速开始

### 1. 环境要求

- JDK 21+、Maven 3.8+
- Docker（可选，用于一键启动 MySQL/Redis）或本机 MySQL 8.x、Redis

### 2. 配置环境变量

复制 `.env.example` 为 `.env` 并填写实际值（MySQL/Redis 密码、JWT 密钥等，JWT 密钥长度需 ≥ 32 字节）：

```bash
cp .env.example .env
```

> 各服务通过 `${VAR}` 读取环境变量；仓库内不包含任何默认密码或硬编码密钥。

### 3. 初始化基础设施与数据库

```bash
docker compose up -d          # 启动 MySQL + Redis（密码取自 .env）
mysql -uroot -p < sql/init.sql   # 初始化数据库与表结构
```

### 4. 编译

```bash
mvn clean install -DskipTests
```

### 5. 启动核心链路

```bash
mvn -pl zx-user spring-boot:run      # 8082
mvn -pl zx-course spring-boot:run    # 8083
mvn -pl zx-auth spring-boot:run      # 8081
mvn -pl zx-gateway spring-boot:run   # 8080
mvn -pl zx-aigc spring-boot:run      # 8089（可选）
mvn -pl zx-insight spring-boot:run   # 8095（可选）
```

### 6. 验证

```bash
# ① 获取首个管理员账号与初始密码
#    启动 zx-auth 后，查看应用根目录下生成的 .bootstrap-credentials 文件获取手机号与初始密码
cat .bootstrap-credentials

# ② 登录获取 accessToken
curl -X POST http://localhost:8080/accounts/admin/login \
  -H "Content-Type: application/json" \
  -d "{\"cellPhone\":\"<.bootstrap-credentials 中的手机号>\",\"password\":\"<.bootstrap-credentials 中的初始密码>\"}"

# ③ 首次登录后立即修改初始密码（修改成功后 .bootstrap-credentials 会被自动删除）
curl -X POST http://localhost:8080/accounts/password/first-change \
  -H "Content-Type: application/json" \
  -d "{\"cellPhone\":\"<手机号>\",\"oldPassword\":\"<初始密码>\",\"newPassword\":\"<新密码>\"}"

# ④ 携带 token 访问课程
curl http://localhost:8080/courses/page -H "Authorization: Bearer <accessToken>"

# ⑤ AI 智能助教（文本对话）
curl -X POST http://localhost:8080/chat/text \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","question":"帮我推荐一门 Java 课程"}'
```

> 在线接口文档：启动对应服务后访问 `http://localhost:{port}/doc.html`（Knife4j）。
> 常见问题（LLM 未配置 / 无 Redis / 端口冲突 / 最小启动链路等）见文末 [FAQ](#-faq)。

---

## 🔄 "知-学-行-评"学习闭环

```
知 Know ──► 课程内容 / 知识库（zx-course、KnowledgeAgent）
   ▲               │
   │               ▼
评 Evaluate ◄── 学 Learn（zx-learning：课表 / 学习记录 / 笔记 / 签到）
   │               │
   │               ▼
   └── 行 Practice ◄── 练习 / 考试 / 交易（zx-exam / zx-trade / zx-promotion）
        ▲
        └── zx-insight：学情报告 / 能力画像 / 学习路径推荐
```

- **知**：课程内容生产与知识问答
- **学**：学习过程追踪（课表、记录、笔记、签到）
- **行**：实践检验（练习、考试、下单购买）
- **评**：AI 学情分析（报告、能力画像、路径推荐），反哺"知"

## 🧠 AI 智能助教（项目差异化亮点）

```
用户提问 → RouteAgent 意图路由
   ├── RecommendAgent（推荐课程）
   ├── BuyAgent（购买咨询）
   ├── KnowledgeAgent（知识问答）
   └── ConsultAgent（默认咨询）
      ↓
LlmClient（OpenAI 兼容协议，可对接 DeepSeek / 通义千问 / OpenAI）
      ↓
SSE 流式输出 + ChatMemory（Redis 会话记忆，20 条 / TTL 7 天）+ RAG 向量检索
```

接入真实大模型（配置见 `.env` / 环境变量）：

```yaml
# .env
ZX_LLM_BASE_URL=https://api.deepseek.com
ZX_LLM_API_KEY=<your-api-key>
ZX_LLM_MODEL=deepseek-chat
ZX_LLM_ENABLED=true
```

未配置 apiKey 时自动返回模拟回复，保证本地可运行、可演示、可测试。

## 🧪 测试

```bash
mvn test
```

现有测试覆盖：统一响应 `RTest`、JWT 工具 `JwtToolTest`、意图路由 `RouteAgentTest`、LLM 客户端 `LlmClientTest`、SSE 并发限流 `ChatServiceLimitTest`，以及骨架模块控制器单测（exam/promotion）。

## 📦 部署

```bash
# Docker 单服务部署
./startup.sh -c zx-auth -n zx-auth -d target/zx-auth.jar -p 8081
```

详细部署（Nacos 切换、负载均衡、大模型接入、凭据环境变量化）见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

---

## 🗺️ 演进路线

### 🏁 已完成里程碑

- **多 Agent 智能助教**：意图路由 + 4 个业务 Agent + SSE 流式 + 会话记忆 + RAG 向量检索（[v1.0.0](docs/CHANGELOG.md)）
- **学情分析闭环**：zx-insight 学情报告 / 能力画像 / 学习路径推荐（[v1.0.0](docs/CHANGELOG.md)）
- **网关 JWT 双 Token 鉴权 + RBAC**：access/refresh 双 Token + user-info 透传 + 角色权限模型（[v1.0.0](docs/CHANGELOG.md)）
- **课程草稿-正式双表**：编辑态与发布态解耦，发布原子校验（[v1.0.0](docs/CHANGELOG.md)）
- **凭据外部化**：数据库/Redis 密码、JWT 密钥全部环境变量注入，零硬编码（[v1.0.0](docs/CHANGELOG.md)）

> 后续规划（扩展位服务落地、Nacos/Sentinel/ES/前端等）见 [docs/ROADMAP.md](docs/ROADMAP.md)。

---

## 📄 许可与说明

- 本项目为个人技术学习与实践项目（MIT License），仅供学习交流使用。
- 生产环境请务必通过环境变量配置 `ZX_JWT_SECRET` 与数据库/Redis 密码，勿使用 `.env.example` 中的占位值。

---

## ❓ FAQ

| 问题 | 解决方案 |
|---|---|
| **未配置 LLM 的 API Key 能跑吗？** | 能。`ZX_LLM_ENABLED` 默认 `false`，`zx-aigc` 自动返回**模拟流式回复**（按真实格式带 `END` 事件），可运行、可演示、可压测。接入真实模型时在 `.env` 配 `ZX_LLM_BASE_URL / ZX_LLM_API_KEY / ZX_LLM_MODEL` 并置 `ZX_LLM_ENABLED=true`（DeepSeek 等任何 OpenAI 兼容接口均可） |
| **没装 Redis 行不行？** | 服务照常启动：AI 会话记忆（`ChatMemory`）在 Redis 不可用时**自动降级为无记忆模式**，对话主流程可用（仅记忆/缓存相关能力缺失）。推荐 `docker compose up -d redis` 一键补齐 |
| **端口冲突如何修改？** | 启动参数覆盖：`java -jar zx-course.jar --server.port=18083`；或改该服务 `application.yml` 的 `server.port`。注意：本地直连模式下其他服务的静态实例地址需同步更新 |
| **MySQL 版本有要求吗？** | **MySQL 8.x**（utf8mb4 字符集）。不保证兼容 5.7 —— 连接驱动、SQL 方言均按 8.x 设计，低版本不排查兼容问题 |
| **最小启动链路是什么？** | 只起 4 个服务即可跑通「登录 → 浏览课程」：`zx-user(8082) → zx-course(8083) → zx-auth(8081) → zx-gateway(8080)`，基础设施只需 MySQL + Redis。AI 助教加 `zx-aigc(8089)`，学情报告加 `zx-insight(8095)`，其余服务按需启动 |
| **JDK 17 能运行吗？** | 不能。项目统一 **Java 21**（`maven.compiler.release=21`，且启用虚拟线程），CI 同样以 JDK 21 构建验证；请安装 JDK 21+ |

> 更多部署细节（Nacos 切换、静态实例直连、RocketMQ/pgvector 可选组件）见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。