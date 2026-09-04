package com.zhixing.aigc.service;

import com.zhixing.aigc.domain.ChatSession;
import com.zhixing.aigc.memory.ChatMemory;
import com.zhixing.aigc.memory.RedisMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 会话管理
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_KEY = "aigc:session:";
    private static final String USER_SESSIONS = "aigc:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final ChatMemory chatMemory;

    public ChatSession createSession(Long userId) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setTitle("新对话");
        session.setUserId(userId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        redisTemplate.opsForHash().putAll(SESSION_KEY + session.getSessionId(), Map.of(
                "title", session.getTitle(),
                "userId", String.valueOf(userId),
                "createTime", session.getCreateTime().toString()
        ));
        redisTemplate.opsForZSet().add(USER_SESSIONS + userId, session.getSessionId(), System.currentTimeMillis());
        redisTemplate.expire(SESSION_KEY + session.getSessionId(), 7, TimeUnit.DAYS);
        return session;
    }

    public List<String> hotQuestions() {
        return List.of(
                "如何挑选适合自己的课程？",
                "课程购买后可以退款吗？",
                "学习过程中遇到问题怎么办？",
                "如何查看我的积分和排名？"
        );
    }

    public List<ChatSession> history(Long userId) {
        Set<String> sessionIds = redisTemplate.opsForZSet()
                .reverseRange(USER_SESSIONS + userId, 0, 50);
        if (sessionIds == null) {
            return List.of();
        }
        return sessionIds.stream().map(id -> {
            ChatSession s = new ChatSession();
            s.setSessionId(id);
            Object title = redisTemplate.opsForHash().get(SESSION_KEY + id, "title");
            s.setTitle(title == null ? "对话" : title.toString());
            s.setUserId(userId);
            return s;
        }).collect(Collectors.toList());
    }

    public List<RedisMessage> detail(String sessionId) {
        return chatMemory.load(sessionId);
    }

    public void delete(String sessionId, Long userId) {
        chatMemory.clear(sessionId);
        redisTemplate.delete(SESSION_KEY + sessionId);
        redisTemplate.opsForZSet().remove(USER_SESSIONS + userId, sessionId);
    }

    public void updateTitle(String sessionId, String title) {
        redisTemplate.opsForHash().put(SESSION_KEY + sessionId, "title", title);
    }
}
