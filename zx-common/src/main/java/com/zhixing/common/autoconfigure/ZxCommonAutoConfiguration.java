package com.zhixing.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.advice.CommonExceptionAdvice;
import com.zhixing.common.advice.ServletCommonExceptionAdvice;
import com.zhixing.common.advice.WrapperResponseBodyAdvice;
import com.zhixing.common.config.MvcConfig;
import com.zhixing.common.config.MybatisConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 公共模块自动配置
 *
 * <p>注意双栈兼容：zx-common 同时被 Servlet 服务与纯 WebFlux 服务（zx-aigc）依赖，
 * 凡引用 jakarta.servlet / spring-webmvc 的组件必须加 Servlet 环境条件，
 * 否则会在 WebFlux 类路径上因 NoClassDefFoundError 启动失败。
 */
@AutoConfiguration
@Import({MybatisConfig.class})
public class ZxCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommonExceptionAdvice commonExceptionAdvice() {
        return new CommonExceptionAdvice();
    }

    /**
     * Servlet 栈专属异常处理（404/405/缺参 400），纯 WebFlux 服务不注册。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.ServletException")
    public ServletCommonExceptionAdvice servletCommonExceptionAdvice() {
        return new ServletCommonExceptionAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice")
    public WrapperResponseBodyAdvice wrapperResponseBodyAdvice(ObjectMapper objectMapper) {
        return new WrapperResponseBodyAdvice(objectMapper);
    }

    /**
     * MVC 拦截器配置仅对 Servlet 栈生效；WebFlux 服务中 WebMvcConfigurer 回调本就不会执行，
     * 条件化注册可避免其类加载依赖 spring-webmvc。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    @Import(MvcConfig.class)
    static class ServletMvcConfiguration {
    }
}
