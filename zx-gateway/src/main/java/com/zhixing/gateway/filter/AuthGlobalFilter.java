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

        // 白名单放行（可选鉴权：不强制登录，但带合法 token 时透传身份，见 optionalIdentity）
        if (isExcludePath(path)) {
            return chain.filter(optionalIdentity(exchange, request));
        }

        String token = resolveToken(request);
        if (token == null) {
            return GatewayErrorResponse.write(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录或登录已过期");
        }
        // 单次验签同时取出身份与角色（见 JwtUtils.parseIdentity：原来要解析三遍）
        JwtUtils.Identity identity = jwtUtils.parseIdentity(token);
        if (identity == null) {
            log.warn("token 解析失败：签名无效/已过期/载荷非法");
            return GatewayErrorResponse.write(exchange, HttpStatus.UNAUTHORIZED, 401, "登录凭证无效或已过期");
        }
        if (identity.userId() == null) {
            return GatewayErrorResponse.write(exchange, HttpStatus.UNAUTHORIZED, 401, "登录凭证无效");
        }

        ServerHttpRequest.Builder builder = request.mutate()
                .header(USER_INFO_HEADER, String.valueOf(identity.userId()));
        // 透传角色（user.type：1员工/2学员/3教师），供下游做接口级角色校验（如知识库上传的教师权限）
        if (identity.roleId() != null) {
            builder.header(ROLE_INFO_HEADER, String.valueOf(identity.roleId()));
        }
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private boolean isExcludePath(String path) {
        return jwtProperties.getExcludePaths().stream()
                .anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    /**
     * 白名单路径的「可选鉴权」。
     *
     * <p>背景：{@code /courses/page} 这类端点既要匿名可浏览（未登录只能看到已上架课程），
     * 又要允许已登录的管理端按 {@code status} 筛选未上架课程。但白名单原先直接
     * {@code chain.filter(exchange)} 放行，**从不注入 user-info** → 下游永远拿不到身份，
     * 管理端课程工作台按 status 筛选恒为空（功能性缺陷）。
     *
     * <p>⚠ 更正一处历史误判：曾把「课程工作台草稿箱标签页恒为空」也归因到这里，
     * 以为草稿是 {@code course.status = 2}。实际上草稿存在**独立的 course_draft 表**
     * （见 zx-course 的 {@code CourseService#draftPage}），与 course 表无关 ——
     * 真正的修法是让草稿箱走 {@code /courses/draft/page}。本方法只解决"白名单路径拿不到身份"。
     *
     * <p>因此：携带合法 token 时透传 {@code user-info}/{@code role-info}；未携带或不合法时
     * 按匿名处理，并**剥离**客户端自带的 {@code user-info}/{@code role-info}，
     * 防止匿名请求伪造下游信任的身份头。
     */
    private ServerWebExchange optionalIdentity(ServerWebExchange exchange, ServerHttpRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            // 单次验签（原来 isValid + parseUserId + parseRoleId 要解析三遍）
            JwtUtils.Identity identity = jwtUtils.parseIdentity(token);
            if (identity != null && identity.userId() != null) {
                ServerHttpRequest.Builder builder = request.mutate()
                        .header(USER_INFO_HEADER, String.valueOf(identity.userId()));
                if (identity.roleId() != null) {
                    builder.header(ROLE_INFO_HEADER, String.valueOf(identity.roleId()));
                }
                return exchange.mutate().request(builder.build()).build();
            }
            log.debug("白名单可选鉴权：token 不合法，按匿名处理");
        }
        // 匿名（或无合法 token）：一律剥离身份头，避免伪造
        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_INFO_HEADER);
                    headers.remove(ROLE_INFO_HEADER);
                })
                .build();
        return exchange.mutate().request(sanitized).build();
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
