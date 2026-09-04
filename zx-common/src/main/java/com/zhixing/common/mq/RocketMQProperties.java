package com.zhixing.common.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 连接配置（通用，供生产者/消费者使用）。
 * <p>
 * 通过 {@code rocketmq.name-server} 触发自动装配，未配置该属性的模块（如响应式 zx-aigc）不初始化 MQ。
 * 前缀与 zx-trade 原 {@code MQProperties} 对齐，确保迁移无感。
 */
@Data
@ConfigurationProperties(prefix = "rocketmq")
public class RocketMQProperties {

    /** NameServer 地址，例如 127.0.0.1:9876 */
    private String nameServer;
    /** 生产者组 */
    private String producerGroup;
    /** 消费者组 */
    private String consumerGroup;
}