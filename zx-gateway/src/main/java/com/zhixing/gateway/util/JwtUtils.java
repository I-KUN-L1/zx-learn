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

    /**
     * 解析结果：一次验签同时取出身份与角色。
     *
     * @param userId 用户 id（token 必带；缺失或非法时为 {@code null}）
     * @param roleId 角色（user.type：1员工/2学员/3教师）；旧 token 无该 claim 时为 {@code null}
     */
    public record Identity(Long userId, Integer roleId) {
    }

    /**
     * 单次解析出 {@link Identity}。
     * <p>
     * 原实现走 {@code isValid() + parseUserId() + parseRoleId()}，同一串 JWT 被
     * {@code parseSignedClaims} 验签/解析**三遍**。网关是每个请求的必经热路径，三次 HMAC
     * 验签纯属重复计算；合并为一次后签名只校验一遍，行为不变。
     *
     * @return {@code null} 表示 token 缺失、被篡改、已过期或载荷非法（调用方一律按未认证处理）
     */
    public Identity parseIdentity(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new Identity(toLong(claims.get("userId")), toInt(claims.get("roleId")));
        } catch (Exception e) {
            return null;
        }
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return toLong(claims.get("userId"));
    }

    /**
     * 解析角色 claim（user.type：1员工/2学员/3教师）；旧 token 无该 claim 时返回 null
     */
    public Integer parseRoleId(String token) {
        try {
            return toInt(Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().get("roleId"));
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

    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number num) {
            return num.longValue();
        }
        return Long.valueOf(String.valueOf(v));
    }

    private Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number num) {
            return num.intValue();
        }
        return Integer.valueOf(String.valueOf(v));
    }
}
