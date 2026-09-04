package com.zhixing.common.utils;

import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.exceptions.CommonException;
import com.zhixing.common.exceptions.ErrorCode;

import java.util.Collection;

/**
 * 统一断言工具（重构：收敛散落各处的 if-throw 校验，统一错误码）
 */
public class AssertUtils {

    private AssertUtils() {
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new BadRequestException(message);
        }
    }

    public static void isTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new CommonException(errorCode.getCode(), errorCode.getMessage());
        }
    }

    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new BadRequestException(message);
        }
    }

    public static void notBlank(String str, String message) {
        if (StringUtils.isBlank(str)) {
            throw new BadRequestException(message);
        }
    }

    public static void notEmpty(Collection<?> coll, String message) {
        if (coll == null || coll.isEmpty()) {
            throw new BadRequestException(message);
        }
    }

    public static void bizIsTrue(boolean condition, String message) {
        if (!condition) {
            throw new BizIllegalException(message);
        }
    }
}
