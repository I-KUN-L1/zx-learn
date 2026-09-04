# 知行智学（zx-learn）面试题库与深度解答

> 用途：简历项目面试准备。覆盖**项目概述、技术实现、架构决策、权衡取舍、问题排查、未来演进、行为面**七大类。
> 建议：先自己作答，再对照参考答案；重点掌握「为什么这么设计」而不是「背代码」。

---

## 目录

- [一、项目概述类](#一项目概述类)
- [二、技术实现类](#二技术实现类)
- [三、架构决策与权衡类](#三架构决策与权衡类)
- [四、问题排查与踩坑类](#四问题排查与踩坑类)
- [五、可扩展与未来演进类](#五可扩展与未来演进类)
- [六、行为面试类](#六行为面试类)
- [七、一句话总结模板](#七一句话总结模板)

---

## 一、项目概述类

### Q1. 介绍一下你的项目？

**参考答案（30 秒电梯陈述）**：

> 这是一个 AI 驱动的在线教育微服务平台，基于 Java 21 + Spring Boot 3.3.5 + Spring Cloud Alibaba 构建，由 17 个 Maven 模块组成。业务覆盖课程、交易、营销、互动四大域，并自研了一个基于多 Agent 的 AI 智能助教。
>
> 我的核心贡献是三部分：一是搭建了公共能力底座 zx-common（统一响应、统一异常、链路追踪）；二是完整实现了认证与课程两个核心链路（JWT 网关鉴权 + Feign 服务调用 + 课程草稿-正式双表）；三是设计实现了 AI 多 Agent 助教（意图路由 + 4 个业务 Agent + Redis 会话记忆 + SSE 流式输出）。其余 10 个业务模块完成了接口契约与骨架实现，形成"骨架先行、逐步补全"的渐进式开发模式。

**加分点**：用「业务 + 技术 + 个人贡献」三段式，突出个人负责的部分而不是背模块清单。

### Q2. 这个项目最大的亮点是什么？

**参考答案**：
- **AI 智能助教**：多 Agent 意图路由 + SSE 流式 + Redis 会话记忆，展示了 LLM 工程化的完整链路；且设计了"未配置 API Key 也能本地跑"的降级机制，开发体验好。
- **微服务公共底座**：统一响应/异常/链路追踪抽到 zx-common，17 个模块复用，代码零重复。
- **草稿-正式双表**：把"编辑中"与"已发布"状态解耦，避免脏数据影响线上，是一个真实业务问题的建模。

### Q3. 项目里你最有成就感/最难的部分？

**参考答案**：
> 最有成就感的是 AI 多 Agent 助教。难点在于三件事：一是**意图路由的准确率**——早期关键词匹配把"什么是微服务"误判为咨询而非知识问答，后来补充了"什么是/什么意思"等前置问法才解决；二是**流式输出与会话记忆的一致性**——必须在流式结束时把完整答案写回 Redis，用 `Flux.defer` 保证记忆在流结束后保存；三是**健壮性**——Redis 不可用、API Key 未配置都要优雅降级，不能让 AI 功能整体不可用。

### Q4. 为什么做微服务而不是单体？

**参考答案**：
> 这是为学习与工程化考量结合的决策。业务上学习平台天然分域（课程/交易/支付/营销），各自独立演进与部署；技术上想完整实践 Spring Cloud 生态（注册中心、网关、Feign、配置中心）。
>
> **坦诚权衡**：如果业务规模很小，单体 + 模块化（Modular Monolith）更合适——少一套分布式复杂度（网络、事务、部署）。本项目微服务化的代价是引入了跨服务调用与最终一致性复杂度，这也是我刻意想展示的能力。面试时我会主动说出这个权衡，体现不是"为了微服务而微服务"。

---

## 二、技术实现类

### Q5. JWT 鉴权是怎么实现的？为什么用网关统一校验？

**参考答案**：
> 登录成功后 auth 服务用 `JwtTool` 签发 **accessToken（30 分钟）+ refreshToken（30 天）**，返回给客户端。后续请求携带 `Authorization: Bearer <token>`。
>
> 网关的 `AuthGlobalFilter`（GlobalFilter，order=-100）做统一校验：白名单路径直接放行；解析并校验 token 有效性，无效返回 401；校验通过后解析出 userId，写入 `user-info` 请求头透传给下游。
>
> **为什么在网关做**：下游服务无需各自解析 token，只需从 `user-info` 头拿 userId 写入 `UserContext`（ThreadLocal），职责单一、无重复代码，也便于统一升级鉴权策略。
>
> **双 Token 设计**：accessToken 短效降低泄露风险，refreshToken 长效用于无感续期，避免频繁重新登录。

### Q6. JWT 的失效问题怎么处理？（重点追问）

**参考答案**：
> 这是 JWT 的经典缺陷——无状态意味着**服务端无法主动失效**。我目前的方案是：
> 1. **短时效**：accessToken 30 分钟，缩短风险窗口；
> 2. **refreshToken 续期**：过期后走刷新接口拿新 token，而不是重新登录；
> 3. 生产可增强：加 Redis 黑名单/白名单（tokenId），或引入网关层校验，实现登出立即失效。
>
> 面试时主动承认"当前未做服务端主动失效"，并说明改进方案，比遮遮掩掩更专业。

### Q7. 统一响应 R<T> 是怎么实现的？有什么坑？

**参考答案**：
> 用 `ResponseBodyAdvice`（`WrapperResponseBodyAdvice`）在 Controller 返回值写出前统一包装为 `{code, msg, data, requestId}`。
>
> **坑与处理**：
> 1. **String 返回值**：String 会被视为已转换的响应体，直接包装会类型不匹配报错，所以 String 类型先 `objectMapper.writeValueAsString(R.ok(body))` 序列化再返回；
> 2. **SSE 流式与 Feign 内部接口**：加了 `@NoWrapper` 注解跳过包装，否则流式协议会被破坏；
> 3. **swagger/api-docs 路径**：在 `beforeBodyWrite` 里按路径排除，避免破坏文档接口。
>
> **统一异常**：`CommonExceptionAdvice` 用 `@RestControllerAdvice + @ExceptionHandler` 把各类业务异常转成统一 R 结构，业务代码只需 `throw new BadRequestException("...")`，不用到处 try-catch。

### Q8. requestId 链路追踪是怎么跨服务透传的？

**参考答案**：
> 请求进入每个服务时，`RequestIdInterceptor` 从请求头读取 `requestId`（没有则生成），写入 **MDC**，日志自动带上。下游透传通过 **Feign 请求拦截器**（`RequestIdRelayConfiguration`）把当前 MDC 中的 `requestId` 写进出站请求头。这样一次用户请求的所有服务日志可以通过 `requestId` 串联。
>
> 同时 `R.requestId` 会返回给前端，前端报错时直接把 `requestId` 给后端，就能精确检索到这条链路的所有日志。

### Q9. 服务间调用为什么用 Feign？介绍一下你项目里的 Feign 用法？

**参考答案**：
> OpenFeign 是声明式 HTTP 客户端，调用远程服务就像调用本地方法，代码可读性高。
>
> 项目里的实践：
> 1. 所有 Feign Client 集中在 `zx-api` 契约层，`@FeignClient(value="user-service", contextId="userClient")`；
> 2. 本地开发**关闭 Nacos**，用 `SimpleDiscoveryClient` 静态配置服务地址（`user-service → http://localhost:8082`），生产启用 Nacos 后自动切换为服务发现；
> 3. 通过 `spring-cloud-starter-loadbalancer` 提供负载均衡；
> 4. 配合 `RequestIdRelayConfiguration` 做链路透传。

### Q10. AI 多 Agent 是怎么设计的？

**参考答案**：
> 采用 **「路由 + 策略」模式**：
> - `RouteAgent` 基于关键词做意图识别，把问题分发给 4 个 Agent：Recommend（推荐）、Buy（购买）、Knowledge（知识）、Consult（默认咨询）；
> - 4 个 Agent 继承 `AbstractAgent`，复用 `LlmClient` 与上下文组装，新增意图只需加一个子类；
> - `LlmClient` 调用 OpenAI 兼容协议（可对接 DeepSeek/通义千问），支持流式与非流式；
> - `ChatMemory` 用 Redis 保存最近 20 条会话，TTL 7 天，让 AI 有"记忆"。
>
> **流式实现**：`Flux.concat(start → delta* → end)` 组合事件流；`ChatService.streamChat` 通过 `Flux.defer` 保证在流结束后才把完整答案写入记忆，避免"回答完了但记忆没存上"。

### Q11. 为什么用 SSE 而不是 WebSocket？流式输出怎么保证记忆完整？

**参考答案**：
> **SSE vs WebSocket**：AI 对话是**单向**（服务端推流）场景，SSE 基于 HTTP 天然支持、实现简单、自动重连，够用且好维护；WebSocket 适合双向高频交互（如实时协作）。这是按场景选型，不是越高级越好。
>
> **记忆完整性**：把"保存完整答案"放在 `Flux.defer(() -> { chatMemory.save(...); return Flux.just(end); })`，即**流结束、答案拼接完成之后**才写入 Redis，保证不会出现半截答案落库。

### Q12. 课程「草稿-正式」双表怎么设计的？为什么？

**参考答案**：
> 编辑中的课程存在 `course_draft`，上架（`upShelf`）时**校验完整性**（名称/分类/价格/教师）后同步到正式表 `course`，并 `publishTimes+1`。
>
> **为什么**：直接改正式表有两个问题——① 编辑到一半的脏数据会暴露给用户；② 上架是"一次性原子动作"，需要统一校验。双表把「编辑态」与「发布态」隔离，编辑自由、发布受控，这是典型的状态机建模。

### Q13. RBAC 权限模型怎么实现的？

**参考答案**：
> 三张主表 `role / menu / privilege` + 三张关联表 `account_role / role_menu / role_privilege`。
> - 菜单权限：控制前端可见的菜单/路由；
> - 权限（privilege）：绑定 `method + uri`，控制 API 访问；
> - 登录时加载用户角色，接口层校验是否有对应路径权限。

### Q14. Redis 在项目里用在哪里？

**参考答案**：
> 1. **AI 会话记忆**（ChatMemory，List 结构存 JSON，TTL 7 天，上限 20 条）；
> 2. **数据看板**（zx-data，Hash 结构存运营指标）；
> 3. 完整模块中的**缓存**（auth/user/course 引入 spring-data-redis 与 Redisson，为分布式锁与缓存预留）；
> 4. Redisson 用于分布式场景（如优惠券防超发，骨架预留）。
>
> 骨架模块里 Redis 不可用时做了**降级**（AI 变无记忆、看板返回默认值），保证主流程可用。

### Q15. 数据库分库是怎么划分的？为什么？

**参考答案**：
> **按服务分库（Database per Service）**：zx_auth / zx_user / zx_course 各一个库。
>
> **为什么**：微服务要求服务间不能直接共享表，分库从物理上保证边界；每个服务拥有数据主权，可独立扩展。
>
> **代价**：跨服务查询只能走 Feign 聚合（如登录时 auth 调 user 校验），不能 join。这是微服务的典型权衡。

---

## 三、架构决策与权衡类

### Q16. 17 个模块的依赖是怎么设计的？如何避免循环依赖？

**参考答案**：
> 分四层：
> 1. `zx-common`：公共底座（R/异常/工具/分页），**不依赖任何业务模块**；
> 2. `zx-api`：依赖 common，只放 Feign Client/DTO，作为服务间契约；
> 3. 业务模块：依赖 common + api；
> 4. `zx-gateway`：纯 WebFlux 网关，**刻意不依赖 common/api**，保持轻量。
>
> **防循环依赖的手段**：① 依赖方向单一（上层依赖下层）；② 跨服务数据通过 zx-api 的 DTO 传递而不是互相引内部类；③ Maven 多模块天然在编译期暴露循环依赖。

### Q17. 骨架模块为什么用内存存储？这样设计合理吗？

**参考答案**：
> 坦诚说：这是**渐进式开发策略**——先把 17 个模块的**接口契约与启动能力**全部打通，形成可编译、可启动、可演示的完整平台，再逐个用 DB/Redis 替换内存实现。
>
> **合理性**：① 契约先行，前端与 Feign 调用方可以先联调；② 降低搭建成本，快速验证整体架构；③ 每个骨架内部用 `ConcurrentHashMap`，接口语义与真实实现一致，替换成本低。
>
> **演进方向**：learning/trade/promotion 等已在 [DATABASE.md](DATABASE.md) 给出推荐表结构，可按优先级替换。

### Q18. 为什么选择 Spring Cloud Alibaba 而不是纯 Spring Cloud Netflix？

**参考答案**：
> Spring Cloud Netflix 的 Eureka/Ribbon/Hystrix 已进入维护状态；Spring Cloud Alibaba 的 Nacos（注册+配置一体）、Sentinel（限流熔断）、Seata（分布式事务）在国内生态成熟、文档完善、与主流云环境集成好。本项目用 Nacos 做服务治理，Sentinel/Seata 作为演进路线（[Roadmap](ARCHITECTURE.md)）。

### Q19. 如何保证 AI 接口在未配置大模型时也可用？（降级策略）

**参考答案**：
> `LlmClient` 构造时读取 `zx.llm.enabled/api-key`，**未启用或 apiKey 为空**时：非流式直接返回模拟回复（mockReply），流式把模拟回复按固定长度切块模拟 SSE。这样：
> 1. 本地/CI 无密钥也能完整演示；2. 密钥配置错误不会让对话整体 500；3. `onErrorReturn` 兜底真实调用失败。
>
> 这是一套「**可插拔 + 优雅降级**」的集成模式，也方便后续替换模型供应商。

---

## 四、问题排查与踩坑类

### Q20. 讲一个你排查最久的问题（Bug）。

**参考答案（选一个最打动人）**：
> **问题**：本地启动 auth/aigc 依赖 Feign 的服务一直失败。
> **排查**：先看启动日志发现是 `LoadBalancer` 相关报错——OpenFeign 在 Spring Cloud 2023 默认走负载均衡，但项目没引入 loadbalancer 依赖；加了依赖后又发现**本地没注册中心**，服务名解析不到地址。
> **根因**：① 缺 `spring-cloud-starter-loadbalancer`；② 本地直连配置（静态地址）没生效。
> **解决**：引入 loadbalancer，并用 `SimpleDiscoveryClient` 的 `instances` 静态配置 `user-service → localhost:8082`。
> **沉淀**：这段经历直接催生了「本地直连 vs 生产 Nacos」的双模式配置方案，写进了部署文档。

### Q21. 遇到过哪些"看着没问题但编译/启动失败"的问题？

**参考答案**：
- **JWT 常量缺失 import** → 编译失败，规范 import 即解决；
- **初始管理员密码 BCrypt 值错误** → 文档承诺的密码与库里实际密文不一致，登录一直失败，重新生成正确的 BCrypt 密文；
- **MyBatis-Plus 强制传递导致骨架服务缺 DataSource 启动失败** → 把 zx-common 里的 mybatis-plus 设为 optional，由数据库模块显式引入；
- **意图路由误判** → "什么是微服务"被分到咨询，补充关键词解决。

### Q22. 单元测试覆盖了什么？为什么这样设计？

**参考答案**：
> 现有 4 个测试类：`RTest`（统一响应）、`JwtToolTest`（JWT 签发解析）、`RouteAgentTest`（意图路由）、`LlmClientTest`（LLM 客户端降级/解析）。选的都是**纯逻辑、无外部依赖**的类，保证测试稳定快速。控制器层（骨架）也补充了内存存储的单测（exam/promotion）。设计原则：优先测**有业务判断的分支**（路由、解析、鉴权），而非测框架本身。

---

## 五、可扩展与未来演进类

### Q23. 如果要把骨架模块换成数据库实现，你的思路？

**参考答案**：
> 以 learning 为例：① 在 `sql/init.sql` 建 `zx_learning` 库与 lesson 表；② 引入 mybatis-plus + 实体 + Mapper + Service；③ 控制器只依赖 Service 接口，内部从 `ConcurrentHashMap` 换成 Mapper 调用；④ 补充分页与校验。**接口层完全不用变**——这正是「契约先行」的收益。

### Q24. 如果要做真实大模型 RAG（检索增强），怎么改？

**参考答案**：
> 当前 `EmbeddingController` 是向量检索的可插拔入口，已预留。完整 RAG 方案：
> 1. 课程/文档切块 → embedding 模型向量化 → 存入向量库（Milvus/pgvector）；
> 2. 用户提问时先 embedding 相似度检索 Top-K 片段；
> 3. 把片段拼进 prompt（带引用），再交给 `LlmClient` 生成；
> 4. 记忆层复用现有 `ChatMemory`。
> 这样 AI 回答可以基于真实课程内容，而不是纯模型知识。

### Q25. 分布式事务怎么处理（如下单扣券）？

**参考答案**：
> 项目已预留 Seata（dependencyManagement 引入 `seata-spring-boot-starter`）。下单 → 扣优惠券 → 创建支付单属于跨服务写操作，方案：
> 1. 首选**最终一致性**：本地消息表 / 事务消息 + 对账补偿（支付回调幂等）；
> 2. Seata AT 模式适合强一致场景，但性能有损耗；
> 3. 关键接口（支付回调、下单）都要做**幂等设计**。
> 面试重点是讲清楚「为什么不用强一致 + 用什么保证最终一致」。

---

## 六、行为面试类

### Q26. 项目中遇到团队协作/分工问题怎么处理？

**参考答案**：
> （结合你的真实经历组织。参考结构：**背景**——多模块并行开发；**冲突**——公共模块改动影响所有服务；**行动**——在 zx-common 定义好稳定接口、变更前评估影响面、用契约测试保护；**结果**——改动可平滑合并。）

### Q27. 你如何保证代码质量？

**参考答案**：
> 1. **分层清晰**：Controller 只做参数接收与转发，业务在 Service，数据在 Mapper；
> 2. **统一异常**：业务校验抛语义化异常，不吞异常；
> 3. **统一响应/日志**：所有接口走 R 包装，关键操作打日志；
> 4. **单元测试**：核心逻辑（路由/解析/鉴权）有单测；
> 5. **文档同步**：改接口必须同步 API 文档。

### Q28. 你最近在学什么新技术？

**参考答案**：
> （按真实情况回答。示例：我在深入 Spring AI / RAG、以及可观测性三件套 Prometheus+Grafana+OTel，计划给项目加指标监控；也在看 Kubernetes 编排，把现有 Docker 部署升级为 K8s。）

---

## 七、一句话总结模板

> 「这是一个 **AI 驱动的在线教育微服务平台**，我用 Java 21 + Spring Cloud Alibaba 搭建了 17 模块的微服务架构，完整实现了网关鉴权、认证登录、课程管理与 **多 Agent 智能助教** 核心链路，并沉淀了统一响应/异常/链路追踪的公共底座；同时以『契约先行』的方式铺开 10 个业务模块，形成了可编译、可运行、可演示、可扩展的完整作品。」
