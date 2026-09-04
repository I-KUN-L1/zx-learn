# 知行智学（zx-learn）版本记录

> 记录项目开发过程中的关键里程碑、问题修复与优化，便于复盘与面试讲述。

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
