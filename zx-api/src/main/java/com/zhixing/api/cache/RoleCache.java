package com.zhixing.api.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 角色缓存（用户-角色集合）
 */
@Component
public class RoleCache {

    private static final String KEY_PREFIX = "cache:role:";
    private static final long TTL_SECONDS = 3600L;

    private final StringRedisTemplate redisTemplate;

    public RoleCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void putUserRoles(Long userId, Set<String> roles) {
        String key = KEY_PREFIX + userId;
        redisTemplate.delete(key);
        redisTemplate.opsForSet().add(key, roles.toArray(new String[0]));
        redisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
    }

    public Set<String> getUserRoles(Long userId) {
        return redisTemplate.opsForSet().members(KEY_PREFIX + userId);
    }

    public void removeUserRoles(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
