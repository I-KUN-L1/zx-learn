-- =====================================================================
-- 2026-09-13 个人中心 / 积分体系 / 课程讨论 增量迁移（幂等 · 可重复执行）
--
-- 变更范围（全部落在 zx_learning 库）：
--   1) points_record   —— 积分明细表（个人中心：我的积分 / 排行榜 / 积分明细的数据源）
--   2) board           —— 课程讨论话题表（参与讨论可自动加分）
--   3) board_reply     —— 话题回复表
--   4) 排行榜数据初始化（按学员 seed 出足够的积分明细，保证页面开箱有数据）
--
-- 执行顺序：sql/init.sql → 既有迁移脚本 → 本文件 → sql/test-data.sql
-- Windows Git Bash 示例：
--   MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
--   "$MYSQL" -uroot -p123456 --default-character-set=utf8mb4 < sql/2026-09-13-profile-points-discussion.sql
-- =====================================================================

SET NAMES utf8mb4;
SET @HASH123456 := '$2a$10$OuwRvnFKhKxYDdlndTfjXOzhWRnUF6jTJ6xZEnFlQHkAwcud6rELG'; -- 明文 123456

USE `zx_learning`;

-- ---------------------------------------------------------------------
-- 1. 积分明细表
--    ref_id 记录业务幂等键（如 "3001:4101" 表示课程 3001 的小节 4101），
--    与 source 组成唯一索引，保证「同一业务动作只加一次分」防刷。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `points_record` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '用户id',
    `points`      INT          NOT NULL DEFAULT 0 COMMENT '积分变动（正数=获得）',
    `source`      VARCHAR(32)  NOT NULL COMMENT '来源:LESSON小节/COURSE课程/QUIZ测验/SIGN签到/DISCUSSION发帖/REPLY回复',
    `description` VARCHAR(255) COMMENT '积分说明（展示在积分明细）',
    `ref_id`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '业务幂等引用（课程:小节 / 题目id / 帖子id …）',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater`     BIGINT,
    `updater`     BIGINT,
    `deleted`     TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_source_ref` (`user_id`, `source`, `ref_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分明细表';

-- ---------------------------------------------------------------------
-- 2. 课程讨论：话题 + 回复（学习通式课程内容页的「讨论」区）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `board` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `course_id`   BIGINT       NOT NULL COMMENT '课程id',
    `user_id`     BIGINT       NOT NULL COMMENT '发帖用户id',
    `user_name`   VARCHAR(64)  COMMENT '发帖人昵称快照（避免跨服务回表）',
    `title`       VARCHAR(255) NOT NULL COMMENT '话题标题',
    `content`     VARCHAR(2000) COMMENT '话题内容',
    `reply_count` INT          DEFAULT 0 COMMENT '回复数',
    `top`         TINYINT      DEFAULT 0 COMMENT '是否置顶:0否/1是',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater`     BIGINT,
    `updater`     BIGINT,
    `deleted`     TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程讨论话题表';

CREATE TABLE IF NOT EXISTS `board_reply` (
    `id`          BIGINT        NOT NULL COMMENT '主键',
    `board_id`    BIGINT        NOT NULL COMMENT '话题id',
    `user_id`     BIGINT        NOT NULL COMMENT '回复用户id',
    `user_name`   VARCHAR(64)   COMMENT '回复人昵称快照',
    `parent_id`   BIGINT        DEFAULT 0 COMMENT '被回复的楼层id（0=直接回复话题）',
    `content`     VARCHAR(2000) NOT NULL COMMENT '回复内容',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater`     BIGINT,
    `updater`     BIGINT,
    `deleted`     TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_board` (`board_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讨论回复表';

-- ---------------------------------------------------------------------
-- 3. 积分种子数据
--    学员 id：2001 / 2101~2107（与 test-data.sql 一致）
--    ref_id 全局唯一即可，重复执行靠 uk_user_source_ref + 显式主键双保险
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `points_record`
(`id`, `user_id`, `points`, `source`, `description`, `ref_id`, `create_time`, `update_time`, `deleted`) VALUES
-- 2104 王浩然（榜首）
(9101, 2104,  50, 'LESSON',     '完成《Spring Cloud Alibaba 微服务实战》第 1.1 节学习', '3011:4401', NOW(), NOW(), 0),
(9102, 2104,  50, 'LESSON',     '完成《Spring Cloud Alibaba 微服务实战》第 1.2 节学习', '3011:4402', NOW(), NOW(), 0),
(9103, 2104,  60, 'SIGN',       '连续签到 12 天奖励',                                    'SIGN:2104:12', NOW(), NOW(), 0),
(9104, 2104, 250, 'QUIZ',       '测验答对 50 题累计得分',                                'QUIZ:2104:50', NOW(), NOW(), 0),
(9105, 2104, 200, 'COURSE',     '完成课程《MySQL 8 性能优化与索引设计》',                 'COURSE:2104:3009', NOW(), NOW(), 0),
-- 2101 陈志远
(9111, 2101, 100, 'LESSON',     '完成《MySQL 8 性能优化与索引设计》全部小节学习',          '3010:4101', NOW(), NOW(), 0),
(9112, 2101,  60, 'SIGN',       '连续签到 12 天奖励',                                    'SIGN:2101:12', NOW(), NOW(), 0),
(9113, 2101, 200, 'QUIZ',       '测验答对 40 题累计得分',                                'QUIZ:2101:40', NOW(), NOW(), 0),
(9114, 2101, 160, 'DISCUSSION', '发布话题《联合索引最左前缀的实践总结》',                  'DISCUSSION:1051', NOW(), NOW(), 0),
-- 2102 林小雨
(9121, 2102, 100, 'LESSON',     '完成《Java 21 核心技术》第 2 章学习',                    '3001:4103', NOW(), NOW(), 0),
(9122, 2102,  80, 'SIGN',       '连续签到 16 天奖励',                                    'SIGN:2102:16', NOW(), NOW(), 0),
(9123, 2102, 180, 'QUIZ',       '测验答对 36 题累计得分',                                'QUIZ:2102:36', NOW(), NOW(), 0),
(9124, 2102, 100, 'REPLY',      '参与课程讨论回复 20 次',                                 'REPLY:2102:20', NOW(), NOW(), 0),
-- 2106 孙一鸣
(9131, 2106, 150, 'LESSON',     '完成课程《Vue 3 进阶：Pinia 与组合式 API》',             'COURSE:2106:3010', NOW(), NOW(), 0),
(9132, 2106,  55, 'SIGN',       '连续签到 9 天奖励',                                     'SIGN:2106:9', NOW(), NOW(), 0),
(9133, 2106, 150, 'QUIZ',       '测验答对 30 题累计得分',                                'QUIZ:2106:30', NOW(), NOW(), 0),
(9134, 2106,  75, 'REPLY',      '参与课程讨论回复 15 次',                                 'REPLY:2106:15', NOW(), NOW(), 0),
-- 2001 知行学员（演示主账号）
(9141, 2001,  50, 'LESSON',     '完成《Java 21 核心技术》第 1.1 节学习',                  '3001:4101', NOW(), NOW(), 0),
(9142, 2001,  50, 'SIGN',       '连续签到 5 天奖励',                                     'SIGN:2001:5', NOW(), NOW(), 0),
(9143, 2001, 160, 'QUIZ',       '测验答对 32 题累计得分',                                'QUIZ:2001:32', NOW(), NOW(), 0),
(9144, 2001, 120, 'DISCUSSION', '发布话题《虚拟线程与线程池该如何选择？》',                'DISCUSSION:1041', NOW(), NOW(), 0),
-- 2103 赵梓涵
(9151, 2103,  50, 'LESSON',     '完成《Vue 3 进阶》第 1 章学习',                          '3010:4501', NOW(), NOW(), 0),
(9152, 2103,  40, 'SIGN',       '连续签到 4 天奖励',                                     'SIGN:2103:4', NOW(), NOW(), 0),
(9153, 2103, 110, 'QUIZ',       '测验答对 22 题累计得分',                                'QUIZ:2103:22', NOW(), NOW(), 0),
(9154, 2103, 100, 'DISCUSSION', '发布话题《Pinia 持久化插件踩坑记录》',                     'DISCUSSION:1053', NOW(), NOW(), 0),
-- 2105 刘思彤
(9161, 2105,  50, 'LESSON',     '完成《Java 21 核心技术》第 1 章学习',                    '3001:4101', NOW(), NOW(), 0),
(9162, 2105,  20, 'SIGN',       '连续签到 2 天奖励',                                     'SIGN:2105:2', NOW(), NOW(), 0),
(9163, 2105,  70, 'QUIZ',       '测验答对 14 题累计得分',                                'QUIZ:2105:14', NOW(), NOW(), 0),
(9164, 2105, 100, 'DISCUSSION', '发布话题《后端转全栈的学习路线求建议》',                    'DISCUSSION:1055', NOW(), NOW(), 0),
-- 2107 周静怡
(9171, 2107,  50, 'LESSON',     '完成《MySQL 8 性能优化》第 1 章学习',                    '3009:4101', NOW(), NOW(), 0),
(9172, 2107,  30, 'QUIZ',       '测验答对 6 题累计得分',                                 'QUIZ:2107:6', NOW(), NOW(), 0),
(9173, 2107,  70, 'REPLY',      '参与课程讨论回复 14 次',                                 'REPLY:2107:14', NOW(), NOW(), 0);

-- ---------------------------------------------------------------------
-- 4. 讨论种子数据（学习通式课程内容页「讨论」区首屏不空白）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `board`
(`id`, `course_id`, `user_id`, `user_name`, `title`, `content`, `reply_count`, `top`, `create_time`, `update_time`, `deleted`) VALUES
(1041, 3001, 2001, '知行学员', '虚拟线程与线程池该如何选择？', 'JDK 21 的虚拟线程在高并发 IO 场景下优势明显，但 CPU 密集型任务是否仍应使用固定线程池？想听听大家的实践经验。', 2, 1, NOW(), NOW(), 0),
(1051, 3009, 2101, '陈志远', '联合索引最左前缀的实践总结', '联合索引 (a,b,c) 中 a 用范围查询后，b、c 就无法继续用于索引定位。我把项目中三条慢 SQL 的改写过程整理了一下。', 1, 0, NOW(), NOW(), 0),
(1053, 3010, 2103, '赵梓涵', 'Pinia 持久化插件踩坑记录', '使用 pinia-plugin-persistedstate 时，若 store 中存放了类实例，刷新后会退化为普通对象。建议只持久化基础类型。', 0, 0, NOW(), NOW(), 0),
(1055, 3001, 2105, '刘思彤', '后端转全栈的学习路线求建议', '目前在做 Java 后端，想补齐前端能力。Vue 3 + Vite 之后应该优先学工程化还是先把 JS 基础打牢？', 0, 0, NOW(), NOW(), 0);

INSERT IGNORE INTO `board_reply`
(`id`, `board_id`, `user_id`, `user_name`, `parent_id`, `content`, `create_time`, `update_time`, `deleted`) VALUES
(1061, 1041, 2104, '王浩然', 0, 'CPU 密集型任务继续用固定大小线程池，虚拟线程的收益主要来自阻塞式 IO 的调度切换。', NOW(), NOW(), 0),
(1062, 1041, 2101, '陈志远', 0, '我们在网关层把下游调用换成虚拟线程后，吞吐提升了约 30%，但要注意别池化虚拟线程。', NOW(), NOW(), 0),
(1063, 1051, 2001, '知行学员', 0, '补充一点：ORDER BY 的字段顺序也要和索引顺序对齐，否则同样会触发 Using filesort。', NOW(), NOW(), 0);

-- ---------------------------------------------------------------------
-- 5. 自检
-- ---------------------------------------------------------------------
SELECT 'points_record' AS table_name, COUNT(*) AS rows_cnt FROM `points_record`
UNION ALL SELECT 'board', COUNT(*) FROM `board`
UNION ALL SELECT 'board_reply', COUNT(*) FROM `board_reply`;

-- 积分榜预览（验证 SUM 聚合与排序）
SELECT p.user_id, u.name, SUM(p.points) AS total
FROM `points_record` p
LEFT JOIN `zx_user`.`user` u ON u.id = p.user_id
WHERE p.deleted = 0
GROUP BY p.user_id, u.name
ORDER BY total DESC
LIMIT 10;
