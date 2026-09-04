package com.zhixing.common.exceptions;

import lombok.Getter;

/**
 * 标准错误码体系（重构：统一全局错误码，替代散落的魔法数字）
 */
@Getter
public enum ErrorCode {

    // ========== 通用 ==========
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "请求资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试"),
    GATEWAY_TIMEOUT(504, "服务调用超时"),

    // ========== 业务 ==========
    BIZ_ILLEGAL(1001, "业务操作不合法"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_EXISTS(1003, "数据已存在"),
    DB_ERROR(1004, "数据库操作异常"),
    RPC_ERROR(1005, "远程服务调用异常"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
