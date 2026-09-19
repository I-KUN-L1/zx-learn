package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.trade.domain.po.CouponUseRecord;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.mapper.CouponUseRecordMapper;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.mq.CouponMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券核销/退回的异步落库服务（由 MQ 消费端调用）。
 * <p>
 * 以"消费流水"（IdempotencyGuard）+ "order_id 唯一"双重保证：
 * 即便 MQ 重复投递、消费者并发消费，一单也只会核销/退回一次。
 * <p>
 * 落库后同步把券状态回写到 zx-promotion（{@link CouponStatusSyncer}）：
 * 这是下单同步回写失败时的兜底通道，保证券列表的"已使用/未使用"与核销流水最终一致。
 * <p>
 * 消息乱序防护：核销（USE）与退回（REFUND）同 topic，若关单先被消费，后到的核销消息
 * 必须跳过（见 {@link #isOrderClosed}），否则会把已退回的券改回"已使用"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponUseRecordService {

    private final CouponUseRecordMapper couponUseRecordMapper;
    private final OrderMapper orderMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final CouponStatusSyncer couponStatusSyncer;

    /** 核销（下单时扣减成功后异步落库） */
    @Transactional(rollbackFor = Exception.class)
    public void submitUse(CouponMsg msg) {
        // 先查：若该订单已落库核销流水，幂等返回
        Long exist = couponUseRecordMapper.selectCount(new LambdaQueryWrapper<CouponUseRecord>()
                .eq(CouponUseRecord::getOrderId, msg.getOrderId()));
        if (exist != null && exist > 0) {
            return;
        }
        // 消息乱序防护：下单消息与关单消息走同一 topic，若关单（退回）先被消费，
        // 后到的核销消息若照常落库 + 回写"已使用"，会出现"订单已关闭、券却显示已使用"的漂移，
        // 且对账任务会把这个错误状态固化。已关闭的订单直接跳过核销。
        if (isOrderClosed(msg.getOrderId())) {
            log.info("订单已关闭，跳过优惠券核销落库（消息乱序）：orderId={}", msg.getOrderId());
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
        // 兜底回写券状态为"已使用"：下单时的同步回写失败（如营销服务不可用）时由本通道补齐。
        // 幂等接口，与同步通道重复执行无副作用。
        couponStatusSyncer.syncUsedAfterCommit(msg.getUserCouponId(), msg.getUserId(),
                msg.getCouponId(), msg.getOrderId());
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
        // 兜底回写券状态为"未使用"（幂等）
        couponStatusSyncer.syncRefundedAfterCommit(msg.getOrderId());
    }

    /** 订单是否已关闭（超时/取消；券此时应保持"未使用"，不得再核销） */
    private boolean isOrderClosed(Long orderId) {
        if (orderId == null) {
            return false;
        }
        Order order = orderMapper.selectById(orderId);
        return order != null && Integer.valueOf(OrderService.STATUS_CLOSED).equals(order.getStatus());
    }

    /**
     * 对账：按核销流水重放券状态，修复"券已核销但 zx-promotion 侧仍是未使用"的漂移。
     * <p>
     * 回写接口幂等，重复重放无副作用；仅处理最近 {@code lookbackHours} 小时内变动过的流水，
     * 避免每轮全表扫描。
     *
     * @return 本轮重放的流水条数
     */
    public int reconcile(int batch, int lookbackHours) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, lookbackHours));
        List<CouponUseRecord> records = couponUseRecordMapper.selectList(
                new LambdaQueryWrapper<CouponUseRecord>()
                        .and(w -> w.ge(CouponUseRecord::getUpdateTime, since)
                                .or().ge(CouponUseRecord::getCreateTime, since))
                        .orderByDesc(CouponUseRecord::getId)
                        .last("LIMIT " + Math.max(1, batch)));
        for (CouponUseRecord r : records) {
            if (Integer.valueOf(1).equals(r.getStatus())) {
                couponStatusSyncer.markUsed(r.getUserCouponId(), r.getUserId(),
                        r.getCouponId(), r.getOrderId());
            } else {
                couponStatusSyncer.markRefunded(r.getOrderId());
            }
        }
        return records.size();
    }
}