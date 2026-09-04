package com.zhixing.common.advice;

import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理
 */
@Slf4j
@RestControllerAdvice
public class CommonExceptionAdvice {

    @ExceptionHandler(UnauthorizedException.class)
    public R<Void> handleUnauthorized(UnauthorizedException e) {
        log.warn("未授权：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public R<Void> handleForbidden(ForbiddenException e) {
        log.warn("无权限：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public R<Void> handleBadRequest(BadRequestException e) {
        log.warn("参数错误：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BizIllegalException.class)
    public R<Void> handleBizIllegal(BizIllegalException e) {
        log.warn("业务异常：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DbException.class)
    public R<Void> handleDb(DbException e) {
        log.error("数据库异常：{}", e.getMessage(), e);
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RequestTimeoutException.class)
    public R<Void> handleTimeout(RequestTimeoutException e) {
        log.warn("请求超时：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(CommonException.class)
    public R<Void> handleCommon(CommonException e) {
        log.warn("通用异常：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = getFirstMessage(e);
        log.warn("参数校验失败：{}", msg);
        return R.error(400, msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = getFirstMessage(e);
        log.warn("参数绑定失败：{}", msg);
        return R.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.error(500, "系统繁忙，请稍后再试");
    }

    private String getFirstMessage(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        return fieldError == null ? "参数错误" : fieldError.getDefaultMessage();
    }
}
