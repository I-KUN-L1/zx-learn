package com.zhixing.common.exceptions;

/**
 * 请求参数错误异常
 */
public class BadRequestException extends CommonException {
    public BadRequestException(String message) {
        super(400, message);
    }
}
