package com.zhixing.common.advice;

import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一异常处理
 */
@Slf4j
@RestControllerAdvice
public class CommonExceptionAdvice {

    /** 从 MySQL 约束错误消息中提取列名，如 "Field 'name'..." / "Data too long for column 'cover_url'" */
    private static final Pattern COLUMN_PATTERN =
            Pattern.compile("(?:Field|column) '([^']+)'", Pattern.CASE_INSENSITIVE);

    @ExceptionHandler(UnauthorizedException.class)
    public R<Void> handleUnauthorized(UnauthorizedException e) {
        log.warn("未授权：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    /**
     * 账号被禁用：返回业务码 423（HTTP 仍为 200，前端读 body.code 弹"请联系管理员"）。
     * 必须排在 CommonException 兜底之前单独声明，保证专属码不被泛化。
     */
    @ExceptionHandler(AccountDisabledException.class)
    public R<Void> handleAccountDisabled(AccountDisabledException e) {
        log.warn("账号已禁用：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public R<Void> handleForbidden(ForbiddenException e) {
        log.warn("无权限：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public R<Void> handleBadRequest(BadRequestException e) {
        log.warn("参数错误：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BizIllegalException.class)
    public R<Void> handleBizIllegal(BizIllegalException e) {
        log.warn("业务异常：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DbException.class)
    public R<Void> handleDb(DbException e) {
        log.error("数据库异常：{}", e.getMessage(), e);
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RequestTimeoutException.class)
    public R<Void> handleTimeout(RequestTimeoutException e) {
        log.warn("请求超时：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(CommonException.class)
    public R<Void> handleCommon(CommonException e) {
        log.warn("通用异常：{}", e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = getFirstMessage(e);
        log.warn("参数校验失败：{}", msg);
        return R.error(400, msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = getFirstMessage(e);
        log.warn("参数绑定失败：{}", msg);
        return R.error(400, msg);
    }

    /**
     * 请求体不可读（JSON 格式错误/类型不匹配）：返回 400。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return R.error(400, "请求体格式错误");
    }

    /**
     * 参数类型不匹配（如路径参数应为数字却传入字符串）：返回 400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getMessage());
        return R.error(400, "参数类型不匹配：" + e.getName());
    }

    /**
     * ResponseStatusException：位于 spring-web，Servlet/WebFlux 双栈通用。
     * WebFlux 栈的未匹配路由（reactive NoResourceFoundException 为其子类）与
     * 方法不支持（MethodNotAllowedException）按原始状态码映射，避免落入兜底 500；
     * Servlet 栈的 NoResourceFoundException 继承 ServletException，
     * 由 ServletCommonExceptionAdvice（更高 @Order）优先处理，互不干扰。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public R<Void> handleResponseStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String msg = switch (status) {
            case 404 -> "接口不存在";
            case 405 -> "请求方法不支持";
            default -> e.getReason() != null ? e.getReason() : "请求处理失败：" + status;
        };
        log.warn("请求状态异常 {}: {}", status, e.getMessage());
        return R.error(status, msg);
    }

    /**
     * 数据完整性约束失败（必填列缺失 / 唯一键冲突 / 字段超长）。
     * <p>
     * 这类错误本质是**调用方数据问题**，不是服务端故障：例如 menu.name 为
     * NOT NULL 无默认值时，MyBatis-Plus 会跳过 null 字段、生成不含 name 列的
     * INSERT，数据库报「Field 'name' doesn't have a default value」。
     * 原先一律落入兜底返回 500「系统繁忙，请稍后再试」——把"你漏填必填项"
     * 说成"系统挂了"，既误导用户也让定位困难。这里映射为 400 并给出可读原因。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public R<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage() : e.getMessage();
        log.warn("数据完整性约束失败：{}", cause);
        return R.error(400, describeIntegrity(cause));
    }

    /**
     * 非 multipart 请求打到文件上传接口：返回 400，而不是 500「系统繁忙」。
     */
    @ExceptionHandler(MultipartException.class)
    public R<Void> handleMultipart(MultipartException e) {
        log.warn("文件上传请求格式错误：{}", e.getMessage());
        return R.error(400, "请以 multipart/form-data 方式上传文件");
    }

    private String describeIntegrity(String cause) {
        if (cause == null) {
            return "数据不完整或违反约束，请检查必填项";
        }
        if (cause.contains("doesn't have a default value")) {
            Matcher m = COLUMN_PATTERN.matcher(cause);
            return m.find() ? "缺少必填字段：" + m.group(1) : "缺少必填字段";
        }
        if (cause.contains("Data too long")) {
            Matcher m = COLUMN_PATTERN.matcher(cause);
            return m.find() ? "字段内容超长：" + m.group(1) : "字段内容超长";
        }
        if (cause.contains("Duplicate entry")) {
            return "数据已存在，请勿重复提交";
        }
        return "数据不完整或违反约束，请检查必填项";
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.error(500, "系统繁忙，请稍后再试");
    }

    private String getFirstMessage(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        return fieldError == null ? "参数错误" : fieldError.getDefaultMessage();
    }
}
