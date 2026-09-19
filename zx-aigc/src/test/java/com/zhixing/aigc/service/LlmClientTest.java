package com.zhixing.aigc.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.zhixing.aigc.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 客户端单元测试：
 * <ul>
 *   <li>未配置 API Key 时的本地模拟回复（保证无 Key 环境可运行）；</li>
 *   <li>SSE 流式对话真实 HTTP 链路：本地起 JDK HttpServer 模拟 OpenAI 兼容端点，
 *       回归「对话返回空白」修复——WebFlux SSE reader 已剥掉 data: 前缀，
 *       旧代码按 startsWith("data:") 过滤会把全部事件滤空；</li>
 *   <li>上游异常降级与接口路径归一化。</li>
 * </ul>
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

    @Test
    void chatStreamParsesSseDeltaContent() throws Exception {
        // 修复回归：走真实 HTTP + text/event-stream，验证 delta.content 逐条提取。
        // 覆盖三类应跳过的事件：仅含 role 的首 chunk、非 JSON 心跳行、[DONE] 结束标记
        String sse = String.join("\n\n",
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"，知行\"}}]}",
                "data: :keep-alive",
                "data: [DONE]") + "\n\n";
        HttpServer server = startServer(ex -> respond(ex, 200, "text/event-stream", sse));
        try {
            LlmClient client = clientFor(server, null);
            String joined = client.chatStream(userMessages()).collectList()
                    .block(Duration.ofSeconds(10)).stream().reduce("", String::concat);
            assertEquals("你好，知行", joined, "应仅拼接 delta.content，空白回复 bug 不得复发");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatStreamDegradesWhenUpstreamFails() throws Exception {
        // 修复回归：上游 5xx 时降级为友好提示，不向前端抛错（曾是"系统繁忙"的诱因之一）
        HttpServer server = startServer(ex -> respond(ex, 500, "text/event-stream", "boom"));
        try {
            LlmClient client = clientFor(server, null);
            List<String> chunks = client.chatStream(userMessages())
                    .collectList().block(Duration.ofSeconds(10));
            assertEquals(List.of("抱歉，AI 服务暂时不可用，请稍后再试。"), chunks);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatUriStripsRedundantSlashes() throws Exception {
        // 修复回归：chatPath 首尾多余斜杠应被剥离，避免 v4//chat 双斜杠路径被网关/厂商拒绝
        Map<String, String> requestedPath = new ConcurrentHashMap<>();
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n";
        HttpServer server = startServer(ex -> {
            requestedPath.put("path", ex.getRequestURI().getPath());
            respond(ex, 200, "text/event-stream", sse);
        });
        try {
            LlmClient client = clientFor(server, "/chat/completions/");
            client.chatStream(userMessages()).collectList().block(Duration.ofSeconds(10));
            assertEquals("/chat/completions", requestedPath.get("path"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatExtractsMessageContent() throws Exception {
        String json = "{\"choices\":[{\"message\":{\"content\":\"测试回复\"}}]}";
        HttpServer server = startServer(ex -> respond(ex, 200, "application/json", json));
        try {
            LlmClient client = clientFor(server, null);
            assertEquals("测试回复", client.chat(userMessages()).block(Duration.ofSeconds(10)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatFallsBackToMockReplyOnUpstreamError() throws Exception {
        HttpServer server = startServer(ex -> respond(ex, 500, "application/json", "boom"));
        try {
            LlmClient client = clientFor(server, null);
            String reply = client.chat(userMessages()).block(Duration.ofSeconds(10));
            assertNotNull(reply, "非流式调用失败应兜底而非返回 null");
            assertTrue(reply.contains("知行智学智能助教"));
        } finally {
            server.stop(0);
        }
    }

    // ==================== 测试基建：本地 OpenAI 兼容端点（JDK 内置 HttpServer，零额外依赖） ====================

    /** 启动本地 HTTP 服务，端口由系统分配 */
    private HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", handler);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return server;
    }

    /** 构造指向本地服务、启用真实调用链路的 LlmClient */
    private LlmClient clientFor(HttpServer server, String chatPath) {
        LlmProperties props = new LlmProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        if (chatPath != null) {
            props.setChatPath(chatPath);
        }
        return new LlmClient(props);
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private List<Map<String, String>> userMessages() {
        return List.of(Map.of("role", "user", "content", "你好"));
    }
}
