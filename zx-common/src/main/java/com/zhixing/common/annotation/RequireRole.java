package com.zhixing.common.annotation;

import com.zhixing.common.constants.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级角色鉴权注解：标注在 Controller 方法或类上，要求当前登录用户具备任一给定角色。
 * <p>
 * 角色来源：登录时 user.type 写入 JWT role claim → 网关解析后经 role-info 头透传 →
 * UserInfoInterceptor 写入 UserContext → RoleInterceptor 统一校验，不满足抛 ForbiddenException(403)。
 * <p>
 * 注意：被 Feign 等内部调用直连（不经网关）的端点缺少 role-info 头，不得标注本注解。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色集合（任一满足即可），为空时仅要求已登录 */
    UserRole[] value();
}
