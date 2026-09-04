package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 优惠券核销流水：MQ 异步落库，order_id 唯一保证一单只核销一次
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon_use_record")
public class CouponUseRecord extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 优惠券 id */
    private Long couponId;

    /** 用户券 id */
    private Long userCouponId;

    /** 使用订单 id */
    private Long orderId;

    /** 抵扣金额（分） */
    private Long amount;

    /** 1-已核销 0-已退回 */
    private Integer status;
}