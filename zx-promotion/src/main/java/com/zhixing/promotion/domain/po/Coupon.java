package com.zhixing.promotion.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 优惠券
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon")
public class Coupon extends BasePO {

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

    /** 已发放数量 */
    private Integer issuedNum;

    /** 状态：0-未开始 1-进行中 2-已结束 3-已下架 */
    private Integer status;

    /** 兑换码（一次性核销的对外领取码） */
    private String exchangeCode;

    /** 生效时间 */
    private LocalDateTime validBeginTime;

    /** 失效时间 */
    private LocalDateTime validEndTime;
}