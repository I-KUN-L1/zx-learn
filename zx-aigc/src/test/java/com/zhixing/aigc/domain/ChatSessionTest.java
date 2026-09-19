package com.zhixing.aigc.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatSession 契约测试：修复点——主键字段曾命名为 sessionId，
 * 前端会话列表以 id 读取，字段名不一致导致删除/切换会话失效。
 */
class ChatSessionTest {

    @Test
    void serializesPrimaryKeyAsIdForFrontendContract() throws Exception {
        ChatSession session = new ChatSession();
        session.setId("abc123");
        session.setTitle("新会话");
        session.setUserId(1L);

        String json = new ObjectMapper().writeValueAsString(session);

        assertTrue(json.contains("\"id\":\"abc123\""), "序列化应输出前端契约的 id 字段");
        assertFalse(json.contains("sessionId"), "不应再出现旧字段名 sessionId");
    }
}
