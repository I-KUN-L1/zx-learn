package com.zhixing.common.exceptions;

/**
 * 无权限访问异常
 */
public class ForbiddenException extends CommonException {
    public ForbiddenException(String message) {
        super(403, message);
    }
}
