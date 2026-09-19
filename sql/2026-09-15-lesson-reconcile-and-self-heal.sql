-- =====================================================================
-- 知行智学 · 课表一致性修复迁移
-- 日期：2026-09-15
-- 幂等：可重复执行（复活用 WHERE deleted<>0；补齐用 NOT EXISTS 去重）
-- ---------------------------------------------------------------------
-- 背景（线上实际出现的问题）：
--   学员在课程界面购买课程 / 加入免费课时，提示"课程已拥有"，但"我的课表"里
--   找不到该课程 —— 两端状态不一致。
--
-- 根因（两个叠加）：
--   1) 支付成功后的开课有两条保障：Feign 同步开课 + 本地消息表 orderPaid 事件（MQ）。
--      当学习服务不可用且 MQ 不可用时，两条都失败；本地消息表重试超过 max_retry
--      后把消息转为**死信**（order_msg.status=3）永久放弃投递 → 订单已支付、课表永远为空。
--      用户再点购买会被"已拥有"拦截，形成死锁。
--   2) lesson 的唯一索引 uk_user_course(user_id, course_id) 是**物理**约束，而删除课表项
--      走的是 MyBatis-Plus 逻辑删除（deleted=1）。学员删过课后重新开课时 insert 撞唯一索引，
--      旧逻辑只做"幂等跳过"，那行 deleted=1 永远不会复活 → 课表里始终看不到。
--
-- 本迁移负责**存量数据修复**；代码侧的自愈（开课时复活 + 已支付未开课自动补开 +
-- 死信对账补偿任务 LessonReconcileJob）保证后续不再复发。
--
-- 执行顺序：init.sql → 其它迁移 → 本脚本 → test-data.sql（可选）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、复活「被逻辑删除、但订单仍处于已支付」的课表项
-- ---------------------------------------------------------------------
UPDATE `zx_learning`.`lesson` l
SET l.`deleted` = 0,
    l.`update_time` = NOW()
WHERE l.`deleted` <> 0
  AND EXISTS (SELECT 1
              FROM `zx_trade`.`trade_order` o
              WHERE o.`user_id` = l.`user_id`
                AND o.`course_id` = l.`course_id`
                AND o.`deleted` = 0
                AND o.`status` = 1);

-- ---------------------------------------------------------------------
-- 二、补齐「已支付订单但课表缺失」的课表项
--     id 不能写固定基数：lesson.id 无自增，固定号段在**重复执行**时会与上次生成的主键
--     冲突（ERROR 1062 Duplicate entry）。这里从当前最大 id 之后继续分配，保证可重复执行。
-- ---------------------------------------------------------------------
SET @seq := (SELECT IFNULL(MAX(`id`), 0) FROM `zx_learning`.`lesson`);

INSERT INTO `zx_learning`.`lesson`
    (`id`, `user_id`, `course_id`, `course_name`, `create_time`, `update_time`, `deleted`)
SELECT (@seq := @seq + 1) AS `id`,
       t.`user_id`,
       t.`course_id`,
       t.`course_name`,
       NOW(),
       NOW(),
       0
FROM (
    SELECT o.`user_id`                AS `user_id`,
           o.`course_id`              AS `course_id`,
           MAX(o.`course_name`)       AS `course_name`
    FROM `zx_trade`.`trade_order` o
    JOIN `zx_course`.`course` c
      ON c.`id` = o.`course_id` AND c.`deleted` = 0
    WHERE o.`deleted` = 0
      AND o.`status` = 1
      AND NOT EXISTS (SELECT 1
                      FROM `zx_learning`.`lesson` l
                      WHERE l.`user_id` = o.`user_id`
                        AND l.`course_id` = o.`course_id`)
    GROUP BY o.`user_id`, o.`course_id`
) t;

-- ---------------------------------------------------------------------
-- 三、把「支付成功」死信重置为待投递，让 MQ 再补投一次
--     （与代码侧 LessonReconcileJob 的 Feign 补开课互为兜底，开课本身幂等）
-- ---------------------------------------------------------------------
-- 说明：仅在 MQ 已恢复时才有意义；未恢复时由 LessonReconcileJob 兜底，故此步可重复执行。
UPDATE `zx_trade`.`order_msg`
SET `status` = 0,
    `retry_count` = 0,
    `next_retry_time` = NOW(),
    `update_time` = NOW()
WHERE `tag` = 'PAID'
  AND `status` = 3;

-- ---------------------------------------------------------------------
-- 四、自检：已支付订单应当都能在课表中找到
-- ---------------------------------------------------------------------
SELECT '已支付但课表缺失的用户课程数（期望 0）' AS `check_item`,
       COUNT(*) AS `cnt`
FROM (
    SELECT o.`user_id`, o.`course_id`
    FROM `zx_trade`.`trade_order` o
    WHERE o.`deleted` = 0
      AND o.`status` = 1
      AND NOT EXISTS (SELECT 1
                      FROM `zx_learning`.`lesson` l
                      WHERE l.`user_id` = o.`user_id`
                        AND l.`course_id` = o.`course_id`
                        AND l.`deleted` = 0)
    GROUP BY o.`user_id`, o.`course_id`
) t;

SELECT '课表总行数' AS `check_item`, COUNT(*) AS `cnt` FROM `zx_learning`.`lesson`;

SELECT 'orderPaid 死信数（期望 0）' AS `check_item`, COUNT(*) AS `cnt`
FROM `zx_trade`.`order_msg` WHERE `tag` = 'PAID' AND `status` = 3;
