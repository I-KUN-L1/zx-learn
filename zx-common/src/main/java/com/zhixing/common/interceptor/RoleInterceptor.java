package com.zhixing.common.interceptor;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 接口级角色鉴权拦截器：校验 Handler 方法/类上的 @RequireRole 注解。
 * <ul>
 *   <li>无注解：放行（仅要求登录，与既有行为一致）</li>
 *   <li>有注解：UserContext 中的 role（user.type）不在允许集内 → ForbiddenException(403)</li>
 *   <li>非 HandlerMethod（静态资源等）：放行</li>
 * </ul>
 * 必须注册在 UserInfoInterceptor 之后（依赖其写入的 role）。
 */
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null || requireRole.value().length == 0) {
            return true;
        }
        Integer[] allowed = Arrays.stream(requireRole.value()).map(role -> role.getCode()).toArray(Integer[]::new);
        if (!UserContext.hasRole(allowed)) {
            throw new ForbiddenException("无权限访问该资源");
        }
        return true;
    }
}
