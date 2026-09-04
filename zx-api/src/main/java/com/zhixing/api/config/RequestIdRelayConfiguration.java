package com.zhixing.api.config;

import feign.RequestInterceptor;
import com.zhixing.common.constants.Constant;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Feign 调用透传 requestId 请求头。
 * 通过 spring.factories/AutoConfiguration.imports 自动注册，避免业务模块重复配置。
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class RequestIdRelayConfiguration {

    @Bean
    public RequestInterceptor requestIdRelayInterceptor() {
        return template -> {
            String requestId = MDC.get(Constant.REQUEST_ID_HEADER);
            if (requestId != null) {
                template.header(Constant.REQUEST_ID_HEADER, requestId);
            }
        };
    }
}
