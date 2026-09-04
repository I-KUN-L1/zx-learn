# 知行智学（zx-learn）版本记录

> 记录项目开发过程中的关键里程碑、问题修复与优化，便于复盘与面试讲述。

---

## v1.2.4（2026-09-04）P1 批量修复：安全基线 / 文档口径 / 可移植性（RELEASE-CHECK P1×9 清零）

### 问题修复

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| P1-1 | 支付回调验签密钥硬编码默认值 `zx-learn-demo-secret`，与"仓库零硬编码密钥"承诺冲突 | `zx-trade` application.yml 与 PayService 均写死默认值 | 两处默认值移除（`pay.callback-secret: ${PAY_CALLBACK_SECRET:}`）；PayService `@PostConstruct` fail-fast 校验——缺失时启动即失败并提示配置 `PAY_CALLBACK_SECRET`；`.env.example` 增补该变量 |
| P1-2 | Dockerfile 基础镜像 `itcast/openjdk:21-jdk-eclipse-temurin` 非公共镜像源，外部用户 `docker build` 拉取失败 | 私有镜像 tag | 换官方 `eclipse-temurin:21-jre`（运行期仅需 JRE） |
| P1-3 | README 成果指标 2 行 `<X>` 占位，而 PERF.md 已有真实压测数据 | 文档不同步 | 用 PERF.md 实测值回填：下单 P99 **28ms @ 50 并发**（2,246 QPS · 0 错误）、SSE TTFT **P99 17~18ms @ 50~500 并发**（LLM mock 流式口径注记保留） |
| P1-4 | README"📸 项目预览"为占位横幅 + 注释掉的图片标签，发布视角空占位 | 素材未录制 | 重写为折叠式"预览素材录制方法"（asciinema + agg 录 SSE 终端动图 / 截图指引），移除空占位横幅，素材就绪后展开即展示 |
| P1-5 | 服务能力口径矛盾："16 个可运行服务" vs 脚注"7 个**契约先行**的扩展位" vs 测试章节"**骨架**模块" | 措辞自我贬低（实测 7 个扩展模块均可运行） | 全文统一"16 个可运行服务 + 2 个公共底座"，删除"契约先行/骨架"措辞，模块清单表补全 7 个扩展模块行 |
| P1-6 | `.env.example` 缺 `POSTGRES_URL`（zx-aigc / pgvector 需要），模板不完整 | 模板遗漏 | 补 `POSTGRES_URL / POSTGRES_USERNAME / POSTGRES_PASSWORD`（RAG 向量知识库连接段） |
| P1-7 | zx-auth/zx-user 等未用 MQ 的服务启动报 `ERROR ... RocketMQ 生产者启动失败：the specified group is blank` | zx-common MQ 自动装配**无条件**创建 RocketMQTemplate 生产者 | `rocketMQTemplate` Bean 加 `@ConditionalOnProperty(prefix = "rocketmq", name = "producer-group")` 条件装配（consumer 监听器容器已有 consumer-group 条件），未配置生产者组的服务不再初始化 |
| P1-8 | README 预览区把 SSE 演示命令指向 `POST /chat/text`（JSON 非流式端点） | 端点笔误 | 预览演示命令改为 `curl -N -X POST http://localhost:8080/chat`（真实 `text/event-stream` 端点） |
| P1-9 | compose mysql 固定 `3306:3306`，宿主机已装 MySQL 的机器 `docker compose up` 直接失败 | 端口写死 | 改 `"${MYSQL_BIND_PORT:-3306}:3306"`；`.env.example` 增补注释项；README FAQ 已有对应条目（`MYSQL_BIND_PORT=13306`） |

### 验证

- `mvn -B -ntp clean verify` 全量 19 个 reactor 模块 BUILD SUCCESS（0 failures / 0 errors）。
- P1-7：zx-course（仅配 consumer-group）启动日志**无** "RocketMQ 生产者启动失败" ERROR（修复前必现）。
- P1-1：zx-trade 不带 `PAY_CALLBACK_SECRET` 启动 → fail-fast 失败并输出明确提示；带上后正常启动。

> **影响面**：zx-common（MQ 条件装配）· zx-trade（回调密钥 fail-fast）· Dockerfile · docker-compose.yml · .env.example · README.md（指标回填 / 预览重写 / 口径统一 / SSE 端点）· docs/RELEASE-CHECK.md（P1×9 清零，结论改判 GO）

---

## v1.2.3（2026-09-04）mq-console 端口冲突修复（RELEASE-CHECK P0-3）

### 问题修复

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| P0-3 | **rocketmq-console 占用宿主机 8080**，与 zx-gateway 统一入口冲突：`docker compose up -d` 后再启动 gateway 必然 `APPLICATION FAILED: Port 8080 was already in use` | `docker-compose.yml` 中 `rocketmq-console` 端口映射写死 `"8080:8080"`（Dashboard 容器默认端口与网关端口撞车） | 端口映射改为 **`"18080:8080"`**，宿主机 8080 预留给 zx-gateway；compose 内注释说明新地址 `http://localhost:18080`；顺带修正 `docs/TRADE-CONSISTENCY.md` 中控制台端口旧笔误（`:8180` → `:18080`） |

### 验证

- `docker compose config` 解析为 `published:"18080" / target:8080`；容器 Recreated 后 `0.0.0.0:18080->8080/tcp`，`http://localhost:18080/` HTTP 200。
- 宿主机 8080 释放后实测 `java -jar zx-gateway.jar` → `Started GatewayApplication in 5.531 seconds`（修复前必失败），无 token 访问受保护接口 401（鉴权正常）。

> **影响面**：docker-compose（console 映射）· docs/TRADE-CONSISTENCY.md（端口笔误）· docs/RELEASE-CHECK.md（P0-3 清零，**3 项 P0 全部清零**）

---

## v1.2.2（2026-09-04）网关白名单与 void Feign 吞错修复（RELEASE-CHECK P0-2）

### 问题修复

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| P0-2 | **网关白名单缺 `/accounts/password/first-change`**：README 验证 ③ 首登强制改密请求无 Authorization 头，被网关 401 拦截 | `JwtProperties.excludePaths` 未收录该路径 | 白名单加入 `/accounts/password/first-change` |
| 新发现（P0 级） | **void Feign 方法业务错误信封被静默吞掉**：首次改密传错旧密码仍返回 200 成功，且 `.bootstrap-credentials` 凭据文件被误删（fail-open） | feign 对 void 返回方法默认不调用 Decoder（`InvocationContext` 中 `isVoidType && !decodeVoid` 直接返回 null）；本项目"HTTP 200 + R 包装体"约定下，`R{code!=200}` 只体现在报文里、HTTP 状态恒为 200，void 方法完全看不见 | ① `FeignRDecoderAutoConfiguration` 新增 `FeignBuilderCustomizer` 开启 `builder.decodeVoid()`，void 响应同样经过 `RDecoder`（业务码转异常），主上下文 Bean 经 `getInstances` 含祖先查找对全部 Feign 客户端生效 ② `AdminBootstrapService.changeBootstrapPassword` 解包 feign `DecodeException`（cause=`CommonException` 还原），"原密码错误"以 400 呈现，校验失败绝不删凭据文件（fail-closed） |

### 验证

- 单元测试：`RDecoderVoidDecodeTest`（2 例：void 方法收到 `R{400}` 必抛 `DecodeException` 且 cause/message 保留；`R{200}` 正常返回），zx-common 17 例 + zx-auth 12 例全绿。
- 运行时 E2E（网关 18080 → zx-auth → zx-user 全链路）：① 无 token + 错误旧密码 → 400"原密码错误"、凭据文件保留（修复前 200 假成功+误删）；② 无 token + 正确旧密码 → 200、凭据文件自动删除；③ 旧密码登录 401；④ 新密码登录 200 且 token 含真实雪花身份（sub/userId/roleId）；⑤ 保护接口无 token 仍 401（白名单未过度放行）。

> **影响面**：zx-gateway（白名单）· zx-common（decodeVoid 自动装配，受影响 void Feign 方法：`changeBootstrapPassword`/`deleteCartByIds`/`sendSms`）· zx-auth（改密解包 fail-closed）· docs/RELEASE-CHECK.md（P0-2 清零）

---

## v1.2.1（2026-09-04）认证链路修复（RELEASE-CHECK P0-1）

### 问题修复

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| P0-1 | **登录不校验凭据**：任意密码、甚至不存在的手机号均返回 200 + token，且 token `sub=null/userId=null`，下游受保护接口全 401 | 服务端 `CommonExceptionAdvice` 对业务异常返回 **HTTP 200 + R 包装体**；Feign 按声明类型（如 `UserDTO`）直接反序列化 R 信封，得到"全字段 null 的空对象"而非异常，`user == null` 校验形同虚设 | ① zx-common 新增 **`RDecoder`**（统一 Feign Decoder，`FeignRDecoderAutoConfiguration` 自动装配）：`code=200` 解包 `data`；`code!=200` 按业务码抛对应异常（400/401/403/404/其他）；非 R 信封（`@NoWrapper` 裸返回、非 JSON 体）按原语义直接反序列化，全兼容 ② `AccountService.login` 增加**空身份防御**：`user.getId() == null` 视为凭据错误抛 401，杜绝签发无身份 token |

### 验证

- 单元测试：`RDecoderTest`（11 例：信封解包 / 无 data 字段的 401 错误体 / 裸返回直解 / 泛型 List / R 目标不解包 / String 目标）+ `AccountServiceTest`（5 例：错误凭据 401 / 远程失败 401 / **空对象 401** / 禁用账号 401 / 正常登录签发带身份 token），zx-common + zx-auth 共 27 例全绿。
- 运行时 E2E（网关 → zx-auth → zx-user 全链路）：错误密码 401 无 token；不存在手机号 401；正确凭据 200 且 token 含 `sub/userId/roleId` 真实身份（雪花 ID）；携带该 token 访问 `/courses/page` 与 `/chat/text` 均 200。

> **影响面**：zx-common（RDecoder + 自动装配）· zx-auth（login 防御）· 全部依赖 zx-api Feign 客户端的服务（解码行为由"静默错数据"变为"显式异常"）· docs/RELEASE-CHECK.md（P0-1 清零）

---

## v1.2.0（2026-09）RAG 检索增强与评域补全

### 里程碑（日期 / 变更 / 影响面）

| 里程碑 | 日期 | 变更 | 影响面 |
|---|---|---|---|
| RAG 检索增强 | 2026-09 | 向量存储由 Milvus 改为 **pgvector**（PostgreSQL 容器 + HNSW 索引）；知识上传按 **500-token 滑窗分片**（50 重叠）；KnowledgeAgent 检索 **Top3 相似片段**作答、超范围礼貌拒答；SSE 支持 **Last-Event-ID 断线重连 + 心跳保活**；`Flux.defer` + `AtomicInteger` 连接级限流（`zx.rag.max-concurrent-streams`，默认 2000） | zx-aigc / docker-compose（新增 postgres+pgvector）/ sql/init.sql / zx-aigc Web 栈整改（排除 servlet 容器，统一 Netty） |
| 学情分析 | 2026-09 | zx-insight **学情报告 / 能力画像 / 学习路径推荐**落地，补全"评"域闭环 | zx-insight / zx-api（InsightClient 契约） |
| 凭据外部化与安全引导 | 2026-09 | 全部凭据（数据库/Redis 密码、JWT 密钥、LLM Key）**环境变量注入**（`.env.example` 模板），仓库零硬编码；**首个管理员安全生成**：强密码生成器 + `.bootstrap-credentials` 文件交付 + 首登强制改密，移除 `init.sql` 硬编码 INSERT | zx-auth / zx-user / sql/init.sql / 部署安全基线 |
| 交易三模块持久化 | 2026-09 | zx-learning / zx-trade / zx-promotion 由内存存储升级为 **MyBatis-Plus + MySQL 分库**落地，含业务校验（订单金额一致性 / 券与订单状态机 / 学习进度不可回退）与单元测试（promotion 20 / trade 17 / learning 12） | zx-learning / zx-trade / zx-promotion / sql/init.sql / docs/DATABASE.md |
| Java 21 统一 | 2026-09 | 全模块 `maven.compiler.release=21`，CI 切换 temurin 21，文档同步 | 全模块 / CI / docs |

---

## v1.1.1（2026-09）优惠券秒杀链路

### 里程碑

- 优惠券秒杀生产级链路：Redis Lua 脚本单次原子完成**限领判重 + 余量判断 + 扣减 + 记录用户**（`seckill_claim.lua`），
  未命中（售罄/重复/未预热）直接拒绝，零 DB 访问、零 MQ 投递。
- 预扣成功 → RocketMQ 异步落库（`SK+雪花` 券码 + 领取记录）→ 入口立即返回「排队中」，前端轮询结果接口（Redis 结果键优先、DB 兜底）。
- 消费端双层幂等：`consume_record` 消费流水表 + `uk_user_coupon` 唯一索引；券不存在属确定性失败，写 `FAILED` 结果不重投。
- 对账补偿：`SeckillReconcileJob` 定时比对 Redis users set 与 DB 领取记录差集自动补发（MQ 停机重启可自愈）；
  `POST /coupons/seckill/reconcile/{couponId}` 手动补偿。
- 网关防刷：`RequestRateLimiter`（Redis 令牌桶）+ `SeckillKeyResolver`（IP+用户 组合 key），仅拦截 POST 领取接口。
- 压测基建：`scenario4-seckill-claim.jmx`（阶梯 50/100/200/500）+ `docs/PERF.md` 秒杀章节模板；链路设计见 `docs/SECKILL.md`（含 3 张 Mermaid 时序图）。
- DDL 增量：`user_coupon.coupon_code`（`uk_coupon_code`）+ `zx_promotion.consume_record`（见 `sql/init.sql`）。

> **影响面**：zx-promotion（秒杀/对账）· zx-gateway（限流路由与 KeyResolver）· zx-common（MQ 复用）· docker-compose（broker JVM 参数）· sql/init.sql（DDL 增量）· perf-test（scenario4）

### 问题修复

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| 1 | MQ 消费位点恒为 0、秒杀消息永不被消费（发送正常） | 容器内 JDK8 在 cgroup v2（Docker Desktop/WSL2）下 `OperatingSystemMXBean` 初始化 NPE → broker 端 `StoreUtil.<clinit>` 失败 → 所有消费位点查询/拉取抛 `NoClassDefFoundError` | docker-compose 为 broker 注入 `JAVA_OPT_EXT=-XX:-UseContainerSupport` 绕过 cgroup 指标，重建容器 |
| 2 | 压测 500 请求全部静默 NOT_READY | JMeter 未传 `-JCOUPON_ID`，脚本默认请求券 1（未预热） | run-perf.ps1 增加 `-CouponId` 参数透传 |

---

## v1.1.0（2026-09）交易链路最终一致性

### 里程碑

- 搭建 RocketMQ 基建（docker-compose NameServer + Broker + Dashboard），生产者/消费者容器/序列化统一沉淀 `zx-common`，`MqTopics` 统一 topic 命名规范。
- zx-trade 下单链路落地**本地消息表**最终一致性：订单与待发送消息同事务落库，定时扫描补偿投递、指数退避重试、超限死信人工处理。
- 新增 `zx_course_quota` 名额状态机（LOCK 锁定 → CONFIRM 转销量 / RELEASE 释放），条件更新防超卖；消费端双层幂等（业务唯一键 + `consume_record` 消费流水表）。
- 支付回调：HMAC 验签 + `trade_pay_record.pay_no` 流水幂等 + 发布 `zx_order_paid` 事件，zx-learning 消费自动开课。
- 超时关单双保险：RocketMQ 延迟消息（15 分钟）+ `OrderTimeoutJob` 定时兜底，关单补偿回滚券与名额。
- 新增 `docs/TRADE-CONSISTENCY.md`（方案取舍 / 三种故障自愈路径 / Mermaid 时序图）与 `sql/reconcile.sql` 八项对账脚本。

> **影响面**：zx-trade（订单/支付/超时）· zx-learning（自动开课消费）· zx-course（名额状态机）· zx-common（MQ 基建沉淀）· docker-compose（RocketMQ 全家桶）· sql（reconcile 对账）

### 关键取舍

- **不引入 Seata**：AT 模式全局锁是秒杀吞吐瓶颈、TCC 三接口改造成本高、TC 需独立运维；
  本地消息表零额外中间件、消息与业务同事务天然可靠，MQ 故障时下单主链路零影响（投递退化为后台补偿）。

---

## v1.0.0（2026-08）

### 里程碑

- 搭建 Spring Cloud Alibaba 微服务骨架，17 个 Maven 模块统一管理。
- 完整实现核心链路：网关鉴权（JWT）→ 登录（auth + user）→ 课程（course）→ AI 助教（aigc）。
- 提供 `zx-common` 统一响应 / 统一异常 / 链路追踪 / 分页等公共能力。
- 10 个业务模块（exam/media/learning/trade/promotion/pay/search/remark/message/data）完成接口契约与骨架实现。
- 完善文档体系：README、架构设计、接口参考、数据库设计、部署指南、零基础学习指南、面试题库。

> **影响面**：全部 Maven 模块（父 POM 统一管理）· zx-common / zx-api 公共底座 · zx-gateway / zx-auth / zx-user / zx-course / zx-aigc 核心链路 · docs 初版全套

### 问题修复（关键复盘点）

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| 1 | 项目无法编译 | `JwtTool` 缺少 `JwtConstants` import | 补全 import |
| 2 | 初始账号无法登录 | `sql/init.sql` 中密码 BCrypt 值与文档承诺的密码不一致 | 重新生成正确的 BCrypt 密文 |
| 3 | Feign 服务启动失败 | 未引入 loadbalancer，本地直连配置无效 | 引入 `spring-cloud-starter-loadbalancer` + `SimpleDiscoveryClient` 静态地址 |
| 4 | 骨架服务无法启动 | `zx-common` 强制传递 MyBatis-Plus，无数据库的服务缺 DataSource | 将 MyBatis-Plus 设为 optional，数据库模块显式引入 |
| 5 | "什么是微服务"误分到咨询 Agent | 意图路由只匹配"是什么"后置问法 | 补充"什么是""什么意思"等前置关键词 |
| 6 | 未装 Redis 时 AI 不可用 | `ChatMemory` 在 Redis 不可用时抛异常 | 降级为无记忆模式，保证对话主流程可用 |
| 7 | requestId 跨服务不生效 | `RequestIdRelayConfiguration` 未被扫描注册 | 通过 `AutoConfiguration.imports` 自动注册 |

### 技术要点

- Java 21 + Spring Boot 3.3.5 + Spring Cloud Alibaba 2023.0.3。
- 多 Agent 智能助教：RouteAgent 意图路由 + 4 个业务 Agent + Redis 会话记忆 + SSE 流式输出。
- 课程草稿-正式双表设计，编辑与发布解耦。
- RBAC 权限模型：role/menu/privilege 三表 + 三张关联表。
- 单元测试：覆盖统一响应、JWT 工具、意图路由、LLM 客户端等 4 个测试类。

---

> Roadmap 见 [ARCHITECTURE.md §7 演进路线](ARCHITECTURE.md)。
