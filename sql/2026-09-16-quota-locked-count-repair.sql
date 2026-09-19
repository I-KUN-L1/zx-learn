-- =====================================================================
-- 课程名额「在途占位」计数修复（幂等，可重复执行）
--
-- 【背景】上线审查发现 course_quota.locked_count 存在**单调递增**的漂移：
--   7 门课程的 locked_count 大于「status=1（已锁定）流水」的实际条数，例如 course 1 为 2/0。
--
-- 【根因】CourseQuotaService.confirm() 的「锁定消息丢失（死信）」容错分支：
--     无 LOCKED 流水时，为做原子超卖校验先 locked_count + 1，随后**直接**落
--     CONFIRMED 流水（不产生 LOCKED 流水）。而 locked_count 的唯一递减点
--     （正常 confirm / release 分支）都要求「先存在 LOCKED 流水」才执行，
--     于是这次 +1 **永远不会被回收** → 计数只增不减。
--
-- 【影响】locked_count 的语义是「已锁定未确认」的在途名额；
--   course_quota.quota 非空（限名额课程）时，超卖校验读的就是 locked_count，
--   因此虚高的计数会逐步占满名额 → 学员端下单/确认时误报「课程名额已满」，属功能性缺陷。
--   修复后该不变量由代码持续维持（confirm 容错分支已改为「占位 + 归还」成对出现），
--   本节只负责把历史脏数据对齐。
--
-- 【口径】locked_count 的权威值 = course_quota_record 中该课程 status=1 且 deleted=0 的条数。
--
-- 【无 DDL 变更】本迁移只纠正数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、把 locked_count 对齐为「在途（已锁定）流水」的实际条数
--     与 sql/reconcile.sql 第 7 项 QUOTA_COUNT_MISMATCH 的判定口径完全一致，
--     执行后该项对账应返回 0 行。
-- ---------------------------------------------------------------------
UPDATE zx_course.course_quota q
LEFT JOIN (
    SELECT course_id, COUNT(*) AS locked_cnt
    FROM zx_course.course_quota_record
    WHERE status = 1 AND deleted = 0
    GROUP BY course_id
) r ON r.course_id = q.course_id
SET q.locked_count = IFNULL(r.locked_cnt, 0)
WHERE q.locked_count <> IFNULL(r.locked_cnt, 0);

-- ---------------------------------------------------------------------
-- 二、自检：应返回 0 行（有结果说明仍存在漂移，需人工核查是否存在并发写入）
-- ---------------------------------------------------------------------
SELECT 'QUOTA_COUNT_RESIDUAL' AS check_item, q.course_id, q.locked_count,
       IFNULL(r.locked_cnt, 0) AS actual_locked
FROM zx_course.course_quota q
LEFT JOIN (
    SELECT course_id, COUNT(*) AS locked_cnt
    FROM zx_course.course_quota_record
    WHERE status = 1 AND deleted = 0
    GROUP BY course_id
) r ON r.course_id = q.course_id
WHERE q.locked_count <> IFNULL(r.locked_cnt, 0);
