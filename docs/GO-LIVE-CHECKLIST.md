# 知行智学（zx-learn）· 上线准备清单

> 版本：2026-09-16（第二轮：**上线前全面审查**后的修订）
> 配套报告：`docs/GO-LIVE-AUDIT-2026-09-16.md`（本轮审查：问题清单 + 修复 + 结论）、
> `docs/FULL-QA-REPORT-2026-09-15.md`、`docs/PRODUCTION-READINESS-2026-09-15.md`
> 部署细节：`docs/DEPLOYMENT.md` · 容器编排：`docker-compose.prod.yml` · 迁移：`scripts/db-migrate.sh`

本清单把"上线"拆成可勾选的 7 组动作。**A/B/C 三组是硬门槛，未全部完成不得上线**；
D 组建议完成；E/F 是上线当天与上线后的动作；G 组是本轮审查新增的验证手段。

> ⚠ **本轮审查修正了两处"判据本身有问题"的地方**（否则门槛会失真）：
> 1. C 组的 `reconcile.sql` 原先声明"每条查询都应返回 0 行"——但其中第 8 项用的是绝对相等，
>    而 `course.sold` 兼作展示用的"学习人数"、种子里写的是营销基线值，**该条件永远不可能成立**。
>    现已把断言项与观察项分开（详见脚本头部口径说明）。
> 2. A 组原先只写"断言全绿"，未要求"代码已入库"。实测**大量源码从未提交**（见 A 组新增项），
>    `git clone` 出来的仓库无法构建运行——这是比任何单个缺陷都严重的上线风险。


---

## A. 代码与功能（硬门槛）

- [x] **全量接口测试通过**：246 个端点 × 3 身份矩阵 = 738 次探测，246/246 可达，0 个 5xx
- [x] **后端断言全绿**：`scripts/verify-full-suite.sh` → **128 / 128 通过**
- [x] **前端断言全绿**：`scripts/verify-frontend.sh` → **56 / 56 通过**（含 `vue-tsc` 类型检查与 `vite build` 生产构建）
- [x] **安全回归通过**：13 类垂直越权 + 19 类水平越权 + 12 个内部接口零信任 + 15 项注入 + 8 项响应脱敏
- [x] **数据一致性**：9 条跨库 SQL 不变量漂移数全为 0
- [x] **全新环境可重建**：已实测把 9 个脚本放到**真实空库**上连续执行 2 遍 → 两遍均 9/9 成功，
      校验：空讲义小节 0、空章节名 0、孤儿目录 0、课程数 13、目录行数 78、`cover_url` 列宽 512
      > 早期版本在**重建时会中断或产生脏数据**（种子 ID 冲突、裸 `ALTER` 非幂等、`course 1` 未种下），已修复；见 GL-001 ~ GL-006
- [x] **性能达标**：核心接口 P95 ≤ 69ms（阈值 500ms）；20 并发 × 200 请求 零失败、883.1 RPS、P95 40ms
- [x] **缺陷闭环**：QA 阶段 **11 个**（P0 ×1 / P1 ×4 / P2 ×4 / P3 ×2）+ 上线准备阶段 **5 个**（P1 ×3 / P2 ×2），
      共 **16 个**全部修复，逐条有回归验证点
      > 根因与验证点：`docs/PRODUCTION-READINESS-2026-09-15.md`；QA 阶段明细：`docs/FULL-QA-REPORT-2026-09-15.md`
- [x] **上线前审查修复项回归**：`scripts/verify-go-live-audit.sh` → **全部通过**
      （覆盖 RAG Embedding 链路、名额计数泄漏、跨服务调用、管理端越权面、前端 /api 反代链路）
- [x] **单元测试**：`bash scripts/mvn.sh -T 1C install` → **281 个 / 0 失败**
- [ ] 🔴 **代码已入库（当前未满足，最高优先级）**：`git status --porcelain` 显示 **250+ 项变更/未跟踪**，
      其中包括大量**从未提交过的源码与配置**：
      `docker-compose.prod.yml`、`scripts/`（全部验证脚本）、`sql/`（全部迁移）、`deploy/`、
      `zx-web/src/api/{points,exam,board,adminOrder,teacher}.ts`、
      `zx-web/src/components/course/CourseCover.vue`、`zx-web/src/composables/{useOwnedCourses,useMyCoupons}.ts`、
      `zx-web/public/covers/course-10~13.svg`、`zx-web/public/banners/` 等。
      > **后果**：`git clone` 得到的仓库**无法构建、无法运行**（缺源码、缺迁移、缺编排文件）。
      > 这是本轮审查发现的最高风险项——"能跑"目前只存在于当前这台工作机上。
      > **动作**：提交前先确认 `.gitignore` 已排除临时产物（已补 `tmp-*`），
      > 然后 `git add -A && git commit`；提交后**在干净目录重新 clone 并完整跑一次 A/C 组验证**。
```bash
# 复现命令（沙箱/本机一键）
cd <repo>
EXTRA_SPECS="zx-media:8085 zx-promotion:8088 zx-aigc:8089 zx-pay:8090 zx-search:8091 \
  zx-remark:8092 zx-message:8093 zx-data:8094" bash scripts/dev-up-core.sh
REUSE=1 bash scripts/verify-full-suite.sh      # 后端 136 断言
bash scripts/verify-frontend.sh                # 前端 56 断言
REUSE=1 bash scripts/verify-go-live-audit.sh   # 本轮审查修复项
```

---

## B. 配置与密钥（硬门槛）

- [ ] **复制环境变量模板**：`cp .env.example .env`，填写全部**必填**项（见下表）
- [ ] **替换 JWT 密钥**：`ZX_JWT_SECRET` 必须是随机强密钥（≥32 字节），生成：`openssl rand -base64 48`
      > 严禁沿用示例值。该密钥同时被网关与认证服务使用，泄露 = 可伪造任意用户身份。
- [ ] **配置支付回调密钥**：`PAY_CALLBACK_SECRET`（生成：`openssl rand -hex 32`）
      > zx-trade **缺失时启动失败**（fail-fast）；zx-pay 缺失时回调 **fail-closed 拒绝**（501）。
      > 该密钥由支付渠道下发，本地联调先用 Mock 值。
- [ ] **数据库连接指向真实实例**：`MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`
      > 各服务已全部改为 `${MYSQL_HOST:localhost}` 形式，无需再改 application.yml。
- [ ] **Redis 口令**：`REDIS_PASSWORD`
- [ ] **确认 `.env` 不会被提交**：`.gitignore` 已含 `.env` 与 `.env.*`（保留 `.env.example`）
- [ ] **网关 CORS**：`CORS_ALLOWED_ORIGINS` 填实际前端域名；同源部署（Nginx 反代）可留默认
- [ ] **确认网关白名单符合预期**（`zx-gateway/.../JwtProperties.java`）：
      `/accounts/login`、`/courses/page`、`/courses/{id}`、`/categorys/all`、`/coupons/page`、
      `/order-details/enrollNum`、`/files/view/**`、`/notify/alipay`、`/notify/wxpay`。
      → 除这些之外**一律需要 JWT**；白名单端点若是"按身份区分数据范围"的类型，走可选鉴权分支。

**必填环境变量速查**

| 变量 | 必填 | 说明 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | ✅ | MySQL 地址（容器内填服务名 `mysql`） |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | ✅ | 业务库账号（建议不要用 root） |
| `MYSQL_ROOT_PASSWORD` | ✅ | 仅容器初始化 MySQL 时使用 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | ✅ | Redis |
| `ZX_JWT_SECRET` | ✅ | JWT 签名密钥，≥32 字节随机值 |
| `PAY_CALLBACK_SECRET` | ✅ | 支付回调验签（zx-trade 缺失即启动失败） |
| `POSTGRES_URL` / `POSTGRES_USERNAME` / `POSTGRES_PASSWORD` | ⚠️ | 仅 RAG 知识库需要；不启用可留空 |
| `ZX_LLM_API_KEY` / `ZX_LLM_ENABLED` | ❌ | 留空则 AI 助教与学情降级为规则/模拟结果 |
| `OSS_*` / `MEDIA_STORAGE_MODE` | ❌ | 留空则图片走本地磁盘兜底（容器部署要挂持久卷） |
| `NACOS_ENABLED` / `NACOS_ADDR` | ❌ | 单机 false；**多实例/滚动发布必须 true** |
| `CORS_ALLOWED_ORIGINS` | ❌ | 跨域前端域名，逗号分隔 |
| `TX_DEAD_ALERT_WEBHOOK` | ❌ | 死信告警外发地址（建议生产接入） |

---

## C. 数据与迁移（硬门槛）

- [ ] **执行迁移**：`bash scripts/db-migrate.sh`
      * 9 个脚本（1 基线 + 8 增量）**全部幂等**，可安全重复执行；
      * 需要演示种子数据时加 `--with-seed`；**生产环境不要加**。
- [ ] **确认迁移自检全绿**：输出中每个脚本均为 `[OK]`，末尾汇总 `失败 0`
- [ ] **确认迁移顺带纠正的三处历史数据**（幂等，重复执行无副作用）：
      * `course.cover_url` / `course_draft.cover_url` 由 `VARCHAR(255)` 加宽到 **512**（原长度装不下 270+ 字符的封面 URL，曾被静默截断）
      * 演示课程 1 / 3009~3012 的名称、简介与课程目录**对齐**（历史库可能残留同 id 的另一套课程名）
      * 课程 3010 目录去重并补齐 4 个小节的讲义（原为空 → 会触发"空讲义小节"一致性断言）；
        课程 1 的第 1 章与 1.1 小节改为幂等写入（原为 UPDATE，空库上命中 0 行）
- [ ] **执行一致性对账**：`mysql -uroot -p --default-character-set=utf8mb4 < sql/reconcile.sql`
      * **断言类查询必须返回 0 行**（这些是真不一致）：
        `DEAD_MSG`、`STUCK_PENDING_MSG`、`PAID_WITHOUT_LESSON`、`PAID_WITHOUT_QUOTA_CONFIRM`、
        `CLOSED_WITHOUT_COUPON_REFUND`、`CLOSED_WITHOUT_QUOTA_RELEASE`、`QUOTA_COUNT_MISMATCH`、
        `SOLD_BELOW_CONFIRMED`
      * **观察类查询有结果属正常**（用于人工确认排除范围/自增趋势）：
        `EXCLUDED_DEMO_OR_FIXTURE_ORDER`、`SOLD_DELTA_OVERVIEW`
      * 重点看：`order_msg` 死信数、滞留待投递消息、已支付但课表缺失、券状态漂移、名额计数漂移
      > ⚠ 早期版本写的是"每条查询都应返回 0 行"，但第 8 项（销量对账）用的是绝对相等，
      > 而 `course.sold` 兼作前端展示的"学习人数"，演示种子写入的是营销基线（如 1233），
      > 该条件**在任何带种子的库上都无法成立** → 门槛形同虚设且可能掩盖真实漂移。
      > 现已改为只告警真正的故障模式（销量**小于**已确认数）并拆出观察项。
- [ ] **备份**：迁移前对现有库做一次物理备份（`mysqldump` 或快照）
- [ ] **确认新库已建**：`zx_pay`（支付单持久化新增，见 BUG-007）

> ⚠️ **迁移执行的两个坑（已实测踩过，脚本里已规避）**
> 1. Windows 版 `mysql.exe` 默认按**当前代码页（GBK）**解析脚本文件，UTF-8 汉字尾字节会
>    吞掉紧随的反引号 → `ERROR 1064`。必须显式 `--default-character-set=utf8mb4`。
> 2. `mysql < file` 在批处理模式下**遇到首个错误即中断**（不传 `--force`）。
>    因此任何"靠忽略报错实现幂等"的 DDL 都会让整条迁移链断掉。
>    本项目已把所有此类语句改为 `INFORMATION_SCHEMA` 存在性判断 + `PREPARE`。

---

## D. 部署形态与运行环境

### D1. 容器化（推荐）

- [ ] 先完成一次本机 Maven 打包（`Dockerfile` 直接 `ADD <模块>/target/<模块>.jar`，不在镜像内编译）：
      ```bash
      <mvn> -DskipTests -T 1C install
      ```
      > ⚠️ 必须先打包再构建镜像；模块 jar 在**各自模块目录**下（`zx-auth/target/zx-auth.jar`），
      > 不在仓库根的 `target/`（根 pom 是聚合 pom）。构建上下文已由根目录 `.dockerignore` 裁剪。
- [ ] 准备 `.env` 后启动：
      ```bash
      docker compose -f docker-compose.prod.yml up -d --build
      docker compose -f docker-compose.prod.yml ps      # 全部应为 healthy
      ```
- [ ] **确认服务间调用地址已被容器服务名覆盖**：`docker compose -f docker-compose.prod.yml config | grep SVC_`
      应看到 15 个 `SVC_*_URI=http://zx-<模块>:<端口>`。
      容器内的 `localhost` 是容器自身，**未覆盖会导致跨服务 Feign 全部打到自己身上**，
      且失败多数被 `fail-open` 吞掉，只表现为"券状态不同步""订单缺用户信息"等静默故障（见 GL-006）
- [ ] 确认**只有网关卡口对外**：`docker-compose.prod.yml` 只发布 `8080`，
      其余 15 个服务与中间件仅在 `zx-net` 网络内可达（防止绕过网关直连业务服务）
- [ ] 若 `MEDIA_STORAGE_MODE=local`，确认 `zx-media-data` 卷已挂载（否则重启丢图）

### D2. 单机 jar / systemd

- [ ] 用 `scripts/dev-up-core.sh` 同款方式启动**是开发用法**（前台进程、无探针）；
      生产请用 `startup.sh` / systemd 单元，并为每个服务配置 `Restart=always`
- [ ] 启动参数统一：`java -jar -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai <module>.jar`
- [ ] 从**仓库根或一级子目录**启动（根 `.env` 由 `spring.config.import: optional:file:./.env` 加载）

### D3. 反向代理与 HTTPS

- [x] **Nginx 反代配置已随仓库提供**：`zx-web/nginx.conf`（静态产物 + `/api` → 网关 `8080`，
      **剥离 `/api` 前缀**，并对 SSE 关闭缓冲）+ `zx-web/Dockerfile` + compose 的 `zx-web` 服务
      > 本轮审查发现的关键缺口：前端生产构建用 `VITE_API_BASE_URL=/api`，而**网关路由不含 `/api` 前缀**；
      > 开发态由 Vite devServer 的 `rewrite` 剥前缀，生产态此前**没有任何反代配置** →
      > 部署后前端全部接口 404。现已内置等价配置，缺了它不得上线。
- [ ] 全站 HTTPS（JWT 走 Authorization 头，明文 HTTP 会泄露凭证）
- [ ] 透传头：`X-Forwarded-For` / `X-Forwarded-Proto`；**不要**让外部传入 `user-info` / `role-info`
      （网关对白名单路径会主动剥离这两个头，非白名单路径会覆盖写入）

### D4. 可观测性

- [ ] 每个服务已暴露 `/actuator/health`、`/actuator/info`、`/actuator/metrics`
      * 生产建议把 `management.endpoint.health.show-details` 由 `always` 改为 `when-authorized`；
      * 这些端口**不要**经网关暴露（当前网关路由表里没有 `/actuator`，符合预期）。
- [ ] 配置死信指标告警：`zx.trade.order.msg.dead > 0` 即告警（或配 `TX_DEAD_ALERT_WEBHOOK`）
- [ ] 日志集中收集；关键日志关键字：`死信`、`Load balancer does not contain an instance`、
      `回调验签失败`、`OOM`

---

## E. 上线当天

- [ ] 停写 → 备份 → `db-migrate.sh` → 启动中间件 → 启动业务服务 → 启动网关
- [ ] 冒烟（逐条打勾）：
      * `GET /courses/page?pageNo=1&pageSize=5` → 200 且有数据
      * 学员 `POST /accounts/login`（13900000001/123456）→ 拿到 token
      * 带 token `GET /lessons/mine/course-ids` → 200
      * 不带 token `GET /users/page` → **401**（网关层拦截，不是业务码）
      * 学员 token 调 `GET /roles/page` → **业务码 403**（RBAC 生效）
      * `POST /notify/alipay` 无 sign → **401**；无密钥配置 → **501**
- [ ] 观察 15 分钟：错误率、P95、GC、DB 连接池
- [ ] 确认 `order_msg` 无死信、`sql/reconcile.sql` 全 0

---

## F. 上线后

- [ ] 首日巡检：死信指标、`order_msg` 滞留待投递、券状态漂移
- [ ] 密钥轮换演练（JWT 轮换需双密钥过渡，本轮未实现，见"已知边界"）
- [ ] 数据库慢查询与索引回归（尤其 `trade_order`、`user_coupon`、`course_catalogue`）

---

## 回滚方案

| 变更类型 | 回滚动作 | 风险 |
|---|---|---|
| 应用代码 | 切回上一个镜像 tag / 上一版 jar | 低 |
| 配置 | 恢复上一版 `.env` 并重启（`NACOS_ENABLED`、`GW_*_URI` 均可用变量回退） | 低 |
| 数据库 | 见下 | **中高** |

**数据库变更可回滚性评估**（**10 个**增量迁移均为**向后兼容的加列/加索引/数据纠正**）：

- 加列（`user_deleted`、`question_result.course_id`…）：回滚只需忽略该列，旧代码不受影响
- 加唯一键（`uk_user_course_paid`、`uk_user_credit`）：**不可盲目删除**，会重新引入重复支付/重复购买
- 新增库表（`zx_pay.*`）：回滚只需停用 zx-pay，不影响其它服务
- 数据纠正（券状态回填、课表自愈）：**不可自动回滚**，回滚前必须备份

> 结论：**迁移前必须先 `mysqldump` 备份**。应用回滚容易，数据回滚不可逆。

---

## 已知边界与后续建议（不阻断上线，但需知晓）

| # | 事项 | 现状 | 建议 |
|---|---|---|---|
| 1 | 支付回调整体链路 | zx-pay 的 `/notify/*` 已可被渠道回调（网关白名单 + HMAC 验签 + 幂等） | 接真实渠道时替换 `buildSignPayload` 为渠道规定的签名算法，并配置真实下发密钥 |
| 2 | 支付单已落库，但**订单→支付单的自动对账**未实现 | `PayClient` 仍是骨架，zx-trade 走自己的 `PayService` | 若要统一走 zx-pay，需补对账任务 |
| 3 | JWT 密钥轮换 | 单密钥，轮换即全员掉线 | 需要双密钥过渡（旧密钥仅验签、新密钥签发） |
| 4 | 用户手机号 | 管理端列表已展示层打码；**API 仍返回明文** | 若合规要求严格，应在服务端脱敏 + 增加"查看完整号码"的审计接口 |
| 5 | 秒杀限流 | 网关 `RequestRateLimiter` 基于 Redis，单节点令牌桶 | 多实例部署时共享 Redis 计数已可工作，注意 Redis 故障降级策略 |
| 6 | `sql/init_new_modules.sql` | 早期分模块初始化脚本，内容已被 `init.sql` 覆盖 | 属冗余文件，可清理（未删除，避免影响未知引用） |
| 7 | **RAG 知识库（向量检索）** | 本轮审查修复了「路径写死 `/v1/embeddings`」「模型名用了 OpenAI 的编码」「维度 1536 与智谱可选值不符」三个配置缺陷；但**当前智谱账号 embedding 资源包余额不足**（错误码 1113），实际仍无法产出真实向量 | 充值或改用有额度的 Embedding 供应商后，用教师端「知识库上传」灌入讲义验证；`/admin/knowledge/search` 可用教师账号做端到端确认 |
| 8 | AI 对话（LLM）链路 | ✅ 已实测可用：`POST {base-url}/chat/completions` + `glm-4.5-air` 正常返回（`ZX_LLM_ENABLED=true`） | 无需动作 |
| 9 | `zx-exam` → `course-service` 静态实例 | 本轮补上（原先缺失 → 题库 `courseName` 恒显示「课程 #<id>」占位；因被 try/catch 兜底而**静默**存在） | 启用 Nacos 后可移除该静态段 |
| 10 | 验证脚本自身可重复性 | 本轮修复两处**假失败/不可重复**：`verify-frontend.sh` 因沙箱批量删除守卫把"环境限制"报成"构建失败"；`verify-modules-e2e.sh` 的积分断言依赖不可重现的绝对基数（跑第二次必失败） | 断言一律改为跨接口自洽/增量口径，见 `docs/GO-LIVE-AUDIT-2026-09-16.md` |
| 11 | `sold` 字段语义 | 既作展示用「学习人数」又作已确认销量计数，导致无法用等号对账 | 若要严格对账，建议拆成 `sold_display` / `sold_confirmed` 两个字段（本轮未改，避免影响前端展示） |

---

## G. 本轮审查新增的验证手段

| 脚本 | 覆盖 | 断言数 |
|---|---|---|
| `scripts/verify-go-live-audit.sh` | RAG Embedding 链路可配置性、名额计数不泄漏、跨服务调用生效、管理端知识库越权面、前端 `/api` 反代链路、密钥占位提示 | 26（+5 运行态） |
| `scripts/mvn.sh` | Maven 调用包装器（Windows/Git Bash 下 `mvn.cmd` 会**静默空跑**，直连 launcher 时 `-classpath` 必须用正斜杠，否则 `ClassNotFoundException`） | — |
| `deploy/pgvector/migrate-embedding-dim.sql` | PG 向量列维度幂等迁移（旧数据卷 1536 → 1024） | — |
| `sql/2026-09-16-quota-locked-count-repair.sql` | 名额"在途占位"计数漂移修复（幂等） | — |
| 7 | `zx-media` 本地存储 | 无 OSS 时落本地磁盘 | 多实例部署须换对象存储，否则图片不共享 |

---

*本清单与 `docker-compose.prod.yml`、`scripts/db-migrate.sh`、`.env.example` 共同构成上线准备物；
所有"实施类"改造均可在 `docs/PRODUCTION-READINESS-2026-09-15.md` 中查到根因与回归验证点。*
