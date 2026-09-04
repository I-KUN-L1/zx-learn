package com.zhixing.trade.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 优惠券核销 / 退回消息（异步落库）
 */
@Data
public class CouponMsg implements Serializable {

    /** USE-核销 REFUND-退回 */
    private String action;
    private Long userId;
    private Long couponId;
    private Long userCouponId;
    private Long orderId;
    /** 抵扣金额（分） */
    private Long amount;
}