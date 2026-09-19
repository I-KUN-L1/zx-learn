package com.zhixing.aigc.service;

import com.zhixing.aigc.domain.ChatSession;
import com.zhixing.aigc.memory.ChatMemory;
import com.zhixing.aigc.memory.RedisMessage;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 会话管理
 * <p>
 * 归属校验（防水平越权 IDOR）：会话归属记录在 {@code aigc:session:{id}} 哈希的 {@code userId} 字段。
 * 修复前 detail / delete / updateTitle **完全不校验归属**，任何已登录用户拿到（或猜到）
 * 他人 sessionId 即可读取、清空、篡改他人 AI 对话记录。
 * <p>
 * 判定口径与 {@code InternalOnlyGuard} / {@code OwnerAccessGuard} 一致：
 * 请求无 user-info 头（currentUserId == null）视为服务间内部调用放行；
 * 其余情况必须是会话归属人本人。
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_KEY = "aigc:session:";
    private static final String USER_SESSIONS = "aigc:user-sessions:";
    private static final String FIELD_USER_ID = "userId";

    private final StringRedisTemplate redisTemplate;
    private final ChatMemory chatMemory;

    public ChatSession createSession(Long userId) {
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        session.setTitle("新会话");
        session.setUserId(userId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        redisTemplate.opsForHash().putAll(SESSION_KEY + session.getId(), Map.of(
                "title", session.getTitle(),
                FIELD_USER_ID, String.valueOf(userId),
                "createTime", session.getCreateTime().toString()
        ));
        redisTemplate.opsForZSet().add(USER_SESSIONS + userId, session.getId(), System.currentTimeMillis());
        redisTemplate.expire(SESSION_KEY + session.getId(), 7, TimeUnit.DAYS);
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
            s.setId(id);
            Object title = redisTemplate.opsForHash().get(SESSION_KEY + id, "title");
            s.setTitle(title == null ? "新会话" : title.toString());
            s.setUserId(userId);
            return s;
        }).collect(Collectors.toList());
    }

    /**
     * 会话消息详情（契约对齐前端 ChatMessage：type USER/AI + agent）。
     *
     * @param currentUserId 当前登录用户（服务间内部调用为 null）
     */
    public List<Map<String, String>> detail(String sessionId, Long currentUserId) {
        assertSessionOwner(sessionId, currentUserId);
        List<RedisMessage> messages = chatMemory.load(sessionId);
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        return messages.stream().map(m -> Map.of(
                "type", "user".equalsIgnoreCase(m.getRole()) ? "USER" : "AI",
                "content", m.getContent() == null ? "" : m.getContent()
        )).collect(Collectors.toList());
    }

    /**
     * 删除会话记录。sessionId 为空时返回 400 参数错误（防御前端空参导致 Redis ZREM null 报 500）
     */
    public void delete(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BadRequestException("会话 ID 不能为空");
        }
        assertSessionOwner(sessionId, userId);
        chatMemory.clear(sessionId);
        redisTemplate.delete(SESSION_KEY + sessionId);
        redisTemplate.opsForZSet().remove(USER_SESSIONS + userId, sessionId);
        // 同时清理 SSE 事件回放缓冲，避免残留
        try {
            redisTemplate.delete("aigc:sse:" + sessionId);
        } catch (Exception ignored) {
        }
    }

    public void updateTitle(String sessionId, String title, Long currentUserId) {
        if (sessionId == null || title == null || title.isBlank()) {
            return;
        }
        assertSessionOwner(sessionId, currentUserId);
        redisTemplate.opsForHash().put(SESSION_KEY + sessionId, "title", title);
    }

    /**
     * 会话归属校验：仅归属人本人（或服务间内部调用）可通过，
     * 会话不存在/已过期一律按"无权访问"处理，避免通过响应差异探测他人 sessionId 是否存在。
     */
    private void assertSessionOwner(String sessionId, Long currentUserId) {
        if (currentUserId == null) {
            // 无 user-info 头：网关认证流量必带该头 → 判定为服务间 Feign 内部调用
            return;
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new BadRequestException("会话 ID 不能为空");
        }
        Object owner = redisTemplate.opsForHash().get(SESSION_KEY + sessionId, FIELD_USER_ID);
        if (owner == null || !String.valueOf(currentUserId).equals(String.valueOf(owner))) {
            throw new ForbiddenException("无权访问该会话");
        }
    }
}
