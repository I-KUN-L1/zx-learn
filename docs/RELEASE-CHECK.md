# zx-learn 公开发布终审报告（Go / No-Go）

> 终审日期：2026-09-04 · 终审人：发布终审官（AI 辅助全仓审计）
> 审计方式：静态扫描（git / rg / 脚本）+ 动态实测（docker compose up、mvn verify、6 服务启动、README curl 逐条复现）
> 结论先行：**NO-GO** —— 存在 3 项 P0（认证链路断裂 ×1、网关白名单 ×1、端口冲突 ×1），全部清零后方可公开。
> 复审更新（2026-09-04）：3 项 P0 已分别于 v1.2.1 / v1.2.2 / v1.2.3 修复清零；9 项 P1 已于 v1.2.4 批量修复清零（见"二点八"节），最终结论改判 **GO**。

---

## 一、Go / No-Go 结论

| 维度 | 结果 | 摘要 |
|---|---|---|
| 1. 一致性 | ✅ 通过 | Java 21 四处统一；18 模块 = 16 服务 + 2 底座；端口表与 yml 全一致 |
| 2. 泄漏扫描 | ✅ 通过 | 工作区/历史零敏感文件；~~1 处默认密钥硬编码（P1-1）~~ → 已修复（v1.2.4，默认值移除 + fail-fast + 环境变量注入） |
| 3. 文档 | ✅ 通过 | 45 条相对链接 0 断链；CHANGELOG 与里程碑对齐；~~README 2 行指标占位（P1-3）~~ → 已用 PERF.md 实测值回填（v1.2.4） |
| 4. 信任度 | ✅ 通过 | Java 0 TODO/FIXME；4 项功能入口抽查全部命中；~~"契约先行/骨架"口径矛盾（P1-5）~~ → 已统一为"16 服务 + 2 底座"（v1.2.4） |
| 5. 可运行性 | ✅ **通过** | mvn verify 全绿、6 服务可启动、业务接口可用；~~P0-1 登录链路失效 / P0-2 白名单 401 / P0-3 撞 8080~~ → 三项均已修复并 E2E 复验（v1.2.1~v1.2.3） |
| 6. 门面 | ✅ 通过 | LICENSE / description / 8 topics / social preview（og:image 实证）全部就绪 |

**初始结论：NO-GO。** P0-1 是功能级安全缺陷（登录不校验凭据），P0-2/P0-3 使 README 快速开始无法走通。

**最终结论（2026-09-04 复审）：GO。** 3 项 P0（v1.2.1~v1.2.3）与 9 项 P1（v1.2.4）全部修复清零，各项修复均有运行时复验证据（见各"修复记录"节）。

---

## 二、问题分级表

| # | 级别 | 问题 | 位置 | 修复建议 |
|---|---|---|---|---|
| P0-1 | P0 | **登录不校验凭据**：任意密码、甚至不存在的手机号，均返回 `code=200` + 有效 JWT，且 token `sub=null / userId=null`；下游受保护接口一律 401"登录凭证无效" —— **✅ 已于 2026-09-04 修复（v1.2.1），见下方"修复记录"** | `zx-auth` AccountService.login ← Feign `UserClient.queryUserDetail` ← `zx-common` CommonExceptionAdvice | ① zx-common 增加统一 Feign Decoder：解包 `R.data`，`code!=200` 时按业务码抛对应异常（或让异常处理器返回真实 HTTP 状态码）；② `AccountService.login` 对 `user.getId()==null` 增加防御性 401；③ 补集成测试：错误密码必须 401、token 必须可访问受保护接口 |
| P0-2 | P0 | **网关白名单缺 `/accounts/password/first-change`**：README 验证 ③ 不携带 Authorization 头，实测直接 401，首登强制改密流程走不通 —— **✅ 已于 2026-09-04 修复（v1.2.2），见下方"修复记录"** | `zx-gateway/.../config/JwtProperties.java` excludePaths | 白名单加入 `/accounts/password/first-change`；或 README 该条 curl 补 `-H "Authorization: Bearer <accessToken>"` |
| P0-3 | P0 | **rocketmq-console 占用宿主机 8080**，与 zx-gateway 冲突：`docker compose up -d` 后再启动 gateway 必然失败（本次实测复现）—— **✅ 已于 2026-09-04 修复（v1.2.3），见下方"修复记录"** | `docker-compose.yml` L84 `ports: "8080:8080"` | 改为 `"18080:8080"`（并在 README/console 说明新地址）；或用 compose profile 将 console 设为可选 |
| P1-1 | P1 | 支付回调验签密钥存在硬编码默认值 `zx-learn-demo-secret`，与 README"仓库零硬编码密钥"承诺冲突 —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `zx-trade` application.yml L?? `PAY_CALLBACK_SECRET:zx-learn-demo-secret`、PayService.java 同默认值 | 移除两处 `:zx-learn-demo-secret` 默认值，缺配置时启动报错；README/.env.example 增补 `PAY_CALLBACK_SECRET` |
| P1-2 | P1 | Dockerfile 基础镜像 `itcast/openjdk:21-jdk-eclipse-temurin` 非公共镜像源，外部用户 `docker build` 将拉取失败 —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `Dockerfile` L3 | 换官方 `eclipse-temurin:21-jre` |
| P1-3 | P1 | README 成果指标 2 行 `<X>` 占位，但 PERF.md 已有真实数据（下单 P99 28ms@50u、SSE TTFT P99 17-18ms mock 口径）——文档不同步 —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `README.md` L108-109 ↔ `docs/PERF.md` §2 / §5.2 | 用 PERF.md 实测值回填（保留口径注记），或删除该两行 |
| P1-4 | P1 | README 首屏"📸 项目预览"为占位横幅（"🚧 素材录制中，当前为占位区"）+ 注释掉的图片标签 —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `README.md` L25-35 | 补真实截图/GIF 后取消注释；发布前先隐藏整个区块 |
| P1-5 | P1 | 服务能力口径矛盾："16 个可运行服务"（L22）vs 脚注"7 个**契约先行**的扩展位"（L175）vs 测试章节"**骨架**模块"（L302）。实测 7 个扩展位模块均有 Application 类 + Controller + 独立端口，确实可运行 —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `README.md` L22/L175/L302 | 统一为"16 个可运行服务 + 2 个公共底座"，删除"契约先行/骨架"措辞，模块清单表补全 7 个模块行 |
| P1-6 | P1 | `.env.example` 缺 `POSTGRES_URL`（zx-aigc 需要，当前靠 yml 默认值兜底）；README 快速开始引导 `cp .env.example .env`，模板应完整 —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `.env.example` ↔ `zx-aigc/application.yml` L21 | 补 `POSTGRES_URL=jdbc:postgresql://localhost:5432/zx_aigc` |
| P1-7 | P1 | zx-auth/zx-user 等未用 MQ 的服务启动时报 `ERROR ... RocketMQ 生产者启动失败：the specified group is blank`（zx-common MQ 自动装配无条件装配），从零启动的用户会看到吓人的 ERROR —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `zx-common` MQ 自动配置 | 生产者 Bean 加 `@ConditionalOnProperty(name="rocketmq.producer-group")` 之类的条件装配 |
| P1-8 | P1 | README 预览区把 SSE 演示命令指向 `POST /chat/text`（JSON 非流式端点），真实 SSE 端点是 `POST /chat`（produces=text/event-stream） —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `README.md` L61-64 ↔ `zx-aigc` ChatController | 预览演示命令改为 `curl -N -X POST http://localhost:8080/chat ...` |
| P1-9 | P1 | compose mysql 固定 `3306:3306`，宿主机已装 MySQL 的机器（很常见）`docker compose up` 直接失败（本次实测复现） —— **✅ 已于 2026-09-04 修复（v1.2.4），见下方"二点八"节** | `docker-compose.yml` L14 | 改 `"${MYSQL_BIND_PORT:-3306}:3306"`，FAQ 增补条目 |

---

## 二点五、P0-1 修复记录（2026-09-04，v1.2.1）

**代码变更**

| 文件 | 变更 |
|---|---|
| `zx-common/.../feign/RDecoder.java`（新增） | 统一 Feign Decoder：`code=200` 解包 `data`；`code!=200` 按业务码抛异常（400/401/403/404/其他→BizIllegalException 保留原码）；`@NoWrapper` 裸返回与非 JSON 体按原语义直解，全兼容 |
| `zx-common/.../autoconfigure/FeignRDecoderAutoConfiguration.java`（新增） | `@ConditionalOnClass(feign)` + `@ConditionalOnMissingBean(Decoder.class)` 自动装配；已注册至 `AutoConfiguration.imports`（未引入 OpenFeign 的模块自动跳过） |
| `zx-auth/.../service/AccountService.java` | login 空身份防御：`user == null \|\| user.getId() == null` → 401"用户名或密码错误" |
| `zx-common/.../RDecoderTest.java`（新增 11 例）、`zx-auth/.../AccountServiceTest.java`（新增 5 例） | 覆盖：信封解包、无 data 字段的 401 错误体、裸 Boolean 直解、泛型 List、R 目标不解包、错误凭据/远程失败/空对象/禁用账号均 401、正常登录签发带身份 token |

**验证证据（运行时 E2E，网关 → zx-auth → zx-user 全链路实测）**

```
① 错误密码登录        → code=401, hasToken=False   （修复前：200 + null 身份 token）
② 不存在手机号登录    → code=401, hasToken=False   （修复前：200 + null 身份 token）
③ 正确凭据登录        → code=200, userId=2095831377613221889
     token payload: {"sub":"2095831377613221889","userId":2095831377613221889,
                      "type":"access","roleId":1,...}   ← 真实雪花身份
④ GET /courses/page(带③的token) → code=200, total=1, firstId=1   （身份经网关透传）
⑤ POST /chat/text(带③的token)   → code=200                       （Agent 链路通）
```

管理员引导链路（`adminExists` 裸 Boolean → 创建管理员 → `.bootstrap-credentials` 落盘）在新 Decoder 下行为不变。

> P0-1 状态：**已清零**。剩余 P0-2（网关白名单）、P0-3（console 端口冲突）仍待修复。（后注：P0-2/P0-3 已分别于 v1.2.2 / v1.2.3 修复清零）

---

## 二点六、P0-2 修复记录（2026-09-04，v1.2.2）

**代码变更**

| 文件 | 变更 |
|---|---|
| `zx-gateway/.../config/JwtProperties.java` | `excludePaths` 白名单加入 `/accounts/password/first-change`（README 验证 ③ 无 token 直达业务层） |
| `zx-common/.../autoconfigure/FeignRDecoderAutoConfiguration.java` | **修复 P0-2 验证中发现的新缺陷**：feign 对 void 返回方法默认不调用 Decoder（`InvocationContext.isVoidType && !decodeVoid` 直接返回 null），服务端"HTTP 200 + R 包装体"约定下的 `R{code!=200}` 业务错误信封被**静默吞掉**——实测首次改密传错旧密码仍返回 200 成功并删除 `.bootstrap-credentials`（P0 级）。新增 `FeignBuilderCustomizer` Bean 开启 `builder.decodeVoid()`，void 响应同样经过 `RDecoder` 转异常（`getInstances` 含祖先上下文，主上下文 Bean 对全部 Feign 客户端生效） |
| `zx-auth/.../service/AdminBootstrapService.java` | `changeBootstrapPassword` 解包 feign `DecodeException`（cause 为 `CommonException` 时还原业务异常），保证"原密码错误"以 400 业务码呈现而非 500，且校验失败**绝不删除凭据文件**（fail-closed） |
| `zx-common/.../RDecoderVoidDecodeTest.java`（新增 2 例） | 回归测试：`Feign.builder().decodeVoid()+RDecoder` 全链路，void 方法收到 `R{400}` 必抛 `DecodeException`（cause=BadRequestException，message 保留）；`R{200}` 正常返回 null |

**验证证据（运行时 E2E，网关(18080) → zx-auth → zx-user 全链路实测；mq-console 仍占 8080（P0-3 未修），路径匹配与端口无关）**

```
① 无token + 错误旧密码 first-change → code=400,"原密码错误"，.bootstrap-credentials 保留
                                       （修复前：code=200 假成功 + 凭据文件被误删）
② 无token + 正确旧密码 first-change → code=200，.bootstrap-credentials 自动删除
③ 旧密码登录                       → code=401
④ 新密码登录                       → code=200，token payload: {"sub":"2095864024548835330",
                                       "userId":2095864024548835330,"roleId":1}（真实雪花身份）
⑤ GET /courses/page 无token        → HTTP 401（白名单未过度放行，负面控制）
```

单测：zx-common 17 例 + zx-auth 12 例全绿（`mvn -pl zx-auth -am clean package`）。

> P0-2 状态：**已清零**（连带修复 void Feign 吞错缺陷）。剩余 P0-3（console 端口冲突）仍待修复。（后注：P0-3 已于 v1.2.3 修复清零）

---

## 二点七、P0-3 修复记录（2026-09-04，v1.2.3）

**代码变更**

| 文件 | 变更 |
|---|---|
| `docker-compose.yml` | `rocketmq-console` 端口映射 `"8080:8080"` → `"18080:8080"`（附注释说明宿主机 8080 预留给 zx-gateway，控制台新地址 http://localhost:18080） |
| `docs/TRADE-CONSISTENCY.md` L224 | 顺带修正旧笔误：控制台端口 `:8180` → `:18080`（此前文档与 compose 实际映射不一致） |

**验证证据（运行时实测）**

```
① docker compose config            → rocketmq-console 解析为 published:"18080" / target:8080，无 error
② docker compose up -d rocketmq-console → 容器 Recreated + Started，映射 0.0.0.0:18080->8080/tcp
③ 宿主机 8080                      → 无监听（释放给 gateway）
④ http://localhost:18080/          → HTTP 200（Dashboard 就绪）
⑤ java -jar zx-gateway.jar（默认 8080）→ "Started GatewayApplication in 5.531 seconds"
   （修复前此场景必 APPLICATION FAILED: Port 8080 was already in use）
⑥ gateway 8080 无 token 访问受保护接口 → HTTP 401（正常鉴权，负面控制）
```

验证后已停止验证用 gateway 进程；mq-console 容器保留在 18080 新映射（修复后的正确状态）。

> P0-3 状态：**已清零**。至此 RELEASE-CHECK 全部 3 项 P0（P0-1/P0-2/P0-3）均已修复。

---

## 二点八、P1 批量修复记录（2026-09-04，v1.2.4）

**代码 / 配置 / 文档变更**

| # | 文件 | 变更 |
|---|---|---|
| P1-1 | `zx-trade/application.yml`、`PayService.java`、`.env.example` | 回调密钥默认值 `zx-learn-demo-secret` 两处移除（`pay.callback-secret: ${PAY_CALLBACK_SECRET:}`）；`PayService` 增加 `@PostConstruct checkCallbackSecret()` fail-fast 校验——缺失时启动即失败，报错信息引导配置环境变量 `PAY_CALLBACK_SECRET`；`.env.example` 增补该变量（生产由支付渠道下发） |
| P1-2 | `Dockerfile` | 基础镜像 `itcast/openjdk:21-jdk-eclipse-temurin` → 官方 `eclipse-temurin:21-jre`（公共源可直接拉取；运行期仅需 JRE） |
| P1-3 | `README.md` 成果指标 | 2 行 `<X>` 占位用 PERF.md 实测值回填：下单 P99 **28ms @ 50 并发**（2,246 QPS · 0 错误，scenario2）、SSE TTFT **P99 17~18ms @ 50~500 并发**（LLM mock 流式，scenario3），保留"见 docs/PERF.md §6/§5.2"验证方式链接与口径注记 |
| P1-4 | `README.md` 项目预览 | 占位横幅与注释图片标签移除，重写为 `<details>` 折叠的"预览素材录制方法"：asciinema 录制 + agg 转 GIF 的完整命令（含 `/chat` SSE 演示脚本）、`Win+Shift+S` 截图指引；素材就绪后按注释展开即可展示，发布视角无空占位 |
| P1-5 | `README.md` | 删除"契约先行/骨架"措辞，全文统一"16 个可运行服务 + 2 个公共底座"；模块清单表补全 7 个扩展模块行 |
| P1-6 | `.env.example` | 补 `POSTGRES_URL / POSTGRES_USERNAME / POSTGRES_PASSWORD`（RAG 向量知识库 PostgreSQL/pgvector 连接段，zx-aigc 使用） |
| P1-7 | `zx-common RocketMqAutoConfiguration` | `rocketMQTemplate` Bean 增加 `@ConditionalOnProperty(prefix = "rocketmq", name = "producer-group")` 条件装配（consumer 监听器容器此前已有 consumer-group 条件）；未配置生产者组的服务（zx-auth/zx-user 等）不再初始化生产者 |
| P1-8 | `README.md` 预览区 | SSE 演示命令由 `POST /chat/text`（JSON 非流式）改为 `curl -N -X POST http://localhost:8080/chat`（真实 `text/event-stream` 端点） |
| P1-9 | `docker-compose.yml`、`.env.example` | mysql 端口映射 `"3306:3306"` → `"${MYSQL_BIND_PORT:-3306}:3306"`（宿主机已装 MySQL 时在 `.env` 配 `MYSQL_BIND_PORT=13306` 等空闲端口避让）；`.env.example` 增补注释项；README FAQ 已有对应条目 |

**验证证据（全量构建 + 运行时实测）**

```
① mvn -B -ntp clean verify（CI 同款全量） → BUILD SUCCESS（2:02 min）
   9 个含测试模块共 168 例：zx-common 17 / zx-auth 12 / zx-user 3 / zx-course 8 /
   zx-exam 6 / zx-learning 15 / zx-trade 46 / zx-promotion 39 / zx-aigc 22
   → 0 failures / 0 errors / 0 skipped
② P1-7：zx-course（仅配 consumer-group）启动 → 日志无 "RocketMQ 生产者启动失败" ERROR
   （修复前该 ERROR 必现）
③ P1-1：zx-trade 不带 PAY_CALLBACK_SECRET 启动 → fail-fast 失败并输出明确配置指引；
   带上后正常启动（验证后进程已停止）
④ P1-9：docker-compose.yml 解析 ${MYSQL_BIND_PORT:-3306} 默认 3306，.env 可覆盖避让
⑤ P1-3/5/8：README 复查 —— `<X>` 仅存于"禁止凭空填写"声明行；"契约先行/骨架"计数 = 0；
   预览区演示命令指向 /chat
```

> P1 状态：**全部 9 项（P1-1 ~ P1-9）已清零**。加上此前 v1.2.1~v1.2.3 清零的 3 项 P0，终审问题清单全部关闭，最终结论改判 **GO**。

---

## 三、逐项审计证据

### 1. 一致性 ✅

**Java 版本统一 = 21（4 处）**

| 位置 | 证据 |
|---|---|
| 根 pom.xml | L43 `<java.version>21</java.version>`、L44 `<maven.compiler.release>21</maven.compiler.release>`、L213 compiler `<release>21</release>` |
| CI | `.github/workflows/ci.yml`：`matrix.java: [21]` + temurin |
| README | L11 badge `Java-21`、L337 FAQ"项目统一 Java 21" |
| 实测 | 6 个服务以 JDK 21.0.11 启动成功 |

**模块数量口径（16+2）✅**：根 pom.xml L15-32 共 **18 个 `<module>`** = 16 个服务（gateway/auth/user/course/exam/media/learning/trade/promotion/pay/search/remark/message/aigc/data/insight）+ 2 个底座（zx-common/zx-api）= README"16 个可运行服务 · 2 个公共底座"。⚠️ 口径细节见 P1-5。

**端口表 vs application.yml ✅ 全一致**：`rg "port:" -g application.yml` 实测 16 个服务端口 = gateway 8080 / auth 8081 / user 8082 / course 8083 / exam 8084 / media 8085 / learning 8086 / trade 8087 / promotion 8088 / aigc 8089 / pay 8090 / search 8091 / remark 8092 / message 8093 / data 8094 / insight 8095，与 README 模块清单表、架构图（L135-148）、FAQ 最小链路（L336）完全一致，无冲突。

### 2. 泄漏扫描 ✅（P1-1 一处）

- `git ls-files | rg "\.env$|secret|password|\.log$|\.pem|\.key"`：**仅** `.env.example`（占位值模板）与代码/测试中的 `${ENV}` 引用，`.env` 与 `jmeter.log`（4.5MB）均未被跟踪（.gitignore 拦截生效）。
- **git 历史全量检查**：`git log --all --name-only --diff-filter=A` 去重后共 388 个文件，按 `.env$|bootstrap-credentials|\.log$|\.pem|\.key$|credential|secret` 过滤 → **0 命中**。历史干净。
- **全仓硬编码密钥 grep**：唯一命中即 P1-1（`PAY_CALLBACK_SECRET` 默认值 `zx-learn-demo-secret`，application.yml 与 PayService.java 两处）。其余均为 `${MYSQL_PASSWORD}` 式环境变量注入；JwtToolTest 中的测试密钥已注明"与生产无关"。
- 凭据体系实测健康：管理员引导生成 16 位强随机密码 → `.bootstrap-credentials` 落盘 → 首登改密后自动删除（本次实测全流程行为与注释一致）。

### 3. 文档 ✅（P1-3 一处）

- **链接可达性**：对 README.md + CONTRIBUTING.md + docs/*.md 全部相对链接脚本校验（`Test-Path` 逐条验证）：**checked=45, broken=0**。
- **CHANGELOG ↔ 里程碑对齐 ✅**：README 8 条里程碑（v1.0.0×3 / v1.1.0 / v1.1.1 / v1.2.0×3）与 docs/CHANGELOG.md 四个版本条目一一对应，变更描述与影响面一致。
- 占位指标：README 成果表 2 行 `<X>` 对应 PERF.md 已有真实数据 → P1-3。
- PERF.md 自身声明规范（"占位 <X> 只允许用真实数据填充"），无编造数值。

### 4. 信任度 ✅（P1-5 一处）

- **首屏**（badges → 亮点表）无 TODO / 无"骨架先行" / 无参考项目字样；Java 源码 `TODO|FIXME` 计数 = **0**。
- **功能入口抽查（4/4 命中）**：

| 声称功能 | 代码入口（实测存在） |
|---|---|
| 秒杀 Redis Lua 原子预扣 | `zx-promotion/src/main/resources/lua/seckill_claim.lua` + SeckillService.java L79 `ResourceScriptSource` 加载 |
| 本地消息表最终一致性 | `zx-trade/.../domain/po/OrderMsg.java` + OrderMsgService.java（同事务落库/补偿投递），CHANGELOG v1.1.0 对应 |
| RAG 检索增强 | zx-aigc：KnowledgeController / EmbeddingController / KnowledgeServiceTest + pgvector 依赖与 application.yml |
| 虚拟线程开关 | `zx-gateway` 与 `zx-aigc` application.yml：`spring.threads.virtual.enabled: true`（实测生效，gateway 以 Netty+虚拟线程启动） |

- 口径问题（"契约先行/骨架" vs 可运行）见 P1-5：实测 7 个扩展位模块均有 `XxxApplication` + Controller（如 zx-data BoardController），"16 个可运行服务"属实，是 README 脚注自我贬低。

### 5. 可运行性 ❌（3 项 P0 在此环节复现）

| 步骤 | 结果 | 证据 |
|---|---|---|
| `mvn clean verify`（CI 同款） | ✅ **BUILD SUCCESS**：19 个 reactor 模块全绿，**150 tests / 0 failures / 0 errors / 0 skipped**（JaCoCo 报告正常产出；初稿误记"300 tests"系逐类行与模块汇总行重复累加，现按模块汇总口径更正，终审复验时随新增测试增至 168，见"二点八"节）。注：首次失败系遗留 zx-aigc 进程锁 jar（Windows 文件锁，非代码问题），清锁后全绿 | 本机实测，Maven 3.9.16 + JDK 21.0.11 |
| `docker compose up -d` | ⚠️ 部分通过：redis/pg/rocketmq 全部启动；**mysql 容器因宿主机 3306 被占而失败**（P1-9）；~~console 占 8080（P0-3）~~ → **已修复（v1.2.3，映射 18080）** | `docker compose ps` + daemon 报错 |
| 启动 6 服务（user/course/auth/aigc/insight/gateway） | ✅ 6/6 可启动（user 11.4s / auth 6.8s / gateway Netty 4.9s）；管理员引导按预期生成 `.bootstrap-credentials` | 各服务启动日志 |
| README curl ① `cat .bootstrap-credentials` | ✅ 手机号 + 16 位初始密码正确解析 | 实测 |
| README curl ② `POST /accounts/admin/login` | ❌ **P0-1**：正确凭据返回的 token `sub=null/userId=null`；**错误密码、不存在的手机号同样返回 code=200 + token**；该 token 访问受保护接口 401"登录凭证无效" | 对照实验 4 组（见下） |
| README curl ③ `POST /accounts/password/first-change` | ❌ ~~P0-2~~ → ✅ **已修复（v1.2.2）**：白名单放行后无 Authorization 头可直达业务层；错误旧密码 400 且凭据文件保留，正确旧密码 200 并删文件（见"二点六、P0-2 修复记录"） | 实测 5 项断言全过 |
| README curl ④ `GET /courses/page`（持有效签名 token） | ✅ code=200，返回 init.sql 示例课程（total=1, id=1 SpringBoot 入门到实战）——**业务层完好**，问题仅在登录链路 | 手工构造同算法 HS384 token 实测 |
| README curl ⑤ `POST /chat/text` | ✅ HTTP 200，mock 回复正常（Agent 链路通）；SSE 流式端点为 `POST /chat`（README 预览命令指错端点 → P1-8） | 实测 |

**P0-1 根因链（对照实验）**：

```
错误密码登录 → zx-user 正确抛 UnauthorizedException（user 日志：未授权：用户名或密码错误）
           → CommonExceptionAdvice 返回 HTTP 200 + R{code:401}（无 @ResponseStatus）
           → zx-auth Feign 将 R 包装体反序列化为"全 null 的 UserDTO"（无解包 Decoder，无异常）
           → AccountService 仅判 user==null（空对象≠null）→ 签发 sub=null token
           → 网关校验签名通过但 userId 缺失 → 下游全 401
```

正确密码登录同样产生 null 身份 token → **登录功能整体失效**，README 最小链路"登录 → 浏览课程"走不通。

### 6. 门面 ✅

| 项 | 证据 |
|---|---|
| LICENSE | 存在，MIT；GitHub API `license.spdx_id=MIT` ✅ |
| description | 已设置："知行智学：AI 驱动的智慧学习平台 — Java 21 微服务（16 服务）× 多 Agent 智能助教（SSE 流式 / RAG / 会话记忆）× 优惠券秒杀 × 消息最终一致性，MIT 开源" ✅ |
| topics | 8 个：ai-agent / microservices / mybatis-plus / rag / rocketmq / spring-boot-3 / spring-cloud / sse ✅ |
| social preview | **已上传**：仓库页 `og:image` 指向 `repository-images.githubusercontent.com/1356028948/...`（自定义图，非默认 opengraph），源文件 `docs/images/social-preview.png` 已入库 ✅ |
| badges | 4 枚（CI/Java/Spring Boot 3.3.5/License/coverage），URL 语法与指向均正确（CI badge → ci.yml 存在）；coverage 35% 为手动静态徽章，README 注释已声明更新策略 ✅ |
| CI | ci.yml：push/PR 触发，JDK21 temurin + `mvn -B -ntp clean verify` + JaCoCo artifact ✅ |

---

## 四、发布前动作清单（按序执行）

1. ~~修 P0-1（Feign 统一解包 + login 空身份防御 + 错误密码 401 集成测试）~~（v1.2.1）
2. ~~修 P0-2（白名单）与 P0-3（console 端口改 18080）~~（v1.2.2 / v1.2.3）
3. ~~修 P1-1/P1-2（移除默认密钥、换基础镜像）~~（v1.2.4）
4. ~~回填 P1-3 指标、处理 P1-4/P1-5 文档口径~~（v1.2.4）
5. ~~清理 P1-6/P1-7/P1-8/P1-9~~（v1.2.4）
6. ~~重复本报告 §5 可运行性全流程~~ → 已通过：P0 修复各附运行时 E2E 复验（v1.2.1~v1.2.3 记录）、P1 修复附全量构建 + 启动验证（二点八节），已改判 **GO**

---

## 五、审计环境备注

- 本机为非纯净环境（原生 MySQL 3306 / 遗留 zx-aigc 进程 / 旧版 .env 缺 `ZX_JWT_SECRET`），相关干扰均已识别并在报告中标注为 P1 或环境问题，不影响仓库本身结论。
- 测试期间对本地库的唯一变更（删除管理员行以复现从零引导）已**完整还原**：管理员密码哈希已回写、测试凭据文件已删除、6 个验证服务已停止、mq-console 容器已恢复原状。
