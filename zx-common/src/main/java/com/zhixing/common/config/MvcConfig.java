package com.zhixing.common.config;

import com.zhixing.common.interceptor.RequestIdInterceptor;
import com.zhixing.common.interceptor.UserInfoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：注册链路追踪与用户信息拦截器
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestIdInterceptor()).addPathPatterns("/**");
        registry.addInterceptor(new UserInfoInterceptor()).addPathPatterns("/**");
    }
}
