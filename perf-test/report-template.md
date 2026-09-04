# 性能测试报告

> 项目：知行智学（zx-learn）&nbsp;|&nbsp;阶段：`(填写 增量/回归)`&nbsp;&nbsp;|&nbsp;&nbsp;测试人：&nbsp;|&nbsp;日期：
> 环境：`(单机 / Docker Compose / 集群)`，JDK 21 / Spring Boot 3 / MySQL 8 / Redis 7 / RocketMQ 4.9 / PostgreSQL(pgvector)

---

## 1. 测试概述

| 项目 | 说明 |
|---|---|
| 测试目标 | 三个核心接口的容量与稳定性基线 |
| 工具 | Apache JMeter 5.x（3 个 .jmx 见 `perf-test/jmeter/`） |
| 并发阶梯 | 50 / 100 / 200 / 500（每档采样时长见下） |
| 压测机 → 被测机 | `(网络拓扑/带宽说明)` |
| 前置条件 | `(Redis/MySQL/MQ 已启动，测试数据已预热，服务已注册 Nacos)` |

**被测接口**

| 场景 | 接口 | 端口 | 采样时长(每档) |
|---|---|---|---|
| 1 课程详情（读多写少·Redis 缓存） | `GET  /course/1` | 8083 | 120s |
| 2 优惠券秒杀下单（写竞争·Redis+MQ） | `POST /orders/placeOrder` | 8087 | 120s |
| 3 AI 助教对话（SSE 长连接） | `POST /chat` | 8089 | 120s |

> 执行方式：每个 .jmx 分别以 `-JTHREADS=50/100/200/500` 跑 4 次：
> `jmeter -n -t scenarioX.jmx -JTHREADS=100 -JHOME=120 -J LO=... -l result.csv`
> （单线程组参数化并发，便于得到干净的每级基线；如需自动阶梯可换 jp@gc Stepping Thread Group。）

---

## 2. 并发阶梯执行记录

| 场景 | 并发 | 采样时长 | 总请求数 | QPS | 平均 RT | P95 RT | P99 RT | 错误率 |
|---|---|---|---|---|---|---|---|---|
| 1 | 50 | 120s |  |  |  |  |  |  |
| 1 | 100 | 120s |  |  |  |  |  |  |
| 1 | 200 | 120s |  |  |  |  |  |  |
| 1 | 500 | 120s |  |  |  |  |  |  |
| 2 | 50 | 120s |  |  |  |  |  |  |
| 2 | 100 | 120s |  |  |  |  |  |  |
| 2 | 200 | 120s |  |  |  |  |  |  |
| 2 | 500 | 120s |  |  |  |  |  |  |
| 3 | 50 | 120s |  |  |  |  |  |  |
| 3 | 100 | 120s |  |  |  |  |  |  |
| 3 | 200 | 120s |  |  |  |  |  |  |
| 3 | 500 | 120s |  |  |  |  |  |  |

> 场景 3 补充：TTFT(首 token) P95 = ___ms，P99 = ___ms；连接中断率 = ___%。

---

## 3. 监控截图占位

> 规则：每个指标贴一张图，图注写清「时间窗口 / 并发 / 对象」。

### 3.1 应用侧（服务 JVM / Tomcat）
| 截图 | 说明 |
|---|---|
| `![](.images/qps_rt.png)` | 被测服务 QPS 与响应时间趋势 |
| `![](.images/tomcat_threads.png)` | Tomcat 活动线程与队列积压 |

### 3.2 中间件
| 截图 | 说明 |
|---|---|
| `![](.images/redis.png)` | Redis 命中率 / ops / 连接数（缓存场景必看） |
| `![](.images/mq.png)` | RocketMQ 消费积压 / 生产 TPS |
| `![](.images/mysql.png)` | MySQL QPS / 慢查询 / 连接数 |
| `![](.images/pg.png)` | pgvector 查询 / 索引扫描 |

### 3.3 容器与主机
| 截图 | 说明 |
|---|---|
| `![](.images/host_cpu_mem.png)` | 主机 CPU / 内存 / 网络 |
| `![](.images/docker.png)` | 容器 CPU / 内存限制水位 |

---

## 4. 瓶颈分析

| # | 症状（指标异常） | 根因假设 | 佐证（日志/监控/火焰图） | 结论 / 归属模块 |
|---|---|---|---|---|
| 1 | 并发 200 时 P99 陡增 |  |  |  |
| 2 | QPS 到 ___ 后不再上升 |  |  |  |
| 3 | 错误率上升且有超时 |  |  |  |
| 4 | Redis 命中率骤降 |  |  |  |
| 5 | MQ 消费积压增量 |  |  |  |

> 结论分级：P0 阻塞 / P1 高优 / P2 一般

---

## 5. 优化前后对比

| 场景/指标 | 优化前 | 优化后 | 提升 |
|---|---|---|---|
| 课程详情 P99 RT | ___ ms | ___ ms | ↓ ___% |
| 课程详情 QPS | ___ | ___ | ↑ ___× |
| 秒杀下单 P99 RT | ___ ms | ___ ms | ↓ ___% |
| 秒杀下单 QPS | ___ | ___ | ↑ ___× |
| 秒杀成功单 / 总请求 | ___ | ___ | — |
| AI 对话 P95 完整时延 | ___ ms | ___ ms | ↓ ___% |
| AI 对话 TTFT P95 | ___ ms | ___ ms | ↓ ___% |
| 错误率 | ___% | ___% | ↓ ___% |

**优化动作清单**（勾选实际执行的）
- [ ] 连接池调优（Tomcat / Druid/HikariCP / Redis / RocketMQ 生产者）
- [ ] Redis Pipeline 批处理改写热点读
- [ ] 慢 SQL 索引优化（见 6.3）
- [ ] JVM 参数调整（见 6.4）
- [ ] 其他：________

---

## 6. 优化建议清单

### 6.1 连接池调优

**Tomcat（`server.tomcat`）**
```yaml
server:
  tomcat:
    max-threads: 300        # 依据第 2 节并发上限设定，避免线程多于可用 CPU * 合理系数
    min-spare-threads: 30
    accept-count: 200       # 队列上限，过大反而拉高 P99
    connection-timeout: 3000
    max-connections: 10000
```

**Druid 数据源（MySQL）**
```yaml
spring:
  datasource:
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50         # 与 MySQL max_connections 及并发模型匹配
      max-wait: 5000         # 获取连接超时（ms）
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
```

**Redis（Lettuce 默认，或换 Jedis pool）**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 32
          max-idle: 16
          min-idle: 4
          max-wait: 3000
```
> 建议：读热点尽量引脚 Redis，配合 6.2 Pipeline 显著降低往返消耗。

**RocketMQ 生产者**
```yaml
rocketmq:
  producer:
    send-message-timeout: 3000
    maxMessageSize: 4194304   # 4M
    retryTimesWhenSendFailed: 2
```
> 生产侧发送失败回调本地消息表补偿，超时/重试值应低于前端订单超时时间（15min）。

### 6.2 Redis Pipeline 批处理

热点读用 `pipeline` 合并多次往返为一次：

```java
List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (Long id : ids) {
        connection.get(("course:detail:" + id).getBytes(StandardCharsets.UTF_8));
    }
    return null;
});
```
- 收益：单次回调内 N 个命令的 RTT 从 `N × RTT` 降为约 `1 × RTT`，显著降低读排线 P99。
- 注意：Pipeline 不保证原子性；需要原子读改写时改用 Lua（如秒杀预扣已用 Lua）。

### 6.3 慢 SQL 索引优化

对 `EXPLAIN` 出现 `type=ALL / Using filesort / Using temporary` 的 SQL 加索引：
```sql
-- 订单表：按用户查询订单
ALTER TABLE trade_order ADD INDEX idx_order_user (user_id, status);
ALTER TABLE trade_order ADD INDEX idx_order_timeout (status, create_time);   -- 超时扫描
-- 学习记录：按课程/课时极速拉取
ALTER TABLE learning_record ADD INDEX idx_record_course_lesson (course_id, lesson_id);
-- 消息表：定时补偿扫描（未发送 + 创建时间）
ALTER TABLE order_msg ADD INDEX idx_msg_pending (state, create_time);
```
> 命中缓存后课程/密卷主读不再落库；写路径（下单/学习进度）若查询成为瓶颈则建立以上索引。

### 6.4 JVM 参数建议（行成配置，供启动参数）

```bash
# 以堆为基线的通用吞吐参数（初值，需 JVM 调优后微调）
java -Xms2g -Xmx2g \
     -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m \
     -XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/dump \
     -XX:+ExitOnOutOfMemoryError \
     -jar zx-xxx-service.jar
```
- 优先固定 `-Xms = -Xmx`，避免扩容抖动。
- G1 目标停顿 ≤100ms；若仍提升 P99，可调 `-XX:G1NewSizePercent` 或切换分代尺寸。
- 生产务必开 `-XX:ExitOnOutOfMemoryError` 让 OOM 自愈重启（结合容器探活）。

---

## 7. 结论与风险

- 结论：`(达标/未达标)`
- 主要瓶颈：`___`
- 风险与观察项：`___`

_（本模板数据区均为待填写桩位；真实执行后回填并配图。）_