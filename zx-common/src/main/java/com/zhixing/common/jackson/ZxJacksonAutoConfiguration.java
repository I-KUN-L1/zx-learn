package com.zhixing.common.jackson;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 全局序列化约定。
 * <p>
 * 统一把超出 JS 安全整数范围的 Long 输出为字符串，避免雪花 id 在浏览器端被浮点舍入
 * （详见 {@link SafeLongSerializer}）。
 * <p>
 * 实现方式：注册一个 {@link Module} Bean —— Spring Boot 的 Jackson 自动配置会把容器中
 * 所有 Module 装配进 ObjectMapper，Servlet 与 WebFlux 两种栈均生效，业务侧无需任何注解。
 * 服务间 MQ 报文反序列化时 Jackson 允许字符串到数值的强制转换，不影响消息兼容。
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class ZxJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "zxSafeLongModule")
    public Module zxSafeLongModule() {
        SafeLongSerializer serializer = new SafeLongSerializer();
        SimpleModule module = new SimpleModule("zx-safe-long");
        // 同时覆盖包装类型与基本类型，避免 long 字段绕过定制序列化
        module.addSerializer(Long.class, serializer);
        module.addSerializer(Long.TYPE, serializer);
        return module;
    }
}
