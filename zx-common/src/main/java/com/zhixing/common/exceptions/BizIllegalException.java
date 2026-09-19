package com.zhixing.common.exceptions;

/**
 * 业务逻辑异常。
 * <p>
 * ⚠ 默认业务码必须是 {@link ErrorCode#BIZ_ILLEGAL}(1001)，**不能是 500**。
 * <p>
 * 历史问题：本类原先用 `super(500, message)` 作默认码，导致全项目 49 处
 * `new BizIllegalException("...")`（手机号已存在、重复下单、券不可用……）
 * 全部返回 code=500 —— 把**明确的业务拒绝**伪装成**服务端故障**：
 * <ul>
 *   <li>前端/调用方无法区分"你操作不合法"与"系统挂了"，监控把业务失败算作 5xx 告警；</li>
 *   <li>与 {@code CommonExceptionAdvice} 兜底的「系统繁忙，请稍后再试」同码，排查时互相混淆。</li>
 * </ul>
 * 统一改为 1001 后，业务拒绝与系统故障在码值上彻底区分开。
 */
public class BizIllegalException extends CommonException {

    public BizIllegalException(String message) {
        super(ErrorCode.BIZ_ILLEGAL.getCode(), message);
    }

    public BizIllegalException(int code, String message) {
        super(code, message);
    }

    /** 推荐用法：直接传语义化错误码（如 {@link ErrorCode#DATA_EXISTS}）。 */
    public BizIllegalException(ErrorCode errorCode, String message) {
        super(errorCode.getCode(), message);
    }
}
