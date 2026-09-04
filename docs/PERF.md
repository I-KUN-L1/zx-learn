# 知行智学（zx-learn）性能测试报告

> **可信度红线（先读这个）**
>
> 1. 本报告所有数值均来自**本机真实压测**（原始 CSV/HTML 归档为证）；`<X>` 为待补测占位，**禁止编造**。
> 2. **笔记本压测 ≠ 生产数据**：压测客户端与被测服务同机部署（CPU / 内存 / 网卡互相争抢），绝对值偏保守且波动大。本报告的价值在于**相对对比（优化前后）与瓶颈定位方法**，而非绝对吞吐数字。生产容量评估请在独立压测机 + 生产同构部署下重测。
> 3. 每张表均注明**生成命令与日期**，原始结果目录可复算（`perf-test/aggregate-csv.ps1`）。

---

## 1. 测试环境（如实记录）

| 项 | 值 |
|---|---|
| 压测机 = 被测机 | 同一台笔记本：AMD Ryzen 9 7940H（8C/16T）· 15GB RAM · Windows 11 23H2（10.0.22631） |
| 服务部署 | 本机直连（Nacos 关闭，SimpleDiscoveryClient 静态实例）；MySQL 8 / Redis 7 / RocketMQ 4.9.7 均为同机 Docker 容器 |
| JDK | openjdk 21.0.11 LTS（`spring.threads.virtual.enabled=true`：gateway / aigc） |
| 压测工具 | JMeter 5.6.3（`D:\1\jmeter`）；JSR223 + JDK HttpURLConnection，`http.keepAlive=true` + `http.maxConnections=2000`，读满响应体**不调用 disconnect()**（防 Windows 临时端口 49152-65535 / TIME_WAIT 240s 耗尽） |
| 并发模型 | 阶梯 50 / 100 / 200 / 500，ramp-up 10s，每线程 × `LOOPS` 次循环控制采样量，档间冷却 10s |
| 预热 | 每个 JMX 内置 `Warmup-Threads` 串行组（`serialize_threadgroups=true`，先于压测组执行）：场景 1 预填课程缓存 20 次；场景 3 轻对话热身 2 次；场景 4 调用业务 warmup 接口 1 次装填库存/用户集 |
| 聚合工具 | `perf-test/aggregate-csv.ps1`（JMeter CSV → N/QPS/avg/P99/max/err；注意 PS 5.1 读 UTF-8 CSV 必须显式 `-Encoding UTF8`，否则中文 label 被 GBK 误解码吞逗号导致整行列错位） |

## 2. 场景总览

| # | 场景 | 脚本（perf-test/jmeter/） | 被测接口 | 端口 | 类型 |
|---|---|---|---|---|---|
| 1 | 课程详情 | `scenario1-course-detail.jmx` | GET /course/{id}（Redis 缓存读） | 8083 | 读 |
| 4 | 优惠券秒杀 | `scenario4-seckill-claim.jmx` | POST /user-coupons/seckill/{couponId}（网关限流 → Redis Lua 原子预扣 → MQ 异步落库） | 8088 | 写竞争 |
| 3 | AI 助教 SSE | `scenario3-aigc-chat.jmx` | POST /chat（SSE 流式长连接，含 TTFT 首 token 子采样） | 8089 | 长连接 |
| 2 | 同步下单（对照） | `scenario2-flashsale-order.jmx` | POST /orders/placeOrder | 8087 | 写 |

业务语义口径：`QUEUING / SOLD_OUT / REPEAT / NOT_READY` 均为**有效业务结果**（HTTP 200），只有 HTTP 非 200 或 `code != 200` 计为错误。

## 3. 场景一：课程详情（读密集 · Redis 缓存）

### 3.1 结果表（优化前实测 → 优化后占位）

**生成命令**：`.\run-perf.ps1 -Scenario 1 -Loops 1000` ｜ **日期**：2026-09-02 ｜ **归档**：`results/20260902-171325`（s1-*.csv，JSR223 连接复用版脚本）

| 并发 | 采样数 | QPS 前 → 后 | P99 ms 前 → 后 | 平均 ms 前 → 后 | 错误率 前 → 后 |
|---|---|---|---|---|---|
| 50 | 50,000 | 4,752 → `<X>` | 2 → `<X>` | 1 → `<X>` | 0.00% → `<X>` |
| 100 | 100,000 | 9,014 → `<X>` | 8 → `<X>` | 3 → `<X>` | 0.00% → `<X>` |
| 200 | 200,000 | 10,689 → `<X>` | 39 → `<X>` | 9 → `<X>` | 0.00% → `<X>` |
| 500 | 500,000 | **11,537** → `<X>` | 93 → `<X>` | 35 → `<X>` | 0.00% → `<X>` |

> 基线解读：QPS 随并发继续爬升但边际收益递减（50u→500u 仅 ×2.4），P99 从 2ms 恶化到 93ms——瓶颈从缓存命中路径转向 **Tomcat 线程/连接调度 + 同机资源争抢**。命中路径单次 ~1ms，说明 Redis 缓存读不是第一瓶颈。

### 3.2 候选优化项（未实施，优化后重测回填 `<X>`）

- 本地 Caffeine 二级缓存（热点课程免 Redis 网络往返）
- 缓存空值 / 布隆过滤器防穿透；逻辑过期防雪崩
- Tomcat `maxConnections` / `acceptCount` 与虚拟线程协同调参

## 4. 场景四：优惠券秒杀（写竞争 · Redis Lua + MQ）

### 4.1 被测链路

网关令牌桶限流（IP+用户，5 QPS/用户）→ Redis Lua 原子预扣（限领判重 + 余量判断 + 扣减 + 记录用户，单次 EVAL）→ MQ 异步落库（雪花券码）→ 轮询结果（Redis 结果键优先、DB 兜底）。方案与可靠性设计见 [SECKILL.md](SECKILL.md)。

### 4.2 结果表（优化前实测 → 优化后占位）

**生成命令**：`POST :8088/coupons/seckill/warmup/{couponId}` 后 `.\run-perf.ps1 -Scenario 4 -CouponId <预热券ID> -Loops 1` ｜ **日期**：2026-09-03 ｜ 活动库存 800

**Run A：抢购语义（500 并发 × 每用户 1 次，ramp 10s）**

| 并发 | QPS | P50 | P95 | P99 | 最大 | 错误率 | 业务结果 |
|---|---|---|---|---|---|---|---|
| 500 | 50.0/s（ramp 限速） | 6ms | 794ms | 1206ms | 1289ms | **0.00%** | 500 用户全部 QUEUING |

> 尾部延迟来自 ramp-up 建连期；QPS 受 10s ramp 限制，不代表吞吐上限（见 Run B）。

**Run B：持续吞吐（500 并发 × 100 循环 = 50,000 采样，全部命中判重拒绝路径）**

| 并发 | 峰值 QPS | 平均 QPS | P50 | P95 | P99 | 最大 | 错误率 |
|---|---|---|---|---|---|---|---|
| 500 | **6,144/s** | 4,223/s | 49ms | 93ms | **150ms** | 1099ms | **0.00%** |

> Run B 中 500 用户均已领取，全部命中 Lua `SISMEMBER` 判重拒绝——即真实秒杀中占比最高的"未命中直接拒绝"路径：单 Redis 往返、零 DB、零 MQ。
> 优化后栏：待实施优化（见 4.4）后同口径重测回填 `<X>`。

### 4.3 正确性验收（无超发 / 无重复领取）—— ✅ 全部通过（2026-09-03）

| 校验项 | SQL / 命令 | 期望 | 实测 |
|---|---|---|---|
| DB 券码数 = Redis 扣减数 | `SELECT COUNT(*) FROM zx_promotion.user_coupon WHERE coupon_id={id};` vs `SCARD coupon:seckill:users:{id}` | 相等 | ✅ 500 = 500 |
| 无超发 | DB 券码数 | ≤ total_num(800) | ✅ 500 |
| 券码无重复 | `COUNT(DISTINCT coupon_code)` | = 总行数 | ✅ 500 = 500 |
| 无重复领取 | `... GROUP BY user_id HAVING COUNT(*)>1` | 0 行 | ✅ 0 行 |
| 消费流水一致 | `COUNT(*) consume_record` vs DB 券码数 | 相等 | ✅ 500 = 500 |
| MQ 停机重启补投 | 停 broker → 10 新用户抢券（全部 QUEUING）→ 重启 broker → 等 60s 对账周期 | DB 追平 SCARD | ✅ 500→510 = SCARD 510，日志 `对账补发完成 compensated=10` |

### 4.4 结论与候选优化项

- 吞吐瓶颈位置：非 Lua/Redis（单次 EVAL ~0.1ms），稳态下落在 **Tomcat 建连与 HTTP 栈**；未命中拒绝路径 500 并发实测 6,144 QPS、P99 150ms、0 错误
- Redis 命中率 ≈100%（库存键 + 用户 set 全内存，无回源）
- 候选优化（未实施）：前置网关连接复用池、拒绝路径直接在 Filter 层短路返回

## 5. 场景三：AI 助教 SSE（长连接容量）

### 5.1 结果表（优化前实测 → 优化后实测）

**优化前生成命令**：`.\run-perf.ps1 -Scenario 3 -Loops 1000` ｜ **日期**：2026-09-02 ｜ **归档**：`results/20260902-171325`（旧版原生采样器脚本）
**优化后生成命令**：`jmeter -n -t scenario3-aigc-chat.jmx -JTHREADS=<50/100/200/500> -JLOOPS=<60/30/15/6>`（每档 3,000 连接受控）｜ **日期**：2026-09-04 ｜ **归档**：`results/20260904-s3v2`

| 并发 | 优化前错误率 | 优化后错误率 | 优化后 QPS | 优化后 P99 | 优化后最大 | 主要错误（前 → 后） |
|---|---|---|---|---|---|---|
| 50 | **1.36%** | **0.00%** | 283/s | 27ms | 111ms | BindException → 无 |
| 100 | **56.30%** | **0.00%** | 291/s | 27ms | 107ms | Connection refused → 无 |
| 200 | **78.09%** | **0.00%** | 296/s | 29ms | 116ms | Connection refused → 无 |
| 500 | **87.06%** | **0.00%** | 298/s | 29ms | 128ms | Connection refused → 无 |

> **优化前基线**：SSE 每轮对话 = 一条独占长连接（读完 END 才收尾），并发爬升时 **accept 队列被打满**，`Connection refused` 错误率从 1.36% 飙到 87.06%——典型的**连接容量瓶颈**而非计算瓶颈（LLM 为 mock 流式，CPU 占用低）。
>
> **已实施优化**：① `Flux.defer` + `AtomicInteger` 连接级并发限流（`zx.rag.max-concurrent-streams`，默认 2000），超限返回降级流（"请稍后再试" + END）——把错误率转化为**显式降级**；② zx-aigc Web 栈由 Tomcat 切换 **Netty**（排除 servlet 容器传递依赖）。
>
> **优化后实测**：四档错误率全部 **0%**，端到端最大耗时从 10~15s（含 refused 快失败与超时）收敛到 **107~128ms**。
>
> **口径说明（诚实对比）**：优化前为 1000 loops 高频建连的稳态吞吐口径（其中包含大量 refused 快失败样本与客户端端口耗尽干扰）；优化后采用**每档 3,000 连接受控**的容量验证口径（SSE 长连接无法复用，客户端 16K 临时端口上限决定了无法复现 50 万采样）。因此 **QPS 两栏不直接可比**（表中只列优化后 QPS 供参考），**错误率与最大耗时是本次的核心可比指标**。

### 5.2 SSE 首 token 延迟（TTFT）

**生成命令**：同 5.1 优化后（JSR223 脚本在 `responseMessage` 记录 `ttft=Xms`，`aggregate-csv.ps1` 后按档提取）｜ **日期**：2026-09-04 ｜ **归档**：`results/20260904-s3v2`（每档 n=3,000）

| 并发 | TTFT P50 | TTFT P95 | TTFT P99 | TTFT 最大 | 备注 |
|---|---|---|---|---|---|
| 50 | 4ms | 13ms | 18ms | 20ms | LLM mock 流式模式（`ZX_LLM_ENABLED=false`） |
| 100 | 4ms | 14ms | 17ms | 24ms | 同上 |
| 200 | 5ms | 14ms | 18ms | 23ms | 同上 |
| 500 | 5ms | 14ms | 17ms | 22ms | 同上 |
| 接真实 LLM | `<X>` | `<X>` | `<X>` | `<X>` | 受上游模型推理时延主导，仅作链路开销参考 |

> TTFT 在 50→500 并发区间几乎无退化（P99 稳定 17~18ms），说明流式首包路径无连接竞争残留；接真实 LLM 后该指标将被模型推理时延主导（预期秒级），mock 口径仅验证**服务端流式链路开销**。

## 6. 附：场景二 同步下单（对照基线，真实数据）

**生成命令**：`.\run-perf.ps1 -Scenario 2 -Loops 1000` ｜ **日期**：2026-09-02 ｜ **归档**：`results/20260902-171325`（s2-*.csv）

| 并发 | 采样数 | QPS | P99 ms | 错误率 |
|---|---|---|---|---|
| 50 | 50,000 | **2,246** | 28 | 0.00% |
| 100 | 100,000 | 1,896 | 105 | 0.28% |
| 200 | 200,000 | 1,640 | 468 | 1.33% |
| 500 | 500,000 | 1,843 | 573 | 0.56% |

> 同步下单走完整事务链（Feign 金额校验 + 状态机 + 本地消息表），吞吐为课程详情读路径的 ~1/5，符合预期。错误样本集中在高并发的锁等待超时。与秒杀异步链路（场景 4）对照可说明**削峰异步化**的收益。

## 7. 瓶颈分析方法论（排查命令速查）

### 7.1 应用侧：连接池与线程

```bash
# Hikari 连接池实时状态（日志开启 metrics 后观察 active/idle/wait）
# 临时调大：spring.datasource.hikari.maximum-pool-size=20（默认 10）
jcmd <pid> Thread.print | findstr /C:"http-nio" /C:"HikariPool"   # 线程栈：看请求堆积在哪一层
jstat -gcutil <pid> 1000                                          # GC 频率/停顿，1s 间隔
netstat -ano | findstr :8083 | findstr /C:"ESTABLISHED" /C:"TIME_WAIT" /C:"LISTENING" | find /C ":"
# TIME_WAIT 持续 >2 万 → 客户端未复用连接；ESTABLISHED 贴近 maxConnections → 连接容量瓶颈
```

### 7.2 MySQL 慢 SQL

```sql
-- 全局开启（压测期临时）：0.1s 阈值
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;
SET GLOBAL slow_query_log_file = '/var/lib/mysql/slow.log';
-- 压测后取样（容器内）：docker exec mysql tail -50 /var/lib/mysql/slow.log
EXPLAIN SELECT ...;                                    -- 全表扫描 / 索引失效
SELECT * FROM sys.schema_tables_with_full_table_scans; -- 系统视图找全表扫描表
SHOW ENGINE INNODB STATUS\G                            -- 锁等待与死信（秒杀写竞争重点看 LATEST DETECTED DEADLOCK）
```

### 7.3 Redis 慢日志与命中率

```bash
redis-cli CONFIG SET slowlog-log-slower-than 1000   # 1ms 阈值（Lua 评估 ~0.1ms，>1ms 即异常）
redis-cli CONFIG SET slowlog-max-len 256
redis-cli SLOWLOG GET 10                            # 压测后取样：慢命令 = 大集合 O(N) / 热键
redis-cli INFO stats | findstr hits                 # keyspace_hits/(hits+misses) = 命中率
redis-cli --latency -h localhost                    # 实时延迟抖动
redis-cli INFO commandstats | findstr -i eval       # Lua EVAL 调用统计
```

### 7.4 SSE / Netty 专项

```bash
netstat -ano | findstr :8089 | find /C "ESTABLISHED"   # 并发连接数逼近 zx.rag.max-concurrent-streams 即触发降级
# Connection refused 集中出现 → accept 队列（ServerSocket backlog）打满，属容量瓶颈：
# 处理路径 = 限流降级（已实施）而非无限调大 backlog
```

## 8. JVM 参数建议

**服务端（2~4C / 1~2GB 容器小规格）**：

```
-Xms1g -Xmx1g                          # 堆一致，避免动态伸缩抖动
-XX:MaxMetaspaceSize=256m              # Spring Cloud 全家桶元空间封顶
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps
-Dio.netty.leakDetection.level=SIMPLE  # aigc（Netty/SSE）建议，防 DirectMemory 泄漏静默积累
# G1 为 JDK21 默认，无需显式；虚拟线程由 spring.threads.virtual.enabled=true 控制（gateway/aigc）
```

**JMeter 客户端（本报告场景实测有效）**：

```
-Xms1g -Xmx1g
-Dsun.net.inetaddr.ttl=0               # 压测中频繁 DNS 解析场景可选
# 连接复用三件套（脚本内已设）：http.keepAlive=true / http.maxConnections=2000 / 不调用 disconnect()
```

**中间件容器**：Redis `maxmemory` 建议封顶并设 noeviction（本项目 compose 已按此约定）；MySQL/Redis 同机时注意与 JVM 服务争抢内存，压测机 15GB 下建议全部容器内存上限 ≤6GB。

## 9. 复现步骤

```powershell
# 0. 启动基础设施与被测服务（见 docs/DEPLOYMENT.md 本地直连模式）
# 1. 秒杀活动预热（couponId 必须与压测一致，否则全部 NOT_READY 秒拒、无参考价值）
curl -X POST http://localhost:8088/coupons/seckill/warmup/2096000000000000001 -H "user-info: 1"
# 2. 阶梯压测（50/100/200/500，档间冷却 10s；结果落 perf-test/results/<时间戳>/）
D:\1\zx-learn\perf-test\run-perf.ps1 -Scenario 4 -CouponId 2096000000000000001 -Loops 1
# 3. 聚合出表（N/QPS/avg/P99/max/err，自动跳过表头，UTF-8 安全）
D:\1\zx-learn\perf-test\aggregate-csv.ps1   # 默认取 results 下最新目录
# 4. 回填本报告对应表格（占位 <X> 只允许用真实数据填充）
```

> **归档纪律**：`results/` 只保留最新一次全量归档目录（含 CSV + HTML 报告），历史结果清理前先摘录结论进本报告。
