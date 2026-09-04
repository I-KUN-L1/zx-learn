package com.zhixing.common.exceptions;

/**
 * 数据库操作异常
 */
public class DbException extends CommonException {
    public DbException(String message) {
        super(500, message);
    }

    public DbException(String message, Throwable cause) {
        super(500, message, cause);
    }
}
