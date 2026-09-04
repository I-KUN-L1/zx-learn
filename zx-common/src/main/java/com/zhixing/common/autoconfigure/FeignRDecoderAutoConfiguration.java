package com.zhixing.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.feign.RDecoder;
import feign.Feign;
import feign.codec.Decoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Feign 统一解码器自动装配。
 *
 * <p>在应用上下文注册 {@link RDecoder} 后，各 Feign 客户端子上下文中的
 * 默认 {@code feignDecoder}（标注 {@code @ConditionalOnMissingBean}）将退避，
 * 从而全局生效：解包 R 信封、业务错误码转为对应异常。
 * 未引入 OpenFeign 的模块（如 zx-gateway）自动跳过。
 */
@AutoConfiguration
@ConditionalOnClass(name = {"feign.codec.Decoder", "org.springframework.cloud.openfeign.FeignAutoConfiguration"})
public class FeignRDecoderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Decoder.class)
    public RDecoder rDecoder(ObjectMapper objectMapper) {
        return new RDecoder(objectMapper);
    }

    /**
     * 开启 Feign 的 decodeVoid：feign 对 void 返回方法默认不调用 Decoder
     * （{@code InvocationContext} 中 {@code isVoidType(returnType) && !decodeVoid} 直接返回 null），
     * 服务端"HTTP 200 + R 包装体"的 {@code R{code!=200}} 业务错误信封会被静默吞掉，
     * 调用方误判为成功（典型事故：首次改密传错旧密码仍返回成功并删除凭据文件）。
     * 开启后 void 响应同样经过 {@link RDecoder}，错误码转为对应异常。
     */
    @Bean
    @ConditionalOnBean(name = "rDecoder")
    public FeignBuilderCustomizer rDecoderDecodeVoidCustomizer() {
        return Feign.Builder::decodeVoid;
    }
}
