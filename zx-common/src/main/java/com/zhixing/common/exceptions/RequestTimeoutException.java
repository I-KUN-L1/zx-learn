package com.zhixing.common.exceptions;

/**
 * 请求超时异常
 */
public class RequestTimeoutException extends CommonException {
    public RequestTimeoutException(String message) {
        super(408, message);
    }
}
