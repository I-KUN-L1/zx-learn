package com.zhixing.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关鉴权配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "zx.jwt")
public class JwtProperties {

    /** 与认证服务一致的签名密钥（由环境变量 ZX_JWT_SECRET 注入，见 .env.example） */
    private String secret;

    /** 白名单路径 */
    private List<String> excludePaths = List.of(
            "/accounts/login",
            "/accounts/admin/login",
            "/accounts/refresh",
            "/accounts/password/first-change",
            "/jwks",
            "/students/register",
            "/v3/api-docs",
            "/doc.html"
    );
}
