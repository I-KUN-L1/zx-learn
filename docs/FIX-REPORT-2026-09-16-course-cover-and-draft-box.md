# 缺陷修复报告：课程封面无法显示 + 教师端草稿箱存不进去

> 日期：2026-09-16 · 模块：zx-course（8083）/ zx-web · 关联脚本：`scripts/verify-draft-and-covers.sh`

---

## 0. 结论速览

| 缺陷 | 根因（一句话） | 修复 |
|---|---|---|
| 部分课程封面无法显示 | 课程 3009~3012 的 `cover_url` 指向**已下线的文生图接口**（按需生成的服务端点，非静态图片资源），实测 HTTP 404 | 封面切到 OSS 静态资源 `covers/course-10~13.svg`；首页 3 张轮播横幅同源失效，一并改本地静态 SVG |
| 教师端新建课程无法存进草稿箱 | 草稿箱 Tab 查的是 `/courses/page?status=2`，而草稿在**独立的 `course_draft` 表**里，`course.status` 只有 0/1 → 恒为空；且表单第 2 步的章节目录没有落库字段，被静默丢弃 | 新增 `GET /courses/draft/page`、`DELETE /courses/draft/{id}`；新增 `course_draft.catalogue_json` 持久化目录；上架时把目录合并进 `course_catalogue` |

验证：新增断言 **56 / 56 全通过**；既有全量回归 **136 / 136**、课程 UI 回归 **71 / 71** 全通过。

---

## 1. 缺陷一：部分课程封面无法显示

### 1.1 现象

课程列表/详情页中，课程 `3009~3012` 的封面是裂图。因为前端 `CourseCover.vue` 有"渐变兜底"，
页面不会出现空白块，所以这个问题被**掩盖了很久**——肉眼看只是"这几门课是渐变封面"，
不容易意识到"图挂了"。

### 1.2 定位过程（证据链）

```bash
# 1) 库里封面地址的长度分布：4 门课 269~285 字符，明显不同于其它 65 字符的 OSS 地址
SELECT id, CHAR_LENGTH(cover_url), cover_url FROM zx_course.course;

# 2) 逐个探测可达性
curl -I "https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-01.svg"     # → 200 image/svg+xml 1254B ✅
curl -I "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=..."   # → 404 Not Found        ❌
```

| 封面来源 | 课程 | 实测结果 |
|---|---|---|
| `zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-0X.svg` | 1、3001~3008 | HTTP 200，`image/svg+xml`，1.2KB 实体 |
| `trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=...` | 3009~3012 | **HTTP 404** |

### 1.3 根因

`cover_url` 存的是**文生图接口地址**，那是"按需生成"的服务端点，**不是静态图片资源**：

- 它每次请求都要跑一次生成，本身不是稳定可缓存的静态资源；
- 该端点现已下线，直接返回 `404 Not Found`；
- 于是这 4 门课的封面必然加载失败。

### 1.4 ⚠ 一处必须纠正的历史误判

`sql/init.sql` 与 `docs/PRODUCTION-READINESS-2026-09-15.md` 曾把封面 404 归因为
「`cover_url` 是 `VARCHAR(255)`，装不下 270 字符的地址，`INSERT IGNORE` 静默截断导致 URL 失效」，
并因此把列宽加到了 `VARCHAR(512)`。

**列宽确实该加宽**（防截断，是结构性加固），**但它不是封面 404 的根因**：

- 库里 3009~3012 的 `cover_url` 实际长度是 269/279/285/270 字符，**完整写入、没有被截断**；
- 真正的原因就是上面那条：接口本身 404。

两件事不能再混为一谈，否则下次遇到同类问题还会去改列宽。相关注释已在 `sql/init.sql`
与本次迁移里改写说明。

### 1.5 修复内容

| # | 文件 | 变更 |
|---|---|---|
| 1 | `tmp-covers/course-1X.svg` → OSS `covers/` | 把缺失的 4 张封面（源文件已在仓库 `tmp-covers/`）上传到 OSS，对象级 `public-read`（用 `.env` 里的 OSS 凭据签名 PUT） |
| 2 | `sql/2026-09-16-draft-box-and-course-covers.sql` | 幂等迁移：`course` 3009~3012 的 `cover_url` → `covers/course-10~13.svg`；同表/`course_draft` 里其它残留失效域名兜底为默认封面 |
| 3 | `sql/init.sql` | 种子数据 3009~3012 封面地址同步修正；改写误导性注释 |
| 4 | `zx-web/public/covers/course-10~13.svg` | 补齐本地封面源文件（原来只有 01~09） |
| 5 | `zx-web/src/views/home/HomeView.vue` | **同源缺陷**：首页 3 张轮播横幅用的是同一个失效接口 → 换成本地静态 SVG |
| 6 | `zx-web/public/banners/banner-01~03.svg` | 新增 3 张 1600×600 本地横幅（不依赖外网、离线可跑） |
| 7 | `zx-web/src/api/mock/data.ts`、`mock/adapter.ts` | Mock 模式的封面/默认封面也在用同一个失效接口 → 改为按 prompt 稳定映射到本地 `course-0X.svg` |
| 8 | `zx-web/src/views/admin/AdminCoursesView.vue` | 管理端列表原来是裸 `<img :src>`（加载失败就是裂图）→ 改用带兜底的 `CourseCover`；新建表单加封面预览 |

> 原则：**封面必须指向稳定的静态资源**（OSS 静态对象 / 本地静态文件），
> 不要指向"按需生成"的服务端点。

### 1.6 验证

- 逐条抓取库中全部 13 个封面地址：**全部 HTTP 200 且响应体非空**（`size ≥ 100B`）。
- `course` / `course_draft` 两表中 `LIKE '%trae-api-cn.mchost.guru%'` 的行数均为 **0**。
- `zx-web/src` 中不再出现 `https://trae-api-cn.*` 的**真实 URL 引用**（注释里的成因说明不算）。

---

## 2. 缺陷二：教师端创建课程无法保存进草稿箱

### 2.1 现象

教师/管理员在「新建课程」分步表单里点保存 → 提示「保存成功」，但切到「草稿箱（编辑态）」
Tab 一直是**空的**，草稿看起来"根本没保存进去"。

### 2.2 根因（三条叠加）

**根因 A（主因）：草稿箱查错了表。**

`AdminCoursesView.vue` 里两个 Tab 都打同一个接口，用 `status` 假装区分：

```ts
const statusByTab = computed(() => (activeTab.value === 'draft' ? 2 : 1))
const res = await pageCourses({ ..., status: statusByTab.value })   // ← 查的是 course 表
```

但草稿存在**独立的 `course_draft` 表**（见 `docs/ARCHITECTURE.md` §4.5），
而 `course.status` 只有 `1-上架 / 0-下架`，**根本不存在 status=2 的行** → 草稿箱永远返回空列表。
而且当时后端**没有任何"列草稿"的接口**，草稿写进去了也没有出口。

> 附带纠正：zx-gateway 的 `optionalIdentity` 曾为解决"草稿 Tab 筛选恒为空"而加，
> 注释里也写的是"按 status 筛选草稿"。那是对同一误判的另一处处置 —— 相关注释已更正。

**根因 B：分步表单第 2 步的章节目录被静默丢弃。**

前端提交了 `catalogueList`，但 `CourseFormDTO` 上没有这个字段、`course_draft` 也没有对应的列，
Jackson 按默认配置**忽略未知字段** → 章节名全部丢掉，重新打开表单只剩一行空白。

**根因 C：草稿永远过不了上架校验。**

表单里没有"授课老师"选项，`teacherId` 恒为 `null`，而 `checkBeforeUpShelf` 要求
`teacherId != null` → 草稿存进去了也一直卡在「请选择授课老师」，表现为"保存成功但发不出去"。

### 2.3 修复内容

**后端（zx-course）**

| # | 位置 | 变更 |
|---|---|---|
| 1 | `sql/2026-09-16-draft-box-and-course-covers.sql`、`sql/init.sql` | `course_draft` 新增 `catalogue_json MEDIUMTEXT`（草稿章节目录 JSON），幂等 DDL |
| 2 | `CourseDraft` | 新增 `catalogueJson`（落库）+ `catalogueList`（`@TableField(exist=false)`，下发给前端回填） |
| 3 | `CourseFormDTO` | 新增 `catalogueList`、`step` |
| 4 | 新增 `CatalogueNodeDTO` / `CourseDraftVO` | 草稿目录节点 / 草稿箱列表视图 |
| 5 | `CourseController` | 新增 `GET /courses/draft/page`（员工+教师）、`DELETE /courses/draft/{id}`（员工+教师） |
| 6 | `CourseService#saveBaseInfo` | 改为**逐字段显式赋值**（不再 `BeanUtils` 批量拷贝）：<br>· 保护 `teacherId` 不被表单的 null 覆盖，并在为空时兜底为**当前登录人**（修根因 C）；<br>· `step`/`submitted` 由服务端掌控，前端不能"伪造成已发布"；<br>· 校验课程名/简介/章节名长度，**超长显式报错而不是静默截断**；免费课价格必须为 0 |
| 7 | `CourseService#draftPage` | 查 `course_draft` 且 `submitted = 0`，按更新时间倒序；`sortBy` 强制置空（免注入面） |
| 8 | `CourseService#deleteDraft` | 逻辑删除；已发布的草稿禁止从草稿箱删（走正式课程的下架/删除流程），返回 400 语义化业务码 |
| 9 | `CourseService#upShelf` | 上架成功后回填 `chapterCount`/`subjectCount`，并把草稿标记 `submitted = 1`（离开草稿箱、进入「已发布」） |
| 10 | `CourseService#syncCatalogue` | 草稿目录 → `course_catalogue`。采用**按名称匹配的增量合并**（复用同名行 / 新增 / 逻辑删除未出现的行），**不是"先清空再重建"** —— 正式表里的讲义正文、要点、学习资料是 2026-09-14 内容迁移写进去的，清空重建会把讲义全丢掉。同名重复按出现顺序配对，因此可重复执行（幂等） |

**前端（zx-web）**

| # | 文件 | 变更 |
|---|---|---|
| 1 | `api/course.ts` | 新增 `pageDrafts()`、`deleteDraft()`，并注明"草稿不在 course 表，别用 status 查" |
| 2 | `types/api.ts` | 新增 `CourseDraftVO`，`CourseFormDTO` 补 `step` |
| 3 | `views/admin/AdminCoursesView.vue` | 草稿箱 Tab 改走 `/courses/draft/page`；发布/删除用**草稿 id**；新增「编辑进度 / 最近编辑」列；保存后**不关弹窗**可继续编辑；「已发布」Tab 改为不带 status 过滤（下架的课程也留在列表里可见，不再"一下架就从管理端消失"）；封面改用 `CourseCover` 兜底组件 |
| 4 | `utils/format.ts` | `COURSE_STATUS_TEXT` 补 `0: 已下架`（原来缺 0，导致下架课程被 `?? '已上架'` 显示成"已上架"） |
| 5 | `api/mock/*` | Mock 按真实语义重建：草稿放进独立的 `drafts` 集合 + `/courses/draft/page` 路由，不再把草稿塞进 `mockCourses` 并用 `status=2` 冒充 |

### 2.4 验证（`scripts/verify-draft-and-covers.sh`，56 条断言全通过）

关键回归点：

| 断言 | 说明 |
|---|---|
| 新建的草稿**出现在草稿箱** | 修复前恒为空，这是主回归点 |
| 草稿箱**不混入正式课程 id** | 证明它查的是 `course_draft`，不是 `course` |
| 草稿箱条数 == `course_draft(submitted=0)` 行数 | 口径一致 |
| 草稿读回 **2 章 3 小节**、章节名原样 | 证明 `catalogue_json` 真的落库了 |
| 重复保存 **不新增**草稿（条数不变） | 更新语义正确 |
| `checkBeforeUpShelf` 通过 | teacherId 兜底生效（根因 C） |
| 发布后：草稿离开草稿箱、正式课程 `status=1`、目录同步 5 行（2 章+3 节） | 上架链路完整 |
| **重复发布目录仍是 5 行** | `syncCatalogue` 幂等 |
| 已发布草稿禁止用草稿接口删除 | 状态机边界 |
| 匿名 → HTTP 401；学员访问/保存/发布/删除 → 业务码 403 | 鉴权基线 |
| 空名 / 超长名 / 超长章节名 / 免费课挂价 / 更新不存在的草稿 → 400 | 严格写入，不静默截断 |

> 脚本里踩到并已记录的一处坑：服务端把 `Long/BIGINT` 序列化为 **JSON 字符串**
> （避免前端 JS 精度丢失），所以 id 断言必须**按字符串比**；按数字比会永远为假，
> 让"期望 0"的断言变成**假阳性通过**。

### 2.5 相邻问题（已修 / 未修）

- **已修**：`COURSE_STATUS_TEXT` 缺 `0` → 下架课程显示成"已上架"。
- **已修**：下架后的课程在管理端彻底消失（已发布 Tab 现在不带 status 过滤）。
- **未修（已知边界，需产品决策）**：**已下架的正式课程目前无法重新上架**。
  `upShelf` 的入参是**草稿 id**，而已发布草稿 `submitted=1` 已不在草稿箱；
  没有"把正式课程重新入草稿箱"的接口。本次未擅自新增该能力（会引入"草稿 id / 课程 id
  混用"的新歧义 —— 这正是本次缺陷的成因类型），留待明确产品口径后再做。

---

## 3. 回归验证

| 套件 | 结果 |
|---|---|
| `verify-draft-and-covers.sh`（本次新增） | **56 / 56 通过** |
| `verify-full-suite.sh`（全量，16 服务） | **136 / 136 通过** |
| `verify-course-ui-and-catalogue.sh` | **71 / 71 通过** |
| 前端 `vue-tsc --noEmit` | 无错误 |
| 前端 `vite build` | 成功 |
| `db-migrate.sh`（10 个脚本，含本次迁移） | 10 / 10 幂等通过 |

复现命令（沙箱下"启动 + 断言"必须在同一条命令内完成）：

```bash
cd "D:/1/zx-learn" && \
EXTRA_SPECS="zx-media:8085 zx-promotion:8088 zx-aigc:8089 zx-pay:8090 zx-search:8091 zx-remark:8092 zx-message:8093 zx-data:8094" \
  /usr/bin/bash scripts/dev-up-core.sh >/dev/null 2>&1; \
  REUSE=1 /usr/bin/bash scripts/verify-draft-and-covers.sh 2>&1 | tail -20
```

---

## 4. 变更文件清单

**SQL / 脚本**

- 新增 `sql/2026-09-16-draft-box-and-course-covers.sql`
- 改 `sql/init.sql`（`course_draft.catalogue_json` 列定义 + 3009~3012 封面种子 + 注释纠正）
- 改 `scripts/db-migrate.sh`（登记新迁移）
- 新增 `scripts/verify-draft-and-covers.sh`
- 迁移 `logs/tmp/oss-put.py` → **`scripts/oss-put.py`**（OSS 签名 PUT 上传小工具，凭据从参数传入、零硬编码；
  以后补封面/静态资源可直接复用）

**后端**

- 新增 `zx-course/.../domain/dto/CatalogueNodeDTO.java`、`.../domain/vo/CourseDraftVO.java`
- 改 `zx-course/.../domain/dto/CourseFormDTO.java`、`.../domain/po/CourseDraft.java`
- 改 `zx-course/.../service/CourseService.java`、`.../controller/CourseController.java`
- 改 `zx-gateway/.../filter/AuthGlobalFilter.java`（更正历史误判注释）

**前端**

- 改 `zx-web/src/views/admin/AdminCoursesView.vue`、`views/home/HomeView.vue`
- 改 `zx-web/src/api/course.ts`、`api/mock/data.ts`、`api/mock/adapter.ts`
- 改 `zx-web/src/types/api.ts`、`utils/format.ts`
- 新增 `zx-web/public/banners/banner-01~03.svg`、`public/covers/course-10~13.svg`

**文档**

- 改 `docs/ARCHITECTURE.md`（§4.5 双表口径 + 接口对照表）、`docs/DATABASE.md`（`course_draft` 补充列）、
  `docs/API-REFERENCE.md`（课程服务接口按实际实现重写）
- 新增本文件、`docs/verify-draft-and-covers-report.txt`
