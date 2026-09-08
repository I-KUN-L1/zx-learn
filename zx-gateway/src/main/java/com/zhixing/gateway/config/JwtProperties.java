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
            "/teachers/register",
            "/v3/api-docs",
            "/doc.html",
            // 公共浏览接口：匿名可访问（自由浏览体验）；管理端写操作由后端 @RequireRole fail-closed 兜底
            "/courses/page",
            // 课程详情仅放行数字 id，避免放行 /courses/upShelf 等管理端点（AntPathMatcher 不校验 HTTP method）
            "/courses/{id:\\d+}",
            "/categorys/all",
            "/coupons/page",
            "/order-details/enrollNum",
            // 图片公开访问（上传 POST /files 不在白名单，仍需员工/教师鉴权）
            "/files/view/**"
    );
}
