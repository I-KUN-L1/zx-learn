# 知行智学（zx-learn）演进路线 Roadmap

> 自 README「演进路线」拆分而来：README 只保留已完成里程碑，
> 未实现的契约占位与基础设施规划统一收纳于此，随进展回填状态。
> 口径：`✅ 完整` / `🔶 契约先行（扩展位）` / `⚪ 规划中`。

---

## 1. 扩展位服务落地（10 个骨架模块）

以下 10 个模块已完成**接口契约与启动能力**（内存存储、可编译可运行），
核心工作是替换/补全为**数据库持久化 + 完整业务校验 + 与核心服务联调**。
按业务闭环依赖排序，标注优先级、依赖与预估工作量（个人视角，供排期参考）。

| 优先级 | 模块 | 端口 | 依赖 | 落地内容 | 预估工作量 |
|---|---|---|---|---|---|
| 🔴 高 | zx-trade | 8087 | user、course、promotion、pay | 购物车 / 订单 / 退款落库；订单号雪花（已具备）；本地消息表 + 延迟关单 | 4~6 人日 |
| 🔴 高 | zx-promotion | 8088 | user、trade | 优惠券 / 兑换码落库；Redis Lua 原子预扣（脚本已备） | 3~5 人日 |
| 🔴 高 | zx-pay | 8090 | trade | 支付单 / 渠道回调落库；回调幂等 + 支付状态机 | 3~5 人日 |
| 🟠 中 | zx-learning | 8086 | user、course | 课表 / 学习记录 / 笔记 / 签到落库；学习进度非回归校验 | 4~6 人日 |
| 🟠 中 | zx-exam | 8084 | course、learning | 题库 / 考试 / 作答记录落库；判分逻辑 | 3~5 人日 |
| 🟠 中 | zx-media | 8085 | course | 媒资落库；视频上传签名（阿里云 OSS 依赖已引入） | 3~4 人日 |
| 🟢 中 | zx-search | 8091 | course、ES(规划) | 搜索 / 推荐；先内存后接 Elasticsearch | 3~5 人日 |
| 🟢 低 | zx-message | 8093 | user | 短信模板 / 收件箱落库 | 2~3 人日 |
| 🟢 低 | zx-remark | 8092 | user、course | 点赞落库；防重复 | 1~2 人日 |
| 🟢 低 | zx-data | 8094 | insight | 数据看板聚合 | 2~3 人日 |

> 落地顺序建议：先 `promotion → pay → trade`（打通交易闭环的发票一致链路），
> 再 `learning → exam → media`（学习闭环），最后 `search / message / remark / data`。

---

## 2. 基础设施 / 中间件规划

| 状态 | 规划项 | 目的 | 备注 |
|---|---|---|---|
| ⚪ | **Nacos 注册中心 + 配置中心** | 服务发现与动态配置 | 本地已通过 `SimpleDiscoveryClient` 静态直连开发；生产需启用（参考 [DEPLOYMENT.md](DEPLOYMENT.md)），`nacos.discovery.enabled=false` 仅为本地调试 |
| ⚪ | **Sentinel 限流 / 熔断** | 网关与服务限流降级 | 秒杀接口已用网关内置 `RequestRateLimiter`（Redis 令牌桶，IP+用户维度，见 [SECKILL.md](SECKILL.md)）覆盖基础限流；Sentinel 补服务级熔断降级，与 SSE `Flux.defer + AtomicInteger` 连接级限流形成分级保护 |
| ⚪ | **Seata 分布式事务** | 下单 + 扣券 + 支付回调跨服务一致性 | 目前用「本地消息表 + 消费幂等」保证最终一致，可平滑演进 |
| ⚪ | **Elasticsearch** | 课程全文搜索 | `zx-search` 依赖项，选型已就绪，待接 |
| ⚪ | **前端接入** | Web / 小程序端联调 | 目前仅 API 层，供学习演示可先行对接 |
| 🔶 | **RAG 向量知识库** | 助教基于课程资料问答 | pgvector + PostgreSQL 已落地（KnowledgeAgent 检索 Top3 片段），后续可增知识上传分片任务 |
| 🟢 | **虚拟线程** | 高并发长连接（SSE） | 已在 `zx-gateway` / `zx-aigc` 开启，见 [VIRTUAL-THREADS.md](VIRTUAL-THREADS.md) |
| 🟢 | **CI / CD** | 自动化构建 | GitHub Actions 已建（`mvn clean verify`，JDK 21），后续可加镜像构建 |

---

## 3. 里程碑回顾

已完成内容见 README「演进路线 → 已完成里程碑」与 [CHANGELOG.md](CHANGELOG.md)（当前最新 v1.0.0）。

---

> 协作约定：本文件与 [ARCHITECTURE.md §演进路线](ARCHITECTURE.md)、[CHANGELOG.md](CHANGELOG.md)
> 三处保持一致；量化数据（压测 QPS/P99）须来自真实测试，不编造。