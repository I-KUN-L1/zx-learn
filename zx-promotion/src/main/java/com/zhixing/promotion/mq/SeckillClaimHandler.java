package com.zhixing.promotion.mq;

import com.zhixing.common.mq.MqHandler;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.common.utils.SnowflakeIdGenerator;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.mapper.CouponMapper;
import com.zhixing.promotion.service.IdempotencyGuard;
import com.zhixing.promotion.service.SeckillService;
import com.zhixing.promotion.service.UserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 秒杀领取消息处理器：异步落库（券码生成 + 领取记录）并写轮询结果键。
 * <p>
 * 幂等双层：消费流水表（seckill:claim:{couponId}:{userId}）+ user_coupon.uk_user_coupon；
 * 券不存在属确定性失败（写 FAILED 结果、不重投）；其余异常 RECONSUME_LATER 重投。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillClaimHandler implements MqHandler {

    private final CouponMapper couponMapper;
    private final UserCouponService userCouponService;
    private final IdempotencyGuard idempotencyGuard;
    private final SeckillService seckillService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public Set<String> subscribeTopics() {
        return Set.of(MqTopics.TOPIC_SECKILL_CLAIM);
    }

    @Override
    public boolean supports(String topic, String tag) {
        return MqTopics.TOPIC_SECKILL_CLAIM.equals(topic)
                && MqTopics.Tags.SECKILL_CLAIM.equals(tag);
    }

    @Override
    public void handle(MessageExt message) throws Exception {
        SeckillClaimMsg msg = seckillService.parseClaimMsg(
                new String(message.getBody(), StandardCharsets.UTF_8));
        Long couponId = msg.getCouponId();
        Long userId = msg.getUserId();
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            // 确定性失败：券已被删除/下架清库，重投无意义，直接写失败结果
            log.warn("秒杀领取失败，优惠券不存在：couponId={}, userId={}", couponId, userId);
            seckillService.writeResult(couponId, userId, SeckillService.STATUS_FAILED + ":优惠券不存在");
            return;
        }
        // 幂等第二层：消费流水表（同事务，失败回滚可重投）
        if (!idempotencyGuard.tryConsume("seckill:claim:" + couponId + ":" + userId,
                MqTopics.TOPIC_SECKILL_CLAIM, MqTopics.Tags.SECKILL_CLAIM)) {
            return;
        }
        String couponCode = "SK" + SnowflakeIdGenerator.getInstance().nextId();
        boolean inserted = userCouponService.claimSeckill(userId, coupon, couponCode);
        // 幂等第一层：uk_user_coupon 唯一索引，重复消息写 REPEAT 结果
        seckillService.writeResult(couponId, userId, inserted ? couponCode : SeckillService.STATUS_REPEAT);
        log.info("秒杀领取落库完成：couponId={}, userId={}, code={}, inserted={}",
                couponId, userId, couponCode, inserted);
    }
}
