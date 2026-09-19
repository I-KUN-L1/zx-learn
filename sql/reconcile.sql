-- =====================================================================
-- 交易链路三方对账脚本（zx-trade / zx-course / zx-learning）
-- 用途：kill 进程模拟故障 → 重启自愈后，验证三方数据最终一致；也是上线前的数据一致性门槛。
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 < sql/reconcile.sql
--
-- 判定口径（上线审查后修正，务必按此理解）：
--   · 第 1、2、3、5、6 项：**必须返回 0 行**，任何结果都是真实不一致。
--   · 第 4、7、8 项：**真实链路必须返回 0 行**；脚本已排除演示种子 / 测试夹具数据
--     （order_no 形如 DEMO* / VTEST* 的订单、以及因此产生的计数），
--     并单独用「EXCLUDED_*」查询把被排除的行列出来，便于人工确认排除是否合理。
--
--   ⚠ 历史口径缺陷：早期版本文档写「每条查询都应返回 0 行」，但第 8 项用的是
--     绝对相等（course.sold = 已确认流水数）。course.sold 同时是前端展示的
--     「学习人数」，演示种子会写入营销基线值（如 1233），绝对相等**永远不成立**，
--     于是该门槛在任何带种子的库上都无法通过，既误导上线判定、也可能掩盖真实漂移。
--     现改为只告警真正的故障模式：销量**小于**已确认数（说明自增丢失/被回退）。
--
-- 注意：需在同一 MySQL 实例上执行（跨库 JOIN）。
-- =====================================================================

-- 演示/测试数据识别规则（集中定义，便于维护）
--   DEMO%   ： sql/test-data.sql 写入的演示订单
--   VTEST%  ： 各验证脚本创建的临时夹具订单
--   T%      ： 早期人工/脚本直接插入 trade_order 的测试订单（T<雪花 id>）

-- 1) 本地消息表死信（投递重试超限，需人工介入补投）—— 必须为 0 行
SELECT 'DEAD_MSG' AS check_item, id, biz_key, topic, tag, retry_count, create_time
FROM zx_trade.order_msg
WHERE status = 3 AND deleted = 0;

-- 2) 滞留待投递消息（超过 5 分钟仍未投递成功，说明 MQ 长时间不可用或扫描任务异常）—— 必须为 0 行
SELECT 'STUCK_PENDING_MSG' AS check_item, id, biz_key, topic, tag, retry_count, create_time
FROM zx_trade.order_msg
WHERE status = 0 AND deleted = 0
  AND next_retry_time < NOW() - INTERVAL 5 MINUTE;

-- 3) 已支付订单但未开通课程（zx_learning.lesson 缺失）—— 必须为 0 行
SELECT 'PAID_WITHOUT_LESSON' AS check_item, o.id AS order_id, o.order_no, o.user_id, o.course_id
FROM zx_trade.trade_order o
LEFT JOIN zx_learning.lesson l
       ON l.user_id = o.user_id AND l.course_id = o.course_id AND l.deleted = 0
WHERE o.status = 1 AND o.deleted = 0 AND l.id IS NULL
  AND o.order_no NOT LIKE 'DEMO%' AND o.order_no NOT LIKE 'VTEST%';

-- 4) 已支付订单但名额未确认（zx_course 流水缺失或仍处于"已锁定"）
--    —— 真实链路必须为 0 行（演示/夹具订单由 SQL 直接插入，本就不经过下单→锁定链路，已排除）
SELECT 'PAID_WITHOUT_QUOTA_CONFIRM' AS check_item, o.id AS order_id, o.order_no, q.status AS quota_status
FROM zx_trade.trade_order o
LEFT JOIN zx_course.course_quota_record q ON q.order_id = o.id AND q.deleted = 0
WHERE o.status = 1 AND o.deleted = 0
  AND (q.id IS NULL OR q.status <> 2)
  AND o.order_no NOT LIKE 'DEMO%' AND o.order_no NOT LIKE 'VTEST%' AND o.order_no NOT LIKE 'T%';

-- 4b) 被排除的演示/夹具订单明细（**非断言**，仅用于人工确认排除范围是否合理）
SELECT 'EXCLUDED_DEMO_OR_FIXTURE_ORDER' AS check_item, o.id AS order_id, o.order_no, o.status
FROM zx_trade.trade_order o
LEFT JOIN zx_course.course_quota_record q ON q.order_id = o.id AND q.deleted = 0
WHERE o.status = 1 AND o.deleted = 0 AND (q.id IS NULL OR q.status <> 2)
  AND (o.order_no LIKE 'DEMO%' OR o.order_no LIKE 'VTEST%' OR o.order_no LIKE 'T%');

-- 5) 已关闭订单但优惠券未退回（coupon_use_record 仍为"已核销"）—— 必须为 0 行
SELECT 'CLOSED_WITHOUT_COUPON_REFUND' AS check_item, o.id AS order_id, o.order_no, o.coupon_id
FROM zx_trade.trade_order o
JOIN zx_trade.coupon_use_record cur ON cur.order_id = o.id AND cur.deleted = 0
WHERE o.status = 2 AND o.coupon_id IS NOT NULL AND o.deleted = 0
  AND cur.status <> 0;

-- 6) 已关闭订单但名额未释放（流水仍处于"已锁定"）—— 必须为 0 行
SELECT 'CLOSED_WITHOUT_QUOTA_RELEASE' AS check_item, o.id AS order_id, o.order_no, q.status AS quota_status
FROM zx_trade.trade_order o
JOIN zx_course.course_quota_record q ON q.order_id = o.id AND q.deleted = 0
WHERE o.status = 2 AND o.deleted = 0
  AND q.status = 1;

-- 7) 名额计数对账：course_quota.locked_count 权威值 = 该课程 status=1 的流水条数 —— 必须为 0 行
--    （历史脏数据由 sql/2026-09-16-quota-locked-count-repair.sql 修复）
SELECT 'QUOTA_COUNT_MISMATCH' AS check_item, q.course_id, q.locked_count,
       IFNULL(r.locked_cnt, 0) AS actual_locked
FROM zx_course.course_quota q
LEFT JOIN (
    SELECT course_id, COUNT(*) AS locked_cnt
    FROM zx_course.course_quota_record
    WHERE status = 1 AND deleted = 0
    GROUP BY course_id
) r ON r.course_id = q.course_id
WHERE q.locked_count <> IFNULL(r.locked_cnt, 0);

-- 8) 销量对账：course.sold 不应**小于**该课程"已确认"流水数 —— 必须为 0 行
--    （sold 兼作前端展示的"学习人数"，种子写入了营销基线，故不能用等号判定；见文件头说明）
SELECT 'SOLD_BELOW_CONFIRMED' AS check_item, c.id AS course_id, c.sold,
       IFNULL(r.confirmed_cnt, 0) AS actual_confirmed
FROM zx_course.course c
LEFT JOIN (
    SELECT course_id, COUNT(*) AS confirmed_cnt
    FROM zx_course.course_quota_record
    WHERE status = 2 AND deleted = 0
    GROUP BY course_id
) r ON r.course_id = c.id
WHERE c.sold < IFNULL(r.confirmed_cnt, 0);

-- 8b) 销量与已确认数的差额速览（**非断言**，仅供观察自增是否在推进）
SELECT 'SOLD_DELTA_OVERVIEW' AS check_item, c.id AS course_id, c.sold,
       IFNULL(r.confirmed_cnt, 0) AS actual_confirmed,
       c.sold - IFNULL(r.confirmed_cnt, 0) AS delta
FROM zx_course.course c
LEFT JOIN (
    SELECT course_id, COUNT(*) AS confirmed_cnt
    FROM zx_course.course_quota_record
    WHERE status = 2 AND deleted = 0
    GROUP BY course_id
) r ON r.course_id = c.id
WHERE c.sold <> IFNULL(r.confirmed_cnt, 0)
ORDER BY delta DESC;
