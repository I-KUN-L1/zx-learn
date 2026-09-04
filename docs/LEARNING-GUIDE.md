# 知行智学（zx-learn）零基础学习指南

> 从零开始，一步步把这个 Spring Cloud 微服务项目**跑起来、看得懂、能讲解**。
> 项目代号：`zx-learn` · Java 21 · Spring Boot 3.3.5 + Spring Cloud Alibaba
>
> 本指南是面试/学习配套文档，配合 [README](../README.md)（项目概述）、[ARCHITECTURE](ARCHITECTURE.md)（架构）、[INTERVIEW-QUESTIONS](INTERVIEW-QUESTIONS.md)（面试）使用。

---

## 目录

1. [这个项目是什么](#一这个项目是什么)
2. [技术栈速览](#二技术栈速览)
3. [前置知识要求与自检清单](#三前置知识要求与自检清单)
4. [推荐学习资源](#四推荐学习资源)
5. [项目结构：17 个模块](#五项目结构17-个模块)
6. [运行前准备](#六运行前准备)
7. [初始化数据库](#七初始化数据库)
8. [在 IDEA 中启动项目](#八在-idea-中启动项目)
9. [核心链路讲解：看懂关键代码](#九核心链路讲解看懂关键代码)
10. [动手练习（由浅入深）](#十动手练习由浅入深)
11. [常见问题排查](#十一常见问题排查)
12. [学习路线与答疑](#十二学习路线与答疑)

---

## 一、这个项目是什么

**知行智学**是一个面向在线教育场景的 **微服务学习平台**，业务上覆盖了：课程学习、交易支付、营销优惠、互动社区，以及一个基于大模型的 **AI 智能助教**。

它最大的价值在于：**用真实业务场景，把微服务开发中常用的技术栈串了起来**。对零基础的同学来说，这个项目是理解"一个企业级 Java 后端长什么样"的最佳样本之一。

### 项目能做什么

| 领域 | 能力 |
|---|---|
| 课程 | 课程草稿-正式分离、分类管理、章节目录、上下架 |
| 学习 | 我的课表、学习记录、笔记、签到 |
| 交易/支付 | 购物车、下单、退款、统一支付渠道 |
| 营销 | 优惠券、兑换码 |
| 互动 | 点赞、积分、排行榜 |
| AI 助教 | 多 Agent 智能问答、流式输出、会话记忆 |

> **实现状态**：`auth`（认证）、`user`（用户）、`course`（课程）、`aigc`（AI）、`gateway`（网关）、`common`、`api` 为完整实现；其余 10 个模块为**骨架实现**（接口契约已就绪，核心逻辑用内存存储演示，可继续扩展为数据库持久化）。零基础学习先从完整模块入手。

---

## 二、技术栈速览

| 分类 | 技术 | 版本 | 作用 |
|---|---|---|---|
| 语言 | Java | 21 | 运行环境 |
| 基础框架 | Spring Boot | 3.3.5 | 快速构建服务 |
| 微服务 | Spring Cloud / Alibaba | 2023.0.3 / 2023.0.3.2 | 服务治理、Feign、Nacos |
| ORM | MyBatis-Plus | 3.5.9 | 数据库操作 |
| 工具库 | Hutool | 5.8.36 | 常用工具（含 BCrypt 加密） |
| 数据库 | MySQL | 8.x | 数据存储 |
| 缓存 | Redis / Redisson | - | 缓存、会话记忆、分布式锁 |
| 鉴权 | JWT（jjwt） | 0.12.6 | 登录令牌 |
| 接口文档 | Knife4j / springdoc | 4.5.0 | 在线接口文档 |
| AI | OpenAI 兼容协议（可插拔） | - | 智能助教 |

> 不需要全部精通，先知道"每个技术是干嘛的"。跑起来之后，再逐个深入。

---

## 三、前置知识要求与自检清单

> 开始前对照自检，缺哪补哪。**不需要全部精通再动手**，60% 达标即可边学边做。

### 3.1 Java 基础（★★★ 必会）

- 面向对象：类、继承、接口、多态
- 集合框架：List / Map / Set
- 异常体系：try-catch、自定义异常
- 常用 API：String、日期时间（LocalDateTime）

**自检题**：
- [ ] 能写一个带构造器、getter/setter 的 Java 类？
- [ ] 知道 `Map` 和 `List` 的区别，能说出何时用哪个？
- [ ] 能解释 `interface` 的作用？

### 3.2 数据库基础（★★★ 必会）

- SQL 增删改查（CRUD）
- 主键、外键、索引的基本概念
- 多表查询（join）

**自检题**：
- [ ] 能写出 `SELECT ... FROM ... WHERE ... ORDER BY ... LIMIT`？
- [ ] 知道为什么需要索引？

### 3.3 Spring 基础（★★★ 必会）

- IoC（控制反转）与 DI（依赖注入）概念
- `@Component / @Service / @Controller` 注解
- 一个 Hello World 的 Spring Boot 应用

**自检题**：
- [ ] 能解释"为什么不用 new 创建对象，而是让 Spring 管理"？
- [ ] 知道 `@Autowired` 是干嘛的？

### 3.4 微服务概念（★★☆ 建议提前了解）

- 单体 vs 微服务的区别
- 网关、注册中心、负载均衡是干什么的
- HTTP 的基本请求方式（GET/POST/PUT/DELETE）

### 3.5 其他（★☆☆ 可边学边补）

- Redis：key-value 存储
- JWT：一种无状态令牌
- 大模型 API：OpenAI 兼容协议的调用方式

> **结论**：前三项达标即可开工；Redis/JWT/AI 在阅读对应代码时现学现用，效率最高。

---

## 四、推荐学习资源

### 4.1 书籍

| 难度 | 书名 | 用途 |
|---|---|---|
| 入门 | 《Java 核心技术 卷 I》 | Java 基础查漏补缺 |
| 入门 | 《Spring 实战（第 6 版）》 | Spring Boot 快速上手 |
| 进阶 | 《Spring Cloud 微服务实战》 | 微服务各组件详解 |
| 进阶 | 《深入理解 Java 虚拟机》 | JVM 面试储备（后期） |
| 实战 | 《MySQL 必知必会》 | SQL 快速查询手册 |

### 4.2 视频/课程

- 《Spring Cloud 微服务实战》知识体系（本项目工程化重构与增强）
- 尚硅谷《Spring Boot3 零基础入门》
- B 站搜索"Spring Cloud Alibaba"系统课

### 4.3 官方文档（最权威）

- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/index.html)
- [Spring Cloud 官方文档](https://docs.spring.io/spring-cloud/index.html)
- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
- [MyBatis-Plus 文档](https://baomidou.com/)
- [Nacos 文档](https://nacos.io/docs/latest/overview/)

### 4.4 本项目内部资料（最贴合）

| 文档 | 用途 |
|---|---|
| [README](../README.md) | 项目全景与快速开始 |
| [ARCHITECTURE](ARCHITECTURE.md) | 架构图与核心设计 |
| [API-REFERENCE](API-REFERENCE.md) | 每个服务的接口清单 |
| [DATABASE](DATABASE.md) | 数据库表结构 |
| [DEPLOYMENT](DEPLOYMENT.md) | 部署与运维 |

---

## 五、项目结构：17 个模块

这是一个 Maven **多模块（multi-module）** 项目，根目录 `pom.xml` 作为父工程，统一管理依赖版本。

```
zx-learn (parent)                          ← 父工程，只做依赖管理，不写业务
├── zx-common       公共基础模块       ← R 统一响应 / 异常 / 分页 / 工具 / 自动配置
├── zx-api          契约层             ← Feign Client / DTO / 缓存（跨服务调用约定）
├── zx-gateway      网关               ← 路由 + JWT 鉴权（统一入口，端口 8080）
├── zx-auth         认证服务           ← 登录 / JWT / RBAC 权限（8081）
├── zx-user         用户服务           ← 用户 / 学员 / 教师 / 员工（8082）
├── zx-course       课程服务           ← 课程 / 分类 / 目录（8083）
├── zx-aigc         AI 服务            ← 多 Agent 智能助教（8089）
├── zx-exam         考试服务（骨架）
├── zx-media        媒资服务（骨架）
├── zx-learning     学习服务（骨架）
├── zx-trade        交易服务（骨架）
├── zx-promotion    营销服务（骨架）
├── zx-pay          支付服务（骨架）
├── zx-search       搜索服务（骨架）
├── zx-remark       点赞服务（骨架）
├── zx-message      消息服务（骨架）
└── zx-data         数据看板服务（骨架）
```

### 服务端口一览

| 服务 | 端口 | 服务 | 端口 |
|---|---|---|---|
| gateway | 8080 | aigc | 8089 |
| auth | 8081 | pay | 8090 |
| user | 8082 | search | 8091 |
| course | 8083 | remark | 8092 |
| exam | 8084 | message | 8093 |
| media | 8085 | data | 8094 |
| learning | 8086 | | |
| trade | 8087 | | |
| promotion | 8088 | | |

### 模块依赖关系（关键）

- `zx-common` 是所有业务模块的**公共底座**（统一响应、异常、工具）。
- `zx-api` 依赖 `zx-common`，定义**跨服务调用约定**（Feign Client + DTO）。
- 业务模块（auth/user/course/aigc…）依赖 `zx-common` + `zx-api`。
- `zx-gateway` 是唯一**不依赖** zx-common/zx-api 的模块（纯 WebFlux 网关）。

---

## 六、运行前准备

> 下面每一步都尽量写清楚。已有部分环境可跳过对应小节。

### 6.1 安装 JDK 21

1. 打开 [Adoptium Temurin 下载页](https://adoptium.net/temurin/releases/?version=21)，选择 Windows x64 的 **JDK 21** 安装包（`.msi`）下载并安装。
2. 安装时勾选 "Set JAVA_HOME variable"，一路下一步。
3. 验证（`Win + R` 输入 `cmd`）：

```bash
java -version
```

看到 `openjdk version "21.x.x"` 即成功。

> 本项目编译目标为 JDK 21，需用 JDK 21 环境运行。

### 6.2 安装 Maven

1. 到 [Maven 官网](https://maven.apache.org/download.cgi) 下载 **Binary zip**（如 3.9.x）。
2. 解压到固定目录，如 `D:\apache-maven-3.9.6`。
3. 配置环境变量：新增 `MAVEN_HOME`，把 `%MAVEN_HOME%\bin` 加入 `Path`。
4. 验证：

```bash
mvn -version
```

> 用 IntelliJ IDEA 的话，IDEA 自带 Maven，可跳过手动安装。

### 6.3 安装 IntelliJ IDEA

1. 到 [JetBrains 官网](https://www.jetbrains.com/idea/download/) 下载 **IntelliJ IDEA Community（社区版，免费）**。
2. 一路默认安装。

### 6.4 安装 MySQL 8

1. 到 [MySQL 官网](https://dev.mysql.com/downloads/installer/) 下载 MySQL Installer，选择 **MySQL Server 8.x**。
2. 安装时记住 root 密码（需与项目环境变量 `MYSQL_PASSWORD` 保持一致，见 6.6 配置说明）。
3. 安装后确认能连接。

### 6.5 安装 Redis（可选，建议装）

- AI 助教的**会话记忆**依赖 Redis；核心服务（认证、课程）也用到 Redis 缓存。
- **不装 Redis 也能跑**（AI 降级为无记忆，不影响登录）。
- 推荐 [tporadowski/redis](https://github.com/tporadowski/redis/releases) Windows 版，解压后启动 `redis-server.exe`。
- **Redis 密码通过环境变量 `REDIS_PASSWORD` 配置**，请据此设置（`redis.windows.conf` 中 `requirepass` 与之保持一致）。

### 6.6 配置说明（重要）

连接凭据全部通过**环境变量注入**（模板见根目录 `.env.example`），无需改动代码：

| 项 | 说明 |
|---|---|
| `MYSQL_USERNAME` | MySQL 用户名（如 `root`） |
| `MYSQL_PASSWORD` | MySQL 密码 |
| `REDIS_HOST` | Redis 地址（默认 `localhost`） |
| `REDIS_PORT` | Redis 端口（默认 `6379`） |
| `REDIS_PASSWORD` | Redis 密码 |

**推荐：环境变量覆盖**

```
MYSQL_USERNAME  = 你的MySQL用户名
MYSQL_PASSWORD  = 你的MySQL密码
REDIS_HOST      = localhost
REDIS_PORT      = 6379
REDIS_PASSWORD  = 你的Redis密码
```

**方式二：直接改配置文件**

修改 `zx-auth` / `zx-user` / `zx-course` / `zx-aigc` / `zx-data` 等模块 `application.yml` 里的 `username` / `password`。

---

## 七、初始化数据库

初始化脚本在 `sql/init.sql`，会创建 **3 个数据库**：`zx_auth`、`zx_user`、`zx_course`，并插入一条初始管理员账号（账号与密码信息由部署方保管，见部署备忘，不在文档中公开）。

### 执行方式（二选一）

**方式一：命令行**

```bash
mysql -uroot -p < sql/init.sql
```

**方式二：图形工具**

用 Navicat / DBeaver / MySQL Workbench 打开 `sql/init.sql` 直接运行。

> 执行成功后在数据库列表能看到 `zx_auth`、`zx_user`、`zx_course` 三个库。

---

## 八、在 IDEA 中启动项目

这是本文档的重点。跟着做即可。

### 8.1 打开项目

1. IDEA → **File → Open**。
2. 选中项目根目录（含 `pom.xml` 的目录），点击 **OK**。
3. 等依赖下载完成（首次较久）。弹出 "Maven projects need to be imported" 选 **Enable Auto-Import**。

### 8.2 配置 JDK 和 Maven

1. **JDK**：`File → Project Structure → Project`，SDK 设为 JDK 21，Language level 选 21。
2. **Maven**：`File → Settings → Build Tools → Maven`，选 Maven home path（或用 Bundled）。

### 8.3 编译项目（验证环境 OK）

打开右侧 **Maven 面板**，在根工程 `zx-learn` 下双击 **`install`**。看到 `BUILD SUCCESS` 说明环境就绪。

> 也可运行 **`test`** 执行单元测试（`RTest`、`JwtToolTest`、`RouteAgentTest`、`LlmClientTest`、骨架控制器测试等）。

### 8.4 启动服务（按顺序）

> 核心链路（登录 → 查课程）只需启动下面 5 个服务：

| 顺序 | 服务 | 启动类 | 端口 | 说明 |
|---|---|---|---|---|
| 1 | zx-user | `com.zhixing.user.UserApplication` | 8082 | auth 登录时通过 Feign 调它校验账号 |
| 2 | zx-course | `com.zhixing.course.CourseApplication` | 8083 | 课程查询 |
| 3 | zx-auth | `com.zhixing.auth.AuthApplication` | 8081 | 提供登录接口 |
| 4 | zx-gateway | `com.zhixing.gateway.GatewayApplication` | 8080 | 统一入口 |
| 5 | zx-aigc | `com.zhixing.aigc.AigcApplication` | 8089 | AI 助教（可选） |

**操作**：左侧找到对应 `XxxApplication` → 右键 → **Run 'XxxApplication'** → 等待 `Started XxxApplication`。

> **为什么要先启动 user？** auth 登录时通过 Feign 调 user 校验密码，user 没启动登录会失败。

### 8.5 验证项目是否跑通

**① 登录（管理员）**

```bash
curl -X POST http://localhost:8080/accounts/admin/login \
  -H "Content-Type: application/json" \
  -d '{"cellPhone":"<账号>","password":"<密码>"}'
```

> 注意访问 **8080（网关）**，网关会自动转发给 auth。返回结果里复制 `accessToken`。

**② 携带 token 查询课程**

```bash
curl http://localhost:8080/courses/page -H "Authorization: Bearer <刚才的accessToken>"
```

**③ 测试 AI 助教（未配置大模型时返回模拟回复）**

```bash
curl -X POST http://localhost:8080/chat/text \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","question":"帮我推荐一门 Java 课程"}'
```

看到类似 `{"code":200,...,"data":"【知行智学智能助教】已收到你的问题..."}` 即成功。

**④ 查看接口文档（Knife4j）**

```
http://localhost:8081/doc.html
```

---

## 九、核心链路讲解：看懂关键代码

### 9.1 一次登录请求的完整流程

```
浏览器/客户端
   │  POST /accounts/admin/login  {cellPhone, password}
   ▼
网关 gateway (8080)
   │  ① AuthGlobalFilter 检查白名单 → /accounts/admin/login 放行
   │  ② 路由转发到 auth-service (8081)
   ▼
auth 服务 (8081)
   │  AccountController.login() → AccountService.login()
   │  ③ 通过 Feign 调用 user：UserClient.queryUserDetail(...)
   ▼
user 服务 (8082)
   │  UserService.queryUserDetail() → BCrypt 校验密码
   │  ④ 返回 UserDTO
   ▼
auth 服务
   │  ⑤ JwtTool.createAccessToken(userId) 签发 JWT
   │  ⑥ 写登录日志，返回 LoginResultVO（含 accessToken）
   ▼
客户端拿到 accessToken，后续请求携带 "Authorization: Bearer <token>"
```

**关键代码位置**：

| 环节 | 文件 |
|---|---|
| 网关鉴权 | `zx-gateway/.../filter/AuthGlobalFilter.java` |
| 登录接口 | `zx-auth/.../controller/AccountController.java` |
| 登录逻辑 | `zx-auth/.../service/AccountService.java` |
| Feign 客户端 | `zx-api/.../client/user/UserClient.java` |
| 密码校验 | `zx-user/.../service/UserService.java` |
| JWT 签发 | `zx-auth/.../common/util/JwtTool.java` |

### 9.2 网关如何鉴权（AuthGlobalFilter）

```java
// 1. 白名单直接放行（登录、刷新 token、注册、文档等）
if (isExcludePath(path)) {
    return chain.filter(exchange);
}
// 2. 解析 Authorization 头里的 token
String token = resolveToken(request);
// 3. 校验 token 是否有效，无效返回 401
if (token == null || !jwtUtils.isValid(token)) {
    return unauthorized(exchange);
}
// 4. 解析出 userId，通过 user-info 请求头透传给下游
Long userId = jwtUtils.parseUserId(token);
request.mutate().header("user-info", String.valueOf(userId)).build();
```

**要点**：网关统一鉴权，下游服务通过 `user-info` 头拿身份（`UserContext`），不再各自校验 token。

### 9.3 服务间如何通信（Feign）

`zx-api` 定义接口：

```java
@FeignClient(value = "user-service", contextId = "userClient")
public interface UserClient {
    @PostMapping("/users/detail/{isStaff}")
    UserDTO queryUserDetail(@RequestBody LoginFormDTO loginFormDTO,
                            @PathVariable("isStaff") boolean isStaff);
}
```

auth 注入 `UserClient` 即可像调用本地方法一样调用 user 服务。

> **本地直连说明**：本地开发默认关闭 Nacos，各服务在 `application.yml` 通过 `spring.cloud.discovery.client.simple.instances` 配置静态地址。生产启用 Nacos 后自动被注册中心取代。

### 9.4 AI 智能助教如何工作（多 Agent）

```
用户提问
   │
   ▼
RouteAgent（路由 Agent，关键词意图识别）
   ├── "推荐/有什么课"      → RecommendAgent（推荐课程）
   ├── "购买/多少钱"        → BuyAgent（购买咨询）
   ├── "是什么/原理/解释"   → KnowledgeAgent（知识问答）
   └── 其他                 → ConsultAgent（默认咨询）
   │
   ▼
LlmClient（调用 OpenAI 兼容接口）
   ├── 未配置 apiKey  → 返回模拟回复（保证本地可跑）
   └── 已配置 apiKey  → 真实流式对话（SSE）
   │
   ▼
ChatMemory（Redis 保存会话记忆，最多 20 条历史，TTL 7 天）
```

> 接入真实大模型：在 `zx-aigc/src/main/resources/application.yml` 配置 `zx.llm`（`base-url`、`api-key`、`model`、`enabled: true`）即可接入任意 OpenAI 兼容服务（如 DeepSeek、通义千问）。

### 9.5 三个通用设计（贯穿全项目）

1. **统一响应 `R<T>`**：所有接口返回 `{code, msg, data, requestId}`。`WrapperResponseBodyAdvice` 自动包装，`@NoWrapper` 跳过（SSE 流式、Feign 内部接口）。
2. **统一异常处理**：`CommonExceptionAdvice` 集中处理各类异常，统一转成 `R` 结构返回。
3. **请求链路追踪**：`requestId` 贯穿请求（MDC + Feign 拦截器透传），方便排查问题。

---

## 十、动手练习（由浅入深）

> 每个练习约 20-40 分钟，做完标记 ✓。这是面试时能讲"动手做过什么"的关键。

### 练习 1：把项目跑起来（★★★）
- [ ] 按第八章启动 5 个核心服务
- [ ] 用 curl 完成「登录 → 查课程 → AI 对话」三步
- [ ] 记录你遇到的第一个报错和解决过程

### 练习 2：读懂一次登录（★★★）
- [ ] 打开 `AccountService.login()`，逐行标注每一步作用
- [ ] 画出「网关→auth→user」的时序图
- [ ] 把 `UserClient` 的 Feign 注解含义写出来

### 练习 3：改一个接口（★★☆）
- [ ] 给课程分页接口 `CourseService.pageQuery` 增加一个 `teacherId` 筛选条件
- [ ] 重启 zx-course 验证效果
- [ ] 思考：如果要按价格区间筛选，怎么改？

### 练习 4：新增一个 Agent（★★☆）
- [ ] 阅读 `AbstractAgent` 与 `ConsultAgent`
- [ ] 新增一个 `HistoryAgent`（继承 AbstractAgent），复述"今天学了什么"
- [ ] 在 `RouteAgent.route()` 注册关键词"今天学了/复习"
- [ ] 写一个 `HistoryAgentTest` 单测并跑通

### 练习 5：给骨架模块加日志与校验（★★☆）
- [ ] 选一个骨架模块（如 zx-exam）
- [ ] 给新增/删除题目加日志与空值校验
- [ ] 写一个 `QuestionControllerTest` 验证新增/查询/删除

### 练习 6：理解流式与记忆（★★★）
- [ ] 阅读 `ChatService.streamChat`，说明为什么用 `Flux.defer` 保存记忆
- [ ] 不装 Redis 启动 aigc，验证对话仍可用（降级生效）

### 练习 7：模拟生产问题排查（★★★）
- [ ] 故意改错 MySQL 密码，复现启动失败，按第十一章 Q1 排查
- [ ] 用 `requestId` 串联一次跨服务调用的日志（auth→user）

---

## 十一、常见问题排查

### Q1：启动报 "Failed to configure a DataSource: 'url' attribute is not specified"

**原因**：数据库模块（auth/user/course）没连上 MySQL，或账号密码不对。
**解决**：检查 MySQL 是否启动，检查 `application.yml` 的 `username`/`password`（或用环境变量覆盖）。

### Q2：登录报 "用户名或密码错误"，但我用的是初始管理员账号

**原因**：`sql/init.sql` 没执行，或数据库没有初始账号。
**解决**：确认完整执行过 `sql/init.sql`。导入过旧版本脚本建议重跑（脚本已修复初始密码 BCrypt 值）。

### Q3：登录接口返回 502 或连接失败

**原因**：auth 通过 Feign 调 user，但 user 没启动。
**解决**：先启动 `zx-user`（8082），再启动 `zx-auth`。

### Q4：访问需要登录的接口返回 401 "未登录或登录已过期"

**原因**：没带 token，或 token 过期（默认 30 分钟）。
**解决**：重新登录获取新 token，请求头携带 `Authorization: Bearer <token>`。

### Q5：IDEA 里 Maven 依赖一直下载失败

**解决**：检查网络，或为 Maven 配置国内镜像（`~/.m2/settings.xml` 加阿里云镜像）；`File → Invalidate Caches` 清理重启；手动 `mvn clean install` 看具体报错。

### Q6：启动 aigc 时提示 Redis 连接失败

**说明**：AI 会话记忆依赖 Redis，但**不装 Redis 也能跑**（自动降级为无记忆）。需要记忆就启动本地 Redis（默认 `localhost:6379`）。

### Q7：端口被占用

**解决**：`netstat -ano | findstr 8080` 找到占用进程，`taskkill /F /PID <pid>` 结束；或改 `application.yml` 端口。

---

## 十二、学习路线与答疑

### 零基础 → 看懂项目，建议顺序：

1. **先跑起来**：按第八章把 5 个核心服务启动，curl 调通登录/查课程/AI 对话。
2. **理解单体部分**：只看 `zx-user`，弄懂 `Controller → Service → Mapper → 数据库`，这是最基础的 MVC。
3. **理解公共模块**：读 `zx-common` 的 `R`（统一响应）、`CommonExceptionAdvice`（统一异常）、`BasePO`（公共字段）。
4. **理解微服务通信**：读 `zx-api` 的 `UserClient`，理解 Feign 怎么让 auth 调 user。
5. **理解网关鉴权**：读 `zx-gateway` 的 `AuthGlobalFilter`，理解 token 怎么校验、怎么透传。
6. **理解 AI 多 Agent**：读 `zx-aigc` 的 `RouteAgent` 和 `LlmClient`，理解意图路由和 LLM 调用。
7. **深入中间件**：数据库分库、Redis 缓存、Redisson 分布式锁、JWT 签名验证。

### 建议配合学习的技术点（按优先级）：

1. Spring Boot 基础（IoC、AOP、自动配置）
2. MyBatis-Plus（BaseMapper、逻辑删除、分页插件）
3. Spring Cloud 核心（OpenFeign、Gateway、Nacos）
4. JWT 鉴权原理
5. Redis 基础
6. 大模型 API 调用（OpenAI 兼容协议）

### 三个常见疑问

**问：我要不要先学完 Spring Cloud 再来读这个项目？**
不要。先跑起来，边跑边学，带着"这段代码在干嘛"的问题去读，效率最高。

**问：骨架模块是不是"没做完"，会不会减分？**
不会。骨架是刻意设计的「契约先行」策略，README 和面试文档里都有合理解释。你可以在面试里主动讲清楚这个设计意图，反而是加分项。

**问：我要不要自己补全一个骨架模块？**
强烈建议。选一个模块（如 zx-exam）用 MyBatis-Plus 落库，这是最好的"把知识变成能力"的练习。

---

> 祝你学习顺利！遇到问题先查第十一章，再对照第九章的核心链路代码逐步定位。
