# 知行智学 ZhiXing Learn · 前后端全量测试计划与模块覆盖清单

> 测试日期：2026-09-15
> 被测版本：本地工作副本（`D:\1\zx-learn`）
> 测试执行方式：脚本化端到端（服务真实启动 + 真实数据库/Redis/MQ + 真实 HTTP 调用）

---

## 一、被测系统架构

| 层次 | 技术栈 |
|---|---|
| 前端 | Vue 3.5 + Vite 5 + TypeScript + Element Plus 2.9 + Tailwind 3 + Pinia + vue-router 4 |
| 网关 | Spring Cloud Gateway（8080，JWT 统一鉴权 + 全局 CORS + 秒杀限流） |
| 后端 | Java 21 + Spring Boot 3.3.5 + Spring Cloud 2023.0.3 + MyBatis-Plus 3.5.9（虚拟线程已启用） |
| 服务发现 | Nacos（本地关闭）→ 改用 `spring.cloud.discovery.client.simple.instances` 静态实例 |
| 数据库 | MySQL 8（`zx_auth`/`zx_user`/`zx_course`/`zx_learning`/`zx_trade`/`zx_promotion`/`zx_exam`…） |
| 缓存 | Redis 7（登录会话、下单锁、券限用计数、AI 会话记忆） |
| 消息 | RocketMQ（`orderPaid`/`couponUse`/`couponRefund` 本地消息表 + 死信补偿） |
| 向量库 | PostgreSQL + pgvector（RAG 知识切片） |
| 对象存储 | 阿里云 OSS（未配置时降级本地磁盘 `MEDIA_LOCAL_DIR`） |

**网关白名单（匿名可访问）**：`/accounts/login`、`/accounts/admin/login`、`/accounts/refresh`、
`/accounts/password/first-change`、`/jwks`、`/students/register`、`/teachers/register`、
`/v3/api-docs`、`/doc.html`、`/courses/page`、`/courses/{id:\d+}`、`/categorys/all`、
`/coupons/page`、`/order-details/enrollNum`、`/files/view/**`。

---

## 二、模块覆盖清单表

18 个 Maven 模块中 `zx-common`、`zx-api` 为公共库（无独立端口），其余 16 个为可运行服务。

| # | 模块 | 端口 | 角色定位 | 端点数 | 优先级 | 覆盖方式 | 结论 |
|---|---|---|---|---|---|---|---|
| 1 | zx-gateway | 8080 | 统一入口 / JWT 鉴权 / 路由 / 限流 / CORS | — | 核心 | 鉴权矩阵、白名单核验、路由命中 | 见主报告 |
| 2 | zx-auth | 8081 | 登录 / JWT / RBAC（角色·菜单·权限点） | 28 | 核心 | 全量矩阵 + 垂直越权专项 | **发现 P0 ×1** |
| 3 | zx-user | 8082 | 用户/学员/教师/员工，启停用、改密、注册 | 24 | 重要 | 全量矩阵 + IDOR + 脱敏 | 通过 |
| 4 | zx-course | 8083 | 课程工作台、分类、目录、讲义 | 27 | 核心 | 全量矩阵 + 注入 + 一致性 | 通过 |
| 5 | zx-exam | 8084 | 题库、判分、错题本、教师统计 | 20 | 重要 | 全量矩阵 + 角色校验 | 通过 |
| 6 | zx-media | 8085 | 图片上传/公开访问/媒资签名 | 9 | 一般 | 全量矩阵 + 上传校验 + 路径穿越 | 通过 |
| 7 | zx-learning | 8086 | 课表、学习记录、笔记、讨论、积分、签到 | 37 | 核心 | 全量矩阵 + IDOR + 一致性 | 通过 |
| 8 | zx-trade | 8087 | 下单、支付回调、订单管理、购物车、退款 | 33 | 核心 | 全量矩阵 + IDOR + 一致性 | 通过 |
| 9 | zx-promotion | 8088 | 优惠券、秒杀、领券核销 | 17 | 重要 | 全量矩阵 + 内部接口鉴权 | 通过 |
| 10 | zx-aigc | 8089 | AI 助教（SSE）、RAG 知识库、向量检索 | 20 | 重要 | 全量矩阵 + 越权会话 + 权限一致性 | **发现 P1/P2 各 ×1** |
| 11 | zx-pay | 8090 | 支付渠道 / 支付单 / 回调 | 5 | 一般 | 全量矩阵 | **发现 P2 ×1** |
| 12 | zx-search | 8091 | 课程门户搜索 / 推荐 / 兴趣 | 7 | 一般 | 全量矩阵 + 匿名可用性 | 通过 |
| 13 | zx-remark | 8092 | 点赞（幂等切换） | 2 | 一般 | 全量矩阵 + 幂等 | 通过 |
| 14 | zx-message | 8093 | 短信 / 站内信 | 3 | 一般 | 全量矩阵 + 角色校验 | 通过 |
| 15 | zx-data | 8094 | 运营看板 / 今日数据 / Top10 | 6 | 一般 | 全量矩阵 + 角色校验 | 通过 |
| 16 | zx-insight | 8095 | 学情画像 / 报告 / 教师视角（fail-open） | 8 | 重要 | 全量矩阵 + IDOR + 契约 | 通过 |
| 17 | zx-common | — | 公共库（异常处理/序列化/守卫/工具） | — | 核心 | 静态审计（`@RequireRole`/`OwnerAccessGuard`/`InternalOnlyGuard`） | 见主报告 |
| 18 | zx-api | — | Feign 契约 DTO | — | 一般 | 静态审计 | 通过 |

> 端点数由 `logs/tmp/scan_api.py` 扫描全部 `*Controller.java` 自动统计，合计 **246** 个，
> 完整清单见《接口覆盖清单表》：`docs/API-COVERAGE-MATRIX.md`。

---

## 三、测试策略与执行顺序

按「核心 > 重要 > 一般」分三批，全部通过 `scripts/verify-full-suite.sh` 的 11 个阶段串行执行，
保证前置状态（登录、夹具）可复用、失败可定位：

| 阶段 | 内容 | 目标 |
|---|---|---|
| 0 | 环境与构建产物 | 16 个 fat jar + 前端 dist 齐备，16 个端口全部可达 |
| 1 | 登录与鉴权基线 | 三种角色登录；匿名 401；篡改签名 / `alg=none` / 非 Bearer 前缀均 401 |
| 2 | 全量接口矩阵 | 246 端点 × 匿名/学员/管理员；判定"零 5xx、零契约破坏、零路由缺失" |
| 3 | 垂直越权 | 学员/教师调用管理端写接口必须 403；管理员对照组必须成功 |
| 4 | 水平越权 IDOR | 他人学情/积分/课表/答题/签到/订单/会话/账号密码必须不可读、不可改、不可删 |
| 5 | 内部接口外部不可达 | `InternalOnlyGuard` 保护的 Feign 端点经网关一律 403，匿名 401 |
| 6 | 参数校验 / 边界 / 注入 | SQL 注入、排序注入、超长串、通配符、类型错误、缺参、不存在资源 |
| 7 | 敏感数据脱敏 | 登录/用户/学员/教师/员工响应体与 JWT 载荷不得出现密码、盐、密钥 |
| 8 | 幂等与凭证安全 | 重复登录/注册/删除/查询、点赞切换、token 一次性假设 |
| 9 | 数据一致性 | 「已支付但课表缺失」「券状态漂移」「paid_key 重复」「死信残留」等 9 条不变量 |
| 10 | 性能 | 9 个核心接口各采样 20 次求 P95；200 次并发成功率 |
| F | 前端（独立脚本） | 类型检查、构建、路由/页面覆盖、状态单例、空错加载态、请求层降级、体积 |

**回归**：改动代码后须重跑既有的 6 个专项脚本
（`verify-admin-student-fixes` / `verify-modules-e2e` / `verify-course-ui-and-catalogue` /
`verify-lesson-consistency` / `verify-coupon-status-sync` / `verify-purchase-guard`），
本套件不替代它们，而是补齐"全量接口 × 安全 × 性能"这三块此前未系统覆盖的维度。

---

## 四、测试环境与数据准备

| 项 | 内容 |
|---|---|
| 操作系统 | Windows（Git Bash 沙箱执行） |
| 启动方式 | `scripts/dev-up-core.sh`（`EXTRA_SPECS` 追加 zx-media/promotion/aigc/pay/search/remark/message/data，共 16 服务） |
| 账号 | 学员 `13900000001/123456`、教师 `13900000002/123456`、管理员 `13800000001/123456` |
| 基线数据 | `sql/init.sql` → 5 个幂等迁移脚本 → `sql/test-data.sql`（13 门课、8 名学员、积分榜、讨论区、题库） |
| 测试夹具 | 阶段 3 的越权探针统一用 `ZZ_PROBE_*` 命名并在结尾清理；破坏性端点统一用哨兵 id `999999999999`；IDOR 会话夹具走 Redis 直写并 TTL 300s 自动过期 |
| 数据安全 | 全程不清空业务表、不删除真实数据；清理在服务关停前完成，避免本地消息表补偿复活数据 |

---

## 五、验收标准

1. **功能**：246 个端点全部可达，无 HTTP 5xx、无不可解析响应、无网关路由缺失。
2. **鉴权**：受保护接口匿名访问 100% 401；越权访问 100% 被拒（业务码 403）；白名单接口匿名可用。
3. **安全**：无垂直越权、无水平越权、无 SQL 注入、无密码字段外泄、无路径穿越。
4. **一致性**：9 条 SQL 不变量全部为 0/成立。
5. **性能**：核心接口 P95 < 500ms（学情报告放宽至 1500ms，含 LLM/DB 多级回退）；并发成功率 100%。
6. **前端**：`vue-tsc --noEmit` 零错误、`vite build` 成功、产物分包有效、最大分片 < 2MB。

---

## 六、已知限制（诚实声明）

- 沙箱内无法访问真实第三方：支付渠道（支付宝/微信）、OSS、短信平台、LLM 供应商均以骨架/降级路径验证，
  真实回调链路（含验签）无法端到端打通 —— 已作为问题记录并给出修复方案。
- 服务间 Feign 调用（无 `user-info` 头）的"内部放行"路径是在服务真实运行下由业务链路间接验证的
  （下单开课、券状态回写），未单独直连服务端口做绕过测试。
- 前端 UI 以静态/产物校验为主（无浏览器渲染环境），未做多分辨率视觉回归与真机交互录制。
