package com.zhixing.promotion.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券视图对象（对齐前端 UserCouponVO 契约）。
 * <p>
 * 前端状态契约：1-未使用 2-已使用 3-已过期；
 * 数据库 user_coupon.status 存储：0-未使用 1-已使用 2-已过期，
 * 故 VO 输出时统一 +1 完成语义对齐。
 */
@Data
public class UserCouponVO implements Serializable {

    private Long id;

    private Long userId;

    /** 优惠券模板 id */
    private Long couponId;

    /** 优惠券名称（领取时快照） */
    private String couponName;

    /** 折扣力度/面值（分）快照 */
    private Long discountValue;

    /** 使用门槛（分）快照 */
    private Long thresholdAmount;

    /** 状态：1-未使用 2-已使用 3-已过期 */
    private Integer status;

    /** 领取时间 */
    private LocalDateTime createTime;
}