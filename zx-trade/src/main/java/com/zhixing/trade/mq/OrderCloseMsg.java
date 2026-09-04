package com.zhixing.trade.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单超时关单消息
 */
@Data
public class OrderCloseMsg implements Serializable {

    private Long orderId;
    private String orderNo;
}