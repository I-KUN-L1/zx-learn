package com.zhixing.common.feign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.exceptions.UnauthorizedException;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Feign 统一响应解码器：解包 {@link R} 信封并将业务错误码转为对应异常。
 *
 * <p>背景：服务端 {@code CommonExceptionAdvice} 对业务异常返回 HTTP 200 + R 包装体
 * （如 {@code {"code":401,"msg":"..."}}），若 Feign 按声明类型直接反序列化，
 * 会得到"全字段为 null 的空对象"而非异常，导致校验形同虚设
 * （典型事故：登录接口任意密码均返回成功 token，见 docs/RELEASE-CHECK.md P0-1）。
 *
 * <p>解码规则：
 * <ul>
 *   <li>code=200：返回解包后的 {@code data}（无 data 视为 null）</li>
 *   <li>code!=200：按业务码抛出对应异常（400/401/403/404/其他），
 *       供调用方 try-catch 或 Feign fallback 处理</li>
 *   <li>非 R 信封（如 {@code @NoWrapper} 裸返回、非 JSON 体）：按声明类型直接反序列化，行为与原 SpringDecoder 兼容</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class RDecoder implements Decoder {

    private final ObjectMapper objectMapper;

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.body() == null) {
            return null;
        }
        String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
        if (body == null || body.isBlank()) {
            return null;
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            // 非 JSON 响应体（如文件流、纯文本），按声明类型直接反序列化
            return objectMapper.readValue(body, javaType);
        }
        if (root == null) {
            return null;
        }

        // 调用方明确声明返回 R<T>：不解包，整体反序列化
        Class<?> rawType = javaType.getRawClass();
        if (rawType != null && R.class.isAssignableFrom(rawType)) {
            return objectMapper.convertValue(root, javaType);
        }

        // R 信封探测：{code, msg?, data?}。错误响应可能无 data 字段，故用 msg 兜底判定；
        // code 必须为数字，避免把恰好含 code/msg 字段的业务 DTO 误判为信封
        if (root.isObject() && root.has("code") && root.get("code").isNumber()
                && (root.has("data") || root.has("msg")) && root.size() <= 4) {
            int code = root.path("code").asInt(-1);
            if (code == 200) {
                JsonNode data = root.get("data");
                if (data == null || data.isNull() || rawType == void.class || rawType == Void.class) {
                    return null;
                }
                // String 返回类型：文本节点直接取值；对象节点（如统一包装的历史行为）序列化透传
                if (rawType == String.class) {
                    return data.isTextual() ? data.asText() : objectMapper.writeValueAsString(data);
                }
                return objectMapper.convertValue(data, javaType);
            }
            String msg = root.path("msg").asText("远程服务调用失败");
            log.warn("Feign 远程调用业务错误：code={}, msg={}, targetType={}", code, msg, type.getTypeName());
            throw mapException(code, msg);
        }

        // 非 R 信封（@NoWrapper 裸返回等）：按声明类型直接反序列化
        return objectMapper.convertValue(root, javaType);
    }

    private RuntimeException mapException(int code, String msg) {
        return switch (code) {
            case 400 -> new BadRequestException(msg);
            case 401 -> new UnauthorizedException(msg);
            case 403 -> new ForbiddenException(msg);
            case 404 -> new BizIllegalException(404, msg);
            default -> new BizIllegalException(code, msg);
        };
    }
}
