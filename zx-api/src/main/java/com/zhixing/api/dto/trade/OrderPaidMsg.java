package com.zhixing.api.dto.trade;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单支付成功事件报文（topic: zx_order_paid）。
 * <p>zx-trade 发布，zx-learning 消费后为用户开通课程（写入课表）。</p>
 */
@Data
public class OrderPaidMsg implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long courseId;
    /** 课程名称快照（开课时冗余写入课表，避免二次远程查询） */
    private String courseName;
    private Long amount;
    private Integer payType;
    private String payNo;
}
