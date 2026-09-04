package com.zhixing.common.mq;

import org.apache.rocketmq.common.message.MessageExt;

import java.util.Set;

/**
 * RocketMQ 消息处理器 SPI（沉淀到 zx-common，供 trade/course/learning 等消费端复用）。
 * <p>
 * 实现方注册为 Spring Bean 后，由 {@link RocketMQConsumerContainer} 自动发现并注册订阅关系；
 * 实现方需在内部做好消费幂等（消费流水表 + 业务唯一键）。
 */
public interface MqHandler {

    /** 声明需要订阅的主题集合（容器据此建立订阅关系） */
    Set<String> subscribeTopics();

    /** 是否处理该 topic+tag 的消息 */
    boolean supports(String topic, String tag);

    /** 处理消息；抛出异常将触发 RECONSUME_LATER 重投 */
    void handle(MessageExt message) throws Exception;
}
