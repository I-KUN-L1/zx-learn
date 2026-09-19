package com.zhixing.trade.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员端订单统计（各状态订单量 + 已支付销售额）。
 */
@Data
public class AdminOrderStatsVO implements Serializable {

    private long totalCount;

    /** 待支付 */
    private long unpaidCount;

    /** 已支付 */
    private long paidCount;

    /** 已关闭 */
    private long closedCount;

    /** 退款中 */
    private long refundingCount;

    /** 已退款 */
    private long refundedCount;

    /** 已支付订单销售额（分） */
    private long totalSales;
}
