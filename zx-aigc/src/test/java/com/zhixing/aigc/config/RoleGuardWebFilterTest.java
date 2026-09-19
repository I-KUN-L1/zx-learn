package com.zhixing.aigc.config;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.Constant;
import com.zhixing.common.constants.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link RoleGuardWebFilter} 单测：锁住响应式栈下 {@code @RequireRole} 的强制力。
 *
 * <p>每条"拒绝"断言都配了"放行"对照；若过滤器退回"永远放行"（= 修复前 Netty 栈上的真实状态），
 * 拒绝类断言立即失败。响应体与状态码用 Mockito 捕获真实写入内容断言，
 * 不依赖 {@code MockServerWebExchange} 的响应实现细节（其响应头为只读包装，与真实 Netty 不同）。
 */
class RoleGuardWebFilterTest {

    private final Fixture bean = new Fixture();

    /** 被测端点样本：方法级注解 / 类级注解 / 无注解 */
    static class Fixture {

        @RequireRole(UserRole.STAFF)
        public void staffOnly() {
        }

        @RequireRole({UserRole.STAFF, UserRole.TEACHER})
        public void staffOrTeacher() {
        }

        public void studentFacing() {
        }
    }

    @RequireRole(UserRole.STAFF)
    static class StaffOnlyController {
        public void anyMethod() {
        }
    }

    /** 测试脚手架：捕获响应状态码与响应体 */
    private record Harness(RoleGuardWebFilter filter, ServerWebExchange exchange,
                           WebFilterChain chain, AtomicBoolean passed,
                           AtomicReference<HttpStatus> status, AtomicReference<String> body) {
    }

    private Harness harness(String path, String roleInfo, HandlerMethod handlerMethod) {
        RequestMappingHandlerMapping mapping = Mockito.mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandler(any())).thenReturn(handlerMethod == null ? Mono.empty() : Mono.just(handlerMethod));

        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post(path);
        if (roleInfo != null) {
            builder = builder.header(Constant.ROLE_INFO_HEADER, roleInfo);
        }

        AtomicReference<HttpStatus> status = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        org.springframework.http.server.reactive.ServerHttpResponse response =
                Mockito.mock(org.springframework.http.server.reactive.ServerHttpResponse.class);
        when(response.bufferFactory()).thenReturn(DefaultDataBufferFactory.sharedInstance);
        when(response.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
        when(response.writeWith(any())).thenAnswer(inv -> {
            org.reactivestreams.Publisher<? extends DataBuffer> publisher = inv.getArgument(0);
            DataBuffer buf = Flux.from(publisher).blockFirst();
            if (buf != null) {
                body.set(buf.toString(StandardCharsets.UTF_8));
            }
            return Mono.empty();
        });
        Mockito.doAnswer(inv -> {
            status.set(inv.getArgument(0));
            return null;
        }).when(response).setStatusCode(any());

        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(builder.build());
        when(exchange.getResponse()).thenReturn(response);

        AtomicBoolean passed = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            passed.set(true);
            return Mono.empty();
        };
        return new Harness(new RoleGuardWebFilter(mapping), exchange, chain, passed, status, body);
    }

    private HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(bean, Fixture.class.getMethod(method));
    }

    // ---------------- 放行组（对照组） ----------------

    @Test
    @DisplayName("员工令牌访问 @RequireRole(STAFF) → 放行且不写响应")
    void staffIsAllowed() throws Exception {
        Harness h = harness("/admin/knowledge/search", String.valueOf(UserRole.STAFF.getCode()), handler("staffOnly"));
        h.filter().filter(h.exchange(), h.chain()).block();
        assertTrue(h.passed().get(), "员工应被放行");
        assertNull(h.status().get(), "放行时不应写状态码");
        assertNull(h.body().get(), "放行时不应写响应体");
    }

    @Test
    @DisplayName("教师令牌访问 @RequireRole(STAFF,TEACHER) → 放行（未误伤教师）")
    void teacherIsAllowed() throws Exception {
        Harness h = harness("/admin/knowledge/upload", String.valueOf(UserRole.TEACHER.getCode()), handler("staffOrTeacher"));
        h.filter().filter(h.exchange(), h.chain()).block();
        assertTrue(h.passed().get());
    }

    @Test
    @DisplayName("无 @RequireRole 的学员侧端点不被误伤（对照组）")
    void studentFacingEndpointNotAffected() throws Exception {
        Harness h = harness("/session/hot", String.valueOf(UserRole.STUDENT.getCode()), handler("studentFacing"));
        h.filter().filter(h.exchange(), h.chain()).block();
        assertTrue(h.passed().get(), "无注解端点必须放行，否则会把学员功能一起挡住");
    }

    // ---------------- 阻断组（缺陷复现即失败） ----------------

    @Test
    @DisplayName("学员令牌访问 @RequireRole(STAFF) → 阻断，HTTP 200 + body.code=403（修复前恒放行）")
    void studentIsBlocked() throws Exception {
        Harness h = harness("/admin/knowledge/search", String.valueOf(UserRole.STUDENT.getCode()), handler("staffOnly"));
        h.filter().filter(h.exchange(), h.chain()).block();
        assertFalse(h.passed().get(), "学员不得访问管理端知识库接口");
        assertEquals(HttpStatus.OK, h.status().get(), "业务异常应保持 HTTP 200");
        assertNotNull(h.body().get());
        assertTrue(h.body().get().contains("\"code\":403"), "响应体必须为业务码 403，实际=" + h.body().get());
    }

    @Test
    @DisplayName("类级 @RequireRole 对未标注的方法同样生效")
    void classLevelAnnotationEnforced() throws Exception {
        HandlerMethod hm = new HandlerMethod(new StaffOnlyController(),
                StaffOnlyController.class.getMethod("anyMethod"));
        Harness h = harness("/admin/x", String.valueOf(UserRole.STUDENT.getCode()), hm);
        h.filter().filter(h.exchange(), h.chain()).block();
        assertFalse(h.passed().get(), "类级注解必须生效");
        assertTrue(h.body().get().contains("\"code\":403"));
    }

    @Test
    @DisplayName("role-info 缺失 → fail-closed 阻断（不因取不到角色而放行）")
    void missingRoleHeaderIsBlocked() throws Exception {
        Harness h = harness("/admin/knowledge/upload", null, handler("staffOnly"));
        h.filter().filter(h.exchange(), h.chain()).block();
        assertFalse(h.passed().get(), "缺角色头时必须拒绝");
        assertTrue(h.body().get().contains("\"code\":403"));
    }

    @Test
    @DisplayName("role-info 非数字 → fail-closed 阻断")
    void invalidRoleHeaderIsBlocked() throws Exception {
        Harness h = harness("/admin/knowledge/upload", "admin", handler("staffOnly"));
        h.filter().filter(h.exchange(), h.chain()).block();
        assertFalse(h.passed().get());
        assertTrue(h.body().get().contains("\"code\":403"));
    }

    @Test
    @DisplayName("路径兜底：解析不到处理器且路径在 /admin/ 下 → 学员阻断（新增管理端点默认不裸奔）")
    void adminPathFallbackBlocksStudent() {
        Harness h = harness("/admin/knowledge/some-new-endpoint",
                String.valueOf(UserRole.STUDENT.getCode()), null);
        h.filter().filter(h.exchange(), h.chain()).block();
        assertFalse(h.passed().get(), "/admin/ 下未解析到处理器也必须对学员 fail-closed");
        assertTrue(h.body().get().contains("\"code\":403"));
    }

    @Test
    @DisplayName("路径兜底不误伤：解析不到处理器但路径非 /admin/ → 放行（交由后续 404 处理）")
    void nonAdminPathFallbackAllows() {
        Harness h = harness("/session/not-exist", String.valueOf(UserRole.STUDENT.getCode()), null);
        h.filter().filter(h.exchange(), h.chain()).block();
        assertTrue(h.passed().get(), "非管理端路径不应被角色过滤器拦截");
    }
}
