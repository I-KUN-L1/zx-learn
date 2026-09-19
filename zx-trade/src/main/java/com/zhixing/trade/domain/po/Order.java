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

    /**
     * 学员侧删除标记：0 未删除 / 1 学员已删除。
     * <p>
     * 刻意不复用 {@code BasePO.deleted}（MyBatis-Plus @TableLogic）：逻辑删除会让
     * 管理端也查不到，而需求要求「用户端删除后管理端仍保留记录」。因此单独一列，
     * 只作用于学员端"我的订单"查询。
     */
    private Integer userDeleted;

    /** 支付方式 */
    private Integer payType;

    /** 支付完成时间 */
    private LocalDateTime payTime;
}