-- =====================================================================
-- 增量迁移脚本：题库联动模块（zx-exam）
-- 执行方式（Windows Git Bash）：
--   "/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 \
--       --default-character-set=utf8mb4 < sql/2026-09-11-exam-and-admin-module.sql
-- 幂等：重复执行安全（CREATE TABLE IF NOT EXISTS + 列存在性判断）
-- =====================================================================

USE `zx_exam`;

-- ---------------------------------------------------------------------
-- 1. 题目表：原实现在内存 ConcurrentHashMap 中，重启即丢、学员端无法接收，
--    此处升级为 MySQL 持久化，并新增 发布状态 / 归属教师 / 关联课程 三个联动字段。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `question` (
    `id`          BIGINT       NOT NULL COMMENT '题目id',
    `name`        VARCHAR(255) NOT NULL COMMENT '题干',
    `type`        INT          DEFAULT 1 COMMENT '类型:1单选/2多选/3判断',
    `difficulty`  INT          DEFAULT 1 COMMENT '难度:1-5',
    `score`       INT          DEFAULT 5 COMMENT '分值',
    `content`     TEXT         COMMENT '题干富文本/补充说明',
    `options`     VARCHAR(2048) COMMENT '选项JSON数组，判断题为["正确","错误"]',
    `answer`      VARCHAR(16)  COMMENT '正确答案(选项字母连写，如 A / AB)',
    `analysis`    VARCHAR(1000) COMMENT '答案解析',
    `teacher_id`  BIGINT       COMMENT '归属教师id（发布人）',
    `course_id`   BIGINT       COMMENT '关联课程id（师生联动的锚点）',
    `status`      TINYINT      DEFAULT 1 COMMENT '状态:0草稿/1已发布(学员可见)',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater`     BIGINT,
    `updater`     BIGINT,
    `deleted`     TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_teacher` (`teacher_id`),
    KEY `idx_course` (`course_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库表';

-- ---------------------------------------------------------------------
-- 2. 答题记录扩充：记录学员自己的作答，供错题本展示"我的作答 vs 正确答案"。
--    MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA 存在性判断实现幂等。
-- ---------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'zx_exam' AND TABLE_NAME = 'question_result' AND COLUMN_NAME = 'user_answer'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `question_result` ADD COLUMN `user_answer` VARCHAR(16) NULL COMMENT ''学员作答'' AFTER `question_name`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'zx_exam' AND TABLE_NAME = 'question_result' AND COLUMN_NAME = 'course_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `question_result` ADD COLUMN `course_id` BIGINT NULL COMMENT ''课程id(冗余,便于按课程统计)'' AFTER `user_answer`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 按用户 + 题目建立查询索引，支撑错题本与正确率统计
SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'zx_exam' AND TABLE_NAME = 'question_result' AND INDEX_NAME = 'idx_user_question'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `question_result` ADD INDEX `idx_user_question` (`user_id`, `question_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'zx-exam 题库联动模块迁移完成' AS result;
