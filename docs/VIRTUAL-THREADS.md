# 虚拟线程（Virtual Threads）能力说明

> 落点：JDK 21 + Spring Boot 3.3.5 的虚拟线程支持。
> 项目统一编译目标与运行环境为 **JDK 21**（`maven.compiler.release=21`），
> 并在 `zx-gateway`、`zx-aigc` 两个服务开启
> `spring.threads.virtual.enabled=true`。

---

## 1. 开启动机（为什么引入虚拟线程）

核心场景：**AI 助教 SSE 长连接的高并发承载**。

SSE 聊天是典型的「高连接数 + 长时间占用 + 大部分时间在等待（LLM 流式返回、DB/Redis IO）」负载。
在传统平台线程模型下，若用「每请求一线程」的阻塞式 Web 容器（Tomcat），
每个活跃 SSE 连接都会持续占用一个平台线程，且该线程在等待远程 LLM/DB 返回时处于阻塞态，
导致：

1. **连接容量受限**：平台线程数与内存/核心数强绑定，长连接场景下容易被接受队列或线程池打满，
   产生 `Connection refused`（本仓库 scenario3 在 500 并发下实测错误率飙升到 87%+，根因即连接容量瓶颈）。
2. **线程资源浪费**：大量平台线程长时间「空等」在外围 IO 上，利用率极低。

虚拟线程（Project Loom）将「1:1 线程-内核」改为「N:1 虚拟线程-载体线程」，
阻塞平台线程昂贵，但阻塞虚拟线程廉价（JDK 自动在阻塞点切换出载体线程），
因此可以在**阻塞式编程模型下**承载海量长连接，而无需重写为异步回调。

> 边界说明（如实记录）：`zx-aigc` / `zx-gateway` 走的是 **Spring WebFlux + Netty 响应式栈**，
> 其请求事件循环本身是非阻塞、不占平台线程池的，天然避免了「长连接占线程」问题。
> 因此 `spring.threads.virtual.enabled=true` 在这两个服务上，作用于
> **Spring 的 TaskExecutor / @Async 异步任务与定时任务执行器**，而非 Netty 的事件循环。
> 虚拟线程真正直接承载「阻塞式 HTTP 请求」的场景，是面向 Tomcat 等阻塞式容器的服务
> （本仓库 course/trade 等如遇连接容量瓶颈，可同样开启该开关验证收益）。
> 本文的 SSE 压测是为「连接容量上限」这一根因提供可量化、可复现的验证方法。

---

## 2. 如何开启

### 2.1 编译 / 运行目标（JDK 21）

根 `/pom.xml` 统一为准：

```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
</properties>
```

所有子模块均**继承父 pom**，不允许局部覆盖编译版本。

### 2.2 服务开关

`zx-gateway/src/main/resources/application.yml`：

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

`zx-aigc/src/main/resources/application.yml`：同上。

### 2.3 兼容性核查结论

对 `zx-gateway`、`zx-aigc` 主代码扫描（`newFixedThreadPool` / `ThreadLocal` /
`synchronized` / `Thread.sleep` / 自建线程 / `Semaphore` 等）结果：**未发现不兼容用法**。

| 位置 | 用法 | 结论 | 说明 |
|---|---|---|---|
| zx-common `UserContext`（ThreadLocal） | 登录用户请求上下文 | ✅ 兼容 | gateway/aigc 未使用；各使用方需在 `finally` 中 `remove()` 防泄漏。虚拟线程下每次请求独享虚拟线程，无平台线程池「重复使用导致脏读」问题 |
| zx-common `SnowflakeIdGenerator.nextId()`（synchronized） | 雪花序列生成 | ✅ 兼容（短临界区） | 临界区为纯内存计算 + 至多自旋到下一毫秒，**无阻塞 I/O**，对虚拟线程 pinned 影响可忽略；秒杀场景保留原子互斥正确性，无需改动 |
| gateway / aigc 业务代码 | — | ✅ 未发现风险 | 两服务均为响应式/Netty 或纯 Controller，无显式线程池、无自建线程 |

> `synchronized` 的坑仅存在于「**临界区内包含阻塞调用**」时（虚拟线程被 pinned 到载体线程）。
> 本处雪花生成不存在阻塞调用，故保留。若未来写入阻塞 IO 的同步临界区，应替换为
> 非阻塞或 `ReentrantLock + 信号量` 方案，本仓库暂无需处理。

---

## 3. 压测方法（500 并发 SSE）

沿用本仓库 JMeter 压测资产：`perf-test/jmeter/scenario3-aigc-chat.jmx`。

前置：
- 启动 `zx-aigc`（`:8089`，Netty）；
- LLM 关闭时用 mock 断言（读到 `"type":"END"` 即成功），不影响连接容量评估；
- JSR223 客户端开启 `http.keepAlive=true` / `http.maxConnections=2000`，响应体读满后用
  `is.close()` 复用底层连接（避免 Windows 临时端口被 TIME_WAIT 耗尽）。

执行（并发档位建议 50 → 100 → 200 → 300 → 500）：

```powershell
# 以 scenario3 为例（-JTHREADS 控制并发）
jmeter -n -t perf-test/jmeter/scenario3-aigc-chat.jmx \
       -JTHREADS=500 -JLOOPS=10 -l s3-result.csv
```

指标：
- **成功率**：读到 `END` 事件的比例（长连接是否被服务器/网关正常关闭的衡量）；
- **错误类型**：`Connection refused`（连接容量不足）vs `ConnectException` 等问题定位；
- **连接侧**：accept 队列 / 并发连接数。

结果输出到 `perf-test/results/` 统一留档。

---

## 4. 压力测试结果（占位）

> 待完成：用上面方法跑出真实数据回填。以下为占位表，**禁止编造**，未跑前保持留空。

| 并发 | 成功率 | 连接错误率 | 平均连接耗时 | 备注（瓶颈/优化动作） |
|---|---|---|---|---|
| 50 |  |  |  |  |
| 100 |  |  |  |  |
| 200 |  |  |  |  |
| 300 |  |  |  |  |
| 500 |  |  |  |  |

---

## 5. 生效验证

1. **编译期**：`mvn clean verify` 在 JDK 21 下全绿（CI 也校验，见 `.github/workflows/ci.yml`）。
2. **服务启动**：确认服务以 JDK 21 启动（`java -version`），并可在启动日志/临时探针中输出
   `Thread.currentThread().isVirtual()` 以确认异步执行器创建虚拟线程；
   对 Netty 服务，重点确认 `NettyWebServer: Netty started on port ... (http)`，事件循环未受影响。
3. **全局一致性**：全仓搜索旧版本文本（如 `17+` 的 JDK 表述）返回 0 命中。