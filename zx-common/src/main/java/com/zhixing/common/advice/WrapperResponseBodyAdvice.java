package com.zhixing.common.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应包装：将 Controller 返回值包装为 R<T>。
 * 跳过：@NoWrapper 标注、返回类型已是 R、springdoc 文档路径。
 */
@RestControllerAdvice
public class WrapperResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public WrapperResponseBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 方法或类上标注 @NoWrapper 则不包装
        if (returnType.hasMethodAnnotation(NoWrapper.class)
                || returnType.getContainingClass().isAnnotationPresent(NoWrapper.class)) {
            return false;
        }
        // 返回类型已是 R 则不包装
        return !R.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (path != null && (path.contains("api-docs") || path.contains("swagger") || path.contains("v3/api-docs"))) {
            return body;
        }
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(R.ok(body));
            } catch (Exception e) {
                return body;
            }
        }
        return R.ok(body);
    }
}
