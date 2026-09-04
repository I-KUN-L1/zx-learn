package com.zhixing.api.client.aigc;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * AI 服务客户端
 */
@FeignClient(value = "aigc-service", contextId = "aigcClient")
public interface AigcClient {

    @PostMapping("/chat/text")
    String chatText(@RequestBody Map<String, Object> request);
}
