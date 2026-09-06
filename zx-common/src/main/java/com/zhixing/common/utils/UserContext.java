package com.zhixing.common.utils;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由网关鉴权后透传 user-info / role-info 请求头，各服务拦截器解析后存入此处。
 * role 取值为 user.type（1员工/2学员/3教师），供 @RequireRole 接口级鉴权使用。
 */
public class UserContext {

    private static final ThreadLocal<Long> TL = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE = new ThreadLocal<>();

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

    /** 写入当前用户角色（user.type） */
    public static void setRole(Integer role) {
        ROLE.set(role);
    }

    /** 当前用户角色（user.type），未登录或网关未透传时为 null */
    public static Integer getRole() {
        return ROLE.get();
    }

    /** 当前用户是否拥有任一给定角色 */
    public static boolean hasRole(Integer... roles) {
        Integer current = ROLE.get();
        if (current == null || roles == null || roles.length == 0) {
            return false;
        }
        for (Integer r : roles) {
            if (r.equals(current)) {
                return true;
            }
        }
        return false;
    }

    public static void remove() {
        TL.remove();
        ROLE.remove();
    }
}
