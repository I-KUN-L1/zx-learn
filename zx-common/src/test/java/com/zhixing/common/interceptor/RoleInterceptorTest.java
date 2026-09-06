package com.zhixing.common.interceptor;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.exceptions.CommonException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 角色鉴权拦截器的单元测试：
 * 校验 @RequireRole 的方法级/类级解析、方法级覆盖类级、空注解放行、非 HandlerMethod 放行等分支
 */
class RoleInterceptorTest {

    private final RoleInterceptor interceptor = new RoleInterceptor();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    /** 无任何注解的控制器 */
    static class PlainController {
        public void open() { }
    }

    /** 方法级注解的控制器 */
    static class MethodAnnotatedController {
        @RequireRole(UserRole.STAFF)
        public void staffOnly() { }

        @RequireRole({UserRole.STAFF, UserRole.TEACHER})
        public void staffOrTeacher() { }
    }

    /** 类级注解的控制器（含方法级覆盖场景） */
    @RequireRole(UserRole.STAFF)
    static class ClassAnnotatedController {
        public void inherited() { }

        @RequireRole(UserRole.STUDENT)
        public void studentOnly() { }
    }

    /** 类级空值注解：应放行 */
    @RequireRole({ })
    static class EmptyValueController {
        public void any() { }
    }

    @BeforeEach
    void setUp() {
        UserContext.remove();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private HandlerMethod handler(Class<?> beanType, String methodName) throws Exception {
        Object bean = beanType.getDeclaredConstructor().newInstance();
        Method method = beanType.getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    @Test
    @DisplayName("非 HandlerMethod（如静态资源处理器）直接放行")
    void non_handler_method_passes() {
        assertDoesNotThrow(() -> assertTrue(interceptor.preHandle(request, response, new Object())));
    }

    @Test
    @DisplayName("方法与类均无注解：仅要求登录，放行")
    void no_annotation_passes() throws Exception {
        assertTrue(interceptor.preHandle(request, response, handler(PlainController.class, "open")));
    }

    @Test
    @DisplayName("方法有注解但未设置角色（网关未透传 role-info）：403")
    void annotated_method_without_role_throws_403() throws Exception {
        HandlerMethod hm = handler(MethodAnnotatedController.class, "staffOnly");
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> interceptor.preHandle(request, response, hm));
        assertEquals(403, ((CommonException) ex).getCode());
        assertEquals("无权限访问该资源", ex.getMessage());
    }

    @Test
    @DisplayName("方法有注解且角色命中：放行")
    void annotated_method_role_match_passes() throws Exception {
        UserContext.setRole(UserRole.STAFF.getCode());
        HandlerMethod hm = handler(MethodAnnotatedController.class, "staffOnly");
        assertTrue(interceptor.preHandle(request, response, hm));
    }

    @Test
    @DisplayName("方法有注解且角色未命中：403")
    void annotated_method_role_mismatch_throws_403() throws Exception {
        UserContext.setRole(UserRole.STUDENT.getCode());
        HandlerMethod hm = handler(MethodAnnotatedController.class, "staffOnly");
        assertThrows(ForbiddenException.class, () -> interceptor.preHandle(request, response, hm));
    }

    @Test
    @DisplayName("多角色注解：任一命中即放行")
    void annotated_method_any_of_roles_passes() throws Exception {
        UserContext.setRole(UserRole.TEACHER.getCode());
        HandlerMethod hm = handler(MethodAnnotatedController.class, "staffOrTeacher");
        assertTrue(interceptor.preHandle(request, response, hm));
    }

    @Test
    @DisplayName("类级注解：角色命中放行，未命中 403")
    void class_level_annotation_enforced() throws Exception {
        UserContext.setRole(UserRole.STAFF.getCode());
        assertTrue(interceptor.preHandle(request, response, handler(ClassAnnotatedController.class, "inherited")));

        UserContext.setRole(UserRole.STUDENT.getCode());
        HandlerMethod hm = handler(ClassAnnotatedController.class, "inherited");
        assertThrows(ForbiddenException.class, () -> interceptor.preHandle(request, response, hm));
    }

    @Test
    @DisplayName("方法级注解覆盖类级注解：以方法级为准")
    void method_annotation_overrides_class_annotation() throws Exception {
        UserContext.setRole(UserRole.STUDENT.getCode());
        assertTrue(interceptor.preHandle(request, response, handler(ClassAnnotatedController.class, "studentOnly")));

        UserContext.setRole(UserRole.STAFF.getCode());
        HandlerMethod hm = handler(ClassAnnotatedController.class, "studentOnly");
        assertThrows(ForbiddenException.class, () -> interceptor.preHandle(request, response, hm));
    }

    @Test
    @DisplayName("注解存在但 value 为空数组：视为不限制，放行")
    void empty_value_annotation_passes() throws Exception {
        UserContext.setRole(UserRole.STUDENT.getCode());
        assertTrue(interceptor.preHandle(request, response, handler(EmptyValueController.class, "any")));
    }
}
