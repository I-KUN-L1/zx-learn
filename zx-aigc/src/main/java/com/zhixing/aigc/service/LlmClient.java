package com.zhixing.aigc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.aigc.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 客户端：调用 OpenAI 兼容接口。
 * 未配置 apiKey 时返回模拟回复，保证本地可运行。
 */
@Slf4j
@Service
public class LlmClient {

    private final LlmProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmClient(LlmProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                // 显式声明接受 SSE 流，避免部分厂商按普通 JSON 聚合响应
                .defaultHeader("Accept", "text/event-stream")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 非流式对话
     */
    public Mono<String> chat(List<Map<String, String>> messages) {
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            return Mono.just(mockReply(messages));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("stream", false);
        applyParams(body);
        return webClient.post()
                .uri(chatUri())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> extractContent(resp))
                .onErrorReturn(mockReply(messages));
    }

    /**
     * 流式对话（SSE）。
     * <p>
     * 注意：{@code bodyToFlux(String.class)} 消费 {@code text/event-stream} 时，
     * WebFlux 的 ServerSentEventHttpMessageReader 会自动解析事件并仅返回 <b>data 部分</b>
     * （不带 {@code data:} 前缀）。因此此处直接解析 JSON，不可按原始行 {@code startsWith("data:")} 过滤，
     * 否则会把所有事件过滤为空，导致回答空白。
     * </p>
     */
    public Flux<String> chatStream(List<Map<String, String>> messages) {
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            String reply = mockReply(messages);
            return Flux.fromArray(reply.split("(?<=\\G.{8})"));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("stream", true);
        applyParams(body);
        return webClient.post()
                .uri(chatUri())
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                // data 已由 SSE reader 解出（无 data: 前缀）：仅过滤结束标记与非 JSON 心跳行
                .filter(data -> data != null && !data.isBlank() && !"[DONE]".equals(data.trim()))
                .mapNotNull(this::extractDelta)
                .filter(content -> !content.isEmpty())
                .onErrorResume(e -> {
                    log.warn("流式调用失败，降级为错误提示：{}", e.getMessage());
                    return Flux.just("抱歉，AI 服务暂时不可用，请稍后再试。");
                });
    }

    /** 拼接补全接口地址：跳过头尾多余的斜杠，避免产生形如 v4//chat 的双斜杠路径 */
    private String chatUri() {
        String path = properties.getChatPath() == null ? "chat/completions" : properties.getChatPath();
        return "/" + path.replaceAll("^/+|/+$", "");
    }

    /** 填充模型生成参数（temperature / top_p / max_tokens），供 ChatGLM 等厂商生效 */
    private void applyParams(Map<String, Object> body) {
        if (properties.getTemperature() != null) {
            body.put("temperature", properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            body.put("top_p", properties.getTopP());
        }
        if (properties.getMaxTokens() != null) {
            body.put("max_tokens", properties.getMaxTokens());
        }
    }

    private String extractContent(Map<?, ?> resp) {
        try {
            List<?> choices = (List<?>) resp.get("choices");
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            Object content = message.get("content");
            return content == null ? "" : String.valueOf(content);
        } catch (Exception e) {
            return "抱歉，我暂时无法回答这个问题。";
        }
    }

    /**
     * 解析流式增量：提取 choices[0].delta.content。
     * content 缺失或为 null（如首 chunk 仅含 role）时返回 null，由调用方跳过。
     */
    private String extractDelta(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            if (content.isMissingNode() || content.isNull()) {
                return null;
            }
            return content.asText("");
        } catch (Exception e) {
            // 心跳/注释等非 JSON 行：跳过
            return null;
        }
    }

    private String mockReply(List<Map<String, String>> messages) {
        String last = messages.isEmpty() ? "" : messages.get(messages.size() - 1).get("content");
        return "【知行智学智能助教】已收到你的问题：" + last + "。\n" +
                "当前未配置大模型 API Key，请在 application.yml 中配置 zx.llm 参数以启用真实对话。";
    }

    // ==================== Function Calling（工具调用） ====================

    private static final int MAX_TOOL_ROUNDS = 4;

    /**
     * 带工具调用的对话：OpenAI 兼容函数调用循环（调用工具 → 结果回填 → 再生成）。
     */
    public Mono<String> chatWithTools(List<Map<String, String>> messages,
                                      List<Map<String, Object>> tools,
                                      ToolRunner runner) {
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            return Mono.just(mockReply(messages));
        }
        return Mono.fromCallable(() -> toolLoop(messages, tools, runner))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String toolLoop(List<Map<String, String>> messages,
                            List<Map<String, Object>> tools,
                            ToolRunner runner) {
        List<Map<String, Object>> conversation = new ArrayList<>();
        for (Map<String, String> m : messages) {
            conversation.add(new HashMap<>(m));
        }
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.getModel());
            body.put("messages", conversation);
            body.put("tools", tools);
            body.put("tool_choice", "auto");
            body.put("stream", false);
            applyParams(body);

            Map<?, ?> resp = webClient.post()
                    .uri(chatUri())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> calls = extractToolCalls(resp);
            if (calls == null || calls.isEmpty()) {
                return extractContent(resp);
            }
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", null);
            assistantMsg.put("tool_calls", calls);
            conversation.add(assistantMsg);

            for (Map<String, Object> call : calls) {
                String id = String.valueOf(call.get("id"));
                Map<?, ?> fn = (Map<?, ?>) call.get("function");
                String name = fn == null ? "" : String.valueOf(fn.get("name"));
                String args = fn == null ? "{}" : String.valueOf(fn.get("arguments"));
                String result = runner.run(name, args);

                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", id);
                toolMsg.put("content", result);
                conversation.add(toolMsg);
            }
        }
        return "抱歉，工具调用次数过多，已停止。";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<?, ?> resp) {
        try {
            List<?> choices = (List<?>) resp.get("choices");
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            Object tc = message.get("tool_calls");
            return (tc == null) ? null : (List<Map<String, Object>>) tc;
        } catch (Exception e) {
            return null;
        }
    }
}
