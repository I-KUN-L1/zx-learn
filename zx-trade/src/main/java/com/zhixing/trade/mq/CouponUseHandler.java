package com.zhixing.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.mq.MqHandler;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.trade.service.CouponUseRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 优惠券核销 / 退回消息处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponUseHandler implements MqHandler {

    private final CouponUseRecordService couponUseRecordService;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> subscribeTopics() {
        return Set.of(MqTopics.TOPIC_COUPON_USE);
    }

    @Override
    public boolean supports(String topic, String tag) {
        return MqTopics.TOPIC_COUPON_USE.equals(topic);
    }

    @Override
    public void handle(MessageExt message) throws Exception {
        CouponMsg couponMsg = objectMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8), CouponMsg.class);
        if (MqTopics.Tags.COUPON_USE.equals(message.getTags())) {
            couponUseRecordService.submitUse(couponMsg);
        } else if (MqTopics.Tags.COUPON_REFUND.equals(message.getTags())) {
            couponUseRecordService.submitRefund(couponMsg);
        } else {
            log.warn("未知优惠券消息 tag：{}", message.getTags());
        }
    }
}
