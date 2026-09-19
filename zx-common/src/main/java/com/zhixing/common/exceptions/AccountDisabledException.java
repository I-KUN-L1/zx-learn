package com.zhixing.common.exceptions;

/**
 * 账号被禁用异常。
 * <p>
 * 与 {@link UnauthorizedException} 区分开：凭据错误归 401（前端仅提示"用户名或密码错误"），
 * 而账号被管理员禁用需要给用户明确的处置指引（"请联系管理员"），
 * 因此单列业务码 {@link ErrorCode#ACCOUNT_DISABLED}(423)，
 * 前端据此弹出专属提示弹窗，而不是泛化的登录失败提示。
 */
public class AccountDisabledException extends CommonException {

    public AccountDisabledException(String message) {
        super(ErrorCode.ACCOUNT_DISABLED.getCode(), message);
    }

    public AccountDisabledException() {
        this(ErrorCode.ACCOUNT_DISABLED.getMessage());
    }
}
