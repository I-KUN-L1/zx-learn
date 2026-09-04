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
-- （检测到无管理员时生成强随机密码，BCrypt 加密入库，凭据写入 .bootstrap-credentials）

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
