-- =====================================================================
-- 知行智学 · 课程章节内容补全迁移
-- 日期：2026-09-14
-- 幂等：可重复执行（加列用 information_schema 判断；目录用主键 UPSERT）
-- ---------------------------------------------------------------------
-- 背景与改动：
--   1) 「学习中心-课程内容」的「章节内容」模块此前只有小节名 + 一段通用占位文案，
--      没有任何真实讲义/要点/资料，属于"界面在、内容是空的"。这里为课程目录补充
--      内容字段：content（讲义正文 Markdown）、key_points（本节要点，"|" 分隔）、
--      attachment_url / attachment_name（学习资料下载）。
--   2) 全站 13 门课程中只有 3 门（id=1、3001、3008）有章节目录，其余 10 门课的
--      "章节内容"打开即空。这里为缺失的课程补齐两级目录（每门 2 章 × 2 小节）并
--      填充讲义内容，保证任何一门课点进去都有可读内容，不出现空白占位。
--   3) 课程 1 / 3008 的既有目录单薄（1 章 1 节 / 1 章 2 节）、且课程 1 的节点名
--      还是英文占位（Chapter-1 Intro / 1.1 Setup）。第四节把它们统一规范化为
--      「2 章 × 2 小节」的中文真实内容，使 13 门课目录结构完全一致。
--
-- 执行顺序：init.sql → 其它迁移 → 本脚本 → test-data.sql（可选）
-- =====================================================================
USE `zx_course`;

-- ---------------------------------------------------------------------
-- 一、课程目录内容字段（幂等加列）
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `course_catalogue` ADD COLUMN `content` TEXT NULL COMMENT ''讲义正文（Markdown）''',
  'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'zx_course' AND TABLE_NAME = 'course_catalogue' AND COLUMN_NAME = 'content');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `course_catalogue` ADD COLUMN `key_points` VARCHAR(512) NULL COMMENT ''本节要点，"|"分隔''',
  'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'zx_course' AND TABLE_NAME = 'course_catalogue' AND COLUMN_NAME = 'key_points');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `course_catalogue` ADD COLUMN `attachment_url` VARCHAR(255) NULL COMMENT ''学习资料地址''',
  'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'zx_course' AND TABLE_NAME = 'course_catalogue' AND COLUMN_NAME = 'attachment_url');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `course_catalogue` ADD COLUMN `attachment_name` VARCHAR(128) NULL COMMENT ''学习资料名称''',
  'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'zx_course' AND TABLE_NAME = 'course_catalogue' AND COLUMN_NAME = 'attachment_name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 注：课程封面地址加宽（course.cover_url / course_draft.cover_url → VARCHAR(512)）
--     已放在 init.sql 的「新增课程」段之前（必须先加宽才能写入长 URL），此处不再重复。

-- ---------------------------------------------------------------------
-- 二、已有小节补齐讲义内容
-- ---------------------------------------------------------------------
-- 课程 1 / 3001 / 3008 既有目录，补齐要点与讲义，避免"目录有、内容空"
UPDATE `course_catalogue` SET
  `key_points` = '开发环境一键搭建|SpringBoot 项目骨架|自动配置原理',
  `content` = '## 本节目标\n\n把 SpringBoot 项目从"能跑"到"跑得明白"：理解 starter 依赖的作用、自动配置的装配时机，以及内嵌容器的启动流程。\n\n### 关键结论\n- `spring-boot-starter-*` 只是依赖描述符，真正生效的是 `AutoConfiguration` 类；\n- 自动配置通过 `@ConditionalOnClass` / `@ConditionalOnMissingBean` 决定是否生效；\n- 内嵌 Tomcat 由 `ServletWebServerFactoryAutoConfiguration` 装配。\n\n### 动手练习\n新建一个 starter 模块，观察 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 的写法。',
  `duration` = 600, `trailer` = 1
WHERE `id` = 900002;

UPDATE `course_catalogue` SET
  `key_points` = 'JDK 21 新特性|PATH 与 JAVA_HOME|多版本共存',
  `content` = '## 本节目标\n\n完成 JDK 21 的安装与环境变量配置，并验证多版本 JDK 共存时的切换方式。\n\n### 步骤\n1. 下载 Temurin 21 LTS 并解压到无空格、无中文的路径；\n2. 配置 `JAVA_HOME` 指向 JDK 根目录，把 `%JAVA_HOME%\\bin` 加入 `PATH`；\n3. `java -version` 应输出 `21.0.x`。\n\n### 常见坑\n- `JAVA_HOME` 指向了 `bin` 目录；\n- 同时存在多个 JDK 时，`PATH` 中靠前的优先，需按顺序核对。',
  `duration` = 600, `trailer` = 1,
  `attachment_url` = '/files/jdk21-setup-checklist.md', `attachment_name` = 'JDK 21 安装自查清单'
WHERE `id` = 4102;

UPDATE `course_catalogue` SET
  `key_points` = 'Hello World 结构|main 方法签名|编译与运行',
  `content` = '## 本节目标\n\n写出第一个 Java 程序，理解 `.java` 源码到 `.class` 字节码，再到 JVM 运行的完整链路。\n\n### 代码骨架\n```java\npublic class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println("Hello, ZhiXing!");\n    }\n}\n```\n\n### 要点\n- 文件名必须与 `public` 类的类名一致；\n- `main` 的签名固定为 `public static void main(String[] args)`；\n- Java 21 起可以直接 `java HelloWorld.java` 运行单文件源码。',
  `duration` = 720, `trailer` = 0
WHERE `id` = 4103;

UPDATE `course_catalogue` SET
  `key_points` = '类与对象|构造器|引用与堆内存',
  `content` = '## 本节目标\n\n建立"类是模板、对象是实例"的直觉，并理解对象在堆上的生命周期。\n\n### 要点\n- `new` 做三件事：分配堆内存 → 调用构造器 → 返回引用；\n- 引用变量存在栈上，对象本体在堆上；\n- 多个引用可以指向同一个对象，修改会互相可见。\n\n### 练习\n写一个 `Course` 类，包含 `name`、`price` 两个字段与全参构造器，创建两个对象比较 `==` 与 `equals`。',
  `duration` = 900, `trailer` = 0
WHERE `id` = 4105;

UPDATE `course_catalogue` SET
  `key_points` = '封装|继承|多态|方法重写',
  `content` = '## 本节目标\n\n掌握面向对象的三大特征，并能用多态写出可扩展的代码。\n\n### 要点\n- 封装：用 `private` 字段 + `public` 方法控制访问，保护不变量；\n- 继承：`extends` 复用父类实现，`super` 调用父类构造；\n- 多态：父类引用指向子类对象，运行时按实际类型调用重写方法；\n- `@Override` 是编译期校验，写错签名会立刻报错。\n\n### 反例\n用 `if (type == ...)` 分支处理不同子类，是典型的多态误用。',
  `duration` = 1100, `trailer` = 0
WHERE `id` = 4106;

UPDATE `course_catalogue` SET
  `key_points` = '课程定位|学习路线|环境准备',
  `content` = '## 本节目标\n\n明确这门课解决什么问题、学完能做出什么，并完成环境准备。\n\n### 学习路线\n1. 语言基础 → 2. 面向对象 → 3. 集合与泛型 → 4. 并发 → 5. 工程实践。\n\n### 环境准备\n- JDK 21（LTS）\n- IDEA 社区版即可\n- 一个写代码的目录，建议路径不含中文与空格',
  `duration` = 300, `trailer` = 1
WHERE `id` = 4202;

UPDATE `course_catalogue` SET
  `key_points` = 'IDEA 与 VS Code|调试断点|常用插件',
  `content` = '## 本节目标\n\n选好主力 IDE 并完成基础配置，把调试断点用起来。\n\n### 要点\n- IDEA：智能补全与重构最强，适合大型 Java 项目；\n- VS Code + Extension Pack for Java：轻量，启动快；\n- 断点调试比 `System.out.println` 高效得多，养成用调试器的习惯。\n\n### 练习\n在 `HelloWorld` 里打断点，用 Step Into / Step Over 观察变量变化。',
  `duration` = 480, `trailer` = 0
WHERE `id` = 4203;

-- ---------------------------------------------------------------------
-- 三、为缺失目录的课程补齐两级目录 + 讲义内容
--     每门课：2 章 × 2 小节 = 4 小节；id 规则 600000 + (courseId-3000)*100 + 序号
--     例外：课程 3010（Vue 3 进阶：Pinia 与组合式 API）的目录已由 init.sql 种子提供
--           （id 4301~4306），本脚本不重复插入，仅做一次幂等清理（见下）。
--     注意：章节名/讲义必须与 init.sql 中的课程定义一致（同一 id 只能对应同一门课）。
-- ---------------------------------------------------------------------

-- ========== 3002 Spring Boot 3 企业级实战 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (600201,3002,'第一章 工程化起步',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600202,3002,'第二章 数据访问与事务',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600211,3002,'1.1 分层架构与包结构',1,2,600201,720,1,'Controller-Service-Mapper 分层|DTO 与 PO 转换|统一响应体',
  '## 本节目标\n\n搭出一套可维护的工程骨架，避免"一个 Service 写三千行"。\n\n### 分层职责\n- Controller：参数校验与协议转换，不写业务；\n- Service：业务规则与事务边界；\n- Mapper：只做数据访问。\n\n### 要点\nDTO 与 PO 必须分开，别让数据库字段泄漏到接口协议里。',NOW(),NOW(),0),
 (600212,3002,'1.2 统一异常与响应封装',2,2,600201,720,0,'业务异常体系|HTTP 200 + code 信封|全局异常处理',
  '## 本节目标\n\n用一套统一的响应体收敛全站的返回格式。\n\n### 实现\n- 定义 `R<T>` 统一信封；\n- `@RestControllerAdvice` 集中处理异常，业务异常返回 `code` 而非 HTTP 4xx；\n- 参数校验失败统一转成可读文案。\n\n### 注意\n前端只读 `body.code` 判断成败，网关鉴权失败才会返回真实 HTTP 401。',NOW(),NOW(),0),
 (600221,3002,'2.1 MyBatis-Plus 实战',1,2,600202,900,0,'条件构造器|分页插件|逻辑删除',
  '## 本节目标\n\n用 MyBatis-Plus 把 CRUD 写短，同时不牺牲可读性。\n\n### 要点\n- `LambdaQueryWrapper` 用方法引用，重构安全；\n- 分页插件统一处理 `count` 与 `limit`；\n- `@TableLogic` 实现逻辑删除，查询自动过滤 `deleted=1`。\n\n### 陷阱\n逻辑删除的列不要复用为业务标记（例如"用户是否已删除该订单"）。',NOW(),NOW(),0),
 (600222,3002,'2.2 声明式事务与失效场景',2,2,600202,1080,0,'@Transactional 传播行为|自调用失效|事务与远程调用',
  '## 本节目标\n\n搞清 `@Transactional` 什么时候不生效。\n\n### 经典失效场景\n- 同类内部方法自调用（代理不经过）；\n- 方法非 `public`；\n- 异常被 `catch` 吞掉未抛出；\n- 抛的是受检异常但没配 `rollbackFor`。\n\n### 实践建议\n事务内不要做远程调用；需要时注册 `afterCommit` 回调，本地先落地、远端再同步。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3003 Python 数据分析与机器学习入门 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (600301,3003,'第一章 数据准备',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600302,3003,'第二章 建模入门',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600311,3003,'1.1 NumPy 与 Pandas 基础',1,2,600301,780,1,'ndarray 与向量化|DataFrame 索引|缺失值处理',
  '## 本节目标\n\n用向量化替代 for 循环，把数据处理速度提升一个量级。\n\n### 要点\n- `ndarray` 的核心是"批量运算"，能不用循环就别用；\n- `DataFrame` 的行列索引要分清 `loc` 与 `iloc`；\n- 缺失值先统计再决策：删除、填充还是标记。\n\n### 练习\n读入一份销售 CSV，统计每列缺失率并按缺失率排序。',NOW(),NOW(),0),
 (600312,3003,'1.2 清洗与特征工程',2,2,600301,840,0,'异常值识别|类别编码|特征缩放',
  '## 本节目标\n\n把"脏数据"变成模型能吃的数值矩阵。\n\n### 步骤\n1. 去重与类型修正；\n2. 异常值识别（IQR 或 Z-Score）；\n3. 类别特征编码（One-Hot / 目标编码）；\n4. 数值特征缩放（标准化 / 归一化）。\n\n### 提醒\n特征缩放参数只能从训练集拟合，再应用到验证集与测试集。',NOW(),NOW(),0),
 (600321,3003,'2.1 线性回归与评估指标',1,2,600302,900,0,'最小二乘|MSE 与 R²|过拟合识别',
  '## 本节目标\n\n建立第一个监督学习模型并用指标说清它好不好。\n\n### 要点\n- 线性回归本质是最小化残差平方和；\n- MSE 反映绝对误差量级，R² 反映解释比例；\n- 训练集很好、验证集很差 = 过拟合。\n\n### 练习\n对同一份数据比较"加特征"前后验证集 MSE 的变化。',NOW(),NOW(),0),
 (600322,3003,'2.2 分类任务与模型选择',2,2,600302,960,0,'逻辑回归|混淆矩阵|交叉验证',
  '## 本节目标\n\n把分类问题做对：不只看准确率。\n\n### 要点\n- 类别不平衡时准确率失真，要看精确率/召回率/F1；\n- 混淆矩阵是理解错误的起点；\n- K 折交叉验证给出更稳的泛化估计。\n\n### 提醒\n先固定随机种子，保证结果可复现。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3004 Go 语言高并发编程实战 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (600401,3004,'第一章 并发基础',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600402,3004,'第二章 并发工程实践',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600411,3004,'1.1 Goroutine 与调度模型',1,2,600401,780,1,'GMP 模型|轻量级协程|调度时机',
  '## 本节目标\n\n理解为什么 Go 能"开一百万个协程"。\n\n### GMP 模型\n- G：goroutine，用户态任务；\n- M：OS 线程，真正执行的载体；\n- P：处理器上下文，持有可运行队列。\n\n### 要点\ngoroutine 初始栈只有几 KB，按需增长，因此创建成本远低于线程。',NOW(),NOW(),0),
 (600412,3004,'1.2 Channel 与同步原语',2,2,600401,840,0,'无缓冲与缓冲通道|select 多路复用|sync 包',
  '## 本节目标\n\n用 channel 表达"数据流"，用 sync 表达"临界区"。\n\n### 要点\n- 无缓冲 channel 是同步交接，发送与接收必须同时就绪；\n- 缓冲 channel 解耦生产与消费，但要防积压；\n- `select` + `default` 可实现非阻塞收发；\n- 计数信号量用 `sync.WaitGroup`，互斥用 `sync.Mutex`。\n\n### 反例\n用 channel 当锁用，会让代码难以推理。',NOW(),NOW(),0),
 (600421,3004,'2.1 Context 与超时控制',1,2,600402,900,0,'取消传播|超时与截止时间|资源释放',
  '## 本节目标\n\n让一次请求的所有下游调用都能被统一取消。\n\n### 要点\n- `context.WithTimeout` 生成带截止时间的子 context；\n- 取消信号沿调用链向下传播，下游必须监听 `ctx.Done()`；\n- 拿到 cancel 函数一定要 `defer cancel()`，否则会泄漏。',NOW(),NOW(),0),
 (600422,3004,'2.2 并发安全与性能调优',2,2,600402,1020,0,'数据竞争|pprof 剖析|连接池',
  '## 本节目标\n\n用工具定位而不是靠猜。\n\n### 要点\n- `go test -race` 检测数据竞争；\n- `pprof` 看 CPU/内存/阻塞热点；\n- 高频小锁改成 `atomic` 或分片锁；\n- 连接池要设上限，避免把下游打穿。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3005 Vue 3 + TypeScript 前端工程化 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (600501,3005,'第一章 组合式 API',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600502,3005,'第二章 工程化实践',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600511,3005,'1.1 setup 与响应式原理',1,2,600501,720,1,'ref 与 reactive|computed|响应式丢失',
  '## 本节目标\n\n分清 `ref` 与 `reactive` 的适用场景，避免踩响应式丢失。\n\n### 要点\n- 基本类型用 `ref`，模板中自动解包；\n- 对象用 `reactive`，但解构会丢响应式，需要 `toRefs`；\n- `computed` 是带缓存的派生值，不要在 getter 里做副作用。\n\n### 常见坑\n把 reactive 对象整体替换（`state = {...}`）会断开响应式。',NOW(),NOW(),0),
 (600512,3005,'1.2 组件通信与插槽',2,2,600501,780,0,'props 与 emit|v-model 双向绑定|作用域插槽',
  '## 本节目标\n\n写出边界清晰、可复用的组件。\n\n### 要点\n- 数据向下用 props，事件向上用 emit；\n- 组件上的 `v-model` 等价于 `modelValue` + `update:modelValue`；\n- 作用域插槽把"渲染权"交给父组件，适合列表卡片这类场景。',NOW(),NOW(),0),
 (600521,3005,'2.1 TypeScript 类型建模',1,2,600502,840,0,'接口契约|泛型|类型收窄',
  '## 本节目标\n\n让类型成为文档而不是负担。\n\n### 要点\n- 接口契约用 `interface` 描述后端返回结构，字段可选性要准确；\n- 分页、响应体这类通用结构用泛型复用；\n- 联合类型配合类型收窄，避免到处 `as any`。\n\n### 提醒\n后端 Long 精度问题会把超长 id 序列化成字符串，前端类型要写成 `number | string`。',NOW(),NOW(),0),
 (600522,3005,'2.2 构建、分包与性能优化',2,2,600502,900,0,'Vite 构建|路由懒加载|首屏体积',
  '## 本节目标\n\n把首屏体积降下来，让页面更快出现。\n\n### 手段\n- 路由级懒加载，按页面分包；\n- 大依赖（图表/富文本）单独拆 chunk；\n- 用 `manualChunks` 把稳定的第三方库单独缓存；\n- 打开构建体积报告，优先削掉最大的 chunk。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3006 React 18 状态管理与性能优化 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (600601,3006,'第一章 状态与渲染',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600602,3006,'第二章 并发特性与优化',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600611,3006,'1.1 Hooks 与状态设计',1,2,600601,780,1,'useState 与 useReducer|依赖数组|闭包陷阱',
  '## 本节目标\n\n写出没有闭包陷阱的 Hooks 代码。\n\n### 要点\n- 状态多且互相关联时用 `useReducer` 收敛更新逻辑；\n- `useEffect` 依赖数组必须完整，缺失会导致读到旧值；\n- 定时器/事件监听里读到的 state 是创建时的快照，需用函数式更新或 ref。\n\n### 练习\n把一段有多处 setState 的代码改写成 useReducer。',NOW(),NOW(),0),
 (600612,3006,'1.2 全局状态与缓存',2,2,600601,840,0,'状态提升|Context 性能|请求缓存',
  '## 本节目标\n\n区分"服务端状态"与"客户端状态"，选对工具。\n\n### 要点\n- 能提升就别用 Context；\n- Context 值变化会让所有消费者重渲染，需要拆分 Provider；\n- 服务端数据用请求缓存库管理，避免手写 loading/error 三件套。',NOW(),NOW(),0),
 (600621,3006,'2.1 并发渲染与过渡',1,2,600602,900,0,'useTransition|useDeferredValue|Suspense',
  '## 本节目标\n\n让重计算更新不再卡住输入。\n\n### 要点\n- `useTransition` 把非紧急更新降级；\n- `useDeferredValue` 延迟使用某个值，先渲染旧内容；\n- `Suspense` 统一处理加载态与错误边界。',NOW(),NOW(),0),
 (600622,3006,'2.2 渲染性能剖析',2,2,600602,1020,0,'memo 与 useMemo|Profiler|列表虚拟化',
  '## 本节目标\n\n先测量再优化。\n\n### 要点\n- 用 React DevTools Profiler 找真正的重渲染热点；\n- `memo` 只解决"父组件无关重渲染"，props 不稳定时无效；\n- 长列表用虚拟化，把 DOM 数量控制住。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3007 大模型应用开发：RAG 与 Agent ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (600701,3007,'第一章 检索增强生成',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600702,3007,'第二章 Agent 与工程化',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600711,3007,'1.1 向量检索与切片策略',1,2,600701,900,1,'文本切片|向量化|相似度召回',
  '## 本节目标\n\n把文档变成可检索的知识库。\n\n### 要点\n- 切片要在语义边界切（标题、段落），不要按固定字数硬切；\n- 切片要保留上下文（标题路径），否则召回后模型看不懂；\n- 向量库选型优先考虑过滤条件与增量更新能力。',NOW(),NOW(),0),
 (600712,3007,'1.2 提示词与答案组装',2,2,600701,840,0,'上下文注入|引用溯源|拒答策略',
  '## 本节目标\n\n让模型"只根据资料回答"，并给出可核对来源。\n\n### 要点\n- 提示词里明确"资料不足时请回答不知道"；\n- 把召回片段的编号带进提示词，便于输出引用；\n- 输出后做一次引用校验，丢掉无来源的断言。',NOW(),NOW(),0),
 (600721,3007,'2.1 工具调用与 Agent 编排',1,2,600702,960,0,'Function Calling|执行循环|失败重试',
  '## 本节目标\n\n让模型能调用真实工具完成任务。\n\n### 要点\n- 工具描述要写清参数语义与适用场景；\n- `思考 → 调用 → 观察` 循环必须设最大轮次，防止死循环；\n- 幂等工具要支持安全重试，非幂等工具要做去重键。',NOW(),NOW(),0),
 (600722,3007,'2.2 效果评估与成本控制',2,2,600702,1080,0,'离线评测集|Token 成本|缓存与降级',
  '## 本节目标\n\n把"感觉不错"变成"可量化、可回归"。\n\n### 要点\n- 建一个固定评测集，每次改提示词都跑回归；\n- 统计 token 消耗，优先在检索阶段削减上下文；\n- 高频问题走缓存，模型不可用时降级到检索直出。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3009 MySQL 8 性能优化与索引设计 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (600901,3009,'第一章 索引原理与设计',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600902,3009,'第二章 查询优化与扩展',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (600911,3009,'1.1 B+Tree 索引与选择策略',1,2,600901,840,1,'B+Tree 结构|联合索引最左前缀|索引选择性',
  '## 本节目标\n\n理解索引为什么快，以及什么时候索引反而不生效。\n\n### 要点\n- InnoDB 索引是 B+Tree：非叶子节点只存键，叶子节点串成有序链表；\n- 联合索引遵循最左前缀，范围条件右侧的列无法继续用于定位；\n- 选择性 = 不同值 / 总行数，选择性低的列单独建索引收益很小。\n\n### 动手练习\n对同一张表分别建单列索引与联合索引，对比 `EXPLAIN` 输出的 `key_len`。',NOW(),NOW(),0),
 (600912,3009,'1.2 Explain 与慢查询定位',2,2,600901,900,0,'执行计划 type/rows|慢查询日志|Profile 剖析',
  '## 本节目标\n\n把"这条 SQL 慢"变成"慢在哪一步"。\n\n### 要点\n- 先看 `type`（const/ref/range 可接受，ALL、index 要警惕），再看 `rows` 估算；\n- `Extra` 出现 `Using filesort`、`Using temporary` 基本都是可优化信号；\n- 打开慢查询日志并用 `SHOW PROFILE` 定位真实耗时阶段。\n\n### 常见坑\n在索引列上做函数运算或隐式类型转换，会导致索引失效。',NOW(),NOW(),0),
 (600921,3009,'2.1 排序分页与覆盖索引',1,2,600902,960,0,'消除 filesort|深分页优化|覆盖索引免回表',
  '## 本节目标\n\n让排序与分页不再扫全表。\n\n### 要点\n- `ORDER BY` 的列顺序与联合索引一致时，可直接利用索引有序性消除 filesort；\n- 深分页（`LIMIT 100000,20`）用延迟关联或游标（上次最大 id）改写；\n- 查询所需列全在索引里即为覆盖索引，可省掉回表。\n\n### 动手练习\n把 `SELECT * FROM t ORDER BY c LIMIT 100000,20` 改写成延迟关联版本并对比耗时。',NOW(),NOW(),0),
 (600922,3009,'2.2 分区、分库分表与读写分离',2,2,600902,1020,0,'分区裁剪|分片键选择|主从延迟',
  '## 本节目标\n\n单表扛不住时，知道下一步该做什么。\n\n### 要点\n- 分区能带来裁剪与易归档，但跨分区查询依然慢，分区键必须在查询条件里；\n- 分片键要选"高频等值查询且分布均匀"的列，尽量避免跨片聚合；\n- 读写分离要面对主从延迟：强一致读必须走主库或等待复制位点。\n\n### 常见坑\n一上来就分库分表，往往掩盖了索引与 SQL 本身的问题。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3010 Vue 3 进阶：Pinia 与组合式 API ==========
-- 该课程的章节目录由 init.sql 种子提供（id 4301~4306，含讲义正文），此处不再插入，
-- 否则同一门课会出现两套章节，且其中一套（init.sql 的小节）讲义为空，
-- 会触发「上架课程存在空讲义小节」的数据一致性断言。
-- 历史版本的本迁移曾把 601001~601022 误挂在 course_id=3010 上，这里做幂等清理。
DELETE FROM `course_catalogue` WHERE `id` IN (601001,601002,601011,601012,601021,601022);

-- ========== 3011 Go 微服务与 gRPC 高并发实战 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (601101,3011,'第一章 goroutine 与并发模型',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (601102,3011,'第二章 gRPC 微服务实战',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (601111,3011,'1.1 goroutine 调度与 GMP 模型',1,2,601101,840,1,'GMP 调度|抢占式调度|GOMAXPROCS',
  '## 本节目标\n\n理解 goroutine 为什么"轻"，以及调度器如何把它映射到线程上。\n\n### 要点\n- G（协程）、M（线程）、P（处理器）配合工作，P 的数量默认等于 CPU 核数；\n- goroutine 初始栈只有几 KB 并按需增长，因此可以轻松开到十万级；\n- 1.14 起支持基于信号的抢占式调度，长循环不再独占 P。\n\n### 常见坑\n在 goroutine 里持续调用阻塞式系统调用，会占满 M 从而拖慢整体吞吐。',NOW(),NOW(),0),
 (601112,3011,'1.2 channel 与并发安全',2,2,601101,900,0,'channel 语义|select 多路复用|sync 原语',
  '## 本节目标\n\n用"通过通信共享内存"，替代"共享内存再通信"。\n\n### 要点\n- 无缓冲 channel 是同步交接，有缓冲 channel 是异步队列，语义不同；\n- `select` 配合 `context.Done()` 实现超时与取消；\n- 计数、单次初始化用 `sync.WaitGroup` / `sync.Once` 比 channel 更直接。\n\n### 常见坑\n向已关闭的 channel 发送会 panic；关闭应由发送方负责，且只能关一次。',NOW(),NOW(),0),
 (601121,3011,'2.1 gRPC 协议与代码生成',1,2,601102,960,0,'Protobuf 定义|四种调用模式|拦截器统一治理',
  '## 本节目标\n\n把接口定义变成强类型客户端与服务端骨架。\n\n### 要点\n- 用 `.proto` 定义消息与服务，`protoc` 生成两端代码，契约即文档；\n- 一元、服务端流、客户端流、双向流按业务场景选择；\n- 鉴权、日志、链路追踪统一放在拦截器里，不散落到业务方法。\n\n### 常见坑\n修改字段只能新增编号，不能复用已删除的编号，否则会破坏线上兼容性。',NOW(),NOW(),0),
 (601122,3011,'2.2 超时、重试与可观测',2,2,601102,1020,0,'deadline 逐层收敛|重试与幂等|链路追踪',
  '## 本节目标\n\n让微服务调用在故障下依然可控。\n\n### 要点\n- deadline 要逐层收敛：下游超时必须小于上游剩余时间，避免雪崩；\n- 只对幂等接口重试，并配合退避与熔断；\n- TraceId 通过 metadata 透传，把跨服务调用串成一条链。\n\n### 动手练习\n给一个 gRPC 客户端加上 200ms deadline 与两次重试，观察超时的传递行为。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 3012 大模型 RAG 应用高级实战 ==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (601201,3012,'第一章 检索质量优化',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (601202,3012,'第二章 编排与生产化',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (601211,3012,'1.1 混合检索与多路召回',1,2,601201,900,1,'稠密向量召回|稀疏关键词召回|倒数排名融合',
  '## 本节目标\n\n单靠向量检索很难覆盖所有问法，用多路召回把命中率提上去。\n\n### 要点\n- 稠密向量擅长语义相似，稀疏检索（BM25/关键词）擅长专有名词与编号；\n- 两路结果用 RRF（倒数排名融合）合并，避免分数量纲不同带来的偏置；\n- 召回条数先放宽，把精度问题交给重排解决。\n\n### 常见坑\n只调 embedding 模型却不看召回集，等于在错误的集合上做优化。',NOW(),NOW(),0),
 (601212,3012,'1.2 重排与上下文压缩',2,2,601201,1020,0,'Cross-Encoder 重排|上下文去冗|按段截断',
  '## 本节目标\n\n把最相关的几段真正送进模型，而不是塞满窗口。\n\n### 要点\n- 重排模型（Cross-Encoder）逐对打分精度高，只对少量候选使用才划算；\n- 相邻切片重复内容要去冗，否则模型看到的可能是同一句话好几遍；\n- 超长时按"保留完整段落"截断，宁可少给也不要给半句。\n\n### 动手练习\n对同一批问题分别跑"仅向量召回"与"召回 + 重排"，对比答案引用准确率。',NOW(),NOW(),0),
 (601221,3012,'2.1 查询改写与多跳问答',1,2,601202,960,0,'查询改写补全|子问题拆解|多跳检索',
  '## 本节目标\n\n让口语提问与复合提问都能被检索到。\n\n### 要点\n- 查询改写把口语化问题补全为可检索的关键词组合；\n- 复合问题拆成子问题分别检索，再汇总作答；\n- 多跳场景把上一跳的答案作为下一跳的检索条件。\n\n### 常见坑\n改写后必须保留原问题的关键实体，否则会检索到主题相近但无关的资料。',NOW(),NOW(),0),
 (601222,3012,'2.2 评估、缓存与成本控制',2,2,601202,900,0,'离线评测集|语义缓存与失效|降级策略',
  '## 本节目标\n\n把 RAG 效果变成可量化、可回归的指标。\n\n### 要点\n- 建固定评测集，至少看"召回命中率"与"答案有据率"两个指标；\n- 高频相似问题走语义缓存，缓存键必须包含知识库版本；\n- 模型不可用或超时时降级为"只返回检索片段"，保证可用性。\n\n### 常见坑\n知识库更新后忘记失效缓存，用户会一直拿到旧答案。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ---------------------------------------------------------------------
-- 四、规范化既有目录：把课程 1 / 3008 补齐为「2 章 × 2 小节」
--     原因：这两门课是早期种子数据，课程 1 的节点名还是英文占位
--     （Chapter-1 Intro / 1.1 Setup），且只有 1 章 1 节；课程 3008 只有
--     1 章 2 节。与其余 11 门课（2 章 4 节、中文真实内容）标准不一致，
--     属于"补全不彻底"。这里统一达标，并去掉英文占位节点名。
--     课程 1   = SpringBoot 入门到实战（付费）
--     课程 3008 = Java 入门第一课（免费）
-- ---------------------------------------------------------------------

-- ========== 课程 1：SpringBoot 入门到实战 ==========
-- 第 1 章 + 1.1：既修正早期英文占位节点名，也补齐**全新环境缺失的行**。
-- 早期这里写的是两条 UPDATE（假设 900001/900002 已由更早的种子存在），但空库重建时
-- 这两行并不存在 → UPDATE 命中 0 行，课程 1 只剩后面 INSERT 的 4 条目录，第 1 章与 1.1 丢失。
-- 改为 INSERT ... ON DUPLICATE KEY UPDATE：幂等，且既有库与空库都成立。
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (900001,1,'第一章 SpringBoot 快速起步',1,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (900002,1,'1.1 项目搭建与自动配置',1,2,900001,600,1,'开发环境一键搭建|SpringBoot 项目骨架|自动配置原理',
  '## 本节目标\n\n把 SpringBoot 项目从"能跑"到"跑得明白"：理解 starter 依赖的作用、自动配置的装配时机，以及内嵌容器的启动流程。\n\n### 关键结论\n- `spring-boot-starter-*` 只是依赖描述符，真正生效的是 `AutoConfiguration` 类；\n- 自动配置通过 `@ConditionalOnClass` / `@ConditionalOnMissingBean` 决定是否生效；\n- 内嵌 Tomcat 由 `ServletWebServerFactoryAutoConfiguration` 装配。\n\n### 动手练习\n新建一个 starter 模块，观察 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 的写法。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (900003,1,'1.2 配置文件与多环境切换',2,2,900001,660,0,'application.yml 层级|Profile 隔离|配置优先级',
  '## 本节目标\n\n用一套配置支撑开发、测试、生产三种环境，避免改代码来切环境。\n\n### 要点\n- `application.yml` 里用 `---` 分段配合 `spring.config.activate.on-profile` 隔离环境；\n- 同名配置的优先级：命令行参数 > 环境变量 > profile 文件 > 默认文件；\n- 敏感项（数据库口令、密钥）走环境变量或外部配置中心，不要提交进仓库。\n\n### 练习\n新增 `application-dev.yml`，把端口改成 9090，验证启动后生效的是 9090。',NOW(),NOW(),0),
 (900011,1,'第二章 数据访问与接口开发',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (900012,1,'2.1 数据访问与 ORM 选型',1,2,900011,840,0,'连接池|MyBatis-Plus|分页与逻辑删除',
  '## 本节目标\n\n把"写 SQL 取数据"变成干净的分层代码。\n\n### 要点\n- 连接池（HikariCP）要设合理的最大连接数，超过数据库承载会把下游打穿；\n- MyBatis-Plus 的条件构造器用方法引用写，重构安全；\n- 分页插件统一处理 `count` 与 `limit`，别在业务里手拼 `LIMIT`；\n- `@TableLogic` 逻辑删除让查询自动过滤 `deleted=1`。\n\n### 陷阱\n事务方法内做远程调用会拉长事务，应注册 `afterCommit` 回调，本地先提交、远端再同步。',NOW(),NOW(),0),
 (900013,1,'2.2 RESTful 接口与统一响应',2,2,900011,780,0,'资源化 URL|参数校验|统一信封与异常',
  '## 本节目标\n\n设计出让前端"一眼看懂"的接口。\n\n### 要点\n- URL 用名词表示资源，动作交给 HTTP 方法（GET/POST/PUT/DELETE）；\n- 入参用 `@Valid` + 注解做声明式校验，错误信息统一收敛；\n- 返回统一信封（`code`/`msg`/`data`），`@RestControllerAdvice` 集中处理异常；\n- 列表接口统一分页结构，前端只认一种格式。\n\n### 提醒\n业务异常建议返回 HTTP 200 + `code`，网关鉴权失败才用真实 401，前端按 `code` 判定成败。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ========== 课程 3008：Java 入门第一课（免费）==========
INSERT INTO `course_catalogue` (`id`,`course_id`,`name`,`index`,`chapter_type`,`parent_id`,`duration`,`trailer`,`key_points`,`content`,`create_time`,`update_time`,`deleted`) VALUES
 (4204,3008,'第二章 第一个 Java 程序',2,1,0,NULL,0,NULL,NULL,NOW(),NOW(),0),
 (4205,3008,'2.1 环境配置与运行机制',1,2,4204,540,0,'JDK 与 JRE|PATH 配置|源码到字节码',
  '## 本节目标\n\n把"能运行 Java"这件事彻底搞懂：为什么装了 JDK 就能编译，`.class` 又是什么。\n\n### 要点\n- JDK 包含 JRE，JRE 包含 JVM；开发装 JDK，运行可只装 JRE；\n- `JAVA_HOME` 指向 JDK 根目录，`PATH` 里要有 `%JAVA_HOME%\\bin`；\n- `javac` 把 `.java` 编译成字节码 `.class`，`java` 启动 JVM 加载字节码执行；\n- Java 21 起可以直接 `java HelloWorld.java` 运行单文件源码。\n\n### 常见坑\n`JAVA_HOME` 误指向 `bin` 目录；机器上有多个 JDK 时 `PATH` 中靠前的优先。',NOW(),NOW(),0),
 (4206,3008,'2.2 变量、类型与输入输出',2,2,4204,600,0,'基本类型|类型转换|控制台输入输出',
  '## 本节目标\n\n写出能与用户交互的第一个交互式程序。\n\n### 要点\n- 八种基本类型要记清取值范围，`int` 与 `long` 溢出不会报错而是"绕回"；\n- 小范围转大范围自动提升，反向必须显式强转且可能丢精度；\n- 输出用 `System.out.println`，输入用 `Scanner`；\n- 字符串拼接用 `+`，大量拼接改用 `StringBuilder`。\n\n### 练习\n读入两个整数，输出它们的和、差、积、商（整除时保留两位小数）。',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`index`=VALUES(`index`),`chapter_type`=VALUES(`chapter_type`),`parent_id`=VALUES(`parent_id`),`duration`=VALUES(`duration`),`trailer`=VALUES(`trailer`),`key_points`=VALUES(`key_points`),`content`=VALUES(`content`),`update_time`=NOW(),`deleted`=0;

-- ---------------------------------------------------------------------
-- 五、自检：每门课程都应有章节目录，且小节都有讲义内容
-- ---------------------------------------------------------------------
SELECT c.id AS course_id, c.name AS course_name,
       COUNT(cat.id) AS catalogue_rows,
       SUM(cat.chapter_type = 2) AS section_rows,
       SUM(cat.chapter_type = 2 AND (cat.content IS NULL OR cat.content = '')) AS section_without_content
FROM `course` c
LEFT JOIN `course_catalogue` cat ON cat.course_id = c.id AND cat.deleted = 0
WHERE c.deleted = 0
GROUP BY c.id, c.name
ORDER BY c.id;
