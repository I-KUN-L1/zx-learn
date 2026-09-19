package com.zhixing.aigc.service;

import com.zhixing.aigc.domain.ChatSession;
import com.zhixing.aigc.memory.ChatMemory;
import com.zhixing.aigc.memory.RedisMessage;
import com.zhixing.common.exceptions.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SessionService 单元测试：覆盖创建/删除/详情契约 + 会话归属校验（防水平越权）。
 * <p>
 * 归属校验约定（见 {@code SessionService.assertSessionOwner}）：
 * {@code currentUserId == null} 代表"无 user-info 头的服务间内部调用"，放行；
 * 非 null 时必须与会话哈希里的 {@code userId} 一致，否则 403。
 * 因此本测试用三种入参形态分别覆盖：内部调用（null）、归属人本人、他人。
 */
class SessionServiceTest {

    private static final String SESSION_KEY = "aigc:session:";
    private static final String FIELD_USER_ID = "userId";

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

    /** 让会话归属校验通过：把 sessionId 的归属人设为 ownerId */
    private void stubOwner(String sessionId, Object ownerId) {
        when(hashOps.get(SESSION_KEY + sessionId, FIELD_USER_ID)).thenReturn(ownerId);
    }

    @Test
    void createSessionStoresInRedis() {
        // when
        ChatSession session = sessionService.createSession(1L);

        // then
        assertNotNull(session.getId());
        assertEquals("新会话", session.getTitle());
        assertEquals(1L, session.getUserId());
        verify(redisTemplate.opsForHash()).putAll(
                anyString(), anyMap());
        verify(redisTemplate.opsForZSet()).add(
                anyString(), eq(session.getId()), anyDouble());
        verify(redisTemplate).expire(anyString(), anyLong(), any());
    }

    @Test
    void deleteSessionClearsAllKeys() {
        // given：归属人本人删除自己的会话
        String sessionId = "test-session-123";
        Long userId = 1L;
        stubOwner(sessionId, "1");

        // when
        sessionService.delete(sessionId, userId);

        // then
        // 1. 清除聊天消息内存
        verify(chatMemory).clear(sessionId);
        // 2. 删除会话元数据
        verify(redisTemplate).delete(SESSION_KEY + sessionId);
        // 3. 从用户会话集合中移除
        verify(redisTemplate.opsForZSet()).remove("aigc:user-sessions:1", sessionId);
    }

    @Test
    void deleteBlankSessionIdRejected() {
        // given: 前端空参防御（历史遗留 bug 曾导致 500 系统繁忙）
        // when + then
        assertThrows(RuntimeException.class, () -> sessionService.delete(null, 1L));
        assertThrows(RuntimeException.class, () -> sessionService.delete("  ", 1L));
        verify(chatMemory, never()).clear(any());
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
    void detailMapsRoleToFrontendContract() {
        // given：服务间内部调用（currentUserId = null），不触发归属校验
        String sessionId = "detail-session";
        when(chatMemory.load(sessionId)).thenReturn(java.util.List.of(
                new RedisMessage("user", "hello"),
                new RedisMessage("assistant", "hi")));

        // when
        List<Map<String, String>> result = sessionService.detail(sessionId, null);

        // then: 契约对齐前端 ChatMessage（type USER/AI）
        assertEquals(2, result.size());
        assertEquals("USER", result.get(0).get("type"));
        assertEquals("hello", result.get(0).get("content"));
        assertEquals("AI", result.get(1).get("type"));
        assertEquals("hi", result.get(1).get("content"));
        verify(chatMemory).load(sessionId);
    }

    @Test
    void updateTitleUpdatesHash() {
        // given：服务间内部调用（currentUserId = null）
        String sessionId = "title-session";
        String newTitle = "Updated Title";

        // when
        sessionService.updateTitle(sessionId, newTitle, null);

        // then
        verify(redisTemplate.opsForHash()).put(
                eq(SESSION_KEY + sessionId),
                eq("title"),
                eq(newTitle));
    }

    // ------------------------------------------------------------------
    // 归属校验（BUG-004 水平越权修复的回归覆盖）
    // ------------------------------------------------------------------

    @Test
    void detailAllowsOwner() {
        // given：会话属于 1L，调用者也是 1L
        String sessionId = "own-session";
        stubOwner(sessionId, "1");
        when(chatMemory.load(sessionId)).thenReturn(List.of(new RedisMessage("user", "hi")));

        // when + then：本人可读
        assertEquals(1, sessionService.detail(sessionId, 1L).size());
    }

    @Test
    void detailRejectsOtherUsersSession() {
        // given：会话属于 999L，调用者是 1L
        String sessionId = "other-session";
        stubOwner(sessionId, "999");

        // when + then：抛 403，且**不得读取消息内容**
        assertThrows(ForbiddenException.class, () -> sessionService.detail(sessionId, 1L));
        verify(chatMemory, never()).load(sessionId);
    }

    @Test
    void detailRejectsMissingSession() {
        // given：会话不存在/已过期（哈希无 userId）—— 一律按"无权访问"处理，
        // 避免通过响应差异探测他人 sessionId 是否存在
        String sessionId = "ghost-session";
        when(hashOps.get(SESSION_KEY + sessionId, FIELD_USER_ID)).thenReturn(null);

        // when + then
        assertThrows(ForbiddenException.class, () -> sessionService.detail(sessionId, 1L));
        verify(chatMemory, never()).load(sessionId);
    }

    @Test
    void deleteRejectsOtherUsersSession() {
        // given
        String sessionId = "other-session-del";
        stubOwner(sessionId, "999");

        // when + then：越权删除被拦，且**不得清空他人会话数据**
        assertThrows(ForbiddenException.class, () -> sessionService.delete(sessionId, 1L));
        verify(chatMemory, never()).clear(sessionId);
        verify(redisTemplate, never()).delete(SESSION_KEY + sessionId);
    }

    @Test
    void updateTitleRejectsOtherUsersSession() {
        // given
        String sessionId = "other-session-title";
        stubOwner(sessionId, "999");

        // when + then：越权改标题被拦，且**不得写入标题**
        assertThrows(ForbiddenException.class,
                () -> sessionService.updateTitle(sessionId, "hacked", 1L));
        verify(hashOps, never()).put(anyString(), any(), any());
    }
}
