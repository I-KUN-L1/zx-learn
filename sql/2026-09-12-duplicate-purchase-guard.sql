-- =====================================================================
-- 增量迁移脚本：课程防重复购买（zx-trade）
-- 执行方式（Windows Git Bash）：
--   "/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 \
--       --default-character-set=utf8mb4 < sql/2026-09-12-duplicate-purchase-guard.sql
-- 幂等：重复执行安全（information_schema 存在性判断 + 预处理语句）
--
-- 目的：
--   1. 同一用户同一课程仅允许一条「已支付」订单（数据库层硬约束），
--      兜底并发双击/双开标签页造成的重复购买，应用层校验见 OrderService。
--   2. 生成列 paid_key：仅 status=1(已支付) 且 deleted=0 的订单生成
--      "userId:courseId" 唯一键；待支付/已关闭/已退款订单为 NULL，
--      MySQL 唯一索引对 NULL 不去重，因此不影响正常多状态订单流转。
--      退款成功(status=4)后 paid_key 变 NULL，用户可重新购买，符合业务预期。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 新增生成列 paid_key（已存在则跳过）
-- ---------------------------------------------------------------------
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zx_trade'
      AND TABLE_NAME   = 'trade_order'
      AND COLUMN_NAME  = 'paid_key'
);
SET @ddl_col = IF(
    @col_exists = 0,
    'ALTER TABLE `zx_trade`.`trade_order` ADD COLUMN `paid_key` VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN `status` = 1 AND `deleted` = 0 THEN CONCAT_WS('':'', `user_id`, `course_id`) END) VIRTUAL COMMENT ''已支付唯一键(生成列):userId:courseId, 防重复购买''',
    'SELECT ''column paid_key already exists, skip'' AS msg'
);
PREPARE stmt FROM @ddl_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 归档存量重复已支付订单（同一用户同一课程保留最新一条，其余置为已关闭）
--    不删除数据，仅状态归档；旧订单对应课表已由 uk_user_course 保证不重复。
-- ---------------------------------------------------------------------
UPDATE zx_trade.trade_order o1 JOIN zx_trade.trade_order o2
    ON o1.user_id = o2.user_id AND o1.course_id = o2.course_id
    AND o1.status = 1 AND o1.deleted = 0 AND o2.status = 1 AND o2.deleted = 0
    AND o1.id < o2.id
SET o1.status = 2, o1.update_time = NOW();

-- ---------------------------------------------------------------------
-- 3. 加唯一索引 uk_user_course_paid（已存在则跳过）
-- ---------------------------------------------------------------------
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'zx_trade'
      AND TABLE_NAME   = 'trade_order'
      AND INDEX_NAME   = 'uk_user_course_paid'
);
SET @ddl_idx = IF(
    @idx_exists = 0,
    'ALTER TABLE `zx_trade`.`trade_order` ADD UNIQUE KEY `uk_user_course_paid` (`paid_key`)',
    'SELECT ''index uk_user_course_paid already exists, skip'' AS msg'
);
PREPARE stmt FROM @ddl_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 自检：生成列与索引均应就位（期望各输出 1）
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS paid_key_column_ready
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'zx_trade' AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'paid_key';

SELECT COUNT(DISTINCT INDEX_NAME) AS uk_index_ready
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'zx_trade' AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'uk_user_course_paid';
