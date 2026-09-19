-- =====================================================================
-- 支付单持久化（幂等，可重复执行）
--
-- 【背景】
--   zx-pay 原先是"骨架实现"：支付单存在进程内的 LinkedHashMap（访问序 LRU，上限 10000）。
--   全量测试（BUG-007）把它从"无界增长必然 OOM"改成"有界 LRU"，但**本质仍是内存态**：
--     · 服务重启 → 已创建的支付单全部丢失，查状态一律"支付单不存在"；
--     · 多实例部署 → 各实例各自持有一份，状态不一致；
--     · LRU 溢出淘汰 → 老支付单静默消失。
--   资金类数据不允许这样，故本迁移把它落库。
--
-- 【本迁移】
--   新增 `zx_pay` 库与两张表：
--     · pay_order      —— 支付单主体，`uk_biz_order_no` 保证"一单一支付单"（重试复用而非新建）；
--     · pay_notify_log —— 渠道回调流水，`uk_channel_pay_no` 保证同一笔回调只受理一次（幂等）。
--
-- 【无破坏性变更】全部为 CREATE ... IF NOT EXISTS，对已有环境无副作用。
--   init.sql 的 DDL 已同步，全新部署直接执行 init.sql 即可，本脚本用于**已有环境增量升级**。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、建库建表
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `zx_pay` DEFAULT CHARACTER SET utf8mb4;
USE `zx_pay`;

CREATE TABLE IF NOT EXISTS `pay_order` (
    `id` BIGINT NOT NULL COMMENT '支付单id（雪花）',
    `biz_order_no` VARCHAR(64) NOT NULL COMMENT '业务订单号（zx-trade 订单号）',
    `amount` BIGINT DEFAULT 0 COMMENT '支付金额(分)',
    `channel` INT DEFAULT 1 COMMENT '渠道:1支付宝/2微信',
    `status` INT DEFAULT 0 COMMENT '状态:0待支付/1已支付/2已关闭',
    `pay_url` VARCHAR(255) COMMENT '收银台跳转地址',
    `pay_no` VARCHAR(64) COMMENT '渠道交易流水号',
    `notify_time` DATETIME COMMENT '回调确认时间',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_order_no` (`biz_order_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';

CREATE TABLE IF NOT EXISTS `pay_notify_log` (
    `id` BIGINT NOT NULL,
    `channel` VARCHAR(16) NOT NULL COMMENT '渠道:alipay/wxpay',
    `biz_order_no` VARCHAR(64) COMMENT '业务订单号',
    `pay_no` VARCHAR(64) COMMENT '渠道流水号',
    `verify_result` TINYINT DEFAULT 0 COMMENT '验签结果:0失败/1通过',
    `raw` VARCHAR(1024) COMMENT '回调原始报文',
    `create_time` DATETIME,
    `update_time` DATETIME,
    `creater` BIGINT,
    `updater` BIGINT,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_pay_no` (`channel`, `pay_no`),
    KEY `idx_biz_order_no` (`biz_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调流水表（幂等）';

-- ---------------------------------------------------------------------
-- 二、自检
-- ---------------------------------------------------------------------
-- 2.1 两张表必须存在（各返回一行）
SELECT '二.1 pay_order 表结构' AS `校验项`, COUNT(*) AS `列数`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'zx_pay' AND TABLE_NAME = 'pay_order';

SELECT '二.2 pay_notify_log 表结构' AS `校验项`, COUNT(*) AS `列数`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'zx_pay' AND TABLE_NAME = 'pay_notify_log';

-- 2.3 唯一键必须存在（保证"一单一支付单"与"回调幂等"）
SELECT '二.3 唯一键清单' AS `校验项`, TABLE_NAME AS `表`, INDEX_NAME AS `唯一键`, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS `列`
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'zx_pay' AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'
GROUP BY TABLE_NAME, INDEX_NAME;

-- 2.4 数据现状（升级后首次执行应为 0 行）
SELECT '二.4 支付单数' AS `校验项`, COUNT(*) AS `结果` FROM `pay_order`;
SELECT '二.5 回调流水数' AS `校验项`, COUNT(*) AS `结果` FROM `pay_notify_log`;
