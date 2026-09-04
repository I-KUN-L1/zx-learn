package com.zhixing.common.utils;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由网关鉴权后透传 user-info 请求头，各服务拦截器解析后存入此处。
 */
public class UserContext {

    private static final ThreadLocal<Long> TL = new ThreadLocal<>();

    public static void setUser(Long userId) {
        TL.set(userId);
    }

    public static Long getUser() {
        return TL.get();
    }

    public static Long getUserId() {
        Long id = TL.get();
        return id == null ? 0L : id;
    }

    public static void remove() {
        TL.remove();
    }
}
