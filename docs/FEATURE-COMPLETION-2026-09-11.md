# 功能补全与测试数据初始化 · 交付说明（2026-09-11）

> 本次交付覆盖四项需求：**师生题库联动**、**教师端数据可视化补全**、**管理员端订单管理**、**全量测试数据初始化**。
> 全部改动均已通过编译、单元测试、前端类型检查与生产构建，并经**网关端到端验证（31 项断言全绿）**。

---

## 0. 交付清单

| 类别 | 文件 | 说明 |
|---|---|---|
| 数据库 | `sql/2026-09-11-exam-and-admin-module.sql` | 题库表新建 + 答题记录列迁移（幂等） |
| 数据库 | `sql/test-data.sql` | 全量测试数据 + 基线复位 + 自检（幂等） |
| 后端 | `zx-common/.../handler/JsonStringListTypeHandler.java` | `List<String>` ↔ JSON 列映射 |
| 后端 | `zx-exam/.../domain/po/Question.java`、`QuestionResult.java` | 题库持久化实体 |
| 后端 | `zx-exam/.../service/QuestionService.java`、`QuestionResultService.java` | 题库与判分服务 |
| 后端 | `zx-exam/.../controller/QuestionController.java`、`QuestionResultController.java` | 题库 / 答题 / 错题本 / 教师统计接口 |
| 后端 | `zx-exam/.../domain/vo/*`（5 个） | 错题本、题目统计、学员正确率、答题总览、判分结果 |
| 后端 | `zx-trade/.../controller/AdminOrderController.java` | 管理端订单接口 |
| 后端 | `zx-trade/.../service/AdminOrderService.java` | 订单分页/筛选/改状态/退款审核/CSV 导出 |
| 前端 | `zx-web/src/composables/useEcharts.ts` | 图表渲染时序缺陷修复（核心） |
| 前端 | `zx-web/src/api/exam.ts`、`adminOrder.ts` | 新增 API 层 |
| 前端 | `zx-web/src/views/exam/ExamView.vue`、`WrongBookView.vue` | 学员在线答题 + 错题本 |
| 前端 | `zx-web/src/views/teacher/TeacherAnswersView.vue` | 教师端答题正确率看板 |
| 前端 | `zx-web/src/views/admin/AdminOrdersView.vue` | 管理员端订单管理 |
| 验证 | `scripts/e2e-verify.sh` | 端到端验证脚本（31 项断言） |

---

## 1. 模块一：教师端 ↔ 学员端题库联动

### 1.1 问题诊断（改造前）

| # | 问题 | 影响 |
|---|---|---|
| 1 | 题目存放在 `ConcurrentHashMap` 内存中 | 服务重启即丢；多实例数据不一致 |
| 2 | `GET /questions/list` 强制要求 `ids` 参数 | 学员端不传参 → 400，无法拉取题目 |
| 3 | `POST /question-results` 只接收**单个对象** | 前端交卷提交的是**数组** → 反序列化失败 400 |
| 4 | 判分依赖前端传入的 `correct`/`score` | 学员可伪造满分 |
| 5 | 题目无「归属教师 / 关联课程 / 发布状态」 | 无法形成"教师发布 → 学员接收"闭环 |

### 1.2 实现思路

**① 题目从内存升级为 MySQL 持久化**，并在表上补齐三个联动字段：

```sql
-- sql/2026-09-11-exam-and-admin-module.sql
CREATE TABLE IF NOT EXISTS `question` (
    `id`         BIGINT       NOT NULL,
    `name`       VARCHAR(255) NOT NULL COMMENT '题干',
    `type`       INT DEFAULT 1  COMMENT '1单选/2多选/3判断',
    `options`    VARCHAR(2048) COMMENT '选项JSON数组',
    `answer`     VARCHAR(16)   COMMENT '正确答案(A / AB)',
    `analysis`   VARCHAR(1000) COMMENT '解析',
    `teacher_id` BIGINT        COMMENT '归属教师(发布人)',
    `course_id`  BIGINT        COMMENT '关联课程(联动锚点)',
    `status`     TINYINT DEFAULT 1 COMMENT '0草稿/1已发布',
    ...
    KEY `idx_teacher` (`teacher_id`), KEY `idx_course` (`course_id`), KEY `idx_status` (`status`)
);
```

选项列以 JSON 数组存储，由自研类型处理器完成映射（无需引入额外依赖）：

```java
// Question.java
@TableField(typeHandler = JsonStringListTypeHandler.class)
private List<String> options;
```

**② 学员端只接收「已发布」题目**——发布开关即师生联动闸门：

```java
// QuestionController#list —— 用 UserContext 判断调用者身份，决定是否过滤草稿
@GetMapping("/list")
public R<List<Question>> list(@RequestParam(required = false) List<Long> ids) {
    // 教师/管理员可看全部；学员只能看已发布
    boolean onlyPublished = !UserContext.hasRole(UserRole.TEACHER.code(), UserRole.STAFF.code());
    return R.ok(questionService.listByIdsOrAll(ids, onlyPublished));
}
```

**③ 交卷改为批量 + 服务端判分**，并回传逐题结果供前端即时反馈：

```java
// QuestionResultService#submitBatch —— 忽略客户端传入的 correct/score，按题库标准答案重新判分
public List<SubmitResultVO> submitBatch(List<QuestionResult> results) {
    ...
    boolean correct = isCorrect(question, item.getUserAnswer());
    record.setCorrect(correct);
    record.setScore(correct ? question.getScore() : 0);
    ...
    vo.setCorrectAnswer(question.getAnswer());   // ← 回传正确答案与解析，前端可即时展示
    vo.setAnalysis(question.getAnalysis());
}

/** 判分：忽略大小写与字母顺序，多选需集合完全一致 */
private boolean isCorrect(Question q, String userAnswer) {
    String expected = normalizeAnswer(q.getAnswer());  // 仅保留字母并大写
    String actual   = normalizeAnswer(userAnswer);
    // AB 与 BA 等价
    return expected.chars().sorted().toArray().length == actual.chars().sorted().toArray().length
        && new String(expected.chars().sorted().toArray())
           .equals(new String(actual.chars().sorted().toArray()));
}
```

### 1.3 接口清单（经网关 8080）

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/questions/all` | 教师 | 全量题库（含草稿） |
| GET | `/questions/teacher/page` | 教师 | 分页 + 关键词/类型/课程筛选 |
| POST | `/questions` | 教师 | 新增（可指定 `courseId`、`status`） |
| PUT | `/questions/{id}` | 教师 | 修改 |
| **PUT** | **`/questions/{id}/publish?published=`** | 教师 | **发布 / 撤回（联动开关）** |
| DELETE | `/questions/{id}` | 教师 | 删除 |
| **GET** | **`/questions/list`** | 学员 | **接收已发布题目** |
| GET | `/question-results/mine` | 学员 | 我的答题记录 |
| GET | `/question-results/mine/stats` | 学员 | 我的正确率 |
| **GET** | **`/question-results/mine/wrong`** | 学员 | **错题本（题干+我的作答+正确答案+解析）** |
| **POST** | **`/question-results`** | 学员 | **批量交卷 → 逐题判分结果** |
| POST | `/question-results/single` | 学员 | 单题提交（错题重做） |
| **GET** | **`/question-results/teacher/overview`** | 教师/管理员 | **整体正确率 + 每题正确率 + 每学员正确率** |
| GET | `/question-results/teacher/question/{id}` | 教师/管理员 | 单题作答明细（下钻） |

### 1.4 前端页面

- **`ExamView.vue`（改造）**：接入真实题库 → 支持单选/多选/判断三种题型作答 → 交卷后逐题展示对错、正确答案与解析 → 一键重练错题。
- **`WrongBookView.vue`（新增）**：按题目去重的错题本，展示「我的作答 vs 正确答案 vs 解析」，支持按课程筛选与重做。
- **`TeacherAnswersView.vue`（新增）**：教师端答题看板——整体正确率 + 横向柱状图列出**最薄弱题目**（正确率越低越红），下钻查看每题作答明细。
- **`TeacherQuestionsView.vue`（改造）**：新增「关联课程」「分值」「学员可见（发布/草稿）」字段与**发布开关**、题型筛选。

---

## 2. 模块二：教师端数据可视化补全

### 2.1 根因分析（图表空白）

图表容器通常位于 `v-if="profile"` 或抽屉内，原 `useEcharts` 存在三处时序缺陷：

| # | 缺陷 | 后果 |
|---|---|---|
| 1 | 仅在 `onMounted` 里 `echarts.init(el)` | 挂载时 `el.value` 仍为 `undefined`，初始化被跳过 |
| 2 | `watch(option)` 默认 `flush: 'pre'`，在组件**重渲染之前**触发 | 数据到达时 DOM 还没生成容器，`el.value` 依旧为空 → **图表永不渲染** |
| 3 | 容器处于 `display:none` / 0 尺寸时 init | ECharts 拿到 0×0 画布，后续不再自适应 → 表现为"区域一片空白" |

### 2.2 修复方案（`zx-web/src/composables/useEcharts.ts`）

```ts
// ① 监听"元素本身"（而非 option），flush: 'post' 保证在 DOM 更新之后执行
watch(el, (dom) => {
  if (!dom) { disposeChart(); return }
  if (chart && chart.getDom() !== dom) disposeChart()  // 容器被 v-if 重建 → 必须重建实例
  renderWhenReady()
}, { flush: 'post' })

// ② 等 nextTick + requestAnimationFrame 让布局稳定；容器仍不可见则逐帧重试（有限次）
function renderWhenReady(retry = 5) {
  nextTick(() => requestAnimationFrame(() => {
    if (hasSize()) render()
    else if (retry > 0) renderWhenReady(retry - 1)
  }))
}

// ③ 容器级自适应：抽屉展开/侧栏折叠/栅格断点切换都能触发重排
function ensureChart() {
  if (!hasSize()) return null                       // 0 尺寸不 init，避免空白画布
  chart = echarts.init(el.value as HTMLElement)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value as HTMLElement)
  ...
}
```

同时导出 `ready` 标志，供组件做**淡入过渡**（图表就绪后再显形，避免闪烁）。

### 2.3 补全的图表

| 界面 | 图表 | 数据源字段 |
|---|---|---|
| 学员画像（`InsightView`） | 能力**雷达图** | `abilities[{name,value}]` |
| 学员画像（`InsightView`） | 近 7 日学习**折线图** | `trends[{date,duration}]` |
| 教师查看学员（`TeacherStudentsView`） | 能力**雷达图** | `abilities` |
| 教师查看学员（`TeacherStudentsView`） | **各维度能力柱状图**（新增，雷达图补充视图） | `abilities` |
| 教师查看学员（`TeacherStudentsView`） | 近 7 日学习**折线图** | `trends` |
| 教师答题情况（`TeacherAnswersView`） | **薄弱题目横向柱状图**（新增） | `questionStats[{questionName,accuracy}]` |

> 所有图表均接入 `useEcharts`，具备响应式布局（`grid.containLabel`、`ResizeObserver`、`animationDuration` 渲染过渡）。

---

## 3. 模块三：管理员端订单管理

### 3.1 权限设计（三重防线）

1. **路由**：`/admin/orders` 位于 `AdminLayout` 之下，父级 `meta.roles = ['admin']`；
2. **后端注解**：`AdminOrderController` 类级 `@RequireRole(UserRole.STAFF)`，由 `RoleInterceptor` 统一校验；
3. **契约隔离**：管理端走 `/orders/admin/**`，与学员端 `/orders/**` **控制器分离**，学员端契约不被筛选条件污染。

> 管理端使用**数据库状态**（`0待支付 1已支付 2已关闭 3退款中 4已退款`），不做学员端那套 `1/2/3/5/6` 映射，便于运营与财务对齐。

### 3.2 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/orders/admin/page` | 分页 + 多条件筛选（订单号/用户/关键词/状态/课程/时间区间/金额区间） |
| GET | `/orders/admin/{id}` | 订单详情 |
| PUT | `/orders/admin/{id}/status?status=` | 修改订单状态（`0..4` 校验，保护"已退款"终态） |
| GET | `/orders/admin/refunds` | 退款申请分页（审核工作台） |
| PUT | `/orders/admin/refund/audit` | 退款审核（`{refundId, approved, remark}`） |
| GET | `/orders/admin/statistics` | 各状态订单量 + 已支付销售额 |
| GET | `/orders/admin/export` | CSV 导出（UTF-8 BOM，Excel 双击可读，单次上限 5000 行） |

### 3.3 前端页面 `AdminOrdersView.vue`

- **顶部统计卡**：订单总量 / 待支付 / 已支付 / 退款中 / 销售额。
- **多条件筛选区**：订单号、用户、状态、课程、下单时间区间、实付金额区间（前端按「元」输入，自动换算为「分」下发）。
- **列表**：ElTable + 分页，行内「详情 / 改状态」操作。
- **详情抽屉**：订单与退款信息聚合展示。
- **退款审核**：Tab 切换退款申请列表，支持「通过 / 拒绝」并填写备注。
- **导出**：一键下载 CSV。

---

## 4. 模块四：全量测试数据初始化

### 4.1 执行顺序（严格按序）

```bash
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"

# 1) 全新建库时执行一次（已存在则仅补建缺失表）
"$MYSQL" -uroot -p123456 --default-character-set=utf8mb4 < sql/init.sql

# 2) 结构迁移：题库表 + 答题记录列（幂等，可重复执行）
"$MYSQL" -uroot -p123456 --default-character-set=utf8mb4 < sql/2026-09-11-exam-and-admin-module.sql

# 3) 全量测试数据（幂等，可重复执行）
"$MYSQL" -uroot -p123456 --default-character-set=utf8mb4 < sql/test-data.sql
```

### 4.2 数据覆盖矩阵

| 域 | 库 | 数据量 | 关键点 |
|---|---|---|---|
| 用户 | `zx_user` | **20 人**（1 管理员 + 3 教师 + 8 学员 + 既有账号） | 三类角色齐全，含 1 个禁用账号（验证状态拦截） |
| 用户详情 | `zx_user` | 10 条 | 教师职称/简介、学员学历/职业 |
| 课程 | `zx_course` | 12 门（复用既有） | 覆盖 Java/Spring/前端/数据/AI/数据库 6 大方向 |
| **题库** | `zx_exam` | **28 题** | 单选/多选/判断三种题型；**每题均绑定 `teacher_id` 与 `course_id`** |
| **答题记录** | `zx_exam` | **76+ 条** | 7 名学员；**正确率 50% ~ 92.3% 呈梯度**；时间分布覆盖近 7 日 |
| 学习记录 | `zx_learning` | 34 条 | 进度/时长/完成态 |
| 购物车 | `zx_trade` | 12 条 | 含课程快照（名称/价格） |
| **订单** | `zx_trade` | **25 单** | **覆盖全部 5 种状态**（待支付/已支付/已关闭/退款中/已退款），时间跨度近 7 日 |
| 订单明细/支付流水 | `zx_trade` | 25 条 / 9 条 | 与已支付订单一一对应，便于对账演示 |
| 退款申请 | `zx_trade` | 3 条 | 覆盖待审核 / 已通过 |
| 优惠券 | `zx_promotion` | 16 张用户券 | 已发放量与实际持有量同步校正 |
| 学情报告 | `zx_insight` | — | 由答题/学习数据实时聚合 |

### 4.3 关联完整性（脚本内置自检）

`sql/test-data.sql` 末尾会输出各表计数，便于人工核对。实测关联校验结果：

| 校验项 | 结果 |
|---|---|
| 题目 → 教师 / 课程 归属 | 28 / 28 全部有归属 |
| 答题记录 → 题库题目 | 76 / 76 全部命中 |
| 订单 → 课程 | 25 / 25 全部命中 |
| 订单 → 用户 | 25 / 25 全部命中（含脚本自动修复的历史孤儿订单） |
| 退款申请 → 订单 / 用户 | 3 / 3 全部命中 |
| 购物车 → 课程 / 用户 | 12 / 12 全部命中 |

### 4.4 幂等与可重放设计

- 全部使用**显式主键 + `INSERT IGNORE`**，重复执行不产生脏数据；
- 列变更通过 `INFORMATION_SCHEMA` 存在性判断实现 `ALTER ... IF NOT EXISTS` 等价语义；
- 末尾附**基线复位块**：把 E2E 验证临时流转过的订单状态、退款审核状态恢复为初始值，**保证反复跑验证后数据不漂移**。

---

## 5. 验证结果

### 5.1 后端单元测试

| 模块 | 用例数 | 结果 |
|---|---|---|
| zx-common | 42 | ✅ 全通过 |
| **zx-exam** | **20**（新增 12 + 8） | ✅ 全通过（含**服务端判分防伪造**回归用例） |
| **zx-trade** | **65**（含新增 `AdminOrderServiceTest` 10 个） | ✅ 全通过 |
| | | **BUILD SUCCESS** |

关键回归用例：
- `submitBatchGradesOnServerSide` —— 客户端谎报 `correct=true, score=100`，服务端仍按标准答案判为错；
- `multiChoiceIgnoresLetterOrder` —— 多选 `AB` 与 `BA` 等价；
- `AdminOrderServiceTest.updateStatusRejectsIllegalValue` —— 越界状态值被拒。

### 5.2 前端

| 检查 | 结果 |
|---|---|
| `vue-tsc --noEmit` 类型检查 | ✅ **0 error** |
| `vite build` 生产构建 | ✅ 成功（12.84s） |

### 5.3 端到端验证（`scripts/e2e-verify.sh`，经网关 8080）

**31 项断言全部通过**（通过 31 / 失败 0），覆盖：

- **连通与认证**：4 项 —— 网关可达、三角色（教师/学员/管理员）登录
- **题库联动**：10 项 —— 教师题库、课程关联、新增草稿、**草稿对学员不可见**、发布、**学员即时接收**、交卷判分、**服务端判分正确**、错题本、教师答题总览
- **学情可视化**：3 项 —— 学员能力维度、学习趋势 7 点、教师查学员画像
- **管理员订单**：7 项 —— 分页、状态筛选、统计、退款列表、**CSV 导出**、**改状态（待支付→已支付）**、**退款审核**
- **权限隔离**：6 项 —— 学员/教师访问管理员接口 403、学员访问教师接口 403、管理员访问教师专属接口 403、未登录 401
- **数据清理**：1 项 —— 自动删除本次验证新增的测试题目

---

## 6. 运行指引

### 6.1 启动后端（Windows PowerShell，推荐）

```powershell
# 一键启动全部 16 个服务
powershell -ExecutionPolicy Bypass -File scripts\dev-start-backend.ps1

# 或只启动本次验证所需的核心 8 个服务
powershell -ExecutionPolicy Bypass -File scripts\dev-start-backend.ps1 `
  zx-auth zx-user zx-course zx-exam zx-learning zx-trade zx-insight zx-gateway
```

> ⚠️ 脚本已内置处理宿主注入的 `SERVER__PORT` / `SERVER__HOST`（会被 Spring 松散绑定为 `server.port` 导致端口冲突），
> 并将 `java.io.tmpdir` 指向仓库内可写目录。

### 6.2 启动前端

```bash
cd zx-web
node node_modules/vite/bin/vite.js dev     # pnpm 未安装时的替代方式
```

### 6.3 执行端到端验证

```bash
bash scripts/e2e-verify.sh
```

### 6.4 演示账号（密码统一 `123456`）

| 角色 | 手机号 | 用户名 | 说明 |
|---|---|---|---|
| 管理员 | 13800000001 | admin001 | 全部管理权限 |
| 教师 | 13900000002 | teacher001 | 题库/答题情况/学员学情 |
| 教师 | 13900000012 | teacher002 | 李明老师 |
| 学员 | 13900000001 | student001 | 在线答题/错题本/学情 |
| 学员 | 13900000201~207 | student101~107 | 8 名学员（含 1 个禁用） |

---

## 7. 已知说明

1. **订单金额单位为「分」**：管理端筛选界面按「元」输入，前端自动换算后再下发。
2. **业务异常返回 HTTP 200 + `body.code`**：`code=403` 表示无权限、`code=401` 表示未登录；
   网关 JWT 校验失败才返回真实 HTTP 401。验证脚本已同时检查两者。
3. **管理端与学员端订单状态语义不同**：管理端用数据库状态（0~4），学员端用展示态（1/2/3/5/6），
   由不同控制器分别处理，互不影响。
4. **E2E 会临时改动少量数据**：验证脚本会临时创建/删除题目、流转 1 笔订单状态、审核 1 笔退款；
   重跑 `sql/test-data.sql` 即可复位（含内置基线复位块）。
