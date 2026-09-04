package com.zhixing.promotion.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券表单
 */
@Data
public class CouponFormDTO {

    private Long id;

    /** 名称 */
    private String name;

    /** 类型：1-满减 */
    private Integer type;

    /** 面值（分） */
    private Long discountAmount;

    /** 使用门槛（分） */
    private Long thresholdAmount;

    /** 发行总量 */
    private Integer totalNum;

    /** 生效时间 */
    private LocalDateTime validBeginTime;

    /** 失效时间 */
    private LocalDateTime validEndTime;

    /** 兑换码（一次性核销，可为空） */
    private String exchangeCode;
}