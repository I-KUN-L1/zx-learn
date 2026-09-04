package com.zhixing.common.annotation;

import java.lang.annotation.*;

/**
 * 标记该方法返回值不需要被 {@link com.zhixing.common.advice.WrapperResponseBodyAdvice} 包装。
 * 例如 SSE 流式输出、文件下载接口。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoWrapper {
}
