package com.zhixing.api.client.message;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 消息服务客户端
 */
@FeignClient(value = "message-service", contextId = "messageClient")
public interface MessageClient {

    @PostMapping("/sms/message")
    void sendSms(@RequestBody Map<String, Object> message);
}
