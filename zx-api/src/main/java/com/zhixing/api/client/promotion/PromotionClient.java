package com.zhixing.api.client.promotion;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 营销服务客户端
 */
@FeignClient(value = "promotion-service", contextId = "promotionClient")
public interface PromotionClient {

    @GetMapping("/user-coupons/rules")
    Map<Long, List<String>> queryCouponRules(@RequestParam("ids") List<Long> ids);
}
