package com.zhixing.aigc.config;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * WebFlux（响应式）栈的 {@code @RequireRole} 鉴权过滤器 —— 补齐 Servlet 栈
 * {@code com.zhixing.common.interceptor.RoleInterceptor} 在该服务里缺失的强制力。
 *
 * <h3>为什么会有这个类（缺陷背景）</h3>
 * 公共模块 {@code zx-common} 的 {@code MvcConfig} 注册的是 {@code WebMvcConfigurer} +
 * {@code org.springframework.web.servlet.HandlerInterceptor}，并整体挂在
 * {@code @ConditionalOnWebApplication(type = SERVLET)} 之下。而本服务为了 SSE 长连接不被
 * Tomcat 200 工作线程拖垮，pom 中显式<b>排除</b>了 {@code spring-boot-starter-web}，
 * 运行在 Netty 响应式栈上（实测 {@code NettyWebServer}）。于是：
 * <ul>
 *   <li>{@code WebMvcConfigurer} 的回调在响应式栈<b>根本不执行</b>；</li>
 *   <li>Spring WebFlux <b>压根没有 HandlerInterceptor 机制</b>（spring-webflux jar 中不存在该类），
 *       那把 Servlet 拦截器"平移"过来是行不通的——必须用 {@link WebFilter}；</li>
 *   <li>结果：{@code @RequireRole} 在本服务<b>形同虚设</b>。实测学员令牌访问
 *       {@code POST /admin/knowledge/search} 返回业务码 200 并拿到知识库切片，
 *       {@code /admin/knowledge/upload} 也可被越权写入（数据完整性风险）。</li>
 * </ul>
 *
 * <h3>判定规则（与 Servlet 栈逐条对齐）</h3>
 * <ol>
 *   <li>用 {@link RequestMappingHandlerMapping#getHandler(ServerWebExchange)} 解析目标处理方法
 *       （只解析、不执行），取方法级 {@code @RequireRole}，其次类级；</li>
 *   <li>无注解 → 放行（仅要求登录，与既有行为一致，不扩大拒绝面）；</li>
 *   <li>{@code role-info} 头缺失或非数字 → <b>fail-closed 拒绝</b>，绝不因"取不到角色"而放行；</li>
 *   <li>角色不在允许集 → 拒绝，按公共约定返回 <b>HTTP 200 + body.code=403</b>
 *       （与 {@code CommonExceptionAdvice} 输出格式一致）；</li>
 *   <li><b>路径兜底</b>：若解析不到处理方法（例如新增端点未走注解控制器、或解析异常），
 *       只要路径落在 {@code /admin/**}（本服务的管理端前缀，学员侧端点均不在其下），
 *       仍要求 员工(1)/教师(3) —— 保证新增管理端点默认<b>不裸奔</b>。</li>
 * </ol>
 *
 * <h3>为什么构造器必须用 {@code @Qualifier} 指定 bean 名</h3>
 * 响应式栈下容器里存在 <b>两个</b> {@link RequestMappingHandlerMapping} 类型的 bean：
 * <ul>
 *   <li>{@code requestMappingHandlerMapping} —— WebFlux 自动配置为业务 {@code @Controller} 注册的映射；</li>
 *   <li>{@code controllerEndpointHandlerMapping} —— Actuator 的
 *       {@code WebFluxEndpointManagementContextConfiguration} 注册，仅供 {@code /actuator/**} 端点的
 *       {@code @ControllerEndpoint} 方法使用。</li>
 * </ul>
 * 两者是并列关系、都<b>没有</b> {@code @Primary}，所以按类型注入会直接抛
 * {@code NoUniqueBeanDefinitionException}（"required a single bean, but 2 were found"）导致启动失败。
 * 本过滤器只关心业务控制器上的 {@code @RequireRole}，因此显式限定为业务映射；
 * {@code /actuator/**} 解析不到处理器时会走上面的"路径兜底"分支——它不在 {@code /admin/} 之下，原样放行，
 * 与既有暴露策略（{@code management.endpoints.web.exposure.include}）保持一致。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class RoleGuardWebFilter implements WebFilter {

    /** 与 CommonExceptionAdvice 兜底格式保持一致：HTTP 200 + 业务码 403 */
    private static final String FORBIDDEN_BODY = "{\"code\":403,\"msg\":\"无权限访问该资源\",\"data\":null}";

    /** 路径兜底规则：管理端前缀下默认要求 员工/教师 */
    private static final String ADMIN_PREFIX = "/admin/";

    /** 业务映射 bean 的固定名称，避开 Actuator 的 controllerEndpointHandlerMapping */
    private static final String BUSINESS_HANDLER_MAPPING_BEAN = "requestMappingHandlerMapping";

    private final RequestMappingHandlerMapping handlerMapping;

    public RoleGuardWebFilter(
            @Qualifier(BUSINESS_HANDLER_MAPPING_BEAN) RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        Integer role = parseRole(request.getHeaders().getFirst(Constant.ROLE_INFO_HEADER));

        return handlerMapping.getHandler(exchange)
                .flatMap(handler -> decide(exchange, chain, handler, role))
                // 解析不到处理方法（404 路由、非注解控制器等）：对管理端前缀 fail-closed
                .switchIfEmpty(Mono.defer(() -> {
                    if (path.startsWith(ADMIN_PREFIX) && !isStaffOrTeacher(role)) {
                        log.warn("[角色鉴权] 路径兜底拒绝 {} {}：role-info={}", request.getMethod(), path, role);
                        return forbid(exchange);
                    }
                    return chain.filter(exchange);
                }))
                .onErrorResume(e -> {
                    log.warn("[角色鉴权] 处理器解析异常，按管理端兜底策略处理 {} {}：{}", request.getMethod(), path, e.toString());
                    if (path.startsWith(ADMIN_PREFIX) && !isStaffOrTeacher(role)) {
                        return forbid(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    /** 命中处理方法后的判定：只看注解，无注解即放行 */
    private Mono<Void> decide(ServerWebExchange exchange, WebFilterChain chain, Object handler, Integer role) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return chain.filter(exchange);
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null || requireRole.value().length == 0) {
            return chain.filter(exchange);
        }
        boolean allowed = role != null
                && Arrays.stream(requireRole.value()).anyMatch(r -> r.getCode() == role);
        if (allowed) {
            return chain.filter(exchange);
        }
        log.warn("[角色鉴权] 拒绝 {} {}：role-info={}，允许={}",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                role, Arrays.toString(requireRole.value()));
        return forbid(exchange);
    }

    private boolean isStaffOrTeacher(Integer role) {
        return role != null && (role == 1 || role == 3);
    }

    /** 写出业务码 403 并终止链路 */
    private Mono<Void> forbid(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory()
                .wrap(FORBIDDEN_BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Integer parseRole(String roleInfo) {
        if (roleInfo == null || roleInfo.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(roleInfo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
