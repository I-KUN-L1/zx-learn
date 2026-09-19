# 知行智学（zx-learn）部署指南

> 覆盖：环境准备、Docker 容器化、一键启动脚本、生产配置切换、常见运维操作。

---

## 1. 环境与软件必要条件

### 1.1 必装软件

| 组件 | 版本 | 用途 |
|---|---|---|
| JDK | 21+（Temurin / OpenJDK 均可） | 全部服务运行环境（编译目标 `release=21`） |
| Maven | 3.9+ | 后端构建（IDEA 自带 Maven 亦可） |
| Node.js | ≥ 18（建议 20 LTS） | 前端 zx-web 开发与构建 |
| pnpm | ≥ 8 | 前端包管理（`npm i -g pnpm`） |
| Git | 近年版本即可 | 源码获取 |

### 1.2 中间件（推荐用 Docker 一键拉起，见第 3 节）

| 组件 | 版本 | 用途 | 默认端口 |
|---|---|---|---|
| MySQL | 8.x | 业务数据库（utf8mb4 / READ-COMMITTED，按服务分库） | 3306 |
| Redis | 7.x | 缓存、AI 会话记忆、优惠券秒杀预扣 | 6379 |
| PostgreSQL + pgvector | 14+ | zx-aigc 向量知识库（RAG 检索） | 5432 |
| RocketMQ | 4.9+ | 订单超时关单、支付成功开课等异步事件（NameServer + Broker） | 9876 |
| rocketmq-console | 可选 | 消息消费观测台 | 18080 |
| Nacos | 2.x（可选） | 注册/配置中心；本地默认关闭、走静态直连，生产再启用 | 8848 |

### 1.3 硬件与端口建议

- **内存 ≥ 16GB**：全量 16 服务 + 4 中间件同启约占 8~10GB，压测另计；
- **磁盘 ≥ 20GB**：Maven 本地仓库、Docker 镜像与日志；
- **空闲端口**：8080-8095（16 个服务）、5173（前端 dev）、3306 / 6379 / 5432 / 9876 / 18080（中间件）；
- **操作系统**：Windows 10+ / macOS / Linux 均可（本文命令以 Windows + PowerShell 为例）。

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

## 2.7 默认账号与初始数据

执行 `sql/init.sql`（或首次 Docker 启动自动挂载执行）后系统自带以下种子数据（全部幂等，可重复执行）：

| 类别 | 内容 |
|---|---|
| 默认学员 | `13900000001` / `123456`（纯数字） |
| 默认教师 | `13900000002` / `123456`（纯数字，含教师职称/简介） |
| 首个管理员 | 启动 zx-auth 自动创建：`13800000000`，初始密码为**系统预设默认值 `123456`**（可用环境变量 `ADMIN_INIT_PASSWORD` 覆盖），凭据同步写入 `.bootstrap-credentials`，首次改密后自动删除 |
| 分类 | 后端开发 / 前端开发 / 人工智能 及 6 个二级分类 |
| 课程 | 8 门上架课程（含 1 门免费课），封面统一 `/covers/course-XX.svg`（前端静态资源，位于 `zx-web/public/covers/`），2 门课含章节目录 |
| 优惠券 | 新人立减券（满 50 减 10，进行中） |

## 2.8 图片存储（OSS）配置

zx-media（8085）支持阿里云 OSS 与本地磁盘双模式，通过环境变量切换（见 `.env.example`）：

| 变量 | 说明 |
|---|---|
| `MEDIA_STORAGE_MODE` | `auto`（默认，OSS 配置齐备即用 OSS，否则本地兜底）/ `oss`（强制，缺失配置启动失败）/ `local` |
| `MEDIA_LOCAL_DIR` | 本地存储目录，默认 `./data/media` |
| `OSS_ENDPOINT` | 如 `https://oss-cn-hangzhou.aliyuncs.com` |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 AccessKey（建议 RAM 子账号，仅授权目标 Bucket） |
| `OSS_BUCKET` | 存储桶名 |
| `OSS_URL_PREFIX` | 可选，CDN 加速域名；为空用 `bucket.endpoint` 默认域名 |

- **本地兜底模式**：上传返回 `/api/files/view/{key}`，经前端代理/网关由媒体服务回源输出；
- **OSS 模式**：图片直传 OSS，访问接口 302 重定向到对象存储/CDN 地址；
- 上传接口 `POST /files` 需员工/教师登录；`GET /files/view/**` 已加入网关白名单供公开浏览（课程封面）。
- Bucket 建议开启公共读（或配合 `OSS_URL_PREFIX` 使用 CDN 鉴权），凭据仅经 `.env` 注入，严禁入库。

## 3. Docker 容器化部署

### 3.1 基础设施（MySQL + Redis）一键启动

```bash
docker compose up -d
```

> 根目录 `docker-compose.yml` 会启动 MySQL 8（端口 3306）与 Redis（6379），密码由 `.env` 中 `MYSQL_ROOT_PASSWORD` / `REDIS_PASSWORD` 提供。

### 3.2 业务服务容器化（单服务）

根目录 `Dockerfile` 为通用镜像，通过 `APP_NAME` 构建参数指定打包哪个服务：

```bash
# 1) 先编译产出 jar（镜像内不编译，直接 ADD target/<module>.jar）
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

### 3.3 全栈一键编排（推荐用于预发/生产）

`docker-compose.prod.yml` 把 **4 类中间件 + 16 个业务服务 + 1 个前端站点（zx-web）**全部编排进同一个自定义网络，
**对外只发布前端站点（默认 80）与网关端口 8080**（其余服务仅在集群内可达，防止绕过网关直连业务服务），
所有服务带 healthcheck 并按 `service_healthy` 依赖顺序启动。

```bash
# 1) 准备环境变量
cp .env.example .env && vi .env      # 至少填 MySQL/Redis/JWT/PAY_CALLBACK_SECRET

# 2) 本机产出 jar（compose 不在镜像内编译）
mvn -DskipTests -T 1C install

# 3) 初始化数据库结构（幂等，可重复执行）
bash scripts/db-migrate.sh

# 4) 起全栈（含前端站点）
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps      # 全部应为 healthy

# 5) 冒烟：前端走 /api 前缀（由 nginx 剥离后转发到网关）
curl -s "http://localhost/api/courses/page?pageNo=1&pageSize=5"
# 直连网关（不带 /api 前缀）：
curl -s "http://localhost:8080/courses/page?pageNo=1&pageSize=5"
```

> 容器内的服务地址通过环境变量注入，**不再依赖 localhost**：
> `MYSQL_HOST=mysql`、`REDIS_HOST=redis`、`ROCKETMQ_NAME_SERVER=rocketmq-namesrv:9876`、
> `GW_AUTH_URI=http://zx-auth:8081`（其余 `GW_*_URI` 同理）。
> 完整清单见 `.env.example` 的「部署形态」段。

#### 前端站点必须与网关同源（关键，否则接口全 404）

前端生产构建（`zx-web/.env.production`）使用 `VITE_API_BASE_URL=/api`，
请求形如 `/api/courses/page`；而**网关的路由断言不含 `/api` 前缀**（真实路径 `/courses/page`）。
开发态由 Vite 的 `server.proxy` + `rewrite` 剥离前缀，生产态必须由反向代理做等价处理：

```nginx
# zx-web/nginx.conf
location /api/ {
    proxy_pass http://zx-gateway:8080/;   # 末尾这个 "/" 就是"剥离 /api 前缀"
}
```

`zx-web/nginx.conf` 已内置该规则（同时关闭了 SSE 缓冲，AI 助教流式对话才能逐字返回），
由 `zx-web/Dockerfile`（Node 构建 → Nginx 托管）打进镜像，再由 compose 的 `zx-web` 服务发布。

**若不用容器化前端**（例如托管到 CDN / 独立 Nginx），必须自行保证两点：
1. `/api/**` 转发到网关时**去掉 `/api` 前缀**；
2. `proxy_buffering off`（或仅对 `/api/chat` 关闭），否则 SSE 会被缓冲、前端收不到增量输出。

#### 已有数据卷的 PG 向量维度迁移（一次性，幂等）

RAG 的向量维度由 `deploy/pgvector/init.sql` 决定（当前 `vector(1024)`）。
`init.sql` 只在 **PG 数据卷为空时**由容器入口执行；若你的环境是旧数据卷（曾为 1536 维），需手动执行一次迁移：

```bash
docker exec -i zx-learn-pg psql -U postgres -d zx_aigc < deploy/pgvector/migrate-embedding-dim.sql
```

该脚本幂等（维度已一致时不做任何变更）；执行后 `knowledge_chunk` 中旧向量会被清空，
需要用教师端「知识库上传」重新灌入讲义。
维度取值依据详见 `deploy/pgvector/init.sql` 注释与 `.env.example` 的 Embedding 段。

## 4. 生产配置切换（全部走环境变量，无需改 yml）

### 4.1 启用 Nacos（注册中心）

本地默认**直连**（Nacos 关闭时靠各服务 `application.yml` 里的 `spring.cloud.discovery.client.simple.instances` 静态实例）。
**多实例 / 滚动发布必须启用 Nacos**，否则服务间 Feign 调用找不到新实例。

```bash
# .env
NACOS_ENABLED=true
NACOS_ADDR=nacos:8848        # 容器内填服务名，本机填 localhost:8848
```

### 4.2 网关下游地址（两种模式任选其一）

网关 15 条路由的目标地址已是环境变量 `GW_<SERVICE>_URI`，默认 `http://localhost:<port>`：

```bash
# 模式 A：容器内直连服务名（不启用 Nacos）
GW_AUTH_URI=http://zx-auth:8081

# 模式 B：启用 Nacos 后走负载均衡（变量值直接写成 lb:// 即可，无需改 yml）
GW_AUTH_URI=lb://auth-service
```

可变量名：`GW_AUTH_URI` `GW_USER_URI` `GW_COURSE_URI` `GW_EXAM_URI` `GW_MEDIA_URI`
`GW_LEARNING_URI` `GW_TRADE_URI` `GW_PROMOTION_URI` `GW_AIGC_URI` `GW_PAY_URI`
`GW_SEARCH_URI` `GW_REMARK_URI` `GW_MESSAGE_URI` `GW_DATA_URI` `GW_INSIGHT_URI`。

### 4.2b 服务间调用地址（**容器化部署必改**）

上面 4.2 解决的是"浏览器 → 网关 → 服务"；服务与服务之间的调用（Feign）走的是另一套配置：
`spring.cloud.discovery.client.simple.instances`（静态服务发现）。

不启用 Nacos 时，6 个服务共 16 处下游地址的默认值是 `http://localhost:<port>`：

| 服务 | 需要解析的下游 |
|---|---|
| zx-auth | user |
| zx-exam | user、learning |
| zx-learning | course、user |
| zx-trade | course、user、learning、promotion |
| zx-aigc | course、trade |
| zx-insight | learning、exam、course、user、trade |

**容器内 `localhost` 是容器自身**，所以这些默认值在容器里会把请求打到自己身上。现已全部参数化：

```bash
# 容器化部署：改成容器服务名（单机 docker compose 已由 x-app-env 统一注入，无需手工填）
SVC_USER_URI=http://zx-user:8082
SVC_COURSE_URI=http://zx-course:8083
# …其余同理
```

可变量名：`SVC_AUTH_URI` `SVC_USER_URI` `SVC_COURSE_URI` `SVC_EXAM_URI` `SVC_MEDIA_URI`
`SVC_LEARNING_URI` `SVC_TRADE_URI` `SVC_PROMOTION_URI` `SVC_AIGC_URI` `SVC_PAY_URI`
`SVC_SEARCH_URI` `SVC_REMARK_URI` `SVC_MESSAGE_URI` `SVC_DATA_URI` `SVC_INSIGHT_URI`。

> ⚠️ 这类故障**不会**报成显眼的 503，多数会被调用方的 `fail-open` 兜底吞掉，
> 只表现为"券状态不同步""订单缺下单用户信息""支付后课程没开通"等**静默数据不一致**。
> 部署后建议执行一次：`docker compose -f docker-compose.prod.yml config | grep SVC_`，
> 确认 15 个 `SVC_*_URI` 都指向容器服务名。

### 4.3 跨域（CORS）

```bash
# .env：生产填实际前端域名，逗号分隔；同源部署（Nginx 同域反代）可留默认
CORS_ALLOWED_ORIGINS=https://learn.example.com
```

> 切勿填 `*`：网关 `allow-credentials: true` 与通配来源组合会被浏览器直接拒绝。

### 4.4 支付回调（上线必做）

`/notify/alipay`、`/notify/wxpay` 已在网关白名单（渠道不持有平台 JWT），
真实性由 zx-pay 的 **HMAC-SHA256 验签**保证：

```bash
# .env：zx-trade 缺失该值会「启动失败」；zx-pay 缺失时回调一律 fail-closed 拒绝（501）
PAY_CALLBACK_SECRET=<openssl rand -hex 32>
```

### 4.5 AI 大模型接入

```yaml
# zx-aigc/src/main/resources/application.yml（或直接给环境变量）
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
docker compose -f docker-compose.prod.yml down

# 健康检查：业务服务各自暴露 actuator（端口 = 服务端口）
curl http://localhost:8081/actuator/health          # zx-auth
# ⚠ 网关没有 actuator 依赖，不要探测 :8080/actuator/health（会 404）；
#   网关是否存活请直接探任意已路由路径或 TCP 端口：
#   bash -c '</dev/tcp/127.0.0.1/8080'

# 数据一致性对账（每条查询都应返回 0 行）
mysql -uroot -p --default-character-set=utf8mb4 < sql/reconcile.sql

# 死信指标（>0 表示有业务事件永久未送达）
curl http://localhost:8087/actuator/metrics/zx.trade.order.msg.dead
```

## 6. 版本发布流程

1. `git tag v1.0.0` 打版本号
2. `mvn clean package -DskipTests` 产出可执行 jar
3. **执行数据库迁移**：`bash scripts/db-migrate.sh`（幂等；生产**不要**加 `--with-seed`）
4. Docker 构建并推送镜像（`ZX_VERSION=1.0.0` 作为 tag）
5. 按依赖顺序滚动发布（mysql/redis/mq → user → course → auth → trade/promotion/pay → 其余 → gateway）
6. 上线后跑冒烟与对账，见 `docs/GO-LIVE-CHECKLIST.md` 第 E/F 节

> 完整上线清单（含回滚方案、密钥要求、已知边界）见 **`docs/GO-LIVE-CHECKLIST.md`**。
