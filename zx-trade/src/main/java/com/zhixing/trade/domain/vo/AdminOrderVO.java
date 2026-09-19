package com.zhixing.trade.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员端订单 VO：订单全字段 + 下单用户信息（管理端需要看到"谁下的单"）。
 */
@Data
public class AdminOrderVO implements Serializable {

    private Long id;

    private String orderNo;

    private Long userId;

    /** 下单用户名（Feign 补全，失败降级为「用户 #id」） */
    private String username;

    private String cellPhone;

    private Long courseId;

    private String courseName;

    /** 课程原价（分） */
    private Long coursePrice;

    /** 实付金额（分） */
    private Long totalFee;

    private Long couponId;

    /** 优惠券抵扣（分） */
    private Long deduction;

    /** 数据库状态：0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款 */
    private Integer status;

    /** 学员侧是否已删除（1=已从"我的订单"移除；管理端仍保留并可查） */
    private Integer userDeleted;

    private Integer payType;

    private LocalDateTime payTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
