package com.zhixing.trade.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象（对齐前端 OrderVO 契约：zx-web/src/types/api.ts）。
 * <p>
 * 字段一一对应：orderNo/totalAmount/realAmount/discountAmount/status/details。
 * 状态语义为前端契约（1-待支付 2-已支付 3-已关闭 5-退款中 6-已退款），
 * 数据库存储状态（0-待支付 1-已支付 2-已取消 3-退款中 4-已退款）由 VO 组装时统一映射。
 */
@Data
public class OrderVO implements Serializable {

    private Long id;

    /** 订单号（雪花） */
    private String orderNo;

    private Long userId;

    /** 订单总额（分，订单课程原价合计） */
    private Long totalAmount;

    /** 实付金额（分） */
    private Long realAmount;

    /** 优惠抵扣金额（分） */
    private Long discountAmount;

    /** 使用的优惠券模板 id */
    private Long couponId;

    /** 状态：1-待支付 2-已支付 3-已关闭 5-退款中 6-已退款 */
    private Integer status;

    private LocalDateTime createTime;

    /** 支付完成时间 */
    private LocalDateTime payTime;

    /** 订单明细（单课程订单为一条） */
    private List<OrderItemVO> details;

    /**
     * 订单明细条目（对齐前端 OrderDetailVO：courseId/courseName/coverUrl/price）
     */
    @Data
    public static class OrderItemVO implements Serializable {

        private Long id;

        private Long orderId;

        private Long courseId;

        /** 课程名称快照 */
        private String courseName;

        /** 课程封面（实时查询补充） */
        private String coverUrl;

        /** 课程价格（分） */
        private Long price;
    }
}
