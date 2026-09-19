-- =====================================================================
-- 优惠券状态同步修复（幂等，可重复执行）
--
-- 【背景】
--   用券核销时只写入了 zx_trade.coupon_use_record（核销流水），
--   从未回写 zx_promotion.user_coupon.status，于是两端状态漂移：
--     · 券列表 / 我的优惠券一直显示"未使用"（券界面不刷新）；
--     · 再次使用同一张券时被 Redis 限用计数拦下，提示"该优惠券已达领取上限"。
--
-- 【本迁移】
--   按核销流水回填券状态。口径：某个 user_coupon 的最终状态由它**最后一条**核销流水决定
--   （用券 → 可能关单退回 → 可能再次使用，最后一次事件即当前真相）。
--
--   * 最后一条为「已核销(1)」 → 券 = 已使用(1)，回填 use_time / order_id
--   * 最后一条为「已退回(0)」 → 券 = 未使用(0)（仍在有效期内）或 已过期(2)，清空 use_time / order_id
--
--   修复后该不变量由代码持续维持，不再依赖本脚本：
--     下单 afterCommit 同步回写（Feign） + MQ 核销流水消费端兜底 + CouponReconcileJob 定时对账。
--
-- 【无 DDL 变更】user_coupon 已有 use_time / order_id 列，本迁移只纠正数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、按"最后一条核销流水"纠正券状态
-- ---------------------------------------------------------------------
UPDATE zx_promotion.user_coupon uc
JOIN (
    SELECT r.user_coupon_id, r.order_id, r.create_time, r.status
    FROM zx_trade.coupon_use_record r
    JOIN (
        SELECT user_coupon_id, MAX(id) AS last_id
        FROM zx_trade.coupon_use_record
        WHERE user_coupon_id IS NOT NULL
        GROUP BY user_coupon_id
    ) t ON t.user_coupon_id = r.user_coupon_id AND t.last_id = r.id
) last ON last.user_coupon_id = uc.id
SET uc.status = CASE
        WHEN last.status = 1 THEN 1
        WHEN uc.valid_end_time IS NOT NULL AND uc.valid_end_time <= NOW() THEN 2
        ELSE 0
    END,
    uc.use_time = CASE
        WHEN last.status = 1 THEN IFNULL(uc.use_time, last.create_time)
        ELSE NULL
    END,
    uc.order_id = CASE WHEN last.status = 1 THEN last.order_id ELSE NULL END
WHERE uc.status <> CASE
        WHEN last.status = 1 THEN 1
        WHEN uc.valid_end_time IS NOT NULL AND uc.valid_end_time <= NOW() THEN 2
        ELSE 0
    END;

-- ---------------------------------------------------------------------
-- 二、自检
-- ---------------------------------------------------------------------
-- 2.1 漂移条数必须为 0（券状态与最后一条核销流水不一致）
SELECT '二.1 券状态漂移数' AS `校验项（必须为 0）`, COUNT(*) AS `结果`
FROM (
    SELECT uc.status AS actual,
           CASE
               WHEN last.status = 1 THEN 1
               WHEN uc.valid_end_time IS NOT NULL AND uc.valid_end_time <= NOW() THEN 2
               ELSE 0
           END AS expected
    FROM zx_promotion.user_coupon uc
    JOIN (
        SELECT r.user_coupon_id, r.status
        FROM zx_trade.coupon_use_record r
        JOIN (
            SELECT user_coupon_id, MAX(id) AS last_id
            FROM zx_trade.coupon_use_record
            WHERE user_coupon_id IS NOT NULL
            GROUP BY user_coupon_id
        ) t ON t.user_coupon_id = r.user_coupon_id AND t.last_id = r.id
    ) last ON last.user_coupon_id = uc.id
) x
WHERE x.actual <> x.expected;

-- 2.2 券状态分布 + 核销/退回流水分布（人工核对）
SELECT '二.2 券状态分布' AS `校验项`, status AS `状态(0未使用/1已使用/2已过期)`, COUNT(*) AS `数量`
FROM zx_promotion.user_coupon
GROUP BY status
ORDER BY status;

SELECT '二.3 核销流水分布' AS `校验项`, status AS `状态(1已核销/0已退回)`, COUNT(*) AS `数量`
FROM zx_trade.coupon_use_record
GROUP BY status
ORDER BY status;
