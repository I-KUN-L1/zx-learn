package com.zhixing.api.client.trade;

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
}
