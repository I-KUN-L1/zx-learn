package com.zhixing.common.exceptions;

import lombok.Getter;

/**
 * 通用业务异常基类
 */
@Getter
public class CommonException extends RuntimeException {

    private final int code;

    public CommonException(String message) {
        this(0, message);
    }

    public CommonException(int code, String message) {
        super(message);
        this.code = code;
    }

    public CommonException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    public CommonException(String message, Throwable cause) {
        this(0, message, cause);
    }

    public CommonException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
