package com.zhixing.aigc.controller;

import com.zhixing.aigc.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * SessionController 单元测试：验证删除会话接口修复后的参数兼容行为。
 * <p>
 * 修复点：delete 接口需同时兼容「规范 JSON body」和「历史遗留 query 参数」两种调用方式，
 * 杜绝不同 Content-Type 或空请求体导致的 400/500 报错。
 * </p>
 */
class SessionControllerTest {

    @Mock
    private SessionService sessionService;

    private SessionController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new SessionController(sessionService);
    }

    @Test
    void deleteWithJsonBodyDelegatesToService() {
        Map<String, String> body = Map.of("sessionId", "json-body-id");
        // 兼容 JSON body：query 参数为 null，userId 正常传递
        assertTrue(controller.delete(body, null, 7L).success());

        verify(sessionService).delete("json-body-id", 7L);
    }

    @Test
    void deleteWithQueryParamDelegatesToService() {
        // 兼容历史 query 参数：body 为 null。
        // userId 缺失必须原样传 null（**不能回退 0L**）：null 表示"无 user-info 头的服务间内部调用"，
        // 是 SessionService 归属校验的放行条件；回退成 0L 会被当成"用户 0"而无权访问任何会话。
        assertTrue(controller.delete(null, "query-param-id", null).success());

        verify(sessionService).delete("query-param-id", null);
    }

    @Test
    void deleteQueryParamTakesPrecedenceOverBody() {
        Map<String, String> body = Map.of("sessionId", "body-id");
        // 两处都传时以 query 参数为准（当前实现优先级）
        assertTrue(controller.delete(body, "query-id", 3L).success());

        verify(sessionService).delete("query-id", 3L);
    }

    @Test
    void deleteWithEmptyBodyAndNoParamDoesNotThrow() {
        // 空请求体 + 无 query 参数：不抛异常，传入 null 会话记录 + null 身份（内部调用语义）
        assertTrue(controller.delete(null, null, null).success());

        verify(sessionService).delete(null, null);
    }
}