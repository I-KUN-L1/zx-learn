# 知行智学（zx-learn）版本记录

> 记录项目开发过程中的关键里程碑、问题修复与优化，便于复盘与面试讲述。

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
