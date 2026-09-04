-- =====================================================================
-- 交易链路三方对账脚本（zx-trade / zx-course / zx-learning）
-- 用途：kill 进程模拟故障 → 重启自愈后，验证三方数据最终一致。
-- 用法：mysql -uroot -p < sql/reconcile.sql
-- 判定：以下每条查询均应返回 0 行（或空集）；有结果即为不一致，需人工核查。
-- 注意：需在同一 MySQL 实例上执行（跨库 JOIN）。
-- =====================================================================

-- 1) 本地消息表死信（投递重试超限，需人工介入补投）
SELECT 'DEAD_MSG' AS check_item, id, biz_key, topic, tag, retry_count, create_time
FROM zx_trade.order_msg
WHERE status = 3 AND deleted = 0;

-- 2) 滞留待投递消息（超过 5 分钟仍未投递成功，说明 MQ 长时间不可用或扫描任务异常）
SELECT 'STUCK_PENDING_MSG' AS check_item, id, biz_key, topic, tag, retry_count, create_time
FROM zx_trade.order_msg
WHERE status = 0 AND deleted = 0
  AND next_retry_time < NOW() - INTERVAL 5 MINUTE;

-- 3) 已支付订单但未开通课程（zx_learning.lesson 缺失）
SELECT 'PAID_WITHOUT_LESSON' AS check_item, o.id AS order_id, o.order_no, o.user_id, o.course_id
FROM zx_trade.trade_order o
LEFT JOIN zx_learning.lesson l
       ON l.user_id = o.user_id AND l.course_id = o.course_id AND l.deleted = 0
WHERE o.status = 1 AND o.deleted = 0 AND l.id IS NULL;

-- 4) 已支付订单但名额未确认（zx_course 流水缺失或仍处于"已锁定"）
SELECT 'PAID_WITHOUT_QUOTA_CONFIRM' AS check_item, o.id AS order_id, o.order_no, q.status AS quota_status
FROM zx_trade.trade_order o
LEFT JOIN zx_course.course_quota_record q ON q.order_id = o.id AND q.deleted = 0
WHERE o.status = 1 AND o.deleted = 0
  AND (q.id IS NULL OR q.status <> 2);

-- 5) 已关闭订单但优惠券未退回（coupon_use_record 仍为"已核销"）
SELECT 'CLOSED_WITHOUT_COUPON_REFUND' AS check_item, o.id AS order_id, o.order_no, o.coupon_id
FROM zx_trade.trade_order o
JOIN zx_trade.coupon_use_record cur ON cur.order_id = o.id AND cur.deleted = 0
WHERE o.status = 2 AND o.coupon_id IS NOT NULL AND o.deleted = 0
  AND cur.status <> 0;

-- 6) 已关闭订单但名额未释放（流水仍处于"已锁定"）
SELECT 'CLOSED_WITHOUT_QUOTA_RELEASE' AS check_item, o.id AS order_id, o.order_no, q.status AS quota_status
FROM zx_trade.trade_order o
JOIN zx_course.course_quota_record q ON q.order_id = o.id AND q.deleted = 0
WHERE o.status = 2 AND o.deleted = 0
  AND q.status = 1;

-- 7) 名额计数对账：course_quota.locked_count 应等于该课程"已锁定"流水数
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

-- 8) 销量对账：course.sold 应等于该课程"已确认"流水数（强校验，弱一致场景可能短暂偏差）
SELECT 'SOLD_MISMATCH' AS check_item, c.id AS course_id, c.sold,
       IFNULL(r.confirmed_cnt, 0) AS actual_confirmed
FROM zx_course.course c
LEFT JOIN (
    SELECT course_id, COUNT(*) AS confirmed_cnt
    FROM zx_course.course_quota_record
    WHERE status = 2 AND deleted = 0
    GROUP BY course_id
) r ON r.course_id = c.id
WHERE c.sold <> IFNULL(r.confirmed_cnt, 0);
