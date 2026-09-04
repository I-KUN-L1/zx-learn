package com.zhixing.common.exceptions;

/**
 * 未登录 / 未授权异常
 */
public class UnauthorizedException extends CommonException {
    public UnauthorizedException(String message) {
        super(401, message);
    }
}
