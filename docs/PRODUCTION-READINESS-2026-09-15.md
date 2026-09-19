# 知行智学（zx-learn）· 上线准备与整改记录

> 日期：2026-09-15 · 范围：全量 QA 后的**上线准备阶段**（配置外置化 / 迁移幂等 / 容器编排 / 支付加固 / 数据一致性）
> 配套：`docs/GO-LIVE-CHECKLIST.md`（勾选清单）、`docs/FULL-QA-REPORT-2026-09-15.md`（QA 阶段 11 个缺陷）、
> `docker-compose.prod.yml`、`scripts/db-migrate.sh`、`docs/DEPLOYMENT.md`

## 0. 结论

- QA 阶段发现的 **11 个缺陷**（BUG-001 ~ BUG-011）已全部修复；本阶段又发现并修复 **7 个上线阻塞缺陷**（GL-001 ~ GL-007）。
- **上线前复测**（2026-09-16，用户反馈"界面提示系统繁忙、部分界面拿不到数据"）又定位并修复 **5 个运行期缺陷**（B-001 ~ B-005），以及 **2 个深层问题**（D-001 业务异常码值污染、D-002 验证脚本假阳性断言）。详见 §1.1。
- 全部验证通过：单元测试 **280 / 0 失败**；数据迁移 **9/9 成功且可重复执行**；后端断言 **136 / 136**；前端断言 **56 / 56**；定向回归 **15 / 15**；支付回调白名单端到端通；**空库重建**（`init.sql` → 8 个增量迁移，连跑两遍）后数据不变量全绿。
- **上线物已齐备**：环境变量模板、迁移执行器、生产容器编排、上线清单、回滚方案。

---

## 1. 本阶段新增的 7 个缺陷（均已修复并有回归验证点）

> 共同特征：**不在功能链路上，只在"重建环境 / 重复执行 / 严格写入 / 容器化"时才暴露**，
> 属于典型的"平时看不出来、上线一定出事"类缺陷。

### GL-001（P1）`init.sql` 孤儿优惠券数据 UPDATE 非幂等 → 重复执行中断整条迁移链

| 项 | 内容 |
|---|---|
| 现象 | `bash scripts/db-migrate.sh` 第 1 步基线即失败：`ERROR 1062 Duplicate entry '2001-6001' for key 'user_coupon.uk_user_coupon'` |
| 根因 | 演示数据里有"把历史遗留券挂到演示账号 2001 名下"的 `UPDATE user_coupon SET user_id=2001 WHERE id=7001`。**首次执行成立，第二次执行该券已在 2001 名下**，再 UPDATE 成同一 (user_id, coupon_id) 组合即撞唯一键 |
| 修复 | 改成"先探测再动作"的条件式写法：`SET @demo2001_has_6001 := (...)`，命中则跳过 UPDATE 并清理重复行，未命中才 UPDATE |
| 验证 | `db-migrate.sh` 连跑 2 次均 `成功 9 / 失败 0`（修复前第 1 次就断） |

### GL-002（P1）`2026-09-14-order-delete-and-close-fix.sql` 裸 ALTER 依赖"忽略报错"实现幂等

| 项 | 内容 |
|---|---|
| 现象 | 迁移第 2 次执行报 `ERROR 1060 Duplicate column name 'user_deleted'` |
| 根因 | 该迁移用**裸 `ALTER TABLE ... ADD COLUMN`**，靠"列已存在时忽略报错"冒充幂等。但 `mysql < file` 是**批处理模式，遇首个错误即中断**（未传 `--force`），于是"加列失败"变成"整条迁移链断掉" |
| 修复 | 改为仓库统一的幂等范式：`INFORMATION_SCHEMA.COLUMNS` 存在性判断 + `PREPARE / EXECUTE / DEALLOCATE`（MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`）；列与索引各一段，末尾带自检查询 |
| 验证 | 连续 2 次执行均 `[OK]`；`user_deleted` 列与 `idx_user_status` 索引只建一次 |

### GL-003（P1）课程目录种子 **ID 冲突** → 上架课程出现 4 个空讲义小节（全新环境必现）

| 项 | 内容 |
|---|---|
| 现象 | `verify-full-suite.sh` 阶段 9 不变量断言失败：`上架课程存在空讲义小节数 = 0 <期望=0 实际=4>`；课程 3010 目录行数 **12 行 / 4 章**（其余 12 门课都是 6 行 / 2 章） |
| 根因 | **同一课程 id 被两套种子占用**：<br>① `init.sql` 定义 `course 3010 = Vue 3 进阶：Pinia 与组合式 API`，并种下目录 `4301~4306`（旧写法**没有 `content` 列 → 小节讲义为 NULL**）；<br>② `2026-09-14-course-content-and-catalogue.sql` 把 `3010` 当成「Python 自动化办公实战」，又插了一套 `601001~601022`。<br>两套目录叠加 → 同一门课 4 章 8 节，其中 init.sql 那套的 4 个小节讲义为空。<br>同类问题还有 3009 / 3011 / 3012：**章节内容与 `init.sql` 里的课程名对不上**（如课程叫"MySQL 8 性能优化与索引设计"，章节却是"服务治理 / 注册发现与网关"）。<br>叠加 `INSERT IGNORE` 的"静默跳过"，历史库里一直没暴露，**但只要在新环境按 `init.sql → 迁移` 重建就必现** |
| 修复 | ① `init.sql`：3010 的目录种子补上 `key_points` + `content`（4 个小节各配真实讲义），并把该 INSERT 改为 `ON DUPLICATE KEY UPDATE`，让历史库也能幂等补齐；<br>② 迁移脚本：**删除**误挂在 3010 上的 `601001~601022`（幂等 `DELETE`），并把 3009 / 3011 / 3012 三块章节与讲义**改写成与 `init.sql` 课程定义一致**的内容（沿用原 id，靠 `ON DUPLICATE KEY UPDATE` 顺带纠正历史库）；<br>③ `init.sql` 的演示课程 INSERT 由 `INSERT IGNORE` 改为 `ON DUPLICATE KEY UPDATE`，把历史库里残留的旧课程名纠偏 |
| 验证 | 空讲义小节 **0**、空章节名 **0**、残留 `6010xx` **0**；3009~3012 全部为 **6 行 / 2 章 / 0 空讲义**；`verify-full-suite.sh` 阶段 9 全绿（128/128） |

### GL-004（P2）`course.cover_url` 列宽不足 → 封面 URL 被**静默截断**

| 项 | 内容 |
|---|---|
| 现象 | 把演示课程写入语句由 `INSERT IGNORE` 改为严格写入后立刻报 `ERROR 1406 Data too long for column 'cover_url' at row 1` |
| 根因 | 演示课程封面用的是文生图 CDN 地址，**实际长度 269~285 字符**，而 `course.cover_url` 与 `course_draft.cover_url` 都是 `VARCHAR(255)`。旧写法 `INSERT IGNORE` 会把"数据过长"降级为 warning 并**截断写入** —— 库里存的是被砍掉尾巴的 URL，图片必然 404（前端被 `CourseCover.vue` 的渐变兜底掩盖，所以一直没被发现） |
| 修复 | ① `init.sql` 的建表 DDL 改为 `VARCHAR(512)`（新库直接够用）；② 在 `init.sql` 的"新增课程"段**之前**加一段幂等加宽 DDL（`INFORMATION_SCHEMA` + `PREPARE`，作用于既有库；顺序很关键——必须先加宽再写入）；③ 迁移脚本中留注释说明该变更由 `init.sql` 承载，避免两处 DDL 分叉 |
| 验证 | `cover_url` 列宽 = **512**；3009~3012 的封面 URL 长度 269 / 270 / 279 / 285 全部完整落库 |

### GL-005（P2）`course 1` 从未被种下 → 空库重建后留下 4 条"父课程不存在"的孤儿目录

| 项 | 内容 |
|---|---|
| 现象 | 空库重建测试中发现：`course_catalogue` 有 4 条记录（`900003` / `900011` / `900012` / `900013`，均 `course_id=1`）指向一个**不存在的课程**；课程 1 的"第 1 章"与"1.1 小节"在空库里根本没有 |
| 根因 | `2026-09-14-course-content-and-catalogue.sql` 第四节明确把课程 1 当作既有数据规范化：它用**两条 `UPDATE`** 去修正 `900001`（第 1 章）与 `900002`（1.1 小节）的英文占位名——**假设这两行已由更早的种子存在**；而 `init.sql` 既没有种下课程 1 这条 `course`，也没有种下 `900001/900002`。于是空库上：两条 UPDATE 命中 0 行（静默），只留下后面 INSERT 的 4 条目录 → 孤儿 + 章节缺失。历史库之所以正常，是因为它的课程 1 来自仓库之外的更早种子 |
| 修复 | ① `init.sql` 新增"基础演示课程"块，幂等种下课程 1（`SpringBoot 入门到实战`，与历史库同名同价，补齐分类/教师/简介等元数据）；② 迁移第四节里那两条 `UPDATE` 改为 **`INSERT ... ON DUPLICATE KEY UPDATE`**（讲义原文复用，既有库与空库都成立，且仍然完成"英文占位名 → 中文名"的规范化） |
| 验证 | 空库重建后：**孤儿目录 0**、课程数 **13**、目录行数 **78**（13 × 6）、课程 1 目录 **6 行 / 2 章**；生产库迁移后同样 `孤儿目录 = 0` |

### GL-006（P0）容器化部署下服务间调用全部指向容器自身 → 跨服务链路整体失效（**最严重**）

| 项 | 内容 |
|---|---|
| 现象 | 按 `docker-compose.prod.yml` 起容器后，所有**跨服务**能力静默失效：券核销后状态不同步（前端永远显示"未使用"）、退单/管理端订单补不出下单用户信息、订单支付后课程不自动开通、学情报告取不到成绩。HTTP 层看不出异常（多数调用被 `fail-open` 兜底），只在数据上表现为"就是没生效" |
| 根因 | 服务间调用走 `spring.cloud.discovery.client.simple.instances` 静态服务发现，而 **6 个服务（zx-auth / zx-exam / zx-learning / zx-trade / zx-aigc / zx-insight）的地址被写死成 `http://localhost:808x`，共 16 处**。开发时"本机跑、服务同机"成立；**容器里 localhost 指容器自身**——`zx-trade` 会去 `zx-trade:8083` 找课程服务，连接直接被拒。<br>更隐蔽的是 `docker-compose.prod.yml` 当时显式设 `NACOS_ENABLED=false`（走静态实例），同时只给 `zx-course` 注入了 `GW_MEDIA_URI`（而 zx-course 并不存在任何下游 Feign 调用，是个**无效覆盖**）——真正需要覆盖的 16 处反而一个都没覆盖 |
| 修复 | ① 6 个服务的 `application.yml`：16 处 `- uri: http://localhost:80xx` 全部改为 **`- uri: ${SVC_XXX_URI:http://localhost:80xx}`**，本机开发默认值不变，容器/多机部署可由环境变量覆盖；<br>② `docker-compose.prod.yml` 的 `x-app-env` 锚点统一注入 **15 个 `SVC_<服务>_URI`**（值为容器服务名，如 `SVC_COURSE_URI: http://zx-course:8083`），16 处调用点全部被覆盖；<br>③ 删掉 `zx-course` 上那行无效的 `GW_MEDIA_URI`（该服务无下游调用，留着重只会误导排查）；<br>④ `.env.example` 新增「服务间调用地址」段，说明为何容器内必须覆盖；<br>⑤ 顺带补齐 compose 中 `POSTGRES_USER` 与 `.env.example` 中 `ZX_VERSION` / `ZX_LLM_CHAT_PATH` / `ZX_LLM_TEMPERATURE` / `ZX_LLM_TOP_P` / `ZX_LLM_MAX_TOKENS` 的声明，使"配置里引用的变量"与"模板里声明的变量"完全一一对应 |
| 验证 | `docker compose -f docker-compose.prod.yml config` 校验通过（exit 0），且 `SVC_*` 已正确合并进每个业务服务；`grep` 复核 6 个服务 jar 内 `BOOT-INF/classes/application.yml` 均为新写法；<br>**A/B 对照实验**（同一 jar 起两个 zx-auth 实例，下游 zx-user 监听 18082）：<br>· A 实例 `SVC_USER_URI=http://localhost:18082`（正确）→ `POST /accounts/login` 返回 `{"code":200}`；<br>· B 实例 `SVC_USER_URI=http://127.0.0.1:65530`（无人监听，等价于容器内 localhost）→ 返回 `{"code":401,"msg":"用户名或密码错误"}`。<br>两者**仅因这一个环境变量不同**而结果相反 → 证明覆盖真实生效；同时 B 也复现了本缺陷最危险的一面：**依赖不可达被翻译成"用户名或密码错误"这类误导性业务提示**，而不是显眼的 503；<br>本机 16 服务全量断言仍 **128 / 128**（默认值未变，开发链路不受影响） |

### GL-007（P0）`Dockerfile` 的 `ADD` 路径不存在 → 任何镜像都构建不出来（容器化部署完全走不通）

| 项 | 内容 |
|---|---|
| 现象 | `docker compose -f docker-compose.prod.yml up -d --build` 在第一个服务就失败：`ADD failed: file not found in build context or excluded by .dockerignore: stat target/zx-auth.jar: file does not exist` |
| 根因 | `docker-compose.prod.yml` 对 16 个服务统一使用 `context: .`（仓库根）+ `args.APP_NAME=<模块名>`，而 `Dockerfile` 写的是 **`ADD target/${APP_NAME}.jar`** —— 解析出来是 `target/zx-auth.jar`，即**仓库根目录下的 `target/`**。但根 `pom.xml` 是**聚合 pom**，模块 jar 实际产出在**各自模块目录**（`zx-auth/target/zx-auth.jar`），根 `target/` 根本不存在这个文件。也就是说：本机 `mvn package` 全绿、`docker compose config` 也全绿，**唯独真正 `docker build` 时必然失败** |
| 修复 | `Dockerfile` 的 `ADD` 改为 **`ADD ${APP_NAME}/target/${APP_NAME}.jar /app.jar`**（带上模块目录），与 compose 的 `context: .` 约定对齐；同时补齐注释说明"模块 jar 在各自模块目录下、根 pom 是聚合 pom"。<br>另外新增 **`.dockerignore`**：排除 `.git` / `zx-web`（含 `node_modules`）/ 各模块 `target/classes` / `logs` / `docs` / `sql` / `**/*.class` 等，并**明确保留 `<模块>/target/<模块>.jar`**；同时把 `.env` 挡在上下文之外（避免本地凭据进入构建上下文）。未加之前构建上下文含整个前端依赖树，达数百 MB |
| 验证 | **实跑一次真实构建**：`docker build --build-arg APP_NAME=zx-user -t zx-learn/zx-user:verify .` → `BUILD_EXIT=0`，日志中 `#7 [3/3] ADD zx-user/target/zx-user.jar /app.jar` 成功；镜像内 `/app.jar` 大小 151,862,354 字节、日期与本轮打包一致（证明装进去的正是本次构建的 fat jar） |

---

## 1.1 上线前复测新发现的 5 个运行期缺陷（B-001 ~ B-005）

> 触发场景：用户反馈"访问界面提示系统繁忙，部分界面无法获得数据"。
> 定位方法：从 `logs/svc/*.log` 中 grep `CommonExceptionAdvice: 系统异常`，提取真实堆栈，再逐个还原触发路径。
> 共同特征：**都是运行期才会走到、且被统一兜底处理器翻译成同一句"系统繁忙"的业务 500** —— 前端只能看到一个笼统报错，用户侧表现就是"界面拿不到数据"。

### B-001（P0）课程列表在登录态下必崩 —— 三元表达式自动拆箱 NPE

| 项 | 内容 |
|---|---|
| 现象 | **登录用户**打开课程列表即返回 `{"code":500,"msg":"系统繁忙，请稍后再试"}`，列表空白；**匿名访问反而正常**。日志中该堆栈出现 **7 次**，为复测期最高频故障 |
| 根因 | `CourseController.page()` 第 85 行：`Integer status = UserContext.getUser() == null ? STATUS_ON_SHELF : status;`。三元表达式两个分支分别是 `int` 常量与 `Integer` 变量，Java 会**做数值提升并自动拆箱** —— 登录态下走的是 `status` 分支，而 `status` 为 `null`（管理端语义是"不过滤"），拆箱即 `NullPointerException`。同时该写法也**丢失了 null 语义**：即便不崩，`null` 也会被替换成"仅上架" |
| 修复 | 改为显式 if/else 分支赋值，保留 `null` 语义：<br>`Integer effectiveStatus;`<br>`if (UserContext.getUser() == null) { effectiveStatus = STATUS_ON_SHELF; } else { effectiveStatus = status; }`<br>`return R.ok(courseService.pageQuery(query, name, effectiveStatus));` |
| 验证 | 学员 / 教师 / 管理员三种身份 + 均**不带 `status`** 参数 → `code=200` 且返回真实数据（`total=13`）；匿名 → 只返回上架课程；`status=1` 作为对照组正常。**该条曾长期无覆盖**，是本次复测暴露"128 项断言全绿但线上必崩"的直接原因（见 D-002） |

### B-002（P2）缺必填项被误判为"服务端故障"

| 项 | 内容 |
|---|---|
| 现象 | `POST /menus`、`POST /roles`、`POST /categories` 不传名称 → `500 系统繁忙`。调用方无法区分"我参数填错了"和"服务挂了" |
| 根因 | 三个 `add()` 方法**未做前置校验**，`null` 名称直接落库，撞上数据库 `NOT NULL` 约束 → `DataIntegrityViolationException` → 被兜底 `@ExceptionHandler(Exception.class)` 吞成 500 |
| 修复 | ① 三个 `add()` 前置判空，缺名称抛 `BadRequestException("…名称不能为空")` → **400**；<br>② `CommonExceptionAdvice` 新增两个专项 handler：`DataIntegrityViolationException` → 400（从 MySQL 异常消息里正则提取列名，给出可读原因）、`MultipartException` → 400（同时修掉 B-005） |
| 验证 | 缺必填项 → `code=400` 且 msg 含字段名；正常入参 → 201/200 |

### B-003（P1）发送短信接口必崩 —— `@Async` 标注在返回 `R` 的方法上

| 项 | 内容 |
|---|---|
| 现象 | `POST /sms/send` → `500 系统繁忙` |
| 根因 | `SmsController` 的发送方法标注了 `@Async`，但返回类型是 `R`（既非 `void` 也非 `Future`）。Spring 在代理阶段即抛 `IllegalArgumentException`，**方法体根本不会执行** |
| 修复 | 去掉 `@Async`（同步发送，业务本身无需异步返回），同步删除对应 `import` 并加注释说明"返回值非 void/Future，不可用 @Async" |
| 验证 | `POST /sms/send` 正常返回；`/sms/**` 全路径无 5xx |

### B-004（P1）订单导出必崩 —— 全局响应包装器把二进制 body 包成了 `R`

| 项 | 内容 |
|---|---|
| 现象 | `GET /orders/admin/export` → `500 系统繁忙`，日志为 `ClassCastException` |
| 根因 | 该接口返回 `ResponseEntity<byte[]>`（CSV 流）。全局的 `WrapperResponseBodyAdvice` 会把所有响应体包装成 `R`，包装器内部对非预期类型强转 → `ClassCastException`。这类"二进制/文件下载"接口必须**显式绕过包装** |
| 修复 | 方法上补 `@NoWrapper`（包装器已支持跳过该注解） |
| 验证 | 导出接口返回 **HTTP 200 + `Content-Type: text/csv` + 可解析 CSV 正文**；`api-matrix.py` 的 `bin_ok` 清单同步加入 `/orders/admin/export`（CSV 是预期非 JSON，不再被误报为异常端点） |

### B-005（P2）非 multipart 请求打到上传接口 → 500

| 项 | 内容 |
|---|---|
| 现象 | 向文件上传接口发非 `multipart/form-data` 请求 → `500 系统繁忙` |
| 根因 | 框架抛 `MultipartException`，未被专项处理，落到兜底 500。属典型的"客户端用法错误被记成服务端故障" |
| 修复 | 同 B-002 ②：`CommonExceptionAdvice` 新增 `MultipartException` → **400** |
| 验证 | 非 multipart 上传 → `code=400` 且提示请求格式有误 |

---

## 1.2 深层问题（修复后一并根治）

### D-001（P0）`BizIllegalException` 默认业务码是 500 → 全项目业务异常伪装成"系统繁忙"

| 项 | 内容 |
|---|---|
| 现象 | 排查时无法从日志/响应区分"业务规则拒绝"（如重复下单、库存不足）与"服务端真故障"，两者的 `code` 都是 500，监控也把正常业务拒绝计入 5xx |
| 根因 | `BizIllegalException` 默认构造器写的是 `super(500, message)`，全项目 **49 处**业务异常都在用这个默认码 |
| 修复 | 默认码改为 `ErrorCode.BIZ_ILLEGAL`（**1001**），并新增 `BizIllegalException(ErrorCode, String)` 构造器作为推荐用法。**约定：新增业务异常一律显式传语义化 ErrorCode** |
| 回归 | 大量脚本/测试原先按 500 编码了旧行为，已 grep 全部断言并同步（越权/重复下单等断言改为接受 1001/1002/1003 等语义码，**不再接受 500**） |

### D-002（P0）验证脚本存在**假阳性断言** → 接口整体崩掉也被判成"通过"

| 项 | 内容 |
|---|---|
| 现象 | `verify-full-suite.sh` 跑出 **128/128 全绿**，但 B-001 这类"接口 500"在线上真实存在。即**测试绿灯与线上可用性脱钩** |
| 根因 | 大量断言用的是 `'ok' if isinstance(d, dict) else '5xx'` 这种写法。而业务异常响应体 `R{code:500}` **本身就是一个 dict** → 断言恒判通过。也就是说：**接口彻底炸掉时，这条断言反而不会失败** |
| 修复 | 在脚本中新增三个强判定函数并替换全部假阳性断言：<br>· `OK200()` —— 必须 HTTP 200 且 `body.code == 200`；<br>· `CE()` —— 必须为 400 / 404（客户端错误，用于"应被拒绝"的用例）；<br>· `NO5XX()` —— 任何 5xx 一律失败。<br>另新增 **8 条**"登录身份 + 不带 `status`"组合断言（学员/教师/管理员各 1 + 匿名 + `status=1` 对照 + 数据非空校验），**正是 B-001 长期缺覆盖的触发路径**；并修正越权订单断言、放宽重复注册断言（非 0/200/500 即通过）。<br>**新增约定：写断言前先自问 —— 接口返回 500 时，这条断言会失败吗？** 若不会，则该断言无效 |
| 验证 | 断言总数 **128 → 136**；修复后再跑全部 136 条通过，且定向回归 `logs/tmp/verify_fixes.py` 针对 5 个缺陷的 15 个检查点全部 PASS |

### R-001（由 D-001/B-002 的修复引入的回归，已彻底解决）

| 项 | 内容 |
|---|---|
| 现象 | 给 `CommonExceptionAdvice` 加上 `DataIntegrityViolationException` 之后，`zx-media` / `zx-message` **启动直接失败**：`NoClassDefFoundError: org/springframework/dao/DataIntegrityViolationException` |
| 根因 | 这两个服务**没有数据库依赖**，类路径里没有 `spring-tx` / `spring-jdbc`。编译期因为 MyBatis-Plus 的 optional 依赖可见而**没有报错**，把问题一路掩盖到运行时启动阶段 |
| 修复 | `zx-common/pom.xml` 显式声明 `spring-tx`，且**不能标 optional**（否则不会传给使用方）；用 `python zipfile` 校验两个服务的 fat jar 内均含 `BOOT-INF/lib/spring-tx-6.1.14.jar`。<br>**约定：改 `zx-common` 后必须实起全部 16 个服务验证 —— 单元测试发现不了这类问题** |
| 验证 | 16 个服务全部启动成功、端口 80xx 全部 LISTENING、`verify-full-suite.sh` 136/136 |

---

## 2. 本阶段完成的上线准备项

| # | 事项 | 交付物 | 说明 |
|---|---|---|---|
| 1 | **配置全量外置化** | 16 个服务的 `application.yml`、`.env.example` | JDBC `${MYSQL_HOST:…}`；Nacos `${NACOS_ENABLED:false}` / `${NACOS_ADDR:…}` / `${NACOS_CONFIG_ENABLED:false}`；网关 15 条路由 `${GW_*_URI:…}`；CORS `${CORS_ALLOWED_ORIGINS:…}`。**改配置不再需要动代码** |
| 2 | **数据迁移执行器** | `scripts/db-migrate.sh` | 固定顺序 `init.sql → 8 个增量 → 可选 test-data.sql`；`--with-seed` 开关；强制 `--default-character-set=utf8mb4`；用 `PIPESTATUS[0]` 取 mysql 真实退出码（避免被管道吞掉） |
| 3 | **支付单持久化** | `zx-pay` + `sql/2026-09-15-pay-order-persistence.sql` | 由进程内 LRU 内存表改为落库：`zx_pay.pay_order`（`uk_biz_order_no`）+ `pay_notify_log`（`uk_channel_pay_no`）；同业务单号**复用而非新建**（复活已关闭单）；回调幂等；新增 15 个单测 |
| 4 | **支付回调白名单 + 验签** | `JwtProperties.java`、`AuthGlobalFilter`、`PayOrderController` | 网关白名单放行 `/notify/alipay`、`/notify/wxpay`（第三方回调无 JWT，靠 HMAC 验签）；业务层 `HMAC-SHA256` 验签，**未配密钥 fail-closed 501**、无签名 401 |
| 5 | **生产容器编排** | `docker-compose.prod.yml` | 21 个服务（4 中间件 + 16 业务 + 网关）；**仅网关发布 8080**，其余只在 `zx-net` 内可达；全部带 healthcheck 并用 `service_healthy` 约束启动顺序；`x-app-env` 锚点统一注入 |
| 6 | **上线清单与回滚方案** | `docs/GO-LIVE-CHECKLIST.md` | A~F 六组动作（硬门槛 / 配置 / 数据 / 部署形态 / 上线当天 / 上线后）+ 数据库可回滚性评估 |
| 7 | **死信可观测** | `OrderMsgMetrics`、`DeadMsgReplayJob` | Micrometer `Gauge zx.trade.order.msg.dead` + 可选 Webhook 告警（冷却 30 分钟、异步不阻塞主流程）；死信重放与告警解耦 |
| 8 | **前端产物瘦身与脱敏** | `utils/markdown.ts`、`utils/format.ts` | `highlight.js` 改为 `lib/core` + 按需注册 11 种语言；管理端手机号展示层打码 `maskPhone()` |

---

## 3. 验证记录（可复现）

```bash
cd <repo>
# 1) 数据迁移：连跑两次，证明幂等
bash scripts/db-migrate.sh && bash scripts/db-migrate.sh     # 两次都应「成功 9 / 失败 0」

# 2) 启动 16 个服务
EXTRA_SPECS="zx-media:8085 zx-promotion:8088 zx-aigc:8089 zx-pay:8090 zx-search:8091 \
  zx-remark:8092 zx-message:8093 zx-data:8094" bash scripts/dev-up-core.sh

# 3) 全量断言
REUSE=1 bash scripts/verify-full-suite.sh     # 后端 136 断言
bash scripts/verify-frontend.sh               # 前端 56 断言

# 3b) 运行期缺陷定向回归（B-001 ~ B-005，经网关 8080）
PY=<python> bash -c 'exec(open("logs/tmp/verify_fixes.py").read())'   # 15 个检查点，应全部 PASS

# 4) 单元测试
<mvn> -DskipTests=false test

# 5) 容器镜像可构建性（GL-007 回归点）
docker build --build-arg APP_NAME=zx-user -t zx-learn/zx-user:verify .   # 应 exit 0
docker compose -f docker-compose.prod.yml config --quiet                 # 应 exit 0
```

| 验证项 | 结果 |
|---|---|
| 单元测试 | **280 个 / 0 失败**，BUILD SUCCESS |
| 数据迁移（生产库，连续 2 次） | **成功 9 / 失败 0**（幂等成立） |
| **空库重建**（9 个脚本连续执行 2 遍） | 两遍均 **9/9 成功**；重建后：空讲义小节 0、空章节名 0、**孤儿目录 0**、课程数 13、目录行数 78、`cover_url` 列宽 512 |
| 后端全量断言 | **136 / 136 通过**（修复前 128；新增/强化 8 条登录态断言并替换假阳性断言，见 D-002） |
| **运行期缺陷定向回归**（`logs/tmp/verify_fixes.py`） | **15 / 15 PASS**（B-001 ~ B-005 逐条端到端复现并验证已修复） |
| 关键回归点（实测） | 学员/教师/管理员**登录 + 不带 `status`** 请求课程列表 → `code=200`、`total=13`；`GET /orders/admin/export` → **HTTP 200 + `text/csv`**；缺必填项（菜单/角色/分类）→ `code=400`；非 multipart 上传 → `code=400`；16 服务 + 前端 5173 全部就绪 |
| 前端全量断言 | **56 / 56 通过**（`vue-tsc` 0 错误 + `vite build` 成功） |
| 支付回调白名单端到端 | 匿名 `POST /notify/alipay` → `{"code":401,"msg":"回调验签失败"}`（**已到达业务层**，非网关 401）；`POST /notify/wxpay` → HTTP 200；`GET /courses/page` → HTTP 200 |
| 前端产物 | `dist` 3844KB → **2956KB**；最大分片 `markdown 1023KB` → **`element 933KB`**；JS+CSS gzip 1086KB → **803KB** |
| **容器镜像构建**（实跑） | `docker build --build-arg APP_NAME=zx-user -t zx-learn/zx-user:verify .` → **exit 0**，`ADD zx-user/target/zx-user.jar` 成功，镜像内 `/app.jar` 151,862,354 字节（本次构建产物） |
| **`SVC_*` 覆盖 A/B 实验** | A（正确地址）→ `code:200`；B（错误地址）→ `code:401 用户名或密码错误`。**仅凭该环境变量不同即结果相反**，覆盖生效；同时复现"依赖不可达被翻译成误导性业务提示"的静默故障 |
| 配置项与模板一致性 | 配置中引用的 **65** 个环境变量，除 `$${POSTGRES_USER}`（容器内部变量，属 compose 转义写法）外，**全部在 `.env.example` 中已声明** |
| 静态语法检查 | Shell **15/15**（`bash -n`）、YAML **21/21**、XML **19/19**、JSON/Python 全部可解析（`zx-web/tsconfig.json` 含注释，属 JSONC 合法写法） |

> **空库重建怎么做的**：把 `sql/` 下 9 个脚本里的库名前缀整体替换为 `zzq_`（`zx_` 只出现在 9 个库名里，无歧义），
> 在真实空库上按 `init.sql → 8 个增量` 顺序执行两遍，校验完再 `DROP` 掉测试库。
> 这一步专门用来抓"只有全新环境才暴露"的问题 —— GL-003 / GL-004 / GL-005 都是这样被找出来的。

---

## 4. 本阶段变更文件（上线需一并提交）

**代码**
- `zx-gateway`：`filter/AuthGlobalFilter.java`、`util/JwtUtils.java`、`config/JwtProperties.java`、`application.yml`
- `zx-pay`：`pom.xml`、`application.yml`、`PayApplication.java`、`domain/po/PayOrder.java`、`domain/po/PayNotifyLog.java`、`mapper/PayOrderMapper.java`、`mapper/PayNotifyLogMapper.java`、`service/PayOrderService.java`、`controller/PayOrderController.java`、`src/test/.../PayOrderServiceTest.java`
- `zx-trade`：`metrics/OrderMsgMetrics.java`、`job/DeadMsgReplayJob.java`、`application.yml`
- `zx-web`：`utils/markdown.ts`、`utils/format.ts`、`views/admin/AdminUsersView.vue`
- 16 个服务的 `application.yml`（配置外置化）；其中 `zx-auth` / `zx-exam` / `zx-learning` / `zx-trade` / `zx-aigc` / `zx-insight` 另含 GL-006 的静态实例地址参数化
- 4 处测试同步：`zx-aigc` 的 `SessionServiceTest` / `SessionControllerTest`、`zx-learning` 的 `SignInServiceTest`、`zx-auth` 的 `AccountServiceTest`

**代码（2026-09-16 复测修复 B-001 ~ B-005 / D-001 / R-001）**
- `zx-course`：`controller/CourseController.java`（B-001 拆箱 NPE）、`controller/CategoryController.java`（B-002 前置判空）
- `zx-message`：`controller/SmsController.java`（B-003 去掉 `@Async`）
- `zx-trade`：`controller/AdminOrderController.java`（B-004 补 `@NoWrapper`）
- `zx-auth`：`controller/MenuController.java`、`controller/RoleController.java`（B-002 前置判空）
- `zx-common`：`advice/CommonExceptionAdvice.java`（B-002 / B-005 新增 400 专项 handler）、`exceptions/BizIllegalException.java`（D-001 默认码 500 → 1001）、`pom.xml`（R-001 显式依赖 `spring-tx`，非 optional）

**SQL / 脚本 / 编排**
- `sql/init.sql`（DDL 与演示种子幂等化、`cover_url` 加宽、3010 目录补讲义）
- `sql/2026-09-14-course-content-and-catalogue.sql`（目录种子去重与对齐）
- `sql/2026-09-14-order-delete-and-close-fix.sql`（幂等 DDL）
- `sql/2026-09-15-pay-order-persistence.sql`（新增）
- `scripts/db-migrate.sh`（新增）、`docker-compose.prod.yml`（新增）、`.env.example`
- `scripts/verify-full-suite.sh`（D-002：新增 `OK200()` / `CE()` / `NO5XX()` 强判定并替换假阳性断言，断言 128 → **136**）、`scripts/api-matrix.py`（`/orders/admin/export` 纳入"预期非 JSON"端点清单）、`logs/tmp/verify_fixes.py`（新增，B-001 ~ B-005 定向回归 15 个检查点）

**文档**
- `docs/GO-LIVE-CHECKLIST.md`、`docs/DEPLOYMENT.md`、`docs/PRODUCTION-READINESS-2026-09-15.md`（本文件）

---

## 5. 已知边界（不阻断上线）

1. **支付回调签名算法**：当前为通用 `HMAC-SHA256`，接入真实渠道时需替换为渠道规定的签名/证书校验方式。
2. **订单 ↔ 支付单自动对账**未实现：`PayClient` 仍是骨架，zx-trade 走自己的 `PayService`。
3. **JWT 密钥轮换**：单密钥，轮换即全员掉线；需双密钥过渡（旧密钥仅验签）。
4. **手机号**：管理端列表已打码，API 仍返回明文；合规严格时应在服务端脱敏。
5. **`sql/init_new_modules.sql`**：早期分模块初始化脚本，内容已被 `init.sql` 覆盖，属冗余文件（未删除，避免影响未知引用）。
6. **`zx-media` 本地存储**：无 OSS 时落本地磁盘，多实例部署须改对象存储。
7. **演示数据与生产数据分离**：`sql/test-data.sql` 仅供演示，生产执行迁移时**不要加 `--with-seed`**。
8. **本地验证前需自行启动服务**：本次复测在受管沙箱内完成，沙箱后台任务结束后其拉起的服务进程会被回收（实测端口 0/16 监听）。要在浏览器里验证界面，请在**本地终端或 IDEA** 启动：
   `EXTRA_SPECS="zx-media:8085 zx-promotion:8088 zx-aigc:8089 zx-pay:8090 zx-search:8091 zx-remark:8092 zx-message:8093 zx-data:8094" bash scripts/dev-up-core.sh`，前端 `cd zx-web && node node_modules/vite/bin/vite.js --port 5173`。

---

*本记录与 `docs/GO-LIVE-CHECKLIST.md` 互为配套：本文件说明"改了什么、为什么改、怎么验证"，清单负责"上线前逐项打勾"。*
