-- =====================================================================
-- 全量基础测试数据初始化脚本（幂等 · 可重复执行）
--
-- 覆盖：用户（教师/学员/管理员）→ 课程 → 题库 → 答题记录 → 购物车 → 多状态订单
--       → 优惠券 → 退款申请 → 学习记录/签到 → 学情报告
-- 关联：题目归属教师并关联课程；答题记录引用题库题目；订单引用课程与用户；
--       退款申请引用订单；学情报告引用学员 —— 全链路逻辑一致，可直接跑通全流程测试。
--
-- 执行顺序（先建表/迁移，再灌数据）：
--   1) sql/init.sql                           （全新建库时执行一次）
--   2) sql/2026-09-11-exam-and-admin-module.sql（题库表 + 答题记录列迁移，幂等）
--   3) sql/test-data.sql                      （本文件）
-- Windows Git Bash 示例：
--   MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
--   "$MYSQL" -uroot -p123456 --default-character-set=utf8mb4 < sql/2026-09-11-exam-and-admin-module.sql
--   "$MYSQL" -uroot -p123456 --default-character-set=utf8mb4 < sql/test-data.sql
-- =====================================================================

SET NAMES utf8mb4;
-- 统一使用显式主键 + INSERT IGNORE，保证幂等
SET @HASH123456 := '$2a$10$OuwRvnFKhKxYDdlndTfjXOzhWRnUF6jTJ6xZEnFlQHkAwcud6rELG'; -- 明文 123456

-- =====================================================================
-- 一、用户（zx_user）：1 管理员 + 3 教师 + 8 学员
-- =====================================================================
USE `zx_user`;

INSERT IGNORE INTO `user` (`id`, `cell_phone`, `username`, `password`, `name`, `type`, `status`, `email`, `city`, `gender`, `create_time`, `update_time`, `deleted`) VALUES
-- 管理员（员工 type=1）。系统首启会自动引导生成管理员，此处额外补一个演示账号
(1001, '13800000001', 'admin001',   @HASH123456, '系统管理员', 1, 1, 'admin@zhixing.learn',   '北京', 1, NOW(), NOW(), 0),
-- 教师（type=3）
(2002, '13900000002', 'teacher001', @HASH123456, '知行教师',   3, 1, 'teacher001@zhixing.learn', '北京', 2, NOW(), NOW(), 0),
(2011, '13900000012', 'teacher002', @HASH123456, '李明老师',   3, 1, 'liming@zhixing.learn',   '上海', 1, NOW(), NOW(), 0),
(2012, '13900000013', 'teacher003', @HASH123456, '王芳老师',   3, 1, 'wangfang@zhixing.learn', '深圳', 2, NOW(), NOW(), 0),
-- 学员（type=2）
(2001, '13900000001', 'student001', @HASH123456, '知行学员',   2, 1, 'student001@zhixing.learn', '杭州', 1, NOW(), NOW(), 0),
(2101, '13900000201', 'student101', @HASH123456, '陈志远',     2, 1, 'chenzy@zhixing.learn',   '杭州', 1, NOW(), NOW(), 0),
(2102, '13900000202', 'student102', @HASH123456, '林小雨',     2, 1, 'linxy@zhixing.learn',    '成都', 2, NOW(), NOW(), 0),
(2103, '13900000203', 'student103', @HASH123456, '赵梓涵',     2, 1, 'zhaozh@zhixing.learn',   '广州', 2, NOW(), NOW(), 0),
(2104, '13900000204', 'student104', @HASH123456, '王浩然',     2, 1, 'wanghr@zhixing.learn',   '武汉', 1, NOW(), NOW(), 0),
(2105, '13900000205', 'student105', @HASH123456, '刘思彤',     2, 1, 'liust@zhixing.learn',    '西安', 2, NOW(), NOW(), 0),
(2106, '13900000206', 'student106', @HASH123456, '孙一鸣',     2, 1, 'sunym@zhixing.learn',    '南京', 1, NOW(), NOW(), 0),
(2107, '13900000207', 'student107', @HASH123456, '周静怡',     2, 0, 'zhoujy@zhixing.learn',   '重庆', 2, NOW(), NOW(), 0);

-- 用户详情：教师职称/简介 + 学员学历/职业
INSERT IGNORE INTO `user_detail` (`id`, `user_id`, `job_title`, `intro`, `birthday`, `education`, `occupation`, `create_time`, `update_time`, `deleted`) VALUES
(2111, 2011, '资深架构师', '十二年一线后端研发经验，主讲 Spring 生态与微服务治理，累计授课 2000+ 课时。', NULL, NULL, NULL, NOW(), NOW(), 0),
(2112, 2012, '前端技术专家', '十年大厂前端经验，深耕 Vue/React 工程化与性能优化，注重实战与规范。', NULL, NULL, NULL, NOW(), NOW(), 0),
(2201, 2001, '学员', NULL, '1998-03-12', '本科', '后端开发工程师', NOW(), NOW(), 0),
(2202, 2101, '学员', NULL, '1996-07-21', '本科', 'Java 开发', NOW(), NOW(), 0),
(2203, 2102, '学员', NULL, '2000-11-05', '本科', '产品运营', NOW(), NOW(), 0),
(2204, 2103, '学员', NULL, '1999-01-18', '大专', '测试工程师', NOW(), NOW(), 0),
(2205, 2104, '学员', NULL, '1997-09-30', '硕士', '数据工程师', NOW(), NOW(), 0),
(2206, 2105, '学员', NULL, '2001-04-02', '本科', '应届生', NOW(), NOW(), 0),
(2207, 2106, '学员', NULL, '1995-12-11', '本科', '运维工程师', NOW(), NOW(), 0),
(2208, 2107, '学员', NULL, '2000-06-25', '本科', '前端开发', NOW(), NOW(), 0);

-- =====================================================================
-- 二、题库（zx_exam）：教师发布 → 学员接收的联动载体
--    id 1~3 与 init.sql 中既有答题记录对齐，避免出现孤儿记录
-- =====================================================================
USE `zx_exam`;

INSERT IGNORE INTO `question` (`id`, `name`, `type`, `difficulty`, `score`, `content`, `options`, `answer`, `analysis`, `teacher_id`, `course_id`, `status`, `create_time`, `update_time`, `deleted`) VALUES
-- —— 与 init.sql 既有答题记录对应的基础题 ——
(1, 'Java 基本数据类型中，下列哪一项不是基本类型？', 1, 1, 10, NULL,
 '["int","String","boolean","double"]', 'B',
 'String 是引用类型（java.lang.String），int/boolean/double 均为 JVM 规范定义的八种基本类型之一。', 2002, 3001, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(2, 'Spring 中实现依赖注入的方式有哪种？', 1, 2, 10, NULL,
 '["仅构造器注入","仅 Setter 注入","构造器注入与 Setter 注入","只能通过 XML 注入"]', 'C',
 'Spring 支持构造器注入、Setter 注入以及字段注入（@Autowired 直接标注字段），XML 只是配置载体之一。', 2002, 3002, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(3, '关于 JVM 内存模型，下列说法正确的是？', 2, 3, 10, NULL,
 '["方法区用于存放类元信息","虚拟机栈是线程私有的","堆是所有线程共享的","程序计数器是线程共享的"]', 'ABC',
 '程序计数器（PC 寄存器）是线程私有的，用于记录当前线程执行字节码的行号，故 D 错误。', 2002, 3001, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),

-- —— Java 21 核心技术（3001，教师 2002）——
(5001, 'Java 21 中用于简化数据载体类声明的语法是？', 1, 2, 5, NULL,
 '["record","struct","data class","tuple"]', 'A',
 'record 是 Java 16 转正、Java 21 广泛使用的不可变数据载体语法，编译器自动生成构造器、访问器与 equals/hashCode。', 2002, 3001, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(5002, '下列哪些属于 Java 并发包的线程池拒绝策略？', 2, 3, 10, NULL,
 '["AbortPolicy","CallerRunsPolicy","DiscardOldestPolicy","RetryPolicy"]', 'ABC',
 'JDK 提供四种拒绝策略：AbortPolicy、CallerRunsPolicy、DiscardPolicy、DiscardOldestPolicy，没有 RetryPolicy。', 2002, 3001, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(5003, '虚拟线程（Virtual Thread）在 Java 21 中已正式转正。', 3, 2, 5, NULL,
 '["正确","错误"]', 'A',
 'JEP 444 在 Java 21 将虚拟线程正式转正，以极低开销支撑海量并发任务。', 2002, 3001, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(5004, 'JVM 垃圾回收中，用于判断对象存活的主流算法是？', 1, 2, 5, NULL,
 '["引用计数法","可达性分析","标记计数法","分代计数法"]', 'B',
 '主流商用 JVM 使用可达性分析（GC Roots 遍历），引用计数无法处理循环引用。', 2002, 3001, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(5005, '下列哪些是 JVM 运行时数据区中线程私有的部分？', 2, 3, 10, NULL,
 '["程序计数器","虚拟机栈","本地方法栈","堆"]', 'ABC',
 '堆与方法区（元空间）为线程共享；程序计数器、虚拟机栈、本地方法栈为线程私有。', 2002, 3001, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),

-- —— Spring Boot 3 企业级实战（3002，教师 2002）——
(5006, 'Spring Boot 3 要求的最低 JDK 版本是？', 1, 1, 5, NULL,
 '["JDK 8","JDK 11","JDK 17","JDK 21"]', 'C',
 'Spring Framework 6 / Spring Boot 3 基线为 JDK 17，并支持到 JDK 21。', 2002, 3002, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(5007, 'Spring Boot 中用于条件化装配 Bean 的注解包括？', 2, 3, 10, NULL,
 '["@ConditionalOnClass","@ConditionalOnMissingBean","@ConditionalOnProperty","@ConditionalInject"]', 'ABC',
 '@ConditionalInject 并非 Spring 提供的注解；前三个是自动配置中常用的条件注解。', 2002, 3002, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(5008, '微服务架构中，服务注册与发现组件可选用 Nacos。', 3, 1, 5, NULL,
 '["正确","错误"]', 'A',
 'Nacos 兼具服务注册发现与配置管理能力，是 Spring Cloud Alibaba 的核心组件。', 2002, 3002, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(5009, '关于幂等性设计，下列说法错误的是？', 1, 4, 10, NULL,
 '["可通过唯一索引兜底","可用 Redis 原子操作实现","分布式锁是唯一手段","条件更新可保证状态只迁移一次"]', 'C',
 '幂等实现手段多样（唯一索引、Redis setnx/Lua、状态机条件更新、本地消息表），分布式锁并非唯一手段。', 2002, 3002, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),

-- —— MySQL 8 性能优化与索引设计（3009，教师 2011）——
(5010, 'InnoDB 中聚簇索引的叶子节点存储的是？', 1, 3, 10, NULL,
 '["主键值","整行数据","索引列值+主键","二级索引指针"]', 'B',
 'InnoDB 聚簇索引的叶子节点直接存放整行数据；二级索引叶子节点存放索引列值与主键值。', 2011, 3009, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(5011, '下列哪些情况会导致索引失效？', 2, 4, 10, NULL,
 '["对索引列使用函数运算","前导模糊查询 LIKE %abc","隐式类型转换","使用覆盖索引"]', 'ABC',
 '覆盖索引恰恰是优化手段而非失效原因；前三者都会使 B+ 树索引无法有效定位数据。', 2011, 3009, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(5012, 'EXPLAIN 输出中 type=ALL 表示全表扫描。', 3, 2, 5, NULL,
 '["正确","错误"]', 'A',
 'type 字段的取值从优到劣为 system > const > eq_ref > ref > range > index > ALL，ALL 即全表扫描。', 2011, 3009, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(5013, '关于最左前缀原则，下列表述正确的是？', 1, 3, 10, NULL,
 '["联合索引 (a,b,c) 单独用 b 能走索引","联合索引 (a,b,c) 用 a,b 能走索引","联合索引与顺序无关","范围查询后的列仍能走索引"]', 'B',
 '联合索引需从最左列连续匹配；范围查询（如 a>1）之后的列无法继续用于索引定位。', 2011, 3009, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),

-- —— Vue 3 进阶（3010，教师 2012）——
(5014, 'Vue 3 中用于创建响应式基本类型数据的 API 是？', 1, 1, 5, NULL,
 '["reactive","ref","computed","watch"]', 'B',
 'ref 可包装任意类型并返回带 .value 的响应式对象；reactive 仅适用于对象类型。', 2012, 3010, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(5015, '下列哪些属于 Vue 3 组合式 API？', 2, 2, 5, NULL,
 '["setup","onMounted","defineProps","data"]', 'ABC',
 'data 是选项式 API 的写法；setup/onMounted/defineProps 均为组合式 API 的组成部分。', 2012, 3010, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(5016, 'Pinia 的 store 必须在组件内通过 useXxxStore() 调用后使用。', 3, 2, 5, NULL,
 '["正确","错误"]', 'A',
 'Pinia 使用惰性初始化，必须在 setup 或组件上下文中调用 useStore() 才能正确注入实例。', 2012, 3010, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(5017, 'Vue 3 中 watch 与 watchEffect 的主要区别是？', 1, 3, 10, NULL,
 '["watchEffect 需要显式指定侦听源","watch 默认立即执行一次","watch 可获取新旧值，watchEffect 自动收集依赖","两者完全等价"]', 'C',
 'watch 需显式指定源并可拿到新旧值；watchEffect 自动收集依赖、默认立即执行一次但不提供旧值。', 2012, 3010, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),

-- —— 大模型 RAG 应用高级实战（3012，教师 2011）——
(5018, 'RAG 中向量检索的核心步骤是？', 1, 3, 10, NULL,
 '["文本分块与向量化","加密存储","随机采样","全量关键词匹配"]', 'A',
 'RAG 检索链路为：文档切分 → Embedding 向量化 → 向量库存储 → 相似度召回 → 重排。', 2011, 3012, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(5019, '下列哪些手段可以提升 RAG 的召回质量？', 2, 4, 10, NULL,
 '["混合检索（向量+关键词）","结果重排 Rerank","查询改写 Query Rewrite","增大模型温度"]', 'ABC',
 '调高温度只会增加生成的随机性，与召回质量无关。', 2011, 3012, 1, NOW(), NOW(), 0),
(5020, 'RAG 可以有效缓解大模型的幻觉问题。', 3, 2, 5, NULL,
 '["正确","错误"]', 'A',
 '通过检索外部知识作为上下文，RAG 能显著降低模型凭空编造的概率。', 2011, 3012, 1, NOW(), NOW(), 0),

-- —— Python 数据分析（3003，教师 2012）——
(5021, 'Pandas 中用于按列分组聚合的方法是？', 1, 2, 5, NULL,
 '["groupby","pivot","melt","concat"]', 'A',
 'groupby 后接聚合函数（sum/mean/count 等）即可实现分组统计。', 2012, 3003, 1, NOW(), NOW(), 0),
(5022, '下列哪些属于监督学习的典型任务？', 2, 3, 10, NULL,
 '["分类","回归","聚类","排序"]', 'ABD',
 '聚类属于无监督学习；分类、回归与排序（Learning to Rank）均为监督学习任务。', 2012, 3003, 1, NOW(), NOW(), 0),
(5023, 'NumPy 的 ndarray 支持广播机制。', 3, 2, 5, NULL,
 '["正确","错误"]', 'A',
 '广播（broadcasting）让不同形状的数组在算术运算中自动对齐维度。', 2012, 3003, 1, NOW(), NOW(), 0),
(5024, '关于过拟合，下列表述正确的是？', 1, 3, 10, NULL,
 '["训练集表现差、测试集表现好","训练集表现好、测试集表现差","训练与测试表现都差","与数据量无关"]', 'B',
 '过拟合即模型过度记忆训练集噪声，导致泛化能力下降；可通过正则化、早停、扩充数据缓解。', 2012, 3003, 1, NOW(), NOW(), 0),

-- —— 草稿题（验证"教师未发布 → 学员不可见"）——
(5025, '【草稿】下列哪项尚未定稿？', 1, 1, 5, NULL,
 '["A 选项","B 选项","C 选项","D 选项"]', 'A',
 '本题 status=0，仅教师端可见，学员端题库练习不会出现。', 2002, 3001, 0, NOW(), NOW(), 0);

-- =====================================================================
-- 三、答题记录（zx_exam）：多学员 × 多题，时间分布在近 7 日
--    记录刻意覆盖：全对 / 部分错 / 全错，形成真实正确率梯度
-- =====================================================================

-- 学员 2001（知行学员，中等偏上，正确率约 70%）
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `user_answer`, `course_id`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8601, 2001, 1,    (SELECT name FROM `question` WHERE id=1),    'B',  3001, 1,  10, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(8602, 2001, 2,    (SELECT name FROM `question` WHERE id=2),    'C',  3002, 1,  10, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(8603, 2001, 3,    (SELECT name FROM `question` WHERE id=3),    'AB', 3001, 0,   0, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8604, 2001, 5001, (SELECT name FROM `question` WHERE id=5001), 'A',  3001, 1,   5, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8605, 2001, 5002, (SELECT name FROM `question` WHERE id=5002), 'ABC',3001, 1,  10, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8606, 2001, 5004, (SELECT name FROM `question` WHERE id=5004), 'B',  3001, 1,   5, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8607, 2001, 5006, (SELECT name FROM `question` WHERE id=5006), 'D',  3002, 0,   0, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8608, 2001, 5009, (SELECT name FROM `question` WHERE id=5009), 'C',  3002, 1,  10, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8609, 2001, 5010, (SELECT name FROM `question` WHERE id=5010), 'B',  3009, 1,  10, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8610, 2001, 5011, (SELECT name FROM `question` WHERE id=5011), 'AC', 3009, 0,   0, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8611, 2001, 5014, (SELECT name FROM `question` WHERE id=5014), 'B',  3010, 1,   5, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8612, 2001, 5018, (SELECT name FROM `question` WHERE id=5018), 'A',  3012, 1,  10, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8613, 2001, 5019, (SELECT name FROM `question` WHERE id=5019), 'ABC',3012, 1,  10, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8614, 2001, 5021, (SELECT name FROM `question` WHERE id=5021), 'C',  3003, 0,   0, NOW(), NOW(), 0);

-- 学员 2101（陈志远，优秀学员，正确率约 92%）
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `user_answer`, `course_id`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8621, 2101, 5001, (SELECT name FROM `question` WHERE id=5001), 'A',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(8622, 2101, 5002, (SELECT name FROM `question` WHERE id=5002), 'ABC',3001, 1, 10, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(8623, 2101, 5003, (SELECT name FROM `question` WHERE id=5003), 'A',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8624, 2101, 5004, (SELECT name FROM `question` WHERE id=5004), 'B',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8625, 2101, 5005, (SELECT name FROM `question` WHERE id=5005), 'ABC',3001, 1, 10, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8626, 2101, 5006, (SELECT name FROM `question` WHERE id=5006), 'C',  3002, 1,  5, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8627, 2101, 5007, (SELECT name FROM `question` WHERE id=5007), 'ABC',3002, 1, 10, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8628, 2101, 5009, (SELECT name FROM `question` WHERE id=5009), 'B',  3002, 0,  0, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8629, 2101, 5010, (SELECT name FROM `question` WHERE id=5010), 'B',  3009, 1, 10, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8630, 2101, 5011, (SELECT name FROM `question` WHERE id=5011), 'ABC',3009, 1, 10, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8631, 2101, 5013, (SELECT name FROM `question` WHERE id=5013), 'B',  3009, 1, 10, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8632, 2101, 5014, (SELECT name FROM `question` WHERE id=5014), 'B',  3010, 1,  5, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8633, 2101, 5017, (SELECT name FROM `question` WHERE id=5017), 'C',  3010, 1, 10, NOW(), NOW(), 0);

-- 学员 2102（林小雨，基础薄弱，正确率约 45%）
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `user_answer`, `course_id`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8641, 2102, 5001, (SELECT name FROM `question` WHERE id=5001), 'B',  3001, 0,  0, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8642, 2102, 5002, (SELECT name FROM `question` WHERE id=5002), 'AB', 3001, 0,  0, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8643, 2102, 5003, (SELECT name FROM `question` WHERE id=5003), 'A',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8644, 2102, 5006, (SELECT name FROM `question` WHERE id=5006), 'C',  3002, 1,  5, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8645, 2102, 5008, (SELECT name FROM `question` WHERE id=5008), 'A',  3002, 1,  5, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8646, 2102, 5014, (SELECT name FROM `question` WHERE id=5014), 'A',  3010, 0,  0, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8647, 2102, 5015, (SELECT name FROM `question` WHERE id=5015), 'ABCD',3010, 0,  0, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8648, 2102, 5016, (SELECT name FROM `question` WHERE id=5016), 'A',  3010, 1,  5, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8649, 2102, 5018, (SELECT name FROM `question` WHERE id=5018), 'C',  3012, 0,  0, NOW(), NOW(), 0),
(8650, 2102, 5020, (SELECT name FROM `question` WHERE id=5020), 'A',  3012, 1,  5, NOW(), NOW(), 0);

-- 学员 2103（赵梓涵，中等，正确率约 67%）
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `user_answer`, `course_id`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8661, 2103, 5004, (SELECT name FROM `question` WHERE id=5004), 'B',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8662, 2103, 5005, (SELECT name FROM `question` WHERE id=5005), 'AB', 3001, 0,  0, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8663, 2103, 5007, (SELECT name FROM `question` WHERE id=5007), 'ABC',3002, 1, 10, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8664, 2103, 5009, (SELECT name FROM `question` WHERE id=5009), 'C',  3002, 1, 10, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8665, 2103, 5012, (SELECT name FROM `question` WHERE id=5012), 'A',  3009, 1,  5, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8666, 2103, 5013, (SELECT name FROM `question` WHERE id=5013), 'A',  3009, 0,  0, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8667, 2103, 5017, (SELECT name FROM `question` WHERE id=5017), 'C',  3010, 1, 10, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8668, 2103, 5019, (SELECT name FROM `question` WHERE id=5019), 'ABCD',3012, 0,  0, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8669, 2103, 5024, (SELECT name FROM `question` WHERE id=5024), 'B',  3003, 1, 10, NOW(), NOW(), 0);

-- 学员 2104（王浩然，较好，正确率约 80%）
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `user_answer`, `course_id`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8681, 2104, 5001, (SELECT name FROM `question` WHERE id=5001), 'A',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8682, 2104, 5004, (SELECT name FROM `question` WHERE id=5004), 'B',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8683, 2104, 5006, (SELECT name FROM `question` WHERE id=5006), 'C',  3002, 1,  5, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8684, 2104, 5009, (SELECT name FROM `question` WHERE id=5009), 'A',  3002, 0,  0, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8685, 2104, 5010, (SELECT name FROM `question` WHERE id=5010), 'B',  3009, 1, 10, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8686, 2104, 5011, (SELECT name FROM `question` WHERE id=5011), 'ABC',3009, 1, 10, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8687, 2104, 5014, (SELECT name FROM `question` WHERE id=5014), 'B',  3010, 1,  5, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8688, 2104, 5018, (SELECT name FROM `question` WHERE id=5018), 'A',  3012, 1, 10, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8689, 2104, 5021, (SELECT name FROM `question` WHERE id=5021), 'A',  3003, 1,  5, NOW(), NOW(), 0),
(8690, 2104, 5024, (SELECT name FROM `question` WHERE id=5024), 'A',  3003, 0,  0, NOW(), NOW(), 0);

-- 学员 2105（刘思彤，中等偏弱，正确率约 55%）
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `user_answer`, `course_id`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8701, 2105, 5002, (SELECT name FROM `question` WHERE id=5002), 'ABC',3001, 1, 10, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8702, 2105, 5003, (SELECT name FROM `question` WHERE id=5003), 'B',  3001, 0,  0, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8703, 2105, 5005, (SELECT name FROM `question` WHERE id=5005), 'ABC',3001, 1, 10, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8704, 2105, 5007, (SELECT name FROM `question` WHERE id=5007), 'AB', 3002, 0,  0, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8705, 2105, 5012, (SELECT name FROM `question` WHERE id=5012), 'A',  3009, 1,  5, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8706, 2105, 5015, (SELECT name FROM `question` WHERE id=5015), 'ABC',3010, 1,  5, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8707, 2105, 5016, (SELECT name FROM `question` WHERE id=5016), 'B',  3010, 0,  0, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8708, 2105, 5020, (SELECT name FROM `question` WHERE id=5020), 'A',  3012, 1,  5, NOW(), NOW(), 0),
(8709, 2105, 5022, (SELECT name FROM `question` WHERE id=5022), 'AB', 3003, 0,  0, NOW(), NOW(), 0);

-- 学员 2106（孙一鸣，较好，正确率约 78%）
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `user_answer`, `course_id`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8721, 2106, 5001, (SELECT name FROM `question` WHERE id=5001), 'A',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8722, 2106, 5004, (SELECT name FROM `question` WHERE id=5004), 'B',  3001, 1,  5, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8723, 2106, 5008, (SELECT name FROM `question` WHERE id=5008), 'A',  3002, 1,  5, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8724, 2106, 5010, (SELECT name FROM `question` WHERE id=5010), 'A',  3009, 0,  0, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8725, 2106, 5011, (SELECT name FROM `question` WHERE id=5011), 'ABC',3009, 1, 10, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8726, 2106, 5013, (SELECT name FROM `question` WHERE id=5013), 'B',  3009, 1, 10, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8727, 2106, 5019, (SELECT name FROM `question` WHERE id=5019), 'ABC',3012, 1, 10, NOW(), NOW(), 0),
(8728, 2106, 5023, (SELECT name FROM `question` WHERE id=5023), 'A',  3003, 1,  5, NOW(), NOW(), 0),
(8729, 2106, 5024, (SELECT name FROM `question` WHERE id=5024), 'C',  3003, 0,  0, NOW(), NOW(), 0);

-- =====================================================================
-- 四、学习数据（zx_learning）：课表 / 学习记录 / 笔记 / 签到
--    学习记录的 last_learn_time 分布在近 7 日，让"学习趋势"折线图有真实波形
-- =====================================================================
USE `zx_learning`;

INSERT IGNORE INTO `lesson` (`id`, `user_id`, `course_id`, `course_name`, `create_time`, `update_time`, `deleted`) VALUES
(8201, 2001, 3001, 'Java 21 核心技术：从入门到精通', NOW(), NOW(), 0),
(8202, 2001, 3002, 'Spring Boot 3 企业级实战', NOW(), NOW(), 0),
(8211, 2101, 3001, 'Java 21 核心技术：从入门到精通', NOW(), NOW(), 0),
(8212, 2101, 3009, 'MySQL 8 性能优化与索引设计', NOW(), NOW(), 0),
(8213, 2101, 3010, 'Vue 3 进阶：Pinia 与组合式 API', NOW(), NOW(), 0),
(8221, 2102, 3001, 'Java 21 核心技术：从入门到精通', NOW(), NOW(), 0),
(8222, 2102, 3010, 'Vue 3 进阶：Pinia 与组合式 API', NOW(), NOW(), 0),
(8231, 2103, 3002, 'Spring Boot 3 企业级实战', NOW(), NOW(), 0),
(8232, 2103, 3012, '大模型 RAG 应用高级实战', NOW(), NOW(), 0),
(8241, 2104, 3009, 'MySQL 8 性能优化与索引设计', NOW(), NOW(), 0),
(8251, 2105, 3010, 'Vue 3 进阶：Pinia 与组合式 API', NOW(), NOW(), 0),
(8261, 2106, 3003, 'Python 数据分析与机器学习入门', NOW(), NOW(), 0);

INSERT IGNORE INTO `learning_record` (`id`, `user_id`, `course_id`, `lesson_id`, `progress`, `finished`, `learn_duration`, `last_learn_time`, `create_time`, `update_time`, `deleted`) VALUES
-- 学员 2001：近 7 日每日都有学习，时长呈上升趋势
(8101, 2001, 3001, 4101, 100, 1, 3600, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), NOW(), 0),
(8102, 2001, 3002, 4102,  60, 0, 2400, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW(), 0),
(8103, 2001, 3008, 4201, 100, 1, 1200, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW(), 0),
(8104, 2001, 3009, 4103,  75, 0, 3000, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW(), 0),
(8105, 2001, 3009, 4104,  80, 0, 2700, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW(), 0),
(8106, 2001, 3010, 4301,  45, 0, 3300, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 0),
(8107, 2001, 3012, 4302,  30, 0, 2100, NOW(), NOW(), NOW(), 0),
-- 学员 2101：高强度学习
(8111, 2101, 3001, 4101, 100, 1, 4200, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), NOW(), 0),
(8112, 2101, 3001, 4102, 100, 1, 3900, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW(), 0),
(8113, 2101, 3009, 4103,  90, 0, 4500, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW(), 0),
(8114, 2101, 3009, 4104, 100, 1, 5200, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW(), 0),
(8115, 2101, 3010, 4301,  85, 0, 3600, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW(), 0),
(8116, 2101, 3010, 4302,  95, 0, 4800, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 0),
(8117, 2101, 3002, 4105,  40, 0, 3000, NOW(), NOW(), NOW(), 0),
-- 学员 2102：学习时长偏低
(8121, 2102, 3001, 4101,  55, 0,  900, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW(), 0),
(8122, 2102, 3001, 4102,  60, 0, 1200, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW(), 0),
(8123, 2102, 3010, 4301,  35, 0,  800, NOW(), NOW(), NOW(), 0),
-- 学员 2103：稳定中等
(8131, 2103, 3002, 4105,  70, 0, 1800, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW(), 0),
(8132, 2103, 3002, 4106,  75, 0, 2100, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW(), 0),
(8133, 2103, 3012, 4303,  50, 0, 2400, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 0),
(8134, 2103, 3012, 4304,  55, 0, 1900, NOW(), NOW(), NOW(), 0),
-- 学员 2104
(8141, 2104, 3009, 4103,  95, 0, 3900, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW(), 0),
(8142, 2104, 3009, 4104, 100, 1, 4200, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW(), 0),
(8143, 2104, 3012, 4303,  60, 0, 2700, NOW(), NOW(), NOW(), 0),
-- 学员 2105
(8151, 2105, 3010, 4301,  45, 0, 1500, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW(), 0),
(8152, 2105, 3010, 4302,  50, 0, 1700, NOW(), NOW(), NOW(), 0),
-- 学员 2106
(8161, 2106, 3003, 4305,  65, 0, 2200, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW(), 0),
(8162, 2106, 3003, 4306,  80, 0, 2600, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 0),
(8163, 2106, 3009, 4103,  35, 0, 1400, NOW(), NOW(), NOW(), 0);

INSERT IGNORE INTO `note` (`id`, `user_id`, `course_id`, `lesson_id`, `content`, `privacy`, `create_time`, `update_time`, `deleted`) VALUES
(8401, 2001, 3001, 4102, 'JVM 内存区域划分：堆、栈、方法区，GC 分代收集策略。', 0, NOW(), NOW(), 0),
(8402, 2001, 3009, 4103, '联合索引遵循最左前缀原则，范围查询后的列无法继续用于索引定位。', 0, NOW(), NOW(), 0),
(8411, 2101, 3009, 4104, 'EXPLAIN 的 type 字段：ALL 全表扫描 > index 全索引扫描 > range > ref > const，越靠左越差。', 0, NOW(), NOW(), 0),
(8412, 2102, 3001, 4101, 'record 是 Java 的不可变数据载体，编译期自动生成 equals/hashCode。', 0, NOW(), NOW(), 0),
(8431, 2104, 3012, 4303, 'RAG 召回优化三板斧：混合检索 + Query Rewrite + Rerank。', 0, NOW(), NOW(), 0);

INSERT IGNORE INTO `sign_in` (`id`, `user_id`, `sign_date`, `streak`, `points`, `create_time`) VALUES
(8301, 2001, CURDATE(), 5, 50, NOW()),
(8311, 2101, CURDATE(), 12, 60, NOW()),
(8312, 2102, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1, 10, NOW()),
(8313, 2103, CURDATE(), 4, 40, NOW()),
(8314, 2104, CURDATE(), 7, 50, NOW()),
(8315, 2105, CURDATE(), 2, 20, NOW()),
(8316, 2106, CURDATE(), 9, 55, NOW());

-- =====================================================================
-- 五、学情报告（zx_insight）：预置当日报告，学情页无需触发大模型即可秒开
-- =====================================================================
USE `zx_insight`;

INSERT IGNORE INTO `insight_report` (`id`, `user_id`, `report_date`, `engagement`, `completion`, `quiz_ability`, `breadth`, `comprehension`, `weakness`, `recommendations`, `summary`, `ai_generated`, `create_time`, `update_time`, `deleted`) VALUES
(8801, 2001, CURDATE(), 82, 76, 71, 72, 74, '["数据库索引设计偏弱","多选题目失分较多"]', '["保持每日 1 小时学习节奏","优先强化 SQL 与索引优化","针对多选题型做专项训练"]', '综合来看，你的学习投入度高、课程完成度良好，答题正确率处于中上水平。主要薄弱点在数据库索引设计，建议按学习路径优先完成《MySQL 8 性能优化与索引设计》的索引章节，并针对多选题做专项练习。', 0, NOW(), NOW(), 0),
(8811, 2101, CURDATE(), 95, 92, 93, 88, 90, '["知识广度可继续拓展"]', '["保持当前节奏，向架构方向延伸","尝试参与项目实战巩固"]', '学习投入度与答题能力均处于班级前列，课程完成度高。建议在保持现有节奏的基础上拓展知识广度，参与综合性项目实战。', 0, NOW(), NOW(), 0),
(8812, 2102, CURDATE(), 45, 38, 44, 52, 48, '["学习投入不足","基础概念掌握不牢","练习量偏少"]', '["制定每日固定学习时段","从基础课程 3001 重新梳理","先做基础题（难度 1-2）建立信心"]', '当前学习投入与练习量偏低，基础概念掌握不够扎实。建议先降低难度，从 Java 基础与前端基础课程入手，配合每日固定学习时段逐步建立学习习惯。', 0, NOW(), NOW(), 0),
(8813, 2103, CURDATE(), 68, 62, 66, 70, 65, '["多选题目正确率偏低","RAG 相关章节需复习"]', '["加强多选题训练","复习 3012 课程第三、四章"]', '学习节奏稳定，完成度良好。多选题与 RAG 章节是主要失分点，建议针对性复习并增加练习量。', 0, NOW(), NOW(), 0),
(8814, 2104, CURDATE(), 78, 82, 80, 74, 78, '["知识广度一般"]', '["保持优势，横向拓展后端生态"]', '学习完成度与答题正确率均表现良好，建议在纵深的基础上横向拓展知识面。', 0, NOW(), NOW(), 0),
(8815, 2105, CURDATE(), 55, 48, 55, 60, 52, '["学习持续性不足","前端响应式原理不熟"]', '["设定每周学习目标","复习 Vue 响应式原理章节"]', '学习投入呈波动状态，持续性有待加强。建议设定每周可量化目标，并重点复习 Vue 响应式原理相关内容。', 0, NOW(), NOW(), 0),
(8816, 2106, CURDATE(), 76, 74, 78, 72, 75, '["索引优化经验不足"]', '["补充 3009 课程实验练习"]', '整体学习状态良好，索引优化部分缺乏实践经验，建议结合案例多做实验。', 0, NOW(), NOW(), 0);

-- =====================================================================
-- 六、购物车与订单（zx_trade）：覆盖全部状态，关联真实课程与用户
-- =====================================================================
USE `zx_trade`;

INSERT IGNORE INTO `cart` (`id`, `user_id`, `course_id`, `course_name`, `course_price`, `create_time`, `update_time`, `deleted`) VALUES
(8701, 2001, 3003, 'Python 数据分析与机器学习入门', 15900, NOW(), NOW(), 0),
(8702, 2001, 3004, 'Go 语言高并发编程实战', 25900, NOW(), NOW(), 0),
(8711, 2101, 3007, '大模型应用开发：RAG 与 Agent', 39900, NOW(), NOW(), 0),
(8721, 2102, 3005, 'Vue 3 + TypeScript 前端工程化', 16900, NOW(), NOW(), 0),
(8731, 2103, 3006, 'React 18 状态管理与性能优化', 18900, NOW(), NOW(), 0),
(8741, 2104, 3012, '大模型 RAG 应用高级实战', 42900, NOW(), NOW(), 0),
(8751, 2105, 3010, 'Vue 3 进阶：Pinia 与组合式 API', 17900, NOW(), NOW(), 0),
(8761, 2106, 3011, 'Go 微服务与 gRPC 高并发实战', 26900, NOW(), NOW(), 0);

-- 订单：status 0 待支付 / 1 已支付 / 2 已关闭 / 3 退款中 / 4 已退款
INSERT IGNORE INTO `trade_order` (`id`, `order_no`, `user_id`, `course_id`, `course_name`, `course_price`, `total_fee`, `coupon_id`, `deduction`, `status`, `pay_type`, `pay_time`, `create_time`, `update_time`, `deleted`) VALUES
-- 学员 2001
(8601, 'DEMO202606100001', 2001, 3001, 'Java 21 核心技术：从入门到精通', 19900, 19900, NULL,     0, 1, 3, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(8602, 'DEMO202606100002', 2001, 3002, 'Spring Boot 3 企业级实战',        29900, 23900, 6002,  6000, 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8603, 'DEMO202606100003', 2001, 3008, 'Java 入门第一课（免费）',            0,     0, NULL,     0, 1, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8604, 'DEMO202606100004', 2001, 3007, '大模型应用开发：RAG 与 Agent',    39900, 39900, NULL,     0, 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8605, 'DEMO202606100005', 2001, 3006, 'React 18 状态管理与性能优化',     18900, 18900, NULL,     0, 2, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
(8606, 'DEMO202606100006', 2001, 3004, 'Go 语言高并发编程实战',           25900, 25100, 6003,   800, 3, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
-- 学员 2101
(8611, 'DEMO202606100011', 2101, 3001, 'Java 21 核心技术：从入门到精通', 19900, 19900, NULL,     0, 1, 2, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(8612, 'DEMO202606100012', 2101, 3009, 'MySQL 8 性能优化与索引设计',      22900, 22900, NULL,     0, 1, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8613, 'DEMO202606100013', 2101, 3010, 'Vue 3 进阶：Pinia 与组合式 API',  17900, 17100, 6003,   800, 4, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8614, 'DEMO202606100014', 2101, 3012, '大模型 RAG 应用高级实战',         42900, 42900, NULL,     0, 1, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
-- 学员 2102
(8621, 'DEMO202606100021', 2102, 3001, 'Java 21 核心技术：从入门到精通', 19900, 18900, 6001,  1000, 1, 2, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(8622, 'DEMO202606100022', 2102, 3010, 'Vue 3 进阶：Pinia 与组合式 API',  17900, 17900, NULL,     0, 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
-- 学员 2103
(8631, 'DEMO202606100031', 2103, 3002, 'Spring Boot 3 企业级实战',        29900, 29900, NULL,     0, 1, 3, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(8632, 'DEMO202606100032', 2103, 3012, '大模型 RAG 应用高级实战',         42900, 42100, 6003,   800, 3, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
-- 学员 2104
(8641, 'DEMO202606100041', 2104, 3009, 'MySQL 8 性能优化与索引设计',      22900, 21900, 6001,  1000, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(8642, 'DEMO202606100042', 2104, 3003, 'Python 数据分析与机器学习入门',    15900, 15900, NULL,     0, 1, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
-- 学员 2105
(8651, 'DEMO202606100051', 2105, 3010, 'Vue 3 进阶：Pinia 与组合式 API',  17900, 17900, NULL,     0, 2, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
-- 学员 2106
(8661, 'DEMO202606100061', 2106, 3003, 'Python 数据分析与机器学习入门',    15900, 15900, NULL,     0, 1, 3, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(8662, 'DEMO202606100062', 2106, 3009, 'MySQL 8 性能优化与索引设计',      22900, 22900, NULL,     0, 0, NULL, NULL, NOW(), NOW(), 0);

INSERT IGNORE INTO `trade_order_detail` (`id`, `order_id`, `course_id`, `name`, `price`, `create_time`, `update_time`, `deleted`) VALUES
(8611, 8601, 3001, 'Java 21 核心技术：从入门到精通', 19900, NOW(), NOW(), 0),
(8612, 8602, 3002, 'Spring Boot 3 企业级实战',        29900, NOW(), NOW(), 0),
(8613, 8603, 3008, 'Java 入门第一课（免费）',            0, NOW(), NOW(), 0),
(8614, 8604, 3007, '大模型应用开发：RAG 与 Agent',    39900, NOW(), NOW(), 0),
(8615, 8605, 3006, 'React 18 状态管理与性能优化',     18900, NOW(), NOW(), 0),
(8616, 8606, 3004, 'Go 语言高并发编程实战',           25900, NOW(), NOW(), 0),
(8617, 8611, 3001, 'Java 21 核心技术：从入门到精通', 19900, NOW(), NOW(), 0),
(8618, 8612, 3009, 'MySQL 8 性能优化与索引设计',      22900, NOW(), NOW(), 0),
(8619, 8613, 3010, 'Vue 3 进阶：Pinia 与组合式 API',  17900, NOW(), NOW(), 0),
(8620, 8614, 3012, '大模型 RAG 应用高级实战',         42900, NOW(), NOW(), 0),
(8621, 8621, 3001, 'Java 21 核心技术：从入门到精通', 19900, NOW(), NOW(), 0),
(8622, 8622, 3010, 'Vue 3 进阶：Pinia 与组合式 API',  17900, NOW(), NOW(), 0),
(8623, 8631, 3002, 'Spring Boot 3 企业级实战',        29900, NOW(), NOW(), 0),
(8624, 8632, 3012, '大模型 RAG 应用高级实战',         42900, NOW(), NOW(), 0),
(8625, 8641, 3009, 'MySQL 8 性能优化与索引设计',      22900, NOW(), NOW(), 0),
(8626, 8642, 3003, 'Python 数据分析与机器学习入门',    15900, NOW(), NOW(), 0),
(8627, 8651, 3010, 'Vue 3 进阶：Pinia 与组合式 API',  17900, NOW(), NOW(), 0),
(8628, 8661, 3003, 'Python 数据分析与机器学习入门',    15900, NOW(), NOW(), 0),
(8629, 8662, 3009, 'MySQL 8 性能优化与索引设计',      22900, NOW(), NOW(), 0);

-- 支付流水（与已支付订单对应，便于对账演示）
INSERT IGNORE INTO `trade_pay_record` (`id`, `order_id`, `pay_no`, `pay_type`, `amount`, `status`, `callback_time`, `create_time`, `update_time`, `deleted`) VALUES
(8901, 8601, 'PAY202606100001', 3, 19900, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), NOW(), 0),
(8902, 8602, 'PAY202606100002', 1, 23900, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW(), 0),
(8911, 8611, 'PAY202606100011', 2, 19900, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), NOW(), 0),
(8912, 8612, 'PAY202606100012', 1, 22900, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW(), 0),
(8921, 8621, 'PAY202606100021', 2, 18900, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW(), 0),
(8931, 8631, 'PAY202606100031', 3, 29900, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW(), 0),
(8941, 8641, 'PAY202606100041', 1, 21900, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW(), 0),
(8942, 8642, 'PAY202606100042', 2, 15900, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW(), 0),
(8961, 8661, 'PAY202606100061', 3, 15900, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW(), 0);

-- 退款申请：覆盖待审核 / 已通过 / 已拒绝，且与订单状态一一对应
INSERT IGNORE INTO `refund_apply` (`id`, `order_id`, `user_id`, `course_id`, `amount`, `reason`, `status`, `remark`, `create_time`, `update_time`, `deleted`) VALUES
(8951, 8606, 2001, 3004, 25100, '课程内容与预期不符，希望退款', 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(8952, 8613, 2101, 3010, 17100, '重复购买，申请退款',           1, '核实确属重复购买，同意退款', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(8953, 8632, 2103, 3012, 42100, '近期无时间学习，申请退款',     0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0);

-- =====================================================================
-- 七、优惠券（zx_promotion）：券模板已就绪，补发用户券并同步发放量
-- =====================================================================
USE `zx_promotion`;

INSERT IGNORE INTO `user_coupon` (`id`, `user_id`, `coupon_id`, `coupon_name`, `discount_amount`, `threshold_amount`, `status`, `valid_begin_time`, `valid_end_time`, `create_time`, `update_time`) VALUES
(7002, 2001, 6002, '满 300 减 60 通用券',  6000, 30000, 0, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7003, 2001, 6003, '全场 9 折无门槛券',     800,     0, 0, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7004, 2001, 6004, '爆款课程 5 折秒杀券',  5000,     0, 0, '2026-09-01 10:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7011, 2101, 6001, '新人立减券',           1000,  5000, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7012, 2101, 6003, '全场 9 折无门槛券',     800,     0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7021, 2102, 6001, '新人立减券',           1000,  5000, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7031, 2103, 6003, '全场 9 折无门槛券',     800,     0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7041, 2104, 6001, '新人立减券',           1000,  5000, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7051, 2105, 6003, '全场 9 折无门槛券',     800,     0, 0, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7061, 2106, 6004, '爆款课程 5 折秒杀券',  5000,     0, 0, '2026-09-01 10:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 券模板兜底：历史环境可能清理过券模板，而 user_coupon 仍引用其 id，
-- 会造成"券中心列表与已领券数据不一致"。此处按 id 幂等补齐（不改写已有券的名称/类型）。
INSERT INTO `coupon` (`id`, `name`, `type`, `discount_amount`, `threshold_amount`, `total_num`, `issued_num`, `status`, `valid_begin_time`, `valid_end_time`, `create_time`, `update_time`, `deleted`) VALUES
(6001, '新人立减券',            1, 1000,  5000, 1000, 0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0),
(6002, '满 300 减 60 通用券',    1, 6000, 30000,  500, 0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0),
(6003, '全场 9 折无门槛券',      1,  800,     0, 1000, 0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0),
(6004, '爆款课程 5 折秒杀券',    2, 5000,     0,  100, 0, 1, '2026-09-01 10:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `deleted` = 0, `status` = 1;

-- 同步券模板已发放数量（保持与 user_coupon 一致，避免券中心余量数据失真）
UPDATE `coupon` SET `issued_num` = (SELECT COUNT(*) FROM `user_coupon` WHERE `user_coupon`.`coupon_id` = `coupon`.`id`) WHERE `id` IN (6001, 6002, 6003, 6004);

-- ---------------------------------------------------------------------
-- 数据修复：历史压测/联调遗留的孤儿订单（引用了已不存在的 user_id=1），
-- 重新挂到演示学员 2001 名下，避免管理端订单列表出现"未知用户"。
-- ---------------------------------------------------------------------
UPDATE `zx_trade`.`trade_order` o
LEFT JOIN `zx_user`.`user` u ON u.id = o.user_id
SET o.user_id = 2001
WHERE o.deleted = 0 AND u.id IS NULL;


-- ---------------------------------------------------------------------
-- 基线复位：E2E 验证脚本会临时流转订单状态与审核退款单，
-- 此处把演示种子数据恢复到初始状态，保证反复跑验证后数据不漂移。
-- ---------------------------------------------------------------------
UPDATE `zx_trade`.`trade_order` SET `status` = CASE `id`
    WHEN 8601 THEN 1
    WHEN 8602 THEN 1
    WHEN 8603 THEN 1
    WHEN 8604 THEN 0
    WHEN 8605 THEN 2
    WHEN 8606 THEN 3
    WHEN 8611 THEN 1
    WHEN 8612 THEN 1
    WHEN 8613 THEN 4
    WHEN 8614 THEN 1
    WHEN 8621 THEN 1
    WHEN 8622 THEN 0
    WHEN 8631 THEN 1
    WHEN 8632 THEN 3
    WHEN 8641 THEN 1
    WHEN 8642 THEN 1
    WHEN 8651 THEN 2
    WHEN 8661 THEN 1
    WHEN 8662 THEN 0
    ELSE `status` END
WHERE `id` IN (8601, 8602, 8603, 8604, 8605, 8606, 8611, 8612, 8613, 8614, 8621, 8622, 8631, 8632, 8641, 8642, 8651, 8661, 8662);

UPDATE `zx_trade`.`refund_apply` SET `status` = CASE `id`
    WHEN 8951 THEN 0
    WHEN 8952 THEN 1
    WHEN 8953 THEN 0
    ELSE `status` END
WHERE `id` IN (8951, 8952, 8953);

-- =====================================================================
-- 九、积分与讨论演示数据（zx_learning）：排行榜 / 积分明细 / 讨论区
--     points_record 采用「批量汇总」写法，加总即该学员总积分，便于榜单展示；
--     board / board_reply 提供课程讨论区的初始话题与回复。
--     id 段固定为 7001+（与业务自增段隔离），配合下方 DELETE 构成幂等基线复位。
-- 注意：本段是「测试基线复位」——会先清除 id 不在种子段内的存量积分/讨论数据，
--       保证每次执行后榜单与明细都是可复现的固定结果（生产环境请勿执行本文件）。
-- =====================================================================
USE `zx_learning`;

DELETE FROM `points_record` WHERE `ref_id` NOT LIKE '%:batch:%' OR `ref_id` IS NULL;
DELETE FROM `board_reply` WHERE `id` NOT BETWEEN 7001 AND 7006;
DELETE FROM `board` WHERE `id` NOT BETWEEN 7001 AND 7005;

INSERT IGNORE INTO `points_record`
  (`id`, `user_id`, `points`, `source`, `description`, `ref_id`, `create_time`, `update_time`, `creater`, `updater`, `deleted`) VALUES
(7001, 2101, 200, 'COURSE', '完成课程《Java 21 核心技术》等 4 门课程', 'course:batch:2101:1', DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), 2101, 2101, 0),
(7002, 2101, 100, 'LESSON', '完成 10 个课时学习', 'lesson:batch:2101:1', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 2101, 2101, 0),
(7003, 2101, 25, 'QUIZ', '测验答对 5 题', 'quiz:batch:2101:1', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 2101, 2101, 0),
(7004, 2102, 150, 'COURSE', '完成课程《TypeScript 从入门到实战》等 3 门课程', 'course:batch:2102:1', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 2102, 2102, 0),
(7005, 2102, 90, 'LESSON', '完成 9 个课时学习', 'lesson:batch:2102:1', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 2102, 2102, 0),
(7006, 2102, 30, 'QUIZ', '测验答对 6 题', 'quiz:batch:2102:1', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 2102, 2102, 0),
(7007, 2102, 15, 'SIGN', '连续签到奖励', 'sign:batch:2102:1', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 2102, 2102, 0),
(7008, 2001, 150, 'COURSE', '完成课程《Java 21 核心技术》等 3 门课程', 'course:batch:2001:1', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 2001, 2001, 0),
(7009, 2001, 70, 'LESSON', '完成 7 个课时学习', 'lesson:batch:2001:1', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 2001, 2001, 0),
(7010, 2001, 20, 'QUIZ', '测验答对 4 题', 'quiz:batch:2001:1', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 2001, 2001, 0),
(7011, 2001, 10, 'DISCUSSION', '发布 2 个讨论话题', 'board:batch:2001:1', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 2001, 2001, 0),
(7012, 2103, 100, 'COURSE', '完成课程《大厂面试冲刺》等 2 门课程', 'course:batch:2103:1', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 2103, 2103, 0),
(7013, 2103, 80, 'LESSON', '完成 8 个课时学习', 'lesson:batch:2103:1', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 2103, 2103, 0),
(7014, 2103, 25, 'QUIZ', '测验答对 5 题', 'quiz:batch:2103:1', DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), 2103, 2103, 0),
(7015, 2103, 10, 'SIGN', '连续签到奖励', 'sign:batch:2103:1', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 2103, 2103, 0),
(7016, 2104, 100, 'COURSE', '完成课程《大厂面试冲刺》等 2 门课程', 'course:batch:2104:1', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 2104, 2104, 0),
(7017, 2104, 60, 'LESSON', '完成 6 个课时学习', 'lesson:batch:2104:1', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 2104, 2104, 0),
(7018, 2104, 20, 'QUIZ', '测验答对 4 题', 'quiz:batch:2104:1', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 2104, 2104, 0),
(7019, 2105, 50, 'COURSE', '完成课程《Java 21 核心技术》', 'course:batch:2105:1', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 2105, 2105, 0),
(7020, 2105, 70, 'LESSON', '完成 7 个课时学习', 'lesson:batch:2105:1', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 2105, 2105, 0),
(7021, 2105, 20, 'QUIZ', '测验答对 4 题', 'quiz:batch:2105:1', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 2105, 2105, 0),
(7022, 2106, 50, 'COURSE', '完成课程《TypeScript 从入门到实战》', 'course:batch:2106:1', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 2106, 2106, 0),
(7023, 2106, 30, 'LESSON', '完成 3 个课时学习', 'lesson:batch:2106:1', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 2106, 2106, 0),
(7024, 2106, 15, 'QUIZ', '测验答对 3 题', 'quiz:batch:2106:1', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 2106, 2106, 0),
(7025, 2107, 40, 'LESSON', '完成 4 个课时学习', 'lesson:batch:2107:1', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 2107, 2107, 0),
(7026, 2107, 10, 'QUIZ', '测验答对 2 题', 'quiz:batch:2107:1', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 2107, 2107, 0),
(7027, 2107, 10, 'SIGN', '连续签到奖励', 'sign:batch:2107:1', DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), 2107, 2107, 0);

INSERT IGNORE INTO `board`
  (`id`, `course_id`, `user_id`, `user_name`, `title`, `content`, `reply_count`, `top`, `create_time`, `update_time`, `creater`, `updater`, `deleted`) VALUES
(7001, 1, 2001, '知行学员', 'Java 21 虚拟线程和平台线程怎么选？', '最近在做高并发场景优化，想请教大家：什么情况下该用虚拟线程，什么情况下还是平台线程更合适？', 0, 1, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 2001, 2001, 0),
(7002, 1, 2101, '陈志远', '记录一下我的 Java 21 学习路线', '从基础语法到并发容器，再到虚拟线程实战，分享一下我的学习顺序，欢迎交流。', 0, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 2101, 2101, 0),
(7003, 2, 2102, '林小雨', 'TypeScript 类型收窄总觉得写不对', '联合类型在 if 分支里收窄失败，是不是需要用类型谓词？有没有好的实践？', 0, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 2102, 2102, 0),
(7004, 3, 2103, '赵梓涵', '算法面试：双指针和滑动窗口的适用边界', '总结了一下两类题型的判别方法，欢迎补充。', 0, 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 2103, 2103, 0),
(7005, 4, 2105, '刘思彤', '关于数据库索引失效的几个坑', '隐式类型转换、函数包裹列、前导模糊匹配……踩过的坑都列在这了。', 0, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 2105, 2105, 0);

INSERT IGNORE INTO `board_reply`
  (`id`, `board_id`, `user_id`, `user_name`, `parent_id`, `content`, `create_time`, `update_time`, `creater`, `updater`, `deleted`) VALUES
(7001, 7001, 2101, '陈志远', 0, 'IO 密集型任务用虚拟线程收益最大，CPU 密集型还是用平台线程池更稳。', DATE_SUB(NOW(), INTERVAL 30 HOUR), DATE_SUB(NOW(), INTERVAL 30 HOUR), 2101, 2101, 0),
(7002, 7001, 2102, '林小雨', 0, '补充一点：注意 ThreadLocal 在虚拟线程里的语义变化。', DATE_SUB(NOW(), INTERVAL 27 HOUR), DATE_SUB(NOW(), INTERVAL 27 HOUR), 2102, 2102, 0),
(7003, 7002, 2001, '知行学员', 0, '这个路线和官方推荐顺序基本一致，赞一个。', DATE_SUB(NOW(), INTERVAL 24 HOUR), DATE_SUB(NOW(), INTERVAL 24 HOUR), 2001, 2001, 0),
(7004, 7003, 2101, '陈志远', 0, '可以用 `in` 操作符 + 类型谓词函数，比如 `function isA(x): x is A`。', DATE_SUB(NOW(), INTERVAL 21 HOUR), DATE_SUB(NOW(), INTERVAL 21 HOUR), 2101, 2101, 0),
(7005, 7004, 2104, '王浩然', 0, '滑动窗口适合「连续子数组 + 单调条件」，双指针适合「有序数组 + 区间收缩」。', DATE_SUB(NOW(), INTERVAL 18 HOUR), DATE_SUB(NOW(), INTERVAL 18 HOUR), 2104, 2104, 0),
(7006, 7004, 2106, '孙一鸣', 0, '同意，再补一个：滑动窗口的右边界扩张要保证「不进则退」的不变式。', DATE_SUB(NOW(), INTERVAL 15 HOUR), DATE_SUB(NOW(), INTERVAL 15 HOUR), 2106, 2106, 0);

-- 话题回复数按实际回复条数回填
UPDATE `board` b SET b.`reply_count` = (
  SELECT COUNT(*) FROM `board_reply` r WHERE r.`board_id` = b.`id` AND r.`deleted` = 0
) WHERE b.`id` BETWEEN 7001 AND 7005;
-- =====================================================================
-- 八、数据自检（人工核对关联完整性）
-- =====================================================================
SELECT 'users'          AS entity, COUNT(*) AS cnt FROM `zx_user`.`user` WHERE `deleted` = 0
UNION ALL SELECT 'questions',       COUNT(*) FROM `zx_exam`.`question` WHERE `deleted` = 0
UNION ALL SELECT 'answer_records',  COUNT(*) FROM `zx_exam`.`question_result` WHERE `deleted` = 0
UNION ALL SELECT 'learning_records',COUNT(*) FROM `zx_learning`.`learning_record` WHERE `deleted` = 0
UNION ALL SELECT 'carts',           COUNT(*) FROM `zx_trade`.`cart` WHERE `deleted` = 0
UNION ALL SELECT 'orders',          COUNT(*) FROM `zx_trade`.`trade_order` WHERE `deleted` = 0
UNION ALL SELECT 'refund_applies',  COUNT(*) FROM `zx_trade`.`refund_apply` WHERE `deleted` = 0
UNION ALL SELECT 'user_coupons',    COUNT(*) FROM `zx_promotion`.`user_coupon`
UNION ALL SELECT 'points_records',  COUNT(*) FROM `zx_learning`.`points_record` WHERE `deleted` = 0
UNION ALL SELECT 'boards',          COUNT(*) FROM `zx_learning`.`board` WHERE `deleted` = 0;
