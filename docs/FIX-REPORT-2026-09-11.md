# 系统优化与功能完善 · 修复报告

> 项目：知行智学 ZhiXing Learn（Spring Cloud Alibaba 微服务 + Vue3）
> 日期：2026-09-11
> 范围：后端启动修复 + 优惠秒杀 / 购物车与订单 / AI 助教 三模块

---

## 0. 结论摘要

| 项 | 结果 |
|---|---|
| 后端启动失败根因 | **宿主终端注入 `SERVER__PORT`/`SERVER__HOST` 环境变量**，被 Spring 松散绑定为 `server.port`，覆盖 `application.yml`，导致所有服务抢占同一被占用端口 → `APPLICATION FAILED TO START` |
| 修复方式 | 启动前清除注入变量 + 修正 `java.io.tmpdir`；新增一键启动脚本（`scripts/dev-start-backend.*`） |
| 编译 | `mvn clean install -DskipTests` → **BUILD SUCCESS**（18 个模块全绿，含测试代码编译） |
| 启动 | 16 个服务全部启动成功，端口 **8080~8095 全部监听** |
| 前端 | `vue-tsc --noEmit` 通过；`vite build` 成功（16.52s） |
| 三模块接口 | 优惠券 / 秒杀 / 购物车 / 订单 / AI-SSE **全部返回正常数据** |

---

## 1. 后端"无法正常启动"——根因定位与修复

### 1.1 现象

不显式指定端口启动任意服务，必然失败：

```
APPLICATION FAILED TO START
Description:
Web server failed to start. Port 62985 was already in use.
Action:
Identify and stop the process that's listening on port 62985 or configure
this application to listen on another port.
```

注意：报错端口 **62985 并非项目任何配置里的端口**（`application.yml` 中 zx-user 是 8082），且每次都不一样。

### 1.2 定位过程

1. `mvn clean install -DskipTests` 全绿 → 排除编译/测试问题。
2. 逐个服务加 `SERVER_PORT=<port>` 覆盖后**均能正常启动** → 说明代码健康，问题在"端口属性被外部覆盖"。
3. 用最小 JVM 程序打印进程环境变量，抓到真凶：

```
=== ENV VARS ===
SERVER__PORT = 62985
SERVER__HOST = 127.0.0.1
```

该端口正是启动宿主（WorkBuddy 沙箱）自身监听的端口。

### 1.3 根因

宿主/IDE 终端会向子进程注入 `SERVER__PORT` / `SERVER__HOST`。Spring Boot 的**松散绑定（relaxed binding）**把它解析为属性 `server.port` / `server.host`：

> Spring Boot 属性优先级：**命令行参数 > `SPRING_APPLICATION_JSON` > 操作系统环境变量 > `application.yml`**

因此环境变量**优先级高于配置文件**，所有服务都被强制去绑定宿主那个端口 → 端口冲突 → 启动失败。

次要问题：沙箱下 `java.io.tmpdir` 指向 `C:\Windows\`，Tomcat 生成临时目录时报 `AccessDeniedException`。

### 1.4 修复方案（启动期防护）

核心就是启动前清除注入变量并把 tmpdir 指向可写目录：

```bash
env -u SERVER__PORT -u SERVER__HOST \
    JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$ROOT/logs/tmp" \
    mvn -pl zx-user spring-boot:run
```

已固化为脚本：

- `scripts/dev-start-backend.sh`（Git Bash）
- `scripts/dev-start-backend.ps1`（Windows PowerShell）

```bash
# 全量 16 服务
bash scripts/dev-start-backend.sh
# 仅最小链路
bash scripts/dev-start-backend.sh zx-user zx-course zx-auth zx-gateway
```

README 已补充启动入口与 FAQ 条目。

> 说明：这是**运行环境注入**问题，不是业务代码缺陷；IDEA 直接启动不受影响。若你也在被注入变量的终端里启动，用上面的脚本或 `env -u` 前缀即可。

---

## 2. 模块一：优惠秒杀服务完善

### 2.1 问题与修复

| 问题 | 修复 |
|---|---|
| 优惠券中心无内容 | 后端 `CouponService.page()` 补齐前端契约字段（`discountValue`/`remainNum`/`issueBeginTime`/`issueEndTime`）；前端 `fetchCoupons()` 对 `res.list` 做空值防御，避免结构异常导致渲染崩溃 |
| 按钮（全部/秒杀专区/普通券/刷新）点击无反应 | `el-radio-group` 的 `@change` 正确绑定筛选并重置分页；`type` 传空串/数字与后端 `@RequestParam Integer type` 对齐 |
| 界面卡死需刷新 | 列表请求统一 `try/catch/finally`，`loading` 一定复位；空数组兜底，杜绝 `undefined` 参与渲染导致组件异常 |

### 2.2 核心代码

**① 列表分页 + 契约字段补全（`zx-promotion` `CouponService`）**

```java
public PageDTO<CouponVO> page(PageQuery query, String name, Integer status, Integer type) {
    Page<Coupon> page = couponMapper.selectPage(query.toMpPage("create_time", false),
            new LambdaQueryWrapper<Coupon>()
                    .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                    .eq(status != null, Coupon::getStatus, status)
                    .eq(type != null, Coupon::getType, type));
    return PageDTO.of(page, this::toVO);
}

/** 优惠券 PO → VO（补全前端契约字段） */
public CouponVO toVO(Coupon c) {
    CouponVO vo = new CouponVO();
    vo.setId(c.getId());
    vo.setName(c.getName());
    vo.setType(c.getType());
    vo.setDiscountValue(c.getDiscountAmount());
    vo.setThresholdAmount(c.getThresholdAmount());
    vo.setTotalNum(c.getTotalNum());
    vo.setStatus(c.getStatus());
    int issued = c.getIssuedNum() == null ? 0 : c.getIssuedNum();
    vo.setRemainNum((c.getTotalNum() == null ? 0 : c.getTotalNum()) - issued); // 剩余 = 总量 - 已发
    vo.setIssueBeginTime(c.getValidBeginTime());
    vo.setIssueEndTime(c.getValidEndTime());
    return vo;
}
```

**② 按钮筛选 + 领取（`zx-web` `CouponCenterView.vue`）**

```vue
<el-radio-group v-model="query.type" round @change="query.pageNo = 1; fetchCoupons()">
  <el-radio-button :value="''">全部</el-radio-button>
  <el-radio-button :value="2">秒杀专区</el-radio-button>
  <el-radio-button :value="1">普通券</el-radio-button>
</el-radio-group>
<el-button round @click="fetchMine(); fetchCoupons()">刷新</el-button>
```

```ts
async function fetchCoupons() {
  loading.value = true
  try {
    const res = await pageCoupons({ ...query })
    coupons.value = res?.list ?? []   // 结构异常兜底，防渲染崩溃
    total.value = res?.total ?? 0
    pages.value = res?.pages ?? 0
  } catch {
    coupons.value = []
  } finally {
    loading.value = false            // 一定复位，防"一直加载"
  }
}
```

**③ 秒杀链路（Redis Lua 原子预扣 → MQ 异步落库 → 前端轮询）**

```ts
if (c.type === 2) {
  await seckillClaim(c.id)                       // 立即返回 QUEUING/SOLD_OUT/REPEAT/NOT_READY
  for (let i = 0; i < 6; i++) {
    await new Promise(r => setTimeout(r, 2000))
    const res = await seckillResult(c.id)        // 轮询结果
    if (res.success || res.status === 'SUCCESS') { /* 抢到 */ break }
    if (res.status === 'QUEUING') continue
    /* 其它状态：SOLD_OUT/REPEAT/NOT_READY/FAILED 提示并结束 */ break
  }
}
```

### 2.3 验证

```
GET /coupons/page        → total=4，含 2 张秒杀券（type=2）+ 2 张普通券（type=1）
GET /user-coupons        → 2001 已有 2 张券
POST /user-coupons/seckill/6002        → {"status":"REPEAT"}（重复领取被正确拦截）
GET  /user-coupons/seckill/6002/result → {"status":"SUCCESS","couponCode":"SK753542068856471552"}
```

---

## 3. 模块二：购物车与我的订单完善

### 3.1 问题与修复

| 问题 | 修复 |
|---|---|
| "我的订单"进入后一直加载 | 后端 `/orders/page` 从返回 `List<Order>` 改为 `PageDTO<OrderVO>`（对齐前端契约）；前端 `res?.list ?? []` 兜底 + `finally` 复位 `loading`，并保留分页参数 |
| 前端缺失购物车入口 | 顶栏新增**购物车图标 + 数量徽标**（`AppHeader`），下拉菜单新增"我的购物车"；新增路由 `/trade/cart` 与 `CartView.vue` |
| 按钮点击无反应 / 卡死 | 订单列表"去支付/取消订单"绑定事件；进入页面用 `try/finally` 保证状态复位；倒计时定时器在 `onBeforeUnmount` 统一 `clearInterval`，避免内存泄漏 |

### 3.2 核心代码

**① 订单分页（后端 `OrderService`）**

```java
public PageDTO<OrderVO> pageQuery(PageQuery query, Integer frontStatus) {
    Integer dbStatus = frontStatus == null ? null : toDbStatus(frontStatus);
    Page<Order> page = orderMapper.selectPage(query.toMpPage("create_time", false),
            new LambdaQueryWrapper<Order>()
                    .eq(Order::getUserId, UserContext.getUserId())   // 仅本人订单
                    .eq(dbStatus != null, Order::getStatus, dbStatus));
    return PageDTO.of(page, this::toVO);                          // 组装明细 + 状态映射
}

/** 数据库状态(0待支付/1已支付/2已取消/3退款中/4已退款) → 前端契约(1/2/3/5/6) */
private int toFrontStatus(int dbStatus) {
    return switch (dbStatus) {
        case STATUS_UNPAID   -> 1;
        case STATUS_PAID     -> 2;
        case STATUS_CLOSED   -> 3;
        case STATUS_REFUNDING-> 5;
        case STATUS_REFUNDED -> 6;
        default -> 3;
    };
}
```

**② 订单列表渲染 + 倒计时（前端 `OrderListView.vue`）**

```ts
async function fetchOrders() {
  loading.value = true
  try {
    const res = await pageOrders({ ...query })
    orders.value = res?.list ?? []
    total.value  = res?.total ?? 0
    pages.value  = res?.pages ?? 0
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

// 待支付 15 分钟倒计时（对齐后端 RocketMQ 延迟消息超时关单）
onBeforeUnmount(() => {
  countdowns.value.forEach(c => clearInterval(c.timer))   // 防定时器泄漏/页面卡死
  countdowns.value.clear()
})
```

**③ 购物车入口（`AppHeader.vue`）**

```vue
<el-tooltip v-if="userStore.isStudent" content="我的购物车" placement="bottom">
  <el-badge :value="cartCount" :hidden="!cartCount" :max="99">
    <el-button :icon="ShoppingCart" circle text @click="router.push('/trade/cart')" />
  </el-badge>
</el-tooltip>
```

```ts
async function fetchCartCount() {
  if (!userStore.isStudent) { cartCount.value = 0; return }
  try {
    const res = await cartList()
    cartCount.value = Array.isArray(res) ? res.length : 0
  } catch {
    cartCount.value = 0            // 失败静默为 0，不阻塞导航渲染
  }
}
```

**④ 购物车列表（`CartView.vue`）**：合计用课程快照价求和，支持单条移出 / 清空 / 去结算，移除后本地即时过滤，交互不阻塞。

### 3.3 验证

```
POST /carts {"courseId":3012} → 200 OK
GET  /carts                  → 返回 4 条（courseName/coursePrice 快照已补全）
GET  /orders/page?pageNo=1   → total=2，明细 details 含课程名与封面
```

---

## 4. 模块三：AI 助教交互优化

### 4.1 问题与修复

| 问题 | 修复 |
|---|---|
| 模型未出字时只显示空白框 | 新增"思考中。。。"占位：`streaming && !message.content` 时渲染 |
| 点号需要循环出现/消失动画 | 三个 `span` 点分别设置 `animation-delay`，`@keyframes` 控制 `opacity` 与轻微上浮，形成依次闪烁的等待动画 |
| 流式结束状态收敛 | `AssistantView` 用 `pending` 标记 AI 消息，`END`/`onDone`/`onError` 统一复位 |

### 4.2 核心代码

**① 思考中动画（`ChatMessageItem.vue`）**

```vue
<!-- AI 等待首字：思考中动画（点循环出现/消失） -->
<div v-else-if="streaming && !message.content" class="zx-thinking" aria-label="思考中">
  <span class="zx-thinking__text">思考中</span>
  <span class="zx-thinking__dots" aria-hidden="true">
    <i class="zx-dot" /><i class="zx-dot" /><i class="zx-dot" />
  </span>
</div>
```

```css
.zx-dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: var(--zx-primary);
  opacity: 0;
  animation: zx-dot-blink 1.4s ease-in-out infinite;
}
.zx-dot:nth-child(2) { animation-delay: 0.2s; }
.zx-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes zx-dot-blink {
  0%, 60%, 100% { opacity: 0; transform: translateY(0); }
  30%           { opacity: 1; transform: translateY(-3px); }
}
```

**② 流式状态收敛（`AssistantView.vue`）**

```ts
const aiMsg = reactive<LocalMessage>({ type: 'AI', content: '', pending: true })
messages.value.push(aiMsg)

await start({
  sessionId, question,
  onEvent: (ev) => {
    if (ev.type === 'START') aiMsg.agent = ev.agent || aiMsg.agent
    else if (ev.type === 'DELTA') { aiMsg.content += ev.content; scrollToBottom() }
    else if (ev.type === 'END')   { aiMsg.pending = false; scrollToBottom() }
  },
  onDone:  () => { aiMsg.pending = false },
  onError: () => { aiMsg.pending = false; if (!aiMsg.content) aiMsg.content = '_连接中断，请重试。_' },
})
```

模板中 `:streaming="m.pending && m.type === 'AI'"` → 只要 AI 消息仍 `pending` 且尚无内容，就展示"思考中。。。"动画；首字到达即切换为流式光标。

### 4.3 验证

```
POST /chat (text/event-stream)
→ id:0 event:message data:{"type":"START","content":"","agent":"RECOMMEND"}
→ id:1 event:message data:{"type":"DELTA","content":"\n根据您的需求，我为您推荐以下Java课程：..."}
→ id:2 event:message data:{"type":"END","content":"","agent":null}
```

未配置 LLM 时自动降级为模拟流式回复，事件格式与真实一致（`START`/`DELTA`/`END`）。

---

## 5. 交付清单

| 类型 | 文件 | 说明 |
|---|---|---|
| 新增 | `scripts/dev-start-backend.sh` | Git Bash 一键启动（内置端口注入防护） |
| 新增 | `scripts/dev-start-backend.ps1` | Windows PowerShell 一键启动 |
| 修改 | `README.md` | 第 5 节新增"⓪ 一键启动"，FAQ 新增 `SERVER__PORT` 排查条目 |
| 本报告 | `docs/FIX-REPORT-2026-09-11.md` | 根因、方案、核心代码与验证记录 |

> 三模块的业务代码修复此前已在工作区完成并保留（未提交），本次做了编译、启动与接口全链路验证。
