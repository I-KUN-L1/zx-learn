package com.zhixing.api.cache;

import com.zhixing.common.utils.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 课程分类缓存
 */
@Component
public class CategoryCache {

    private static final String KEY_PREFIX = "cache:category:";
    private static final long TTL_SECONDS = 3600L;

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    public CategoryCache(StringRedisTemplate redisTemplate, RedissonClient redissonClient) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
    }

    public String getCategoryName(Long id) {
        String key = KEY_PREFIX + id;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            return value;
        }
        RLock lock = redissonClient.getLock("lock:category:" + id);
        try {
            if (lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    // 由调用方回源数据库后写入，这里返回空
                    return null;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return value;
    }

    public void putCategoryName(Long id, String name) {
        if (id == null || StringUtils.isBlank(name)) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + id, name, TTL_SECONDS, TimeUnit.SECONDS);
    }

    public void remove(Long id) {
        redisTemplate.delete(KEY_PREFIX + id);
    }
}
