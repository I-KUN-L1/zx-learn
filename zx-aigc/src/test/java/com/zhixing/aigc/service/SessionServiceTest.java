package com.zhixing.aigc.service;

import com.zhixing.aigc.domain.ChatSession;
import com.zhixing.aigc.memory.ChatMemory;
import com.zhixing.aigc.memory.RedisMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SessionService 单元测试：覆盖删除会话核心逻辑
 */
class SessionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        sessionService = new SessionService(redisTemplate, chatMemory);
    }

    @Test
    void createSessionStoresInRedis() {
        // when
        ChatSession session = sessionService.createSession(1L);

        // then
        assertNotNull(session.getSessionId());
        assertEquals("新对话", session.getTitle());
        assertEquals(1L, session.getUserId());
        verify(redisTemplate.opsForHash()).putAll(
                anyString(), anyMap());
        verify(redisTemplate.opsForZSet()).add(
                anyString(), eq(session.getSessionId()), anyDouble());
        verify(redisTemplate).expire(anyString(), anyLong(), any());
    }

    @Test
    void deleteSessionClearsAllKeys() {
        // given
        String sessionId = "test-session-123";
        Long userId = 1L;

        // when
       sessionService.delete(sessionId, userId);

        // then
        // 1. 清除聊天消息内存
        verify(chatMemory).clear(sessionId);
        // 2. 删除会话元数据
        verify(redisTemplate).delete("aigc:session:" + sessionId);
        // 3. 从用户会话集合中移除
        verify(redisTemplate.opsForZSet()).remove("aigc:user-sessions:1", sessionId);
    }

    @Test
    void historyReturnsEmptyWhenNoSessions() {
        // given
        Long userId = 999L;
        when(zSetOps.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(null);

        // when
        List<ChatSession> sessions = sessionService.history(userId);

        // then
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void detailDelegatesToChatMemory() {
        // given
        String sessionId = "detail-session";
        List<RedisMessage> expected = List.of(new RedisMessage("user", "hello"));
        when(chatMemory.load(sessionId)).thenReturn(expected);

        // when
        List<RedisMessage> result = sessionService.detail(sessionId);

        // then
        assertSame(expected, result);
        verify(chatMemory).load(sessionId);
    }

    @Test
    void updateTitleUpdatesHash() {
        // given
        String sessionId = "title-session";
        String newTitle = "Updated Title";

        // when
        sessionService.updateTitle(sessionId, newTitle);

        // then
        verify(redisTemplate.opsForHash()).put(
                eq("aigc:session:" + sessionId),
                eq("title"),
                eq(newTitle));
    }
}
