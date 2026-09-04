package com.zhixing.aigc.service;

import com.zhixing.aigc.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 客户端单元测试：验证未配置 API Key 时的本地模拟回复
 */
class LlmClientTest {

    @Test
    void chatWithoutKeyReturnsMockReply() {
        LlmProperties props = new LlmProperties();
        props.setEnabled(false);
        props.setApiKey("");
        LlmClient client = new LlmClient(props);

        String reply = client.chat(List.of(Map.of("role", "user", "content", "你好"))).block();
        assertNotNull(reply);
        assertTrue(reply.contains("知行智学"), "模拟回复应包含平台名称");
        assertTrue(reply.contains("你好"), "模拟回复应回显用户问题");
    }

    @Test
    void streamWithoutKeyReturnsNonEmptyFlux() {
        LlmProperties props = new LlmProperties();
        props.setEnabled(false);
        LlmClient client = new LlmClient(props);

        String joined = client.chatStream(List.of(Map.of("role", "user", "content", "测试"))).collectList().block()
                .stream().reduce("", String::concat);
        assertFalse(joined.isBlank(), "流式模拟回复不应为空");
    }
}
