package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.trade.domain.po.CouponUseRecord;
import com.zhixing.trade.mapper.CouponUseRecordMapper;
import com.zhixing.trade.mq.CouponMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 优惠券核销/退回的异步落库服务（由 MQ 消费端调用）。
 * <p>
 * 以"消费流水"（IdempotencyGuard）+ "order_id 唯一"双重保证：
 * 即便 MQ 重复投递、消费者并发消费，一单也只会核销/退回一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponUseRecordService {

    private final CouponUseRecordMapper couponUseRecordMapper;
    private final IdempotencyGuard idempotencyGuard;

    /** 核销（下单时扣减成功后异步落库） */
    @Transactional(rollbackFor = Exception.class)
    public void submitUse(CouponMsg msg) {
        // 先查：若该订单已落库核销流水，幂等返回
        Long exist = couponUseRecordMapper.selectCount(new LambdaQueryWrapper<CouponUseRecord>()
                .eq(CouponUseRecord::getOrderId, msg.getOrderId()));
        if (exist != null && exist > 0) {
            return;
        }
        String consumeKey = "coupon:use:" + msg.getOrderId();
        if (!idempotencyGuard.tryConsume(consumeKey, MqTopics.TOPIC_COUPON_USE,
                MqTopics.Tags.COUPON_USE)) {
            return;
        }
        CouponUseRecord record = new CouponUseRecord();
        record.setUserId(msg.getUserId());
        record.setCouponId(msg.getCouponId());
        record.setUserCouponId(msg.getUserCouponId());
        record.setOrderId(msg.getOrderId());
        record.setAmount(msg.getAmount());
        record.setStatus(1);
        couponUseRecordMapper.insert(record);
        log.info("优惠券核销落库成功：orderId={}, couponId={}, amount={}",
                msg.getOrderId(), msg.getCouponId(), msg.getAmount());
    }

    /** 退回（订单超时关单 / 取消） */
    @Transactional(rollbackFor = Exception.class)
    public void submitRefund(CouponMsg msg) {
        String consumeKey = "coupon:refund:" + msg.getOrderId();
        if (!idempotencyGuard.tryConsume(consumeKey, MqTopics.TOPIC_COUPON_USE,
                MqTopics.Tags.COUPON_REFUND)) {
            return;
        }
        int rows = couponUseRecordMapper.update(null, new LambdaUpdateWrapper<CouponUseRecord>()
                .eq(CouponUseRecord::getOrderId, msg.getOrderId())
                .set(CouponUseRecord::getStatus, 0)
                .set(CouponUseRecord::getUpdateTime, LocalDateTime.now()));
        log.info("优惠券退回落库完成：orderId={}, affected={}", msg.getOrderId(), rows);
    }
}