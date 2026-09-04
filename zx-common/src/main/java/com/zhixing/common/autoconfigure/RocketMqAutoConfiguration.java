package com.zhixing.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.mq.MqHandler;
import com.zhixing.common.mq.RocketMQConsumerContainer;
import com.zhixing.common.mq.RocketMQProperties;
import com.zhixing.common.mq.RocketMQTemplate;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 通用生产者 / 消费者 / 属性自动装配。
 * <p>
 * 仅在配置了 {@code rocketmq.name-server} 的模块生效（如 zx-trade）；
 * 未配置的模块（如响应式 zx-aigc）不会初始化 MQ，避免额外连接与依赖。
 * <ul>
 *   <li>生产者：配置 name-server 即装配 {@link RocketMQTemplate}；</li>
 *   <li>消费者：存在 {@link MqHandler} Bean 且配置了 rocketmq.consumer-group 时，
 *       装配 {@link RocketMQConsumerContainer}（各服务须配置不同的 consumer-group）。</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(RocketMQProperties.class)
@ConditionalOnClass(DefaultMQProducer.class)
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class RocketMqAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RocketMQTemplate rocketMQTemplate(RocketMQProperties properties, ObjectMapper objectMapper) {
        RocketMQTemplate template = new RocketMQTemplate(properties, objectMapper);
        template.start();
        return template;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "rocketmq", name = "consumer-group")
    public RocketMQConsumerContainer rocketMQConsumerContainer(RocketMQProperties properties,
                                                               ObjectProvider<MqHandler> handlers) {
        RocketMQConsumerContainer container = new RocketMQConsumerContainer(properties, handlers.stream().toList());
        container.start();
        return container;
    }
}