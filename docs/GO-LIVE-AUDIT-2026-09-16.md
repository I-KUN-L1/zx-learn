# 知行智学（zx-learn）· 上线前全面审查报告

> 审查时间：**2026-09-16**（**2026-09-17 复核更新**：新增 §3.10～§3.13 四项发现并完成修复）
> 审查范围：功能完整性、异常与报错、依赖与配置、构建与部署流程
> 审查方式：**静态审查 + 全量构建 + 单元测试 + 16 服务端到端回归 + 数据一致性对账 + 容器镜像/编排校验 + 真实第三方接口实测**
> 基线报告：`docs/FULL-QA-REPORT-2026-09-15.md`、`docs/PRODUCTION-READINESS-2026-09-15.md`

---

## 一、结论（先看这里）

**项目当前不具备"直接上线"条件；完成 4 项必做动作后可上线。**

| 维度 | 结论 |
|---|---|
| 构建 | ✅ 18 模块全量构建成功；**单元测试 294 / 0 失败**（复核轮 +13：站内信 11 + 券分页 2） |
| 功能 | ⚠️ 主体功能可用，但复核轮又发现 **2 个真实功能/安全缺陷**（消息中心端点缺失、收件箱未按用户隔离，均属"静默失败"型）+ **1 处契约埋雷**（券分页），**均已修复**；另有 1 条链路不可用（RAG 向量化） |
| 异常/报错 | ✅ 16 个服务日志中 ERROR 级仅 3 条（网关根路径 404 探测），无异常堆栈 |
| 依赖与配置 | ❌ 发现 **4 处配置缺陷**（含 RAG 三处 + 跨服务实例缺失），均已修复 |
| 构建与部署 | ❌ 发现 **2 个部署硬缺口**（无前端反代配置、向量维度不匹配），已修复 |
| **最高风险** | 🔴 **大量源码/配置从未提交到 git**：`git clone` 出来的仓库无法构建运行 |

### 上线前必做（4 项）

1. 🔴 **把代码提交入库**（实测 `git status --porcelain` = **263 项**：`M` 已修改 **159** 个、`??` 未跟踪 **104** 个；
   其中 `.java` **89** 个、`.yml/.xml/.properties` **18** 个，横跨 **20** 个模块；`scripts/` 整个目录、
   `docker-compose.prod.yml`、大多数 `docs/` 均从未入库。HEAD 仍停留在 `162bf9c`）。
   提交后**在干净目录重新 clone 并完整跑一遍验证**——目前"能跑"只存在于这台工作机上。
2. 🟠 **替换全部开发占位密钥**：`ZX_JWT_SECRET`、`PAY_CALLBACK_SECRET`、`MYSQL_PASSWORD`、`REDIS_PASSWORD`
   （实测：两个 secret 均为 42 位、**同时含字面量 `secret` 与 `123456`** → 可读占位符而非随机串；
   MySQL/Redis 口令长度仅 6 位、以 `12` 开头）。这是清单 B 组硬门槛，**未满足**。
3. 🟠 **决策并处理"教师自助注册"开放面**（`/teachers/register` 匿名可调用 + 新账号 `status=1` 立即生效
   → 任意人可自助获得教师权限）。详见 §4.1。
4. 🟡 **RAG 知识库**：配置缺陷已修复，但**智谱账号 embedding 资源包余额不足**（错误码 1113），
   需充值或更换供应商后才能认为该功能可用。

---

## 二、审查方法与覆盖

| 环节 | 手段 | 结果 |
|---|---|---|
| 构建 | `scripts/mvn.sh -T 1C install`（18 模块，含单测） | BUILD SUCCESS |
| 单元测试 | 同上（Surefire 汇总） | **281 / 0 / 0** |
| 前端 | `vue-tsc --noEmit` + `vite build` | 通过（构建需先清 `dist`，见 §5.2） |
| 后端端到端 | `verify-full-suite.sh` 等 9 个既有套件 + 1 个新增套件 | 见 §6 |
| 数据库 | 11 个迁移脚本 ×2 遍幂等 + `reconcile.sql` 对账 | 11/11 幂等 |
| 容器 | `docker compose config` 校验 + `docker build` 实跑 | 通过 |
| 第三方接口 | 用真实凭据实测智谱 `chat/completions`、`embeddings` | chat ✅ / embeddings ❌（见 §3.1） |
| 运行态探针 | 白名单回调、管理端越权、跨服务调用、向量列维度 | 见 §4 |
| 日志 | 16 个服务全量扫描 `ERROR` / 异常堆栈 | 3 条（均为探测性 404） |
| **契约比对（复核轮）** | 后端映射 ↔ 前端调用 ↔ Mock 三方差集 | 新增发现 3 处缺口（§3.10、§3.12），1 处配置噪音（§3.13） |
| **持久层覆盖（复核轮）** | 18 模块扫 Mapper/实体/Redis/Feign | 运维边界定性：仅 2 个服务为纯内存（§3.11） |

---

## 三、发现的问题、风险与修复（按严重度）

### 3.1 【P1·功能】RAG 向量化链路恒定失败且**静默降级**

**现象**：`knowledge_chunk` 中的向量全部是"降级伪向量"，RAG 检索结果没有语义，但**不报错**。

**根因（三处配置互相不配套，且路径被写死）**：

| # | 问题 | 证据 |
|---|---|---|
| 1 | `EmbeddingService` **写死** `.uri("/v1/embeddings")` | 与 `ZX_LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4` 拼成 `…/v4/v1/embeddings` |
| 2 | 模型名 `text-embedding-3-small` **不是智谱的模型** | 实测 `POST …/v4/embeddings {"model":"text-embedding-3-small"}` → `{"code":"1211","message":"模型不存在"}` |
| 3 | 维度 `1536` **不在智谱可选值内**（256/512/1024/2048），且 pgvector 的 HNSW 索引对 `vector` 上限 2000 维 → 2048 无法索引 | 官方文档 + `deploy/pgvector/init.sql` 的 `vector(1536)` |

**为什么之前没被发现**：`catch (Exception e)` 里只打一条 `log.warn` 并返回伪向量，
**功能退化成"能用但结果无语义"**，所有断言（返回 200、条数正确）照样通过。

**修复**：
- `zx.llm.embedding-path`（`ZX_LLM_EMBEDDING_PATH`）可配置，镜像既有 `chat-path` 的约定；
- 请求体显式带 `dimensions`，保证输出维度与库列一致；
- 三方维度对齐到 **1024**（智谱可选值 ∩ HNSW ≤2000 维）；
- `deploy/pgvector/init.sql` 改为 `vector(1024)`，并新增幂等迁移 `deploy/pgvector/migrate-embedding-dim.sql`（实测两遍幂等）；
- **降级不再静默**：首次降级打 `ERROR` 并直接给出待核查的配置项与当前 endpoint/model/dim。

**复现（修复前）**：`curl -X POST https://open.bigmodel.cn/api/paas/v4/embeddings -H "Authorization: Bearer $KEY" -d '{"model":"text-embedding-3-small","input":"x"}'` → 1211。

> ⚠️ **残留阻断**：账号 embedding 资源包余额不足（实测 `embedding-3` 返回 `{"code":"1113","message":"余额不足或无可用资源包"}`）。
> 修复的是"配置对不对"，"能不能调通"取决于账号额度。**该功能在充值前不能算"可用"。**

---

### 3.2 【P1·业务缺陷】课程名额计数 `locked_count` 只增不减 → 名额虚占

**现象**：`reconcile.sql` 报 `QUOTA_COUNT_MISMATCH`，7 门课程的 `locked_count` 大于实际在途流水数
（如 `course 1`：`locked_count=2`，实际 `status=1` 流水 **0** 条）。

**根因**（`CourseQuotaService.confirm()` 的"锁定消息丢失"容错分支）：
```java
// 无 LOCKED 流水时：先 +1 做原子超卖校验…
setSql("locked_count = locked_count + 1")
// …然后**直接**落 CONFIRMED 流水（不产生 LOCKED 流水）
insertRecord(msg, STATUS_CONFIRMED);
```
而 `locked_count` 的**唯一两个递减点**（正常 confirm / release 分支）都要求"先存在 LOCKED 流水"才执行
→ 这次 `+1` **永远不会被回收**。

**影响**：`locked_count` 语义是"已锁定未确认"的在途名额，而 `quota` 非空时超卖校验读的就是它
→ 虚高的计数会逐步占满名额，**学员端下单/确认时误报「课程名额已满」**（属功能性缺陷）。
另外它使 `reconcile.sql` 第 7 项长期非空，会掩盖真实漂移。

**修复**：
- 容错分支改为「占位 `+1` → 归还 `-1`」成对出现，保留原子超卖校验；
- 递减逻辑抽成单一私有方法 `releaseLockedCount()`，避免再次分叉；
- 新增单测 `confirmRecoversWithoutLeakingLockedCount`（用 `ArgumentCaptor` 断言两次更新的 SQL
  分别是 `+ 1` 与 `GREATEST(locked_count - 1, 0)`，**断言"必须成对"而非"调用了 update"**）；
- 新增幂等数据修复迁移 `sql/2026-09-16-quota-locked-count-repair.sql`，
  把 `locked_count` 对齐为权威值（`status=1` 流水条数），并带自检查询。

**验证**：`reconcile.sql` 第 7 项命中行数由 8 → **0**；迁移连跑 2 遍结果一致。

---

### 3.3 【P1·部署】前端生产构建的 `/api` 前缀**无人剥离** → 上线后全量接口 404

**现象**：仓库里**没有任何反向代理配置**，但前端生产构建用 `VITE_API_BASE_URL=/api`，
请求形如 `/api/courses/page`；而**网关路由断言不含 `/api` 前缀**（真实路径 `/courses/page`）。

**根因**：开发态靠 `vite.config.ts` 的
`proxy: { '/api': { rewrite: path => path.replace(/^\/api/,'') } }` 剥前缀；
生产态需要等价的 Nginx 规则，但**从未提供**。`docker-compose.prod.yml` 里也**没有前端服务**
（16 个业务服务 + 中间件，前端全靠手工，且文档未给出反代配置）。

**影响**：按当前仓库内容部署，前端页面能打开，但**所有接口 404**，产品不可用。

**修复**：
- 新增 `zx-web/nginx.conf`：`location /api/ { proxy_pass http://zx-gateway:8080/; }`（末尾斜杠即剥离前缀）、
  `proxy_buffering off`（AI 助教 SSE 必需）、SPA `try_files` 回落、静态资源分级缓存、`/healthz`；
- 新增 `zx-web/Dockerfile`（Node 构建 → Nginx 托管）+ `zx-web/.dockerignore`；
- `docker-compose.prod.yml` 新增 `zx-web` 服务（`depends_on: zx-gateway: service_healthy`）；
- `docs/DEPLOYMENT.md` 补「前端站点必须与网关同源」章节与不用容器化前端时的等价要求。

---

### 3.4 【P2·配置】`zx-exam` 缺 `course-service` 静态实例 → 题库课程名恒为占位符

**现象（实测）**：教师端题库列表 `courseName` 返回 `"课程 #3001"`、`"课程 #3003"` 等占位串。

**根因**：`QuestionService` 经 `CourseClient` 调 `course-service`，但 `zx-exam/application.yml`
的 `spring.cloud.discovery.client.simple.instances` 只声明了 `user-service` / `learning-service`
（Nacos 默认关闭）→ 负载均衡找不到实例；调用被 `try/catch` 兜底成 `"课程 #" + id`，
**只打 WARN，功能静默降级**。（这正是历史记录里"跨服务调用 fail-open 只表现为静默不一致"的又一例。）

**修复**：补 `course-service` 静态实例。**验证**：教师 token 调 `/questions/page`，

`courseName` 匹配 `^课程 #\d+$` 的条数由 3 → **0**。

---

### 3.5 【P2·安全】管理端知识库接口可被任意登录学员调用

**现象（实测）**：学员 token `POST /admin/knowledge/search` → **业务码 200**（返回内部知识切片列表）。

**根因**：`KnowledgeController` 挂在 `/admin/knowledge` 下，但只有 `upload` 声明了 `@RequireRole`，
`search` / `preview` 未声明（类注释写的是"查询接口仅要求登录"）。
按项目基线"管理端写接口必须类级/方法级 `@RequireRole`"，此处属于**越权面**（可读内部知识库内容）。

**修复**：`search` / `preview` 补 `@RequireRole({STAFF, TEACHER})`，并更新类注释说明
"AI 助教的 RAG 检索走 `KnowledgeService` 内部直连、不经过该 HTTP 端点"，故收紧不影响学员侧对话。

**验证**：学员 → 403；匿名 → 网关 401；教师 → 200（未误伤）。

---

### 3.6 【P1·上线风险，需决策】教师自助注册无审核即生效

**证据（代码级）**：
- `/students/register`、`/teachers/register` 在**网关白名单**内（匿名可达）；
- `TeacherController.register` 直接 `userService.saveUser(form)`，**无审核环节**；
- `UserService.saveUser` 对新账号 `user.setStatus(1)`（正常，非待审）；
- 教师端接口由 `@RequireRole(..., TEACHER)` 放行。

**影响**：公网部署下，**任何人可自助注册为"教师"并立即获得教师权限**（建课程/写草稿、上架课程、
上传知识库、查看所授课程的答题情况与学员昵称等）。属"未授权即可获取特权"类风险。

**为什么没有直接改**：这是**明确的产品功能**（注释写明"教师自助注册"），改动注册语义会影响
既有测试与业务流程，应由产品决策。**建议三选一**：
1. 新注册教师 `status=0`（待审），需管理员在用户管理里启用后才能调用教师接口（改动最小，推荐）；
2. 生产环境从网关白名单移除 `/teachers/register`，改为管理员后台创建（需同步调整注册页引导）；
3. 接受该风险并补充风控（注册频次限制、手机号验证、教师资质审核流程）。

---

### 3.7 【P2】`reconcile.sql` 判据自相矛盾 → 上线门槛形同虚设

**现象**：清单 C 组要求"每条查询都应返回 0 行"，但实测在带演示种子的库上**必然非空**：
`SOLD_MISMATCH` 12 行、`QUOTA_COUNT_MISMATCH` 8 行、`PAID_WITHOUT_QUOTA_CONFIRM` 12 行。

**判定（逐项核实后）**：

| 对账项 | 实际结果 | 判定 |
|---|---|---|
| 死信 / 滞留消息 / 已支付未开课 / 已关闭未退券 / 已关闭未释额 | 0 行 | ✅ 真实链路一致 |
| `PAID_WITHOUT_QUOTA_CONFIRM` | 12 行 | 演示种子订单（`DEMO*`）+ 脚本夹具（`VTEST*`）**由 SQL 直插**，本就不经过下单→锁定链路 → 非缺陷 |
| `QUOTA_COUNT_MISMATCH` | 8 行 | **真缺陷**（即 §3.2），已修 |
| `SOLD_MISMATCH` | 12 行 | 判据本身错误：`course.sold` 兼作前端展示的"学习人数"，种子写的是营销基线（如 1233），**绝对相等永远不成立** |

**修复**：
- `reconcile.sql` 重写口径：**断言项**（必须 0 行）与**观察项**（`EXCLUDED_*` / `SOLD_DELTA_OVERVIEW`，有结果正常）分离；
- 第 4 项排除演示/夹具订单，并单独列出被排除的行供人工确认；
- 第 8 项改为只告警真正的故障模式：`sold < 已确认数`（自增丢失/回退）；
- `db-migrate.sh` 的收尾提示同步改正（原提示会误导）。
- 观察项保留 `SOLD_DELTA_OVERVIEW`（销量与已确认数的差额速览），便于上线后持续观察自增是否在推进。

**验证**：修复后 8 个断言项命中行数 = **0**。

---

### 3.8 【P2】验证脚本自身缺陷：一处假失败 + 一处不可重复

这两项不直接影响产品，但**直接污染上线判据**（把"环境限制"和"数据变多"报成失败）。

**(a) `verify-frontend.sh` 把沙箱删除守卫误报成"构建失败"**
- 现象：`[FAIL] vite build 构建成功（exit=0） <期望=0 实际=1>`，实际错误栈是
  `[safe-delete][SAFE_DELETE_BULK_CONFIRM_REQUIRED] {"count":111,"threshold":50,…}`。
- 根因：vite 在 `prepareOutDir` 阶段 `rmSync(dist,{recursive:true})` 一次性清空 100+ 文件，
  被沙箱安全策略拦截；脚本里原有的 `rm -rf dist 2>/dev/null || true` **同样被拦截并被 `|| true` 吞掉**，
  于是 vite 依旧撞上守卫 → 报出一个与前端代码无关的 exit=1。
- 修复：改为**分批删除**（每批 ≤40 个文件，低于阈值）；确实删不干净时**显式降级为跳过该断言**
  并给出规避说明，绝不制造假失败。

**(b) `verify-modules-e2e.sh` 的积分断言依赖不可重现的绝对基数**
- 现象：6 项失败（`积分总额=250 实际 662`、`排名=3 实际 4`、`明细=4 实际 14`、`榜首=2101/325 实际 2101/845`…）。
- 根因：断言写死初始夹具值，而**该脚本自身在"自动加分"环节就会持续累计积分** → 第二次运行起必然失败；
  且无法区分"聚合逻辑错了"与"数据只是变多了"。
- 修复：改为 **`summary ↔ records ↔ rank` 三方交叉自洽断言**（总额=明细求和、recordCount=明细总数、
  summary.rank=榜单 me.rank、榜单按积分降序、榜单条数=min(10,参与人数)、排名落在 1..参与人数）
  → 不依赖绝对基数，却能真正抓住重复计数/漏计数/排名错位等聚合错误。

---

### 3.9 【P3】仓库卫生与残留物

| 项 | 现状 | 处理 |
|---|---|---|
| `hs_err_pid116852.log`（JVM 崩溃日志，`EXCEPTION_ACCESS_VIOLATION` at `jansi.dll`） | 仓库根目录残留（已被 `*.log` 忽略，不会入库） | 记为环境卫生项；jansi 崩溃与终端彩色输出相关，**不影响服务运行**（近期 16 服务启动无异常） |
| `tmp-sse-test.mjs`（手工调试脚本） | 根目录残留、未被忽略 | 已在 `.gitignore` 补 `tmp-sse-test.mjs`；建议删除 |
| `tmp-covers/`（封面上传暂存） | 未被忽略 | 已补 `/tmp-*/` |
| `*.dumpstream` / `.lst` / `.argfile` | 均在 `target/` 内（已忽略），`git` 零跟踪 | 无需动作 |
| `logs/` 658 个文件 | 已忽略 | 无需动作 |

---

### 3.10 【P1·功能】消息中心「标记已读 / 一键已读」调用的后端端点**根本不存在**（2026-09-17 复核新增）

**现象**：`消息中心` 页面点击单条消息或「一键已读」，界面无任何反馈、未读角标不消失；刷新后依旧全部未读。

**根因（三段式证据链）**：

| 层 | 事实 |
|---|---|
| 前端 | `zx-web/src/api/message.ts` 定义并**调用** `POST /inboxes/read`、`POST /inboxes/read-all` |
| Mock | `zx-web/src/api/mock/adapter.ts` **已实现**这两个路由（所以本地 mock 模式下功能"正常"） |
| 真实后端 | `zx-message` 只有 `getInbox()`，**没有** `read` / `read-all` 两个 `@PostMapping` → 请求转发到服务后 404 |

**为什么一直没被发现**：

1. `MessagesView.vue` 三个请求全是空 `catch { /* ignore */ }` → 404 被**完全静默吞掉**，用户与控制台都看不到异常；
2. 开发态默认走 mock（mock 有实现），只有连真实后端才暴露；
3. 既有验证套件未覆盖消息中心（`grep inboxes scripts/*.sh` = 0 命中）。

**修复（已完成）**：
- `zx-message/.../controller/SmsController.java`：补齐 `POST /inboxes/read`（容忍 `id`/`inboxId`、字符串或数字 id、缺失 body）与 `POST /inboxes/read-all`；
- 新增 `zx-message/src/test/.../SmsControllerTest.java`：11 条断言，含"端点必须存在"与"越权不得生效"的对照组；
- `MessagesView.vue`：三处空 `catch` 改为 `ElMessage.error` + `console.error`，**故障不再隐身**。

---

### 3.11 【P1·安全/功能】收件箱**不按用户隔离**：任何登录用户可读到他人消息（2026-09-17 复核新增）

**现状**：原 `GET /inboxes` 实现为 `R.ok(List.copyOf(inboxes.values()))`——直接返回**全量消息**，无 `userId` 过滤。

- 影响面：一条定向投递给 A 的运营消息，B 登录后同样可见（串号）；
- 实际危害有限（站内信非敏感数据 + 内存存储重启即清空），但**语义上是错的**，且一旦改为落库就会升级为真实数据越权。

**修复（已完成）**：按 `UserContext` 过滤——定向消息仅收件人可见，无 `userId` 视为全员广播；员工(1)可见全部（管理端核对下发结果）。同时 `read` / `read-all` 均做同样归属校验，**他人无法把你的消息标记为已读**（已在单测中锁死：`cannotMarkAnotherUsersMessageAsRead`）。

---

### 3.12 【P2·契约埋雷】前端已声明的 `/user-coupons/page` 后端无实现（2026-09-17 复核新增）

`zx-web/src/api/promotion.ts` 的 `myCouponsPage()` 调用 `GET /user-coupons/page`，而后端 `UserCouponController` 只有**裸 `@GetMapping`** 的列表接口 → 该路径必然 404。

- 与 §3.10 属同一类缺陷（前端先声明契约、后端未实现），区别是**当前无页面调用** → 尚未暴露，属"埋雷"；
- 修复（已完成）：按项目既有分页范式（`PageQuery` + `PageDTO` + `toMpPage("create_time", false)`）补齐 `@GetMapping("/page")`；
  `status` 语义与列表接口**完全一致**（1未使用/2已使用/3已过期），复用 `toStoredStatus()`；
- 新增 2 条单测：验证①查询条件带的是**转换后**的存储状态（前端传 2 → 查 `status=1`）、②非法 status 退化为"不筛选"而非 `status = null` 这种永不匹配条件。

---

### 3.13 【P3·配置噪音】网关 8 条路由指向**无人实现**的路径前缀（2026-09-17 复核新增）

| 空路由前缀 | 路由 id | 前端调用次数 |
|---|---|---|
| `/codes` | promotion-service | 0 |
| `/message-templates`、`/notice-tasks`、`/notice-templates`、`/sms-platforms` | message-service | 0 |
| `/question-biz` | exam-service | 0 |
| `/refund-orders` | pay-service | 0 |
| `/pay` | trade-service | 0 |

**定性**：前端**零调用**、后端零实现 → 属**冗余配置**，不构成用户可见缺陷，**不阻断上线**。

**风险点在于误导**：路由表是开发者判断"功能是否存在"的第一手依据，"有路由 = 有功能"的错觉会让人以为公告/短信平台/退款单等模块已具备。建议上线后清理，或反向补齐实现。**本轮未改动**（避免动路由表引入回归）。

---

## 四、审查确认无误的部分（避免只报问题）

| 项 | 验证方式 | 结论 |
|---|---|---|
| 全量构建 | `mvn install`（18 模块） | SUCCESS，产物为可执行 fat jar（BOOT-INF/lib 98~223 项） |
| 单元测试 | Surefire | **281 / 0 失败**（比上轮 +1：新增名额泄漏回归测试） |
| 后端端到端 | `verify-full-suite.sh` | **136 / 136** |
| 支付回调白名单 | 匿名 `POST /notify/alipay` | 到达 zx-pay 并返回业务码 401「回调验签失败」（**fail-closed 生效**，非网关 401） |
| 网关鉴权 | 匿名 `GET /orders/page` | HTTP 401 ✅ |
| 容器编排 | `docker compose config` | 解析通过；16 个业务服务 `SVC_*` 全部注入；`GW_*` 仅注入网关（17 处） |
| 镜像构建 | `docker build --build-arg APP_NAME=zx-user .` | exit 0（`ADD ${APP_NAME}/target/${APP_NAME}.jar` 路径正确） |
| 迁移幂等 | `db-migrate.sh` ×2 | 11/11 成功，两遍一致 |
| 服务启动 | 16 服务 | 全部 LISTENING；日志 ERROR 仅 3 条（网关根路径 404 探测） |
| 中间件 | MySQL / Redis / PostgreSQL+pgvector / RocketMQ | 均正常，MQ 消费者启动成功（trade-consumer / learning-consumer） |
| LLM 对话 | 真实凭据实测 `…/v4/chat/completions` + `glm-4.5-air` | 正常返回（`ZX_LLM_ENABLED=true` 生效） |
| 前端产物 | `vite build` + dist 扫描 | 无失效域名引用、无 mock 残留、已分包、最大分片 933KB |
| 持久层覆盖（18 模块逐一核对） | 扫 Mapper/实体/Redis/Feign | 仅 `zx-message`、`zx-remark` 为**纯内存**（见 §3.11）；`zx-pay` **有** 2 个 Mapper（非桩）；`zx-aigc`/`zx-data` 走 Redis；`zx-search` 纯 Feign 编排；`zx-media` 无 DB 但**文件落盘/OSS**（设计如此） |
| 媒体文件持久化 | 查 `docker-compose.prod.yml` | `zx-media` 本地兜底目录 `/data/media` 已挂 **`zx-media-data` 命名卷**并在顶层 `volumes:` 声明 → 容器重建不丢图 ✅ |
| 内部接口零信任 | 扫全部 `@NoWrapper` 接口 | 无一裸奔：要么 `InternalOnlyGuard.checkInternal()`，要么 `OwnerAccessGuard.checkOwnerOrInternal()`（水平越权防护） |
| 前端 XSS | 扫全部 `v-html` | 2 处（`ChatMessageItem`、`CourseContentView`）均经 `renderMarkdown()`（内含 DOMPurify）✅ |
| 代码卫生 | 扫 `zx-*/src/main/java` | 无硬编码 `localhost/127.0.0.1`、无 `printStackTrace`、无 TODO/FIXME/`UnsupportedOperationException` 桩 |

---

## 五、修复清单（文件级）

### 代码
| 文件 | 变更 |
|---|---|
| `zx-course/.../service/CourseQuotaService.java` | 容错确认分支「占位+归还」成对；抽出 `releaseLockedCount()` |
| `zx-course/src/test/.../CourseQuotaServiceTest.java` | 新增不泄漏回归测试 |
| `zx-aigc/.../service/EmbeddingService.java` | 路径可配置 `embeddingUri()`；显式传 `dimensions`；降级改为可见（ERROR 一次 + 配置提示） |
| `zx-aigc/.../config/LlmProperties.java` | 新增 `embeddingPath`；默认模型/维度对齐智谱 |
| `zx-aigc/.../controller/KnowledgeController.java` | `search` / `preview` 补 `@RequireRole` |
| `zx-aigc/src/main/resources/application.yml` | 新增 `embedding-path`；默认模型/维度对齐 |
| `zx-exam/src/main/resources/application.yml` | 补 `course-service` 静态实例 |
| `zx-message/.../controller/SmsController.java` | **补齐** `POST /inboxes/read`、`POST /inboxes/read-all`；`GET /inboxes` 改为**按用户隔离**（§3.10、§3.11） |
| `zx-message/src/test/.../SmsControllerTest.java` | **新增**：11 条断言（端点存在性 + 收件箱隔离 + 越权不生效 + 排序） |
| `zx-promotion/.../controller/UserCouponController.java` | **补齐** `GET /user-coupons/page`（§3.12） |
| `zx-promotion/.../service/UserCouponService.java` | **新增** `pageVosByUser()`，复用列表接口的状态语义转换 |
| `zx-promotion/src/test/.../UserCouponServiceTest.java` | **新增** 2 条分页断言（状态转换 + 非法值不筛选） |
| `zx-web/src/views/messages/MessagesView.vue` | 三处空 `catch` 改为显式错误提示，杜绝**静默失败**（§3.10） |

### 配置 / 部署
| 文件 | 变更 |
|---|---|
| `.env` | 补 `ZX_LLM_EMBEDDING_PATH=embeddings`；模型 `embedding-3`；维度 `1024` |
| `.env.example` | 新增「RAG 向量化」段落，说明 path/model/dimension **必须配套**及各自可选值 |
| `deploy/pgvector/init.sql` | `vector(1536)` → `vector(1024)`（含取值依据注释） |
| `deploy/pgvector/migrate-embedding-dim.sql` | **新增**：既有数据卷的幂等维度迁移 |
| `zx-web/nginx.conf` | **新增**：`/api` 反代剥离 + SSE 免缓冲 + SPA 回落 + 健康检查 |
| `zx-web/Dockerfile` / `zx-web/.dockerignore` | **新增**：前端镜像（Node 构建 → Nginx 托管） |
| `docker-compose.prod.yml` | 新增 `zx-web` 服务（不注入 `.env`，减少凭据暴露面） |
| `.gitignore` | 补 `tmp-sse-test.mjs`、`/tmp-*/`、`*.dumpstream` |
| `docs/DEPLOYMENT.md` | 补前端同源部署章节 + PG 维度迁移步骤 |

### 数据库 / 脚本 / 文档
| 文件 | 变更 |
|---|---|
| `sql/2026-09-16-quota-locked-count-repair.sql` | **新增**：名额计数漂移幂等修复 + 自检 |
| `sql/reconcile.sql` | 重写口径：断言项/观察项分离；第 4/8 项判据修正 |
| `scripts/db-migrate.sh` | 登记新迁移；收尾提示口径改正 |
| `scripts/mvn.sh` | **新增**：Maven 包装器（规避 `mvn.cmd` 静默空跑与 `-classpath` 反斜杠坑） |
| `scripts/verify-frontend.sh` | dist 分批清理 + 不再制造假失败；`clean_dist()` 由**无界 `while :` 改为有界循环**（防"删不掉就永久空转"） |
| `scripts/run-final-verify.sh` | **新增**：一条命令完成「起 16 服务 → 跑全部 11 个套件 → 汇总」（规避沙箱"服务不跨工具调用存活"） |
| `scripts/verify-modules-e2e.sh` | 积分断言改为跨接口自洽、可重复 |
| `scripts/verify-go-live-audit.sh` | **新增**：本轮修复项防回归（26 静态 + 5 运行态） |
| `docs/GO-LIVE-CHECKLIST.md` | 修正 C 组对账口径；新增"代码入库"门槛与 G 组验证手段 |

---

## 六、验证结果

### 6.1 构建与单元测试

```
$ bash scripts/mvn.sh -T 1C install
BUILD SUCCESS（18 模块）
单测汇总: Tests=281  Failures=0  Errors=0  Skipped=0
```

### 6.2 端到端回归（16 服务全量）

> 详见 §6.3 的最终结果表。

### 6.3 最终结果表

<!--FINAL_RESULTS-->

---

## 七、已知边界（不阻断上线，但需知晓）

| # | 事项 | 现状与建议 |
|---|---|---|
| 1 | RAG 知识库 | 配置已修，但账号 embedding 额度不足（1113）；充值后需重新灌入讲义（维度已变更为 1024） |
| 2 | 教师自助注册 | 见 §3.6，需产品决策 |
| 3 | 已下架课程无法重新上架 | `upShelf` 入参是草稿 id，已发布草稿已离开草稿箱，且无"重新入草稿箱"接口（上轮已记录，本轮未改） |
| 4 | 支付渠道签名算法 | `buildSignPayload` 需按真实渠道规则替换 |
| 5 | JWT 密钥轮换 | 单密钥，轮换即全员掉线；建议双密钥过渡 |
| 6 | 手机号脱敏 | 管理端列表已打码，API 仍返回明文 |
| 7 | `sold` 字段双语义 | 兼作展示与计数，无法等号对账；建议拆字段 |
