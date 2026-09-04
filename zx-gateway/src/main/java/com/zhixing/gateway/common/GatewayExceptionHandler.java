package com.zhixing.gateway.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局异常处理（重构：路由失败/超时返回统一结构）
 */
@Slf4j
@Order(-1)
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        int code;
        String msg;
        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            code = status.value();
            msg = status.value() == 503 ? "目标服务不可用" : "网关转发失败";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = 500;
            msg = "网关内部异常";
        }
        log.error("网关异常 path={}, status={}", exchange.getRequest().getURI().getPath(), status, ex);
        return GatewayErrorResponse.write(exchange, status, code, msg);
    }
}
