package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 本地消息表（Outbox）：与订单同事务落库，定时任务补偿投递
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_msg")
public class OrderMsg extends BasePO {

    /** 订单 id */
    private Long orderId;

    /** 业务键（orderId:eventType），唯一防重 */
    private String bizKey;

    /** MQ 主题 */
    private String topic;

    /** 消息 tag */
    private String tag;

    /** 消息体 JSON */
    private String payload;

    /** 0-待投递 1-已投递 2-已消费 3-死信 */
    private Integer status;

    /** 已投递次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 下次投递时间 */
    private LocalDateTime nextRetryTime;
}