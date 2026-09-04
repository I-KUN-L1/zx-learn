package com.zhixing.gateway.filter;

import com.zhixing.gateway.common.GatewayErrorResponse;
import com.zhixing.gateway.config.JwtProperties;
import com.zhixing.gateway.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 统一鉴权过滤器：校验 JWT 并透传用户身份
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_INFO_HEADER = "user-info";
    public static final String ROLE_INFO_HEADER = "role-info";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtProperties jwtProperties;
    private final JwtUtils jwtUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单放行
        if (isExcludePath(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(request);
        if (token == null) {
            return GatewayErrorResponse.write(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录或登录已过期");
        }
        if (!jwtUtils.isValid(token)) {
            return GatewayErrorResponse.write(exchange, HttpStatus.UNAUTHORIZED, 401, "登录凭证无效或已过期");
        }

        Long userId;
        try {
            userId = jwtUtils.parseUserId(token);
        } catch (Exception e) {
            log.warn("token 解析失败: {}", e.getMessage());
            return GatewayErrorResponse.write(exchange, HttpStatus.UNAUTHORIZED, 401, "登录凭证无效");
        }
        if (userId == null) {
            return GatewayErrorResponse.write(exchange, HttpStatus.UNAUTHORIZED, 401, "登录凭证无效");
        }

        ServerHttpRequest mutated = request.mutate()
                .header(USER_INFO_HEADER, String.valueOf(userId))
                .build();
        // 透传角色（user.type：1员工/2学员/3教师），供下游做接口级角色校验（如知识库上传的教师权限）
        Integer role = jwtUtils.parseRoleId(token);
        if (role != null) {
            mutated = mutated.mutate()
                    .header(ROLE_INFO_HEADER, String.valueOf(role))
                    .build();
        }
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isExcludePath(String path) {
        return jwtProperties.getExcludePaths().stream()
                .anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst("authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
