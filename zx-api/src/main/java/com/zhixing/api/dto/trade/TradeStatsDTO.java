package com.zhixing.api.dto.trade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 交易统计聚合 DTO（内部 Feign 接口，供学情看板等消费）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeStatsDTO {

    /** 已支付订单总量 */
    private Long totalOrders;

    /** 已支付销售总额（分） */
    private Long totalSales;

    /** 近 7 日订单趋势（含销售额，缺数日期补 0） */
    private List<TrendPoint> orderTrend;

    /** 热门课程 TOP5（按已支付订单量） */
    private List<CourseCount> hotCourses;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        /** 日期（yyyy-MM-dd） */
        private String date;
        /** 订单量 */
        private Long count;
        /** 销售额（分） */
        private Long amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseCount {
        /** 课程 id */
        private Long courseId;
        /** 订单量 */
        private Long count;
    }
}
