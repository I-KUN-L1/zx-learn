package com.zhixing.api.client.pay;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 支付服务客户端
 */
@FeignClient(value = "pay-service", contextId = "payClient")
public interface PayClient {

    @PostMapping("/pay-orders")
    Map<String, Object> applyPayOrder(@RequestBody Map<String, Object> request);
}
