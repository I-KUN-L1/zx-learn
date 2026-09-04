package com.zhixing.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关 JWT 验签工具
 */
@Component
public class JwtUtils {

    private final SecretKey key;

    public JwtUtils(com.zhixing.gateway.config.JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Object userId = claims.get("userId");
        if (userId instanceof Number num) {
            return num.longValue();
        }
        return Long.valueOf(String.valueOf(userId));
    }

    /**
     * 解析角色 claim（user.type：1员工/2学员/3教师）；旧 token 无该 claim 时返回 null
     */
    public Integer parseRoleId(String token) {
        try {
            Object role = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().get("roleId");
            if (role instanceof Number num) {
                return num.intValue();
            }
            return role == null ? null : Integer.valueOf(String.valueOf(role));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
