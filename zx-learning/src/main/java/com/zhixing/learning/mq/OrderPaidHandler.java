package com.zhixing.learning.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.dto.trade.OrderPaidMsg;
import com.zhixing.common.mq.MqHandler;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.learning.service.IdempotencyGuard;
import com.zhixing.learning.service.LessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 订单支付成功事件处理器：为用户开通课程（写入课表）。
 * <p>
 * 幂等：消费流水表（lesson:paid:{orderId}）+ lesson.uk_user_course 唯一索引双保险，
 * MQ 重复投递不会产生重复课表项。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidHandler implements MqHandler {

    private final LessonService lessonService;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> subscribeTopics() {
        return Set.of(MqTopics.TOPIC_ORDER_PAID);
    }

    @Override
    public boolean supports(String topic, String tag) {
        return MqTopics.TOPIC_ORDER_PAID.equals(topic)
                && MqTopics.Tags.ORDER_PAID.equals(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(MessageExt message) throws Exception {
        OrderPaidMsg msg = objectMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8), OrderPaidMsg.class);
        // 幂等第一层：消费流水（与开课同事务，失败一并回滚，重投可重新处理）
        if (!idempotencyGuard.tryConsume("lesson:paid:" + msg.getOrderId(),
                MqTopics.TOPIC_ORDER_PAID, MqTopics.Tags.ORDER_PAID)) {
            return;
        }
        // 幂等第二层：uk_user_course 唯一索引
        lessonService.enroll(msg.getUserId(), msg.getCourseId(), msg.getCourseName());
    }
}
