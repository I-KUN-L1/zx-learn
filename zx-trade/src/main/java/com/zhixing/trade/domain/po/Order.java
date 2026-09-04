package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order")
public class Order extends BasePO {

    /** 订单号 */
    private String orderNo;

    /** 用户 id */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课程名称快照 */
    private String courseName;

    /** 课程价格（分） */
    private Long coursePrice;

    /** 实付金额（分） */
    private Long totalFee;

    /** 使用的优惠券 id */
    private Long couponId;

    /** 优惠券抵扣金额（分） */
    private Long deduction;

    /** 状态：0-待支付 1-已支付 2-已取消 */
    private Integer status;

    /** 支付方式 */
    private Integer payType;

    /** 支付完成时间 */
    private LocalDateTime payTime;
}