package com.zhixing.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixing.api.dto.trade.QuotaMsg;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.common.utils.SnowflakeIdGenerator;
import com.zhixing.course.domain.po.Course;
import com.zhixing.course.domain.po.CourseQuota;
import com.zhixing.course.domain.po.CourseQuotaRecord;
import com.zhixing.course.mapper.CourseMapper;
import com.zhixing.course.mapper.CourseQuotaMapper;
import com.zhixing.course.mapper.CourseQuotaRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程名额服务（由 MQ 消费端调用，保证跨服务最终一致）。
 * <p>
 * 名额生命周期：下单 LOCK（锁定）→ 支付 CONFIRM（锁定转销量）/ 关单 RELEASE（释放）。
 * <ul>
 *   <li>course_quota_record.order_id 唯一：一单只会锁定/确认/释放一次（幂等第一层）；</li>
 *   <li>消费流水表（IdempotencyGuard）：重复投递兜底去重（幂等第二层）；</li>
 *   <li>条件更新 {@code locked_count < quota OR quota IS NULL}：并发下不超卖。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseQuotaService {

    /** 已锁定 */
    public static final int STATUS_LOCKED = 1;
    /** 已确认（转销量） */
    public static final int STATUS_CONFIRMED = 2;
    /** 已释放 */
    public static final int STATUS_RELEASED = 0;

    private final CourseQuotaMapper courseQuotaMapper;
    private final CourseQuotaRecordMapper quotaRecordMapper;
    private final CourseMapper courseMapper;
    private final IdempotencyGuard idempotencyGuard;

    /**
     * 锁定名额（下单事件）。名额已满抛出异常触发消息重投（最终死信人工介入）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void lock(QuotaMsg msg) {
        // 幂等第一层：订单名额流水已存在则跳过
        if (findRecord(msg.getOrderId()) != null) {
            return;
        }
        // 幂等第二层：消费流水去重
        if (!tryConsume("quota:lock:" + msg.getOrderId(), MqTopics.Tags.QUOTA_LOCK)) {
            return;
        }
        ensureQuotaRow(msg.getCourseId());
        insertRecord(msg, STATUS_LOCKED);
        // 条件更新防超卖：quota 为 NULL 不限名额；否则要求 locked_count < quota
        int rows = courseQuotaMapper.update(null, new LambdaUpdateWrapper<CourseQuota>()
                .eq(CourseQuota::getCourseId, msg.getCourseId())
                .and(w -> w.isNull(CourseQuota::getQuota)
                        .or().apply("locked_count < quota"))
                .setSql("locked_count = locked_count + 1"));
        if (rows == 0) {
            throw new BizIllegalException("课程名额已满，锁定失败：courseId=" + msg.getCourseId());
        }
        log.info("课程名额锁定成功：orderId={}, courseId={}", msg.getOrderId(), msg.getCourseId());
    }

    /**
     * 确认名额（支付成功事件）：锁定转销量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(QuotaMsg msg) {
        CourseQuotaRecord record = findRecord(msg.getOrderId());
        if (record == null) {
            // 锁定消息丢失（如死信）的容错路径：确认前同样校验余量，防止超卖
            if (!tryConsume("quota:confirm:" + msg.getOrderId(), MqTopics.Tags.QUOTA_CONFIRM)) {
                return;
            }
            ensureQuotaRow(msg.getCourseId());
            int rows = courseQuotaMapper.update(null, new LambdaUpdateWrapper<CourseQuota>()
                    .eq(CourseQuota::getCourseId, msg.getCourseId())
                    .and(w -> w.isNull(CourseQuota::getQuota)
                            .or().apply("locked_count < quota"))
                    .setSql("locked_count = locked_count + 1"));
            if (rows == 0) {
                throw new BizIllegalException("课程名额已满，确认失败：courseId=" + msg.getCourseId());
            }
            // 归还上面这次"占位"：本分支直接落 CONFIRMED，不会产生 LOCKED 流水，
            // 而 locked_count 的唯一递减点（正常 confirm/release 分支）都要求先有 LOCKED 记录。
            // 若保留占位，locked_count 将**只增不减**，逐步虚占名额：一旦课程设置了 quota，
            // 会越卖越"满"，最终误报"名额已满"而无法下单（可复现的历史缺陷）。
            // 这里保留"先 +1 再 -1"而非直接去掉 +1，是为了让超卖校验仍是原子的条件更新。
            releaseLockedCount(msg.getCourseId());
            insertRecord(msg, STATUS_CONFIRMED);
            increaseSold(msg.getCourseId());
            log.info("课程名额补确认成功（原锁定消息丢失）：orderId={}, courseId={}",
                    msg.getOrderId(), msg.getCourseId());
            return;
        }
        if (!Integer.valueOf(STATUS_LOCKED).equals(record.getStatus())) {
            // 已确认 / 已释放：幂等跳过
            return;
        }
        if (!tryConsume("quota:confirm:" + msg.getOrderId(), MqTopics.Tags.QUOTA_CONFIRM)) {
            return;
        }
        // 条件更新状态机：仅 1锁定 → 2确认 可迁移一次
        int rows = quotaRecordMapper.update(null, new LambdaUpdateWrapper<CourseQuotaRecord>()
                .eq(CourseQuotaRecord::getId, record.getId())
                .eq(CourseQuotaRecord::getStatus, STATUS_LOCKED)
                .set(CourseQuotaRecord::getStatus, STATUS_CONFIRMED));
        if (rows == 0) {
            return;
        }
        // 锁定释放 + 销量 +1（GREATEST 兜底，避免计数被手工数据干扰为负）
        releaseLockedCount(msg.getCourseId());
        increaseSold(msg.getCourseId());
        log.info("课程名额确认成功：orderId={}, courseId={}", msg.getOrderId(), msg.getCourseId());
    }

    /**
     * 释放名额（超时关单 / 取消事件）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(QuotaMsg msg) {
        CourseQuotaRecord record = findRecord(msg.getOrderId());
        if (record == null) {
            // 未知订单的释放：补记录后忽略（保持流水完整，便于对账）
            if (!tryConsume("quota:release:" + msg.getOrderId(), MqTopics.Tags.QUOTA_RELEASE)) {
                return;
            }
            insertRecord(msg, STATUS_RELEASED);
            return;
        }
        if (!Integer.valueOf(STATUS_LOCKED).equals(record.getStatus())) {
            return;
        }
        if (!tryConsume("quota:release:" + msg.getOrderId(), MqTopics.Tags.QUOTA_RELEASE)) {
            return;
        }
        int rows = quotaRecordMapper.update(null, new LambdaUpdateWrapper<CourseQuotaRecord>()
                .eq(CourseQuotaRecord::getId, record.getId())
                .eq(CourseQuotaRecord::getStatus, STATUS_LOCKED)
                .set(CourseQuotaRecord::getStatus, STATUS_RELEASED));
        if (rows == 0) {
            return;
        }
        releaseLockedCount(msg.getCourseId());
        log.info("课程名额释放成功：orderId={}, courseId={}", msg.getOrderId(), msg.getCourseId());
    }

    private CourseQuotaRecord findRecord(Long orderId) {
        return quotaRecordMapper.selectOne(new LambdaQueryWrapper<CourseQuotaRecord>()
                .eq(CourseQuotaRecord::getOrderId, orderId));
    }

    /**
     * 归还一个"在途"名额占位：{@code locked_count = max(locked_count - 1, 0)}。
     *
     * <p>{@code GREATEST(...,0)} 是兜底，避免历史脏数据（手工改库 / 早期版本遗留）把计数减成负数。
     * {@code locked_count} 的语义是「已锁定但未确认」的在途名额数，因此**每一个 +1 都必须有且仅有一次对应的 -1**；
     * {@code course_quota_record} 中以 status=1（已锁定）存在的行数即为其权威值，可用
     * {@code sql/reconcile.sql} 的第 7 项对账。
     */
    private void releaseLockedCount(Long courseId) {
        courseQuotaMapper.update(null, new LambdaUpdateWrapper<CourseQuota>()
                .eq(CourseQuota::getCourseId, courseId)
                .setSql("locked_count = GREATEST(locked_count - 1, 0)"));
    }

    /** course_quota 行不存在则初始化（quota 默认 NULL 不限名额），并发靠 course_id 唯一索引兜底 */
    private void ensureQuotaRow(Long courseId) {
        CourseQuota quota = courseQuotaMapper.selectOne(new LambdaQueryWrapper<CourseQuota>()
                .eq(CourseQuota::getCourseId, courseId));
        if (quota != null) {
            return;
        }
        CourseQuota row = new CourseQuota();
        row.setId(SnowflakeIdGenerator.getInstance().nextId());
        row.setCourseId(courseId);
        row.setQuota(null);
        row.setLockedCount(0);
        try {
            courseQuotaMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发初始化：另一线程已创建
        }
    }

    private void insertRecord(QuotaMsg msg, int status) {
        CourseQuotaRecord record = new CourseQuotaRecord();
        record.setId(SnowflakeIdGenerator.getInstance().nextId());
        record.setOrderId(msg.getOrderId());
        record.setCourseId(msg.getCourseId());
        record.setUserId(msg.getUserId());
        record.setStatus(status);
        try {
            quotaRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("名额流水重复插入，忽略：orderId={}", msg.getOrderId());
        }
    }

    private void increaseSold(Long courseId) {
        courseMapper.update(null, new LambdaUpdateWrapper<Course>()
                .eq(Course::getId, courseId)
                .setSql("sold = IFNULL(sold, 0) + 1"));
    }

    private boolean tryConsume(String consumeKey, String tag) {
        return idempotencyGuard.tryConsume(consumeKey, MqTopics.TOPIC_COURSE_QUOTA, tag);
    }
}
