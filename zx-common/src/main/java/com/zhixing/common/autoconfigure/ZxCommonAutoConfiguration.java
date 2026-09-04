package com.zhixing.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.advice.CommonExceptionAdvice;
import com.zhixing.common.advice.WrapperResponseBodyAdvice;
import com.zhixing.common.config.MvcConfig;
import com.zhixing.common.config.MybatisConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 公共模块自动配置
 */
@AutoConfiguration
@Import({MvcConfig.class, MybatisConfig.class})
public class ZxCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommonExceptionAdvice commonExceptionAdvice() {
        return new CommonExceptionAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice")
    public WrapperResponseBodyAdvice wrapperResponseBodyAdvice(ObjectMapper objectMapper) {
        return new WrapperResponseBodyAdvice(objectMapper);
    }
}
