-- ============================================================
-- 知行智学（zx-learn）数据库初始化脚本
-- 按服务分库：zx_auth / zx_user / zx_course / zx_learning /
-- zx_exam / zx_insight / zx_trade / zx_promotion
-- 公共字段约定：id / create_time / update_time / creater / updater / deleted（逻辑删除）
-- ============================================================

-- ===================== 认证服务库 zx_auth =====================
CREATE DATABASE IF NOT EXISTS `zx_auth` DEFAULT CHARACTER SET utf8mb4;
USE `zx_auth`;

CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(64) COMMENT '角色编码',
    `remark` VARCHAR(255) COMMENT '备注',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `creater` BIGINT COMMENT '创建人',
    `updater` BIGINT COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `menu` (
    `id` BIGINT NOT NULL,
    `parent_id` BIGINT DEFAULT 0 COMMENT '父菜单id',
    `name` VARCHAR(64) NOT NULL COMMENT '菜单名称',
    `path` VARCHAR(255) COMMENT '路由地址',
    `component` VARCHAR(255) COMMENT '组件路径',
    `icon` VARCHAR(255) COMMENT '图标',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `type` INT DEFAULT 1 COMMENT '类型',
    `status` INT DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

CREATE TABLE IF NOT EXISTS `privilege` (
    `id` BIGINT NOT NULL,
    `menu_id` BIGINT COMMENT '菜单id',
    `method` VARCHAR(16) COMMENT '请求方法',
    `uri` VARCHAR(255) COMMENT '请求路径',
    `name` VARCHAR(64) COMMENT '权限名称',
    `description` VARCHAR(255) COMMENT '描述',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `account_role` (
    `id` BIGINT NOT NULL,
    `account_id` BIGINT NOT NULL COMMENT '账号id',
    `role_id` BIGINT NOT NULL COMMENT '角色id',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号角色关联表';

CREATE TABLE IF NOT EXISTS `role_menu` (
    `id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS `role_privilege` (
    `id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `privilege_id` BIGINT NOT NULL,
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS `login_record` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT COMMENT '用户id',
    `cell_phone` VARCHAR(20) COMMENT '手机号',
    `ipv4` VARCHAR(64) COMMENT '登录IP',
    `login_type` INT COMMENT '登录类型',
    `login_time` DATETIME COMMENT '登录时间',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录记录表';

-- ===================== 用户服务库 zx_user =====================
CREATE DATABASE IF NOT EXISTS `zx_user` DEFAULT CHARACTER SET utf8mb4;
USE `zx_user`;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL,
    `cell_phone` VARCHAR(20) COMMENT '手机号',
    `username` VARCHAR(64) COMMENT '用户名',
    `password` VARCHAR(128) COMMENT '密码(BCrypt)',
    `name` VARCHAR(64) COMMENT '姓名',
    `type` INT DEFAULT 2 COMMENT '类型:1员工/2学员/3教师',
    `status` INT DEFAULT 1 COMMENT '状态:0禁用/1正常',
    `icon` VARCHAR(255) COMMENT '头像',
    `email` VARCHAR(128) COMMENT '邮箱',
    `city` VARCHAR(64) COMMENT '城市',
    `gender` INT COMMENT '性别',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_cell_phone` (`cell_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `user_detail` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT COMMENT '用户id',
    `job_title` VARCHAR(64) COMMENT '教师职称',
    `intro` VARCHAR(1000) COMMENT '教师简介',
    `birthday` DATE COMMENT '学员生日',
    `education` VARCHAR(32) COMMENT '学历',
    `occupation` VARCHAR(64) COMMENT '职业',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户详情表';

-- 首个管理员不再在脚本中硬编码凭据：由 zx-auth 启动时的安全引导生成
-- （检测到无管理员时使用系统预设密码（默认 123456，可用 ADMIN_INIT_PASSWORD 覆盖），
--   BCrypt 加密入库，凭据写入 .bootstrap-credentials，首次改密后自动删除）

-- ===================== 默认账号种子数据（密码均为纯数字 123456） =====================
-- BCrypt hash of '123456': $2a$10$OuwRvnFKhKxYDdlndTfjXOzhWRnUF6jTJ6xZEnFlQHkAwcud6rELG
-- 默认学员：13900000001 / 123456
INSERT IGNORE INTO `user` (`id`, `cell_phone`, `username`, `password`, `name`, `type`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(2001, '13900000001', 'student001', '$2a$10$OuwRvnFKhKxYDdlndTfjXOzhWRnUF6jTJ6xZEnFlQHkAwcud6rELG', '知行学员', 2, 1, NOW(), NOW(), 0);
-- 默认教师：13900000002 / 123456
INSERT IGNORE INTO `user` (`id`, `cell_phone`, `username`, `password`, `name`, `type`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(2002, '13900000002', 'teacher001', '$2a$10$OuwRvnFKhKxYDdlndTfjXOzhWRnUF6jTJ6xZEnFlQHkAwcud6rELG', '知行教师', 3, 1, NOW(), NOW(), 0);

-- 教师详情（职称/简介，供教师主页展示）
INSERT IGNORE INTO `user_detail` (`id`, `user_id`, `job_title`, `intro`, `create_time`, `update_time`, `deleted`) VALUES
(2102, 2002, '高级讲师', '十年一线研发与教学经验，主讲 Java 后端与微服务架构课程。', NOW(), NOW(), 0);

-- ===================== 课程服务库 zx_course =====================
CREATE DATABASE IF NOT EXISTS `zx_course` DEFAULT CHARACTER SET utf8mb4;
USE `zx_course`;

CREATE TABLE IF NOT EXISTS `course` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(128) NOT NULL COMMENT '课程名称',
    `cover_url` VARCHAR(255) COMMENT '封面',
    `price` BIGINT DEFAULT 0 COMMENT '价格(分)',
    `category_id_lv1` BIGINT COMMENT '一级分类',
    `category_id_lv2` BIGINT COMMENT '二级分类',
    `category_id_lv3` BIGINT COMMENT '三级分类',
    `teacher_id` BIGINT COMMENT '老师id',
    `status` INT DEFAULT 0 COMMENT '状态:1上架/0下架',
    `free` INT DEFAULT 0 COMMENT '是否免费:0收费/1免费',
    `publish_times` INT DEFAULT 0 COMMENT '发布次数',
    `description` VARCHAR(2000) COMMENT '简介',
    `chapter_count` INT DEFAULT 0 COMMENT '章数',
    `subject_count` INT DEFAULT 0 COMMENT '小节数',
    `sold` INT DEFAULT 0 COMMENT '销量',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

CREATE TABLE IF NOT EXISTS `course_draft` (
    `id` BIGINT NOT NULL,
    `course_id` BIGINT COMMENT '对应正式课程id',
    `name` VARCHAR(128) COMMENT '课程名称',
    `cover_url` VARCHAR(255) COMMENT '封面',
    `price` BIGINT DEFAULT 0 COMMENT '价格(分)',
    `category_id_lv1` BIGINT,
    `category_id_lv2` BIGINT,
    `category_id_lv3` BIGINT,
    `teacher_id` BIGINT,
    `free` INT DEFAULT 0,
    `description` VARCHAR(2000),
    `step` INT DEFAULT 1 COMMENT '编辑步骤',
    `submitted` INT DEFAULT 0 COMMENT '是否已提交上架',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程草稿表';

CREATE TABLE IF NOT EXISTS `course_catalogue` (
    `id` BIGINT NOT NULL,
    `course_id` BIGINT COMMENT '课程id',
    `name` VARCHAR(128) COMMENT '目录名称',
    `media_id` BIGINT COMMENT '媒资id',
    `index` INT COMMENT '顺序',
    `chapter_type` INT COMMENT '类型:1章/2小节',
    `parent_id` BIGINT COMMENT '父章id',
    `duration` INT COMMENT '时长(秒)',
    `trailer` INT DEFAULT 0 COMMENT '是否试看',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程目录表';

CREATE TABLE IF NOT EXISTS `category` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(64) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类id',
    `level` INT DEFAULT 1 COMMENT '层级',
    `status` INT DEFAULT 1 COMMENT '状态:1启用/0停用',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程分类表';

-- 初始分类数据
INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `status`, `sort`) VALUES
(1, '后端开发', 0, 1, 1, 1),
(2, '前端开发', 0, 1, 1, 2),
(3, '人工智能', 0, 1, 1, 3),
(11, 'Java', 1, 2, 1, 1),
(12, 'Python', 1, 2, 1, 2),
(13, 'Go', 1, 2, 1, 3)
ON DUPLICATE KEY UPDATE `name` = `name`;

-- 补充二级分类（前端/人工智能）
INSERT IGNORE INTO `category` (`id`, `name`, `parent_id`, `level`, `status`, `sort`) VALUES
(21, 'Vue.js', 2, 2, 1, 1),
(22, 'React', 2, 2, 1, 2),
(31, '大模型应用', 3, 2, 1, 1);

-- ===================== 初始课程种子数据（封面托管于阿里云 OSS，对象级公共读） =====================
-- 教师统一为默认教师 2002；价格单位为分；封面源文件位于 zx-web/public/covers/（已上传 OSS covers/ 前缀）
INSERT IGNORE INTO `course` (`id`, `name`, `cover_url`, `price`, `category_id_lv1`, `category_id_lv2`, `teacher_id`, `status`, `free`, `publish_times`, `description`, `chapter_count`, `subject_count`, `sold`, `create_time`, `update_time`, `deleted`) VALUES
(3001, 'Java 21 核心技术：从入门到精通', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-01.svg', 19900, 1, 11, 2002, 1, 0, 2, '系统讲解 Java 21 新特性、并发编程与 JVM 调优，配有大纲级实战案例，助你夯实后端核心功底。', 3, 12, 1232, NOW(), NOW(), 0),
(3002, 'Spring Boot 3 企业级实战', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-02.svg', 29900, 1, 11, 2002, 1, 0, 1, '以真实电商项目为载体，覆盖 Spring Boot 3 全家桶、微服务治理与上线部署全流程。', 4, 18, 866, NOW(), NOW(), 0),
(3003, 'Python 数据分析与机器学习入门', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-03.svg', 15900, 1, 12, 2002, 1, 0, 1, '从 NumPy/Pandas 到 scikit-learn 建模，零基础掌握数据分析全链路与常用算法。', 3, 10, 731, NOW(), NOW(), 0),
(3004, 'Go 语言高并发编程实战', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-04.svg', 25900, 1, 13, 2002, 1, 0, 1, 'goroutine/channel 深入剖析，结合网关与限流实战掌握 Go 高并发服务设计。', 3, 9, 388, NOW(), NOW(), 0),
(3005, 'Vue 3 + TypeScript 前端工程化', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-05.svg', 16900, 2, 21, 2002, 1, 0, 1, '组合式 API、Pinia 状态管理与 Vite 工程化实践，构建可维护的中大型前端项目。', 4, 14, 1024, NOW(), NOW(), 0),
(3006, 'React 18 状态管理与性能优化', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-06.svg', 18900, 2, 22, 2002, 1, 0, 1, 'Hooks 心智模型、并发特性与渲染性能调优，写出高性能可测试的 React 应用。', 3, 11, 512, NOW(), NOW(), 0),
(3007, '大模型应用开发：RAG 与 Agent', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-07.svg', 39900, 3, 31, 2002, 1, 0, 1, '从 Prompt 工程到向量检索与 Agent 编排，手把手构建生产级大模型应用。', 4, 16, 1588, NOW(), NOW(), 0),
(3008, 'Java 入门第一课（免费）', 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-08.svg', 0, 1, 11, 2002, 1, 1, 1, '零基础免费入门课：环境搭建、第一个程序与开发工具选择，带你轻松开启 Java 之旅。', 1, 2, 5210, NOW(), NOW(), 0);

-- 课程目录种子（Java 核心技术 3001：2 章 4 节）
INSERT IGNORE INTO `course_catalogue` (`id`, `course_id`, `name`, `media_id`, `index`, `chapter_type`, `parent_id`, `duration`, `trailer`, `create_time`, `update_time`, `deleted`) VALUES
(4101, 3001, '第一章 Java 生态与开发环境', NULL, 1, 1, 0, NULL, 0, NOW(), NOW(), 0),
(4102, 3001, '1.1 JDK 21 安装与配置', NULL, 1, 2, 4101, 600, 1, NOW(), NOW(), 0),
(4103, 3001, '1.2 第一个 Java 程序', NULL, 2, 2, 4101, 720, 0, NOW(), NOW(), 0),
(4104, 3001, '第二章 面向对象基础', NULL, 2, 1, 0, NULL, 0, NOW(), NOW(), 0),
(4105, 3001, '2.1 类与对象', NULL, 1, 2, 4104, 900, 0, NOW(), NOW(), 0),
(4106, 3001, '2.2 封装、继承与多态', NULL, 2, 2, 4104, 1100, 0, NOW(), NOW(), 0);
-- 课程目录种子（免费课 3008：1 章 2 节）
INSERT IGNORE INTO `course_catalogue` (`id`, `course_id`, `name`, `media_id`, `index`, `chapter_type`, `parent_id`, `duration`, `trailer`, `create_time`, `update_time`, `deleted`) VALUES
(4201, 3008, '第一章 走进 Java', NULL, 1, 1, 0, NULL, 0, NOW(), NOW(), 0),
(4202, 3008, '1.1 课程导学', NULL, 1, 2, 4201, 300, 1, NOW(), NOW(), 0),
(4203, 3008, '1.2 开发工具选择', NULL, 2, 2, 4201, 480, 0, NOW(), NOW(), 0);

-- ===================== 学习服务库 zx_learning =====================
CREATE DATABASE IF NOT EXISTS `zx_learning` DEFAULT CHARACTER SET utf8mb4;
USE `zx_learning`;

CREATE TABLE IF NOT EXISTS `learning_record` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `course_id` BIGINT NOT NULL COMMENT '课程id',
    `lesson_id` BIGINT NOT NULL COMMENT '课时id',
    `progress` INT DEFAULT 0 COMMENT '学习进度0-100',
    `finished` TINYINT DEFAULT 0 COMMENT '是否学完',
    `learn_duration` INT DEFAULT 0 COMMENT '累计学习时长(秒)',
    `last_learn_time` DATETIME COMMENT '最近学习时间',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_course` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习进度记录表';

CREATE TABLE IF NOT EXISTS `note` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `course_id` BIGINT COMMENT '课程id',
    `lesson_id` BIGINT COMMENT '课时id',
    `content` VARCHAR(2000) COMMENT '笔记内容',
    `privacy` TINYINT DEFAULT 0 COMMENT '是否私密:0公开/1私密',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习笔记表';

CREATE TABLE IF NOT EXISTS `sign_in` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `sign_date` DATE NOT NULL COMMENT '签到日期',
    `streak` INT DEFAULT 1 COMMENT '连续签到天数',
    `points` INT DEFAULT 0 COMMENT '获得积分',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录表';

-- 我的课表（用户看课清单，course_id 与课程服务保持一致）
CREATE TABLE IF NOT EXISTS `lesson` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `course_id` BIGINT NOT NULL COMMENT '课程id',
    `course_name` VARCHAR(128) COMMENT '课程名称快照',
    `plan` TEXT COMMENT '学习计划JSON',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_course` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='我的课表';

-- ===================== 考试服务库 zx_exam =====================
CREATE DATABASE IF NOT EXISTS `zx_exam` DEFAULT CHARACTER SET utf8mb4;
USE `zx_exam`;

CREATE TABLE IF NOT EXISTS `question_result` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `question_id` BIGINT NOT NULL COMMENT '题目id',
    `question_name` VARCHAR(255) COMMENT '题目名称快照',
    `correct` TINYINT DEFAULT 0 COMMENT '是否答对',
    `score` INT DEFAULT 0 COMMENT '得分',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录表';

-- ===================== 智能学情分析服务库 zx_insight =====================
CREATE DATABASE IF NOT EXISTS `zx_insight` DEFAULT CHARACTER SET utf8mb4;
USE `zx_insight`;

CREATE TABLE IF NOT EXISTS `insight_report` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `report_date` DATE NOT NULL COMMENT '报告日期',
    `engagement` INT DEFAULT 0 COMMENT '学习投入度',
    `completion` INT DEFAULT 0 COMMENT '学习完成度',
    `quiz_ability` INT DEFAULT 0 COMMENT '答题能力',
    `breadth` INT DEFAULT 0 COMMENT '知识广度',
    `comprehension` INT DEFAULT 0 COMMENT '综合理解力',
    `weakness` VARCHAR(1024) COMMENT '薄弱点JSON',
    `recommendations` VARCHAR(2048) COMMENT '学习建议JSON',
    `summary` VARCHAR(2048) COMMENT '分析总结',
    `ai_generated` TINYINT DEFAULT 0 COMMENT '是否AI生成',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学情报告表';

-- ===================== 交易服务库 zx_trade =====================
CREATE DATABASE IF NOT EXISTS `zx_trade` DEFAULT CHARACTER SET utf8mb4;
USE `zx_trade`;

CREATE TABLE IF NOT EXISTS `cart` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `course_id` BIGINT NOT NULL COMMENT '课程id',
    `course_name` VARCHAR(128) COMMENT '课程名称快照',
    `course_price` BIGINT DEFAULT 0 COMMENT '课程价格(分)快照',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_course` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

CREATE TABLE IF NOT EXISTS `trade_order` (
    `id` BIGINT NOT NULL,
    `order_no` VARCHAR(40) NOT NULL COMMENT '订单号(雪花算法)',
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `course_id` BIGINT NOT NULL COMMENT '课程id',
    `course_name` VARCHAR(128) COMMENT '课程名称快照',
    `course_price` BIGINT DEFAULT 0 COMMENT '课程价格(分)',
    `total_fee` BIGINT DEFAULT 0 COMMENT '实付金额(分)',
    `coupon_id` BIGINT COMMENT '使用的优惠券id',
    `deduction` BIGINT DEFAULT 0 COMMENT '优惠券抵扣金额(分)',
    `status` INT DEFAULT 0 COMMENT '状态:0待支付/1已支付/2已关闭/3退款中/4已退款',
    `pay_type` INT COMMENT '支付方式',
    `pay_time` DATETIME COMMENT '支付完成时间',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE IF NOT EXISTS `trade_order_detail` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL COMMENT '订单id',
    `course_id` BIGINT NOT NULL COMMENT '课程id',
    `name` VARCHAR(128) COMMENT '课程名称快照',
    `price` BIGINT DEFAULT 0 COMMENT '价格(分)',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 本地消息表（Outbox）：与订单同事务落库，定时任务补偿投递，保证发消息不丢失
CREATE TABLE IF NOT EXISTS `order_msg` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL COMMENT '订单id',
    `biz_key` VARCHAR(80) NOT NULL COMMENT '业务键(订单id:事件类型)',
    `topic` VARCHAR(64) NOT NULL COMMENT 'MQ主题',
    `tag` VARCHAR(64) COMMENT '消息tag',
    `payload` TEXT COMMENT '消息体JSON',
    `status` TINYINT DEFAULT 0 COMMENT '0待投递/1已投递/2已消费/3死信',
    `retry_count` INT DEFAULT 0 COMMENT '已投递次数',
    `max_retry` INT DEFAULT 5 COMMENT '最大重试次数',
    `next_retry_time` DATETIME COMMENT '下次投递时间',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_key` (`biz_key`),
    KEY `idx_status_time` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表(Outbox)';

-- 交易支付回调流水表（支付幂等：pay_no 唯一）
CREATE TABLE IF NOT EXISTS `trade_pay_record` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL COMMENT '订单id',
    `pay_no` VARCHAR(64) NOT NULL COMMENT '渠道交易流水号',
    `pay_type` INT COMMENT '支付方式',
    `amount` BIGINT DEFAULT 0 COMMENT '支付金额(分)',
    `status` INT DEFAULT 0 COMMENT '0处理中/1成功',
    `callback_time` DATETIME COMMENT '回调时间',
    `raw` TEXT COMMENT '回调原始报文',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_no` (`pay_no`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易支付回调流水表';

-- MQ 消费流水表（幂等第二层：消费端以 consume_key 唯一去重）
CREATE TABLE IF NOT EXISTS `consume_record` (
    `id` BIGINT NOT NULL,
    `consume_key` VARCHAR(80) NOT NULL COMMENT '消费幂等键(消息key)',
    `topic` VARCHAR(64) COMMENT '主题',
    `tag` VARCHAR(64) COMMENT 'tag',
    `status` TINYINT DEFAULT 1 COMMENT '1已消费',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consume_key` (`consume_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消费流水表';

-- 优惠券核销流水（异步落库，order_id 唯一保证一单只核销一次）
CREATE TABLE IF NOT EXISTS `coupon_use_record` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `coupon_id` BIGINT NOT NULL COMMENT '优惠券id',
    `user_coupon_id` BIGINT COMMENT '用户券id',
    `order_id` BIGINT NOT NULL COMMENT '使用订单id',
    `amount` BIGINT DEFAULT 0 COMMENT '抵扣金额(分)',
    `status` TINYINT DEFAULT 1 COMMENT '1已核销/0已退回',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order` (`order_id`),
    KEY `idx_user_coupon` (`user_coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券核销流水表';

-- 退款申请表（退款状态机联动订单 3退款中/4已退款）
CREATE TABLE IF NOT EXISTS `refund_apply` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL COMMENT '订单id',
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `course_id` BIGINT COMMENT '课程id',
    `amount` BIGINT DEFAULT 0 COMMENT '退款金额(分)',
    `reason` VARCHAR(500) COMMENT '退款原因',
    `status` TINYINT DEFAULT 0 COMMENT '状态:0待审核/1已通过/2已拒绝',
    `remark` VARCHAR(500) COMMENT '审核说明',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order` (`order_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请表';

-- ===================== 营销服务库 zx_promotion =====================
CREATE DATABASE IF NOT EXISTS `zx_promotion` DEFAULT CHARACTER SET utf8mb4;
USE `zx_promotion`;

CREATE TABLE IF NOT EXISTS `coupon` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(64) NOT NULL COMMENT '优惠券名称',
    `type` INT DEFAULT 1 COMMENT '类型:1满减',
    `discount_amount` BIGINT DEFAULT 0 COMMENT '面值(分)',
    `threshold_amount` BIGINT DEFAULT 0 COMMENT '使用门槛(分)',
    `total_num` INT DEFAULT 0 COMMENT '发行总量',
    `issued_num` INT DEFAULT 0 COMMENT '已发放数量',
    `status` INT DEFAULT 0 COMMENT '状态:0未开始/1进行中/2已结束/3已下架',
    `exchange_code` VARCHAR(64) COMMENT '兑换码(一次性核销)',
    `valid_begin_time` DATETIME COMMENT '生效时间',
    `valid_end_time` DATETIME COMMENT '失效时间',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exchange_code` (`exchange_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

-- 初始优惠券种子（进行中的满减券：满 50 减 10，供领券/下单链路测试）
INSERT IGNORE INTO `coupon` (`id`, `name`, `type`, `discount_amount`, `threshold_amount`, `total_num`, `issued_num`, `status`, `valid_begin_time`, `valid_end_time`, `create_time`, `update_time`, `deleted`) VALUES
(6001, '新人立减券', 1, 1000, 5000, 1000, 0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0);

CREATE TABLE IF NOT EXISTS `user_coupon` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `coupon_id` BIGINT NOT NULL COMMENT '优惠券id',
    `coupon_name` VARCHAR(64) COMMENT '优惠券名称快照',
    `discount_amount` BIGINT DEFAULT 0 COMMENT '面值(分)快照',
    `threshold_amount` BIGINT DEFAULT 0 COMMENT '门槛(分)快照',
    `status` INT DEFAULT 0 COMMENT '状态:0未使用/1已使用/2已过期',
    `valid_begin_time` DATETIME COMMENT '生效时间',
    `valid_end_time` DATETIME COMMENT '失效时间',
    `use_time` DATETIME COMMENT '使用时间',
    `order_id` BIGINT COMMENT '使用订单id',
    `coupon_code` VARCHAR(64) COMMENT '券码(秒杀领取异步生成的唯一核销码)',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`),
    UNIQUE KEY `uk_coupon_code` (`coupon_code`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- 演示用户优惠券种子（供领券/下单/结算链路复用，user_id=1 为演示学员）
INSERT IGNORE INTO `user_coupon` (`id`, `user_id`, `coupon_id`, `coupon_name`, `discount_amount`, `threshold_amount`, `status`, `valid_begin_time`, `valid_end_time`, `create_time`, `update_time`) VALUES
(7001, 1, 6001, '新人立减券', 1000, 5000, 0, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- MQ 消费流水表（秒杀异步落库幂等第二层：consume_key 唯一去重）
CREATE TABLE IF NOT EXISTS `consume_record` (
    `id` BIGINT NOT NULL,
    `consume_key` VARCHAR(80) NOT NULL COMMENT '消费幂等键(消息key)',
    `topic` VARCHAR(64) COMMENT '主题',
    `tag` VARCHAR(64) COMMENT 'tag',
    `status` TINYINT DEFAULT 1 COMMENT '1已消费',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consume_key` (`consume_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消费流水表';
-- =====================================================================
-- 交易最终一致性（本地消息表 + RocketMQ）增量 DDL
-- 模块：zx-course / zx-learning（消费端幂等表）
-- =====================================================================

-- ===================== 课程服务库 zx_course =====================
USE `zx_course`;

-- 课程名额表（与课程 1:1；quota 为 NULL 表示不限名额）
CREATE TABLE IF NOT EXISTS `course_quota` (
    `id` BIGINT NOT NULL,
    `course_id` BIGINT NOT NULL COMMENT '课程id',
    `quota` INT NULL COMMENT '名额上限(NULL不限)',
    `locked_count` INT DEFAULT 0 COMMENT '已锁定未确认名额数',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程名额表';

-- 名额变更流水（订单维度生命周期 LOCK->CONFIRM/RELEASE，order_id 唯一幂等）
CREATE TABLE IF NOT EXISTS `course_quota_record` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL COMMENT '订单id(唯一)',
    `course_id` BIGINT NOT NULL COMMENT '课程id',
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `status` TINYINT DEFAULT 1 COMMENT '1已锁定/2已确认(转销量)/0已释放',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order` (`order_id`),
    KEY `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程名额变更流水表';

-- MQ 消费流水表（幂等第二层：consume_key 唯一去重）
CREATE TABLE IF NOT EXISTS `consume_record` (
    `id` BIGINT NOT NULL,
    `consume_key` VARCHAR(80) NOT NULL COMMENT '消费幂等键(消息key)',
    `topic` VARCHAR(64) COMMENT '主题',
    `tag` VARCHAR(64) COMMENT 'tag',
    `status` TINYINT DEFAULT 1 COMMENT '1已消费',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consume_key` (`consume_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消费流水表';

-- ===================== 学习服务库 zx_learning =====================
USE `zx_learning`;

-- MQ 消费流水表（幂等第二层：consume_key 唯一去重）
CREATE TABLE IF NOT EXISTS `consume_record` (
    `id` BIGINT NOT NULL,
    `consume_key` VARCHAR(80) NOT NULL COMMENT '消费幂等键(消息key)',
    `topic` VARCHAR(64) COMMENT '主题',
    `tag` VARCHAR(64) COMMENT 'tag',
    `status` TINYINT DEFAULT 1 COMMENT '1已消费',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consume_key` (`consume_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消费流水表';

-- =====================================================================
-- 追加演示数据：新课程 / 可领优惠券 / 测试账号初始数据（全部幂等，可重复执行）
-- =====================================================================

-- ===================== 新增课程（zx_course） =====================
USE `zx_course`;
-- 新课程封面使用文生图地址，无需上传即可正常展示；分类沿用已有两级分类
INSERT IGNORE INTO `course` (`id`, `name`, `cover_url`, `price`, `category_id_lv1`, `category_id_lv2`, `teacher_id`, `status`, `free`, `publish_times`, `description`, `chapter_count`, `subject_count`, `sold`, `create_time`, `update_time`, `deleted`) VALUES
(3009, 'MySQL 8 性能优化与索引设计', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=online%20course%20cover%20art%2C%20MySQL%20database%20engine%20and%20index%20tree%20icon%2C%20performance%20optimization%20concept%2C%20blue%20and%20teal%20tech%20flat%20illustration%20style&image_size=landscape_4_3', 22900, 1, 11, 2002, 1, 0, 1, '深入索引底层原理、Explain 执行计划与慢查询优化，覆盖缓存与分库分表，助你打造高性能数据库。', 3, 10, 412, NOW(), NOW(), 0),
(3010, 'Vue 3 进阶：Pinia 与组合式 API', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=online%20course%20cover%20art%2C%20Vue.js%20logo%20beside%20modern%20SPA%20UI%20components%2C%20frontend%20engineering%2C%20green%20and%20white%20tech%20flat%20illustration%20style&image_size=landscape_4_3', 17900, 2, 21, 2002, 1, 0, 1, '从组合式 API 到 Pinia 状态管理，再到组件设计模式与性能优化，构建可维护的大型 Vue 3 应用。', 3, 11, 298, NOW(), NOW(), 0),
(3011, 'Go 微服务与 gRPC 高并发实战', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=online%20course%20cover%20art%2C%20Go%20gopher%20mascot%20with%20microservices%20nodes%20and%20gRPC%20diagram%2C%20high%20concurrency%20backend%2C%20gold%20and%20dark%20navy%20illustration%20style&image_size=landscape_4_3', 26900, 1, 13, 2002, 1, 0, 1, 'goroutine 调度、channel 并发模型与 gRPC 微服务实战，掌握 Go 高并发分布式服务设计。', 4, 13, 356, NOW(), NOW(), 0),
(3012, '大模型 RAG 应用高级实战', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=online%20course%20cover%20art%2C%20AI%20large%20language%20model%20with%20knowledge%20base%20and%20RAG%20retrieval%20vector%2C%20futuristic%20purple%20gradient%20tech%20illustration&image_size=landscape_4_3', 42900, 3, 31, 2002, 1, 0, 1, '从向量检索、重排到多路召回与知识库编排，深入构建生产级检索增强生成应用。', 4, 15, 520, NOW(), NOW(), 0);
-- 新课程目录种子（Vue 进阶 3010：2 章 4 节，供课程详情页展示）
INSERT IGNORE INTO `course_catalogue` (`id`, `course_id`, `name`, `media_id`, `index`, `chapter_type`, `parent_id`, `duration`, `trailer`, `create_time`, `update_time`, `deleted`) VALUES
(4301, 3010, '第一章 组合式 API 风格', NULL, 1, 1, 0, NULL, 0, NOW(), NOW(), 0),
(4302, 3010, '1.1 Setup 语法与响应式原理', NULL, 1, 2, 4301, 720, 1, NOW(), NOW(), 0),
(4303, 3010, '1.2 组合式函数封装', NULL, 2, 2, 4301, 820, 0, NOW(), NOW(), 0),
(4304, 3010, '第二章 Pinia 状态管理', NULL, 2, 1, 0, NULL, 0, NOW(), NOW(), 0),
(4305, 3010, '2.1 Store 定义与 devtools', NULL, 1, 2, 4304, 680, 0, NOW(), NOW(), 0),
(4306, 3010, '2.2 模块化与持久化', NULL, 2, 2, 4304, 760, 0, NOW(), NOW(), 0);

-- ===================== 优惠券种子（zx_promotion，可领/可用） =====================
USE `zx_promotion`;
INSERT IGNORE INTO `coupon` (`id`, `name`, `type`, `discount_amount`, `threshold_amount`, `total_num`, `issued_num`, `status`, `valid_begin_time`, `valid_end_time`, `create_time`, `update_time`, `deleted`) VALUES
(6002, '满 300 减 60 通用券', 1, 6000, 30000, 500, 0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0),
(6003, '全场 9 折无门槛券', 1, 800, 0, 1000, 0, 1, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0),
(6004, '爆款课程 5 折秒杀券', 2, 5000, 0, 100, 0, 1, '2026-09-01 10:00:00', '2026-12-31 23:59:59', NOW(), NOW(), 0);

-- 演示学员 2001 的已有优惠券（修复原 user_id=1 的孤儿数据，对齐默认学员账号）
UPDATE `user_coupon` SET `user_id` = 2001 WHERE `id` = 7001;
INSERT IGNORE INTO `user_coupon` (`id`, `user_id`, `coupon_id`, `coupon_name`, `discount_amount`, `threshold_amount`, `status`, `valid_begin_time`, `valid_end_time`, `create_time`, `update_time`) VALUES
(7002, 2001, 6002, '满 300 减 60 通用券', 6000, 30000, 0, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7003, 2001, 6003, '全场 9 折无门槛券', 800, 0, 0, '2026-09-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- ===================== 测试账号初始数据：学员 2001（13900000001 / 123456） =====================
-- 学情报告：预置当日报告，学情页无需触发大模型即可秒开
USE `zx_insight`;
INSERT IGNORE INTO `insight_report` (`id`, `user_id`, `report_date`, `engagement`, `completion`, `quiz_ability`, `breadth`, `comprehension`, `weakness`, `recommendations`, `summary`, `ai_generated`, `create_time`, `update_time`, `deleted`) VALUES
(8801, 2001, CURDATE(), 82, 76, 65, 72, 74, '["算法与数据库维度偏弱"]', '["保持每日 1 小时学习节奏","优先强化 SQL 与索引优化"]', '综合来看，你的学习投入度高，课程完成度良好，主要薄弱点在数据库索引设计。建议按学习路径优先完成《MySQL 8 性能优化与索引设计》补强基础。', 0, NOW(), NOW(), 0);

-- 学习课表 / 学习记录 / 笔记 / 签到（zx_learning）
USE `zx_learning`;
INSERT IGNORE INTO `lesson` (`id`, `user_id`, `course_id`, `course_name`, `create_time`, `update_time`, `deleted`) VALUES
(8201, 2001, 3001, 'Java 21 核心技术：从入门到精通', NOW(), NOW(), 0),
(8202, 2001, 3002, 'Spring Boot 3 企业级实战', NOW(), NOW(), 0);
INSERT IGNORE INTO `learning_record` (`id`, `user_id`, `course_id`, `lesson_id`, `progress`, `finished`, `learn_duration`, `last_learn_time`, `create_time`, `update_time`, `deleted`) VALUES
(8101, 2001, 3001, 4101, 85, 1, 3600, NOW(), NOW(), NOW(), 0),
(8102, 2001, 3002, 4102, 60, 0, 2400, NOW(), NOW(), NOW(), 0),
(8103, 2001, 3008, 4201, 100, 1, 1200, NOW(), NOW(), NOW(), 0);
INSERT IGNORE INTO `note` (`id`, `user_id`, `course_id`, `lesson_id`, `content`, `privacy`, `create_time`, `update_time`, `deleted`) VALUES
(8401, 2001, 3001, 4102, 'JVM 内存区域划分：堆、栈、方法区，GC 分代收集策略。', 0, NOW(), NOW(), 0);
INSERT IGNORE INTO `sign_in` (`id`, `user_id`, `sign_date`, `streak`, `points`, `create_time`) VALUES
(8301, 2001, CURDATE(), 5, 50, NOW());

-- 答题记录（zx_exam）
USE `zx_exam`;
INSERT IGNORE INTO `question_result` (`id`, `user_id`, `question_id`, `question_name`, `correct`, `score`, `create_time`, `update_time`, `deleted`) VALUES
(8501, 2001, 1, 'Java 基本数据类型', 1, 10, NOW(), NOW(), 0),
(8502, 2001, 2, 'Spring 依赖注入方式', 1, 10, NOW(), NOW(), 0),
(8503, 2001, 3, 'JVM 内存模型', 0, 0, NOW(), NOW(), 0);

-- 订单 / 购物车（zx_trade）
USE `zx_trade`;
INSERT IGNORE INTO `trade_order` (`id`, `order_no`, `user_id`, `course_id`, `course_name`, `course_price`, `total_fee`, `coupon_id`, `deduction`, `status`, `pay_type`, `pay_time`, `create_time`, `update_time`, `deleted`) VALUES
(8601, 'DEMO202606100001', 2001, 3001, 'Java 21 核心技术：从入门到精通', 19900, 19900, NULL, 0, 1, 3, NOW(), NOW(), NOW(), 0),
(8602, 'DEMO202606100002', 2001, 3002, 'Spring Boot 3 企业级实战', 29900, 23900, 6002, 6000, 1, 1, NOW(), NOW(), NOW(), 0);
INSERT IGNORE INTO `trade_order_detail` (`id`, `order_id`, `course_id`, `name`, `price`, `create_time`, `update_time`, `deleted`) VALUES
(8611, 8601, 3001, 'Java 21 核心技术：从入门到精通', 19900, NOW(), NOW(), 0),
(8612, 8602, 3002, 'Spring Boot 3 企业级实战', 29900, NOW(), NOW(), 0);
INSERT IGNORE INTO `cart` (`id`, `user_id`, `course_id`, `course_name`, `course_price`, `create_time`, `update_time`, `deleted`) VALUES
(8701, 2001, 3003, 'Python 数据分析与机器学习入门', 15900, NOW(), NOW(), 0);
