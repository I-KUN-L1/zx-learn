# 知行智学（zx-learn）部署指南

> 覆盖：环境准备、Docker 容器化、一键启动脚本、生产配置切换、常见运维操作。

---

## 1. 环境要求

| 组件 | 版本 | 用途 |
|---|---|---|
| JDK | 21+ | 运行环境 |
| Maven | 3.8+ | 构建 |
| MySQL | 8.x | 业务数据库 |
| Redis | 5+ | 缓存 / AI 会话记忆 |
| Docker | 20+（可选） | 容器化部署 |

## 2. 本地开发（IDEA）

1. 初始化数据库：`mysql -uroot -p < sql/init.sql`
2. 配置连接（凭据通过环境变量注入，见根目录 `.env.example`；可覆盖 `MYSQL_USERNAME / MYSQL_PASSWORD / REDIS_HOST / REDIS_PORT / REDIS_PASSWORD`）：

```
MYSQL_USERNAME / MYSQL_PASSWORD / REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
```

3. 编译：`mvn clean install -DskipTests`
4. 启动顺序（核心链路 8 个服务）：

| 顺序 | 模块 | 启动类 | 端口 |
|---|---|---|---|
| 1 | zx-user | UserApplication | 8082 |
| 2 | zx-course | CourseApplication | 8083 |
| 3 | zx-trade | TradeApplication | 8087 |
| 4 | zx-promotion | PromotionApplication | 8088 |
| 5 | zx-learning | LearningApplication | 8086 |
| 6 | zx-auth | AuthApplication | 8081 |
| 7 | zx-gateway | GatewayApplication | 8080 |
| 8 | zx-aigc | AigcApplication | 8089 |

> 顺序原因：auth 登录时通过 Feign 调用 user 校验账号；trade 下单校验课程价格、learning 维护课表一致性均通过 Feign 调用 course；gateway 是统一入口；aigc 提供 AI 能力。
> 本地直连（不启用 Nacos）时，trade / learning 参考本文档 **2.6 命令行启动课程交易链路** 的命令行方式声明 course-service 静态实例，并通过 `user-info` 请求头透传用户身份。

5. 验证：

```bash
# 登录（凭据由部署方配置）
curl -X POST http://localhost:8080/accounts/admin/login \
  -H "Content-Type: application/json" \
  -d '{"cellPhone":"<账号>","password":"<密码>"}'

# 携带 token 查课程
curl http://localhost:8080/courses/page -H "Authorization: Bearer <token>"

# AI 对话
curl -X POST http://localhost:8080/chat/text \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","question":"帮我推荐一门 Java 课程"}'
```

### 2.6 命令行启动课程交易链路（course / trade / promotion / learning）

> 适用：本机已装 MySQL/Redis、未起 Docker/Nacos 时，用可执行 jar 直接启动（本地直连，Nacos `enabled: false`）。
> 前提：MySQL 本机运行且已执行 `mysql -uroot -p < sql/init.sql`；Redis 本机运行，**Redis 密码须与本机实例一致**（示例实例 `requirepass 123456`）。

**打包**（`-am install` 触发 spring-boot repackage 产出可执行 fat jar）：

```bash
mvn -pl zx-course,zx-trade,zx-promotion,zx-learning -am install package -DskipTests
```

**启动命令**（jar + 覆盖数据源/Redis 密码；凭据也可用 `.env`，此处为命令行直传）：

```bash
# course :8083
java -jar zx-course/target/zx-course.jar \
  --server.port=8083 \
  --spring.datasource.username=root --spring.datasource.password=123456 \
  --spring.data.redis.password=123456

# trade :8087 —— 必须声明 course-service 静态实例，否则 Feign 负载均衡报 "does not contain an instance" 返回 500
java -jar zx-trade/target/zx-trade.jar \
  --server.port=8087 \
  --spring.datasource.username=root --spring.datasource.password=123456 \
  --spring.data.redis.password=123456 \
  --spring.cloud.discovery.client.simple.instances.course-service[0].uri=http://localhost:8083

# promotion :8088
java -jar zx-promotion/target/zx-promotion.jar \
  --server.port=8088 \
  --spring.datasource.username=root --spring.datasource.password=123456 \
  --spring.data.redis.password=123456

# learning :8086（application.yml 已配置 course-service 静态实例，无需命令行追加）
java -jar zx-learning/target/zx-learning.jar \
  --server.port=8086 \
  --spring.datasource.username=root --spring.datasource.password=123456 \
  --spring.data.redis.password=123456
```

**关键提示**：

- 各服务都通过 `user-info` 请求头注入用户身份（网关鉴权后透传），本地直连需手动带：`-H "user-info: 1"`。
- RocketMQ(9876) 未启动时 trade 会优雅降级：下单/支付主链路可用，超时关单走**定时兜底扫描**；优惠券核销流水（`coupon_use_record`）由 MQ 消费端异步落库，故 MQ 未启用时该表为空（预期行为）。
- MQ 恢复后，本地消息表滞留消息由扫描任务自动补投（指数退避），链路自愈；故障演练与对账验证见 `docs/TRADE-CONSISTENCY.md` 第 5/7 节，三方一致性用 `sql/reconcile.sql` 校验。
- course/learning 消费端已启用（`rocketmq.name-server` + 各自独立 `consumer-group`）：zx-course 消费名额事件、zx-learning 消费支付成功开课事件；MQ 未启用时支付后课程不会自动入课表（可手动调 learning 接口补齐）。
- 用券下单走 Redis Lua 原子预扣（防超卖/超领），需先预置券库存，否则判"库存不足"：
  ```bash
  redis-cli -a 123456 SET "coupon:stock:{couponId}" 100
  ```
- PowerShell 中向 curl.exe 传 JSON 请用**单引号**包裹且内部用 `\"` 转义（`-d '{\"courseId\":1,\"totalFee\":19900}'`）；若用双引号会被二次转义产生 `\` 前缀，服务端报 `JSON parse error`（HTTP 400/500）。
- 全链路联调（下单→核券→模拟支付→学习记录→签到）示例：
  ```bash
  # 核券：建券→发放→兑换
  curl -s -X POST localhost:8088/coupons -H "Content-Type: application/json" \
    -d '{\"name\":\"满减券\",\"discountAmount\":500,\"thresholdAmount\":1000,\"totalNum\":100,\"exchangeCode\":\"T06CODE\"}'
  curl -s -X PUT   localhost:8088/coupons/{id}/issue
  curl -s -X POST "localhost:8088/user-coupons/redeem?couponId={id}&code=T06CODE" -H "user-info: 1"
  # 下单（金额须与课程价格一致，用券时 totalFee<=price）
  curl -s -X POST localhost:8087/orders/placeOrder -H "Content-Type: application/json" -H "user-info: 1" \
    -d '{\"courseId\":1,\"totalFee\":19900}'
  # 模拟支付：先取签名，再回调
  curl -s "localhost:8087/orders/pay/sign?orderId={orderId}&amount=19900&payNo=PAY001"
  curl -s -X POST localhost:8087/orders/pay/callback -H "Content-Type: application/json" \
    -d '{\"id\":{orderId},\"totalFee\":19900,\"payType\":1,\"payNo\":\"PAY001\",\"sign\":\"{sign}\"}'
  # 学习记录（进度单调递增，倒退会被拦截）与签到
  curl -s -X POST localhost:8086/learning-records/progress -H "Content-Type: application/json" -H "user-info: 1" \
    -d '{\"courseId\":1,\"lessonId\":1,\"progress\":80,\"learnDuration\":60}'
  curl -s -X POST localhost:8086/sign-ins -H "user-info: 1"
  ```

## 3. Docker 容器化部署

### 3.1 基础设施（MySQL + Redis）一键启动

```bash
docker compose up -d
```

> 根目录 `docker-compose.yml` 会启动 MySQL 8（端口 3306）与 Redis（6379），密码由 `.env` 中 `MYSQL_ROOT_PASSWORD` / `REDIS_PASSWORD` 提供。

### 3.2 业务服务容器化

根目录 `Dockerfile` 为通用镜像，通过 `APP_NAME` 构建参数指定打包哪个服务：

```bash
# 1) 先编译产出 jar
mvn clean package -DskipTests

# 2) 构建镜像并启动（示例：auth 服务）
./startup.sh -c zx-auth -n zx-auth -d target/zx-auth.jar -p 8081
```

`startup.sh` 参数说明：

| 参数 | 说明 | 默认 |
|---|---|---|
| -c | 容器名 | 必填 |
| -n | 项目名（镜像名 zx-learn/{project}） | 必填 |
| -d | jar 路径 | 必填 |
| -p | 对外端口 | 8080 |
| -o | JVM 参数 | `-Xms256m -Xmx512m` |
| -a | 调试端口（映射容器 5005） | 空 |

## 4. 生产配置切换

### 4.1 启用 Nacos（注册中心 + 配置中心）

本地默认**直连**（Nacos 关闭）。生产环境：

```yaml
# 每个服务 application.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
        enabled: true
      config:
        enabled: true
```

### 4.2 网关路由切换负载均衡

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service     # 由 http://localhost:8081 改为 lb://service-name
```

### 4.3 AI 大模型接入

```yaml
# zx-aigc/src/main/resources/application.yml
zx:
  llm:
    base-url: https://api.deepseek.com   # OpenAI 兼容地址
    api-key: <your-key>
    model: deepseek-chat
    enabled: true
```

## 5. 常见运维操作

```bash
# 查看日志
docker logs -f zx-auth

# 重启服务
docker restart zx-auth

# 清理全部
docker compose down

# 服务健康检查（各服务已暴露管理端点）
curl http://localhost:8080/actuator/health
```

## 6. 版本发布流程

1. `git tag v1.0.0` 打版本号
2. `mvn clean package -DskipTests` 产出可执行 jar
3. Docker 构建并推送镜像
4. 按依赖顺序滚动发布（user → course → auth → gateway → aigc）
