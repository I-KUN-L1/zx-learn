package com.zhixing.gateway.common;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 网关统一错误响应（重构：与下游 R 结构对齐，携带 requestId 便于链路追踪）
 */
public final class GatewayErrorResponse {

    private GatewayErrorResponse() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, int code, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        response.getHeaders().set("requestId", requestId);
        String body = "{\"code\":" + code + ",\"msg\":\"" + escape(msg)
                + "\",\"requestId\":\"" + requestId + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
