package com.zhixing.api.client.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 认证服务客户端
 */
@FeignClient(value = "auth-service", contextId = "authClient")
public interface AuthClient {

    @GetMapping("/jwks")
    String getJwk();
}
