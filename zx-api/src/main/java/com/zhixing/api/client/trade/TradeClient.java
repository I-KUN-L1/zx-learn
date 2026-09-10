package com.zhixing.api.client.trade;

import com.zhixing.api.dto.trade.TradeStatsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 交易服务客户端
 */
@FeignClient(value = "trade-service", contextId = "tradeClient")
public interface TradeClient {

    @GetMapping("/order-details/enrollNum")
    Integer countEnrollNum(@PathVariable("courseId") Long courseId);

    @GetMapping("/order-details/course/{id}")
    Boolean checkCourseBought(@PathVariable("id") Long courseId);

    /**
     * 交易统计聚合（已支付订单量/销售额、近 7 日趋势、热门课程 TOP5）
     */
    @GetMapping("/order-details/stats/dashboard")
    TradeStatsDTO queryTradeStats();
}
