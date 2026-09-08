package com.zhixing.common.advice;

import com.zhixing.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Servlet（MVC）栈专属异常处理。
 *
 * <p>这里的三类异常（MissingServletRequestParameterException / HttpRequestMethodNotSupportedException /
 * NoResourceFoundException）直接或间接继承 jakarta.servlet.ServletException，
 * 在纯 WebFlux 服务（如 zx-aigc，排除 spring-boot-starter-web）的类路径上无法加载，
 * 与 CommonExceptionAdvice 混在一起会导致 @RestControllerAdvice 反射内省时抛
 * NoClassDefFoundError。故拆分出来，仅在 Servlet Web 环境注册。
 *
 * <p>必须排在 {@link CommonExceptionAdvice} 之前（@Order）：异常解析按 advice 顺序
 * 「第一个含任意匹配 handler 的 advice 胜出」，否则本类的具体 handler 会被
 * CommonExceptionAdvice 的 Exception 兜底抢先吞掉。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RestControllerAdvice
public class ServletCommonExceptionAdvice {

    /**
     * 缺失必填请求参数（如 GET 缺 query 参数）：返回 400 而非落入兜底 500。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数：{}", e.getMessage());
        return R.error(400, "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 请求资源不存在（无对应处理器/静态资源）：返回 404 而非 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("资源不存在：{}", e.getResourcePath());
        return R.error(404, "接口不存在");
    }

    /**
     * HTTP 方法不支持（如对只读端点发 DELETE）：返回 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP 方法不支持：{}", e.getMessage());
        return R.error(405, "请求方法不支持");
    }
}
