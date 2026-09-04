package com.zhixing.promotion.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户优惠券（领取后的券，带状态机：未使用 -> 已使用 / 已过期）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_coupon")
public class UserCoupon extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 优惠券 id */
    private Long couponId;

    /** 优惠券名称快照 */
    private String couponName;

    /** 面值（分）快照 */
    private Long discountAmount;

    /** 使用门槛（分）快照 */
    private Long thresholdAmount;

    /** 状态：0-未使用 1-已使用 2-已过期 */
    private Integer status;

    /** 生效时间 */
    private LocalDateTime validBeginTime;

    /** 失效时间 */
    private LocalDateTime validEndTime;

    /** 使用时间 */
    private LocalDateTime useTime;

    /** 使用订单 id */
    private Long orderId;

    /** 券码（秒杀领取时异步生成的唯一核销码） */
    private String couponCode;
}