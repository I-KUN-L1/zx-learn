package com.zhixing.auth.common.constants;

/**
 * JWT 常量
 */
public interface JwtConstants {

    String PAYLOAD_USER_KEY = "userId";
    String PAYLOAD_ROLE_KEY = "roleId";
    String JWT_REFRESH_COOKIE_KEY = "zx-refresh-token";
    String JWT_ACCESS_COOKIE_KEY = "zx-access-token";
    String JWT_ADMIN_REFRESH_COOKIE_KEY = "zx-admin-refresh-token";
    String JWT_ADMIN_ACCESS_COOKIE_KEY = "zx-admin-access-token";

    String USER_HEADER = "user-info";
    String REQUEST_ID = "requestId";
}
