# 知行智学（zx-learn）性能测试报告

> 压测环境：Windows 本机直连（Nacos 关闭、静态实例），JMeter 5.6.3（`D:\1\jmeter`），
> 脚本位于 `perf-test/jmeter/`，一键执行 `perf-test/run-perf.ps1`（阶梯 50/100/200/500 并发）。
> 口径约束：**量化数据必须来自真实压测**，下表占位值在实测后回填，禁止编造。

---

## 1. 环境与口径

| 项 | 值 |
|---|---|
| 压测机 / 被测机 | 同一台 Windows（localhost 直连，存在资源争抢，绝对值偏保守） |
| JDK | 21（虚拟线程开启：gateway / aigc） |
| 客户端策略 | JSR223 + JDK HttpURLConnection，`http.keepAlive=true` + `http.maxConnections=2000`，不调用 `disconnect()`（防临时端口耗尽） |
| 采样规模 | 每档并发 × 每线程 1000 次循环（SSE 场景除外） |

## 2. 场景总览

| 场景 | 脚本 | 被测接口 | 端口 |
|---|---|---|---|
| 1 课程详情 | scenario1-course-detail.jmx | GET /course/{id} | 8083 |
| 2 秒杀下单 | scenario2-flashsale-order.jmx | POST /orders/placeOrder | 8087 |
| 3 AI 助教 SSE | scenario3-aigc-chat.jmx | POST /chat（SSE 流式） | 8089 |
| 4 优惠券秒杀 | scenario4-seckill-claim.jmx | POST /user-coupons/seckill/{couponId} | 8088 |

> 场景 1~3 已有实测数据（结论摘录见 README「项目亮点」；原始结果按隐私规范留存于本地，不入库）。本章模板自场景 4 起新增。

## 3. 压测前 checklist

- [ ] MySQL / Redis / RocketMQ 已启动（`docker compose ps`）
- [ ] zx-promotion(8088) 已启动；测网关则 zx-gateway(8080) 也启动
- [ ] 秒杀活动已预热：`POST :8088/coupons/seckill/warmup/{couponId}`（未预热全部 NOT_READY 秒拒，无参考价值）
- [ ] 基线清理：`DELETE FROM zx_promotion.user_coupon WHERE coupon_id={id}` + `DEL coupon:seckill:*:{id}`（或换新 couponId）
- [ ] 档间冷却 10s（run-perf.ps1 已内置）

## 4. 场景 4：优惠券秒杀（章节模板）

### 4.1 被测链路

网关令牌桶限流（IP+用户，5 QPS/用户）→ Redis Lua 原子预扣 → MQ 异步落库 → 轮询拿券码。
业务语义：`QUEUING/SOLD_OUT/REPEAT` 均为**有效业务结果**（200），只有 HTTP 非 200 / `code!=200` 计为错误。
方案与可靠性设计见 [SECKILL.md](SECKILL.md)。

### 4.2 阶梯压测数据

**实测环境**：Windows 本机直连（被测 zx-promotion 8088，MySQL/Redis/RocketMQ 同机 Docker/本机），
活动库存 800，JDK 21，2026-09-03。

**Run A：抢购语义（500 并发 × 每用户 1 次，ramp 10s）**

| 并发 | QPS | P50 (ms) | P95 (ms) | P99 (ms) | 最大 (ms) | 错误率 | 业务结果 |
|---|---|---|---|---|---|---|---|
| 500 | 50.0/s（ramp 限速） | 6 | 794 | 1206 | 1289 | **0.00%** | 500 用户全部 QUEUING |

> 尾部延迟来自 ramp-up 建连期；QPS 受 10s ramp 限制，不代表吞吐上限（见 Run B）。

**Run B：持续吞吐（500 并发 × 100 循环 = 50,000 采样，REPEAT 判重路径）**

| 并发 | 峰值 QPS | 平均 QPS | P50 (ms) | P95 (ms) | P99 (ms) | 最大 (ms) | 错误率 |
|---|---|---|---|---|---|---|---|
| 500 | **6144/s** | 4223/s | 49 | 93 | 150 | 1099 | **0.00%** |

> Run B 中 500 用户均已领取，全部命中 Lua `SISMEMBER` 判重拒绝（REPEAT）——
> 即真实秒杀中占比最高的"未命中直接拒绝"路径：单 Redis 往返，零 DB、零 MQ，500 并发下 6144 QPS、P99 150ms。
> Redis 命中率接近 100%（库存键 + 用户 set 全内存，无回源）。

### 4.3 正确性验收（无超发 / 无重复领取）—— ✅ 全部通过

| 校验项 | SQL / 命令 | 期望 | 实测 |
|---|---|---|---|
| DB 券码数 = Redis 扣减数 | `SELECT COUNT(*) FROM zx_promotion.user_coupon WHERE coupon_id={id};` vs `SCARD coupon:seckill:users:{id}` | 相等 | ✅ 500 = 500 |
| 无超发 | DB 券码数 | ≤ total_num(800) | ✅ 500 |
| 券码无重复 | `COUNT(DISTINCT coupon_code)` | = 总行数 | ✅ 500 = 500 |
| 无重复领取 | `... GROUP BY user_id HAVING COUNT(*)>1` | 0 行 | ✅ 0 行 |
| 消费流水一致 | `COUNT(*) consume_record` vs DB 券码数 | 相等 | ✅ 500 = 500 |
| **MQ 停机重启补投** | 停 broker → 10 新用户抢券（全部 QUEUING，Redis 预扣成功）→ 重启 broker → 等 60s 对账周期 | DB 追平 SCARD | ✅ 500→510 = SCARD 510，日志 `对账补发完成 compensated=10` |

### 4.4 Redis 命中率

压测期间 `redis-cli info stats` 取 `keyspace_hits / (keyspace_hits + keyspace_misses)`；
秒杀链路预期接近 100%（库存键 + 用户 set 全内存命中，未预热场景的 EXISTS miss 属预期拒绝）。

### 4.5 结论

- 吞吐瓶颈位置：非 Lua/Redis（单次 EVAL ~0.1ms），稳态下落在 Tomcat 建连与 HTTP 栈；未命中拒绝路径 500 并发实测 6144 QPS、P99 150ms、0 错误
- 500 并发下无超发、无重复领取：**是**（DB 券码数 500 = Redis SCARD = 消费流水数，0 重复用户）
- MQ 停机自愈：**通过**（停机期预扣不丢，重启后 60s 内对账自动补投 10 条，DB 追平 Redis）
- 与场景 2（同步下单）对照：待同口径实测后回填（占位）

## 5. 复现步骤

```powershell
# 1. 预热秒杀活动（couponId 须与压测一致）
curl -X POST http://localhost:8088/coupons/seckill/warmup/2096000000000000001 -H "user-info: 1"

# 2. 阶梯压测（50/100/200/500，场景 4 固定打 8088；-CouponId 必传，否则默认券1 全部 NOT_READY）
D:\1\zx-learn\perf-test\run-perf.ps1 -Scenario 4 -TargetHost localhost -CouponId 2096000000000000001 -Loops 1

# 3. 回填第 4.2 节数据（CSV 位于 perf-test/results/）
```
