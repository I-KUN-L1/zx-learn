package com.zhixing.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.mq.MqHandler;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 订单超时关单处理器（RocketMQ 延迟消息触发）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutHandler implements MqHandler {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> subscribeTopics() {
        return Set.of(MqTopics.TOPIC_ORDER_TIMEOUT);
    }

    @Override
    public boolean supports(String topic, String tag) {
        return MqTopics.TOPIC_ORDER_TIMEOUT.equals(topic)
                && MqTopics.Tags.ORDER_CLOSE.equals(tag);
    }

    @Override
    public void handle(MessageExt message) throws Exception {
        OrderCloseMsg closeMsg = objectMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8), OrderCloseMsg.class);
        orderService.closeExpired(closeMsg.getOrderId());
        log.info("超时关单完成：orderId={}", closeMsg.getOrderId());
    }
}
