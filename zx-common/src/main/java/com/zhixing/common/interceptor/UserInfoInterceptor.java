package com.zhixing.common.interceptor;

import com.zhixing.common.constants.Constant;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.common.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 解析网关透传的 user-info / role-info 请求头，写入 UserContext
 */
public class UserInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userInfo = request.getHeader(Constant.USER_INFO_HEADER);
        if (StringUtils.isNotBlank(userInfo)) {
            try {
                UserContext.setUser(Long.parseLong(userInfo));
            } catch (NumberFormatException ignored) {
                // 忽略非法 userId
            }
        }
        String roleInfo = request.getHeader(Constant.ROLE_INFO_HEADER);
        if (StringUtils.isNotBlank(roleInfo)) {
            try {
                UserContext.setRole(Integer.parseInt(roleInfo));
            } catch (NumberFormatException ignored) {
                // 忽略非法 role
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.remove();
    }
}
