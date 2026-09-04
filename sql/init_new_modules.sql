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
