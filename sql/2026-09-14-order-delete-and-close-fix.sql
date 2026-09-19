-- =====================================================================
-- 知行智学 · 订单删除与关单修复迁移
-- 日期：2026-09-14
-- 幂等：可重复执行（DDL 走 INFORMATION_SCHEMA 存在性判断，不依赖"忽略报错"）
-- ---------------------------------------------------------------------
-- 背景与改动：
--   1) 需求 4「用户端删除订单后管理端仍保留记录」→ 不能复用 BasePO.deleted
--      （MyBatis-Plus @TableLogic 会让管理端一起看不到），因此新增独立列
--      `user_deleted`：仅代表"学员侧已从我的订单中移除"，管理端查询不加此条件。
--   2) 需求 3「已拥有课程不得出现在待支付列表 / 倒计时结束自动关闭」→ 需要按
--      用户 + 状态快速捞出待支付单做对账关闭，故补 (user_id, status) 组合索引。
--
-- 【2026-09-15 修正】原先这两条 ALTER 是裸语句，脚本头却写着"列已存在时忽略报错"
--   —— 这要求调用方额外传 --force，否则 `mysql < file` 在批处理模式下**遇到首个错误即中断**，
--   导致整个迁移链断掉（已由 scripts/db-migrate.sh 实测复现：ERROR 1060 Duplicate column）。
--   现改为与本仓库其它迁移一致的 INFORMATION_SCHEMA + PREPARE 幂等范式。
-- =====================================================================
USE `zx_trade`;

-- ---------------------------------------------------------------------
-- 1. 学员侧删除标记：0=未删除 1=学员已删除（管理端仍可见）
--    MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA 存在性判断实现幂等。
-- ---------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'zx_trade' AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'user_deleted'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `user_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''学员侧删除标记:0未删除/1学员已删除(管理端仍可见)''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 学员"我的订单"过滤 + 待支付对账扫描走该索引
-- ---------------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'zx_trade' AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'idx_user_status'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `trade_order` ADD KEY `idx_user_status` (`user_id`, `status`, `user_deleted`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 兼容历史库：清理"关单幂等流水"造成的僵尸订单
-- ---------------------------------------------------------------------
-- 旧实现把 `order:close:{orderId}` 作为永久消费流水，一旦写入就再也不能关单；
-- 若订单被测试数据复位脚本改回 status=0，该订单将永远关不掉（前端倒计时归零仍显示待支付）。
-- 新实现改为「条件更新」保证幂等，不再依赖该流水，故删除历史关单流水让其可重新关单。
DELETE FROM `consume_record` WHERE `consume_key` LIKE 'order:close:%';

-- ---------------------------------------------------------------------
-- 4. 自检
-- ---------------------------------------------------------------------
SELECT '4.1 user_deleted 列已就位（期望 1）' AS `校验项（必须为 1）`, COUNT(*) AS `结果`
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'zx_trade' AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'user_deleted';

SELECT '4.2 idx_user_status 索引已就位（期望 1）' AS `校验项（必须为 1）`, COUNT(DISTINCT INDEX_NAME) AS `结果`
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = 'zx_trade' AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'idx_user_status';
