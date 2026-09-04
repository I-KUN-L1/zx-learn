package com.zhixing.trade.domain.dto;

import lombok.Data;

/**
 * 下单 / 支付回调表单
 */
@Data
public class OrderFormDTO {

    /** 订单 id（支付回调使用） */
    private Long id;

    /** 课程 id */
    private Long courseId;

    /** 实付金额（分） */
    private Long totalFee;

    /** 支付方式：1-微信 2-支付宝 3-余额 */
    private Integer payType;

    // ========== 使用优惠券下单 ==========

    /** 优惠券 id（在 promotion-service 中定义的优惠券模板） */
    private Long couponId;

    /** 用户券 id（用户领取到的具体一张券） */
    private Long userCouponId;

    // ========== 支付回调（Mock 渠道） ==========

    /** 渠道交易流水号 */
    private String payNo;

    /** 回调签名 */
    private String sign;

    /** 回调原始报文 */
    private String raw;
}