package com.zhixing.aigc.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis 的会话记忆
 */
@Component
@RequiredArgsConstructor
public class ChatMemory {

    private static final String KEY_PREFIX = "aigc:memory:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_HISTORY = 20;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveMessage(String sessionId, String role, String content) {
        List<RedisMessage> messages = load(sessionId);
        messages.add(new RedisMessage(role, content));
        if (messages.size() > MAX_HISTORY) {
            messages = messages.subList(messages.size() - MAX_HISTORY, messages.size());
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, objectMapper.writeValueAsString(messages), TTL);
        } catch (Exception ignored) {
            // Redis 不可用时降级为无记忆，不影响对话主流程
        }
    }

    public List<RedisMessage> load(String sessionId) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            // Redis 不可用时返回空历史
            return new ArrayList<>();
        }
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RedisMessage>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, String>> loadAsMap(String sessionId) {
        List<Map<String, String>> result = new ArrayList<>();
        for (RedisMessage m : load(sessionId)) {
            result.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        return result;
    }

    public void clear(String sessionId) {
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception ignored) {
        }
    }
}
