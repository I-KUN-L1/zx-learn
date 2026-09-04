package com.zhixing.common.exceptions;

/**
 * 业务逻辑异常
 */
public class BizIllegalException extends CommonException {
    public BizIllegalException(String message) {
        super(500, message);
    }

    public BizIllegalException(int code, String message) {
        super(code, message);
    }
}
