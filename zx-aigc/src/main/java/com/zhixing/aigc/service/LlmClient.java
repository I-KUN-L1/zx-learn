package com.zhixing.aigc.service;

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

    public LlmClient(LlmProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    /**
     * 非流式对话
     */
    public Mono<String> chat(List<Map<String, String>> messages) {
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            return Mono.just(mockReply(messages));
        }
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", messages,
                "stream", false);
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> extractContent(resp))
                .onErrorReturn(mockReply(messages));
    }

    /**
     * 流式对话（SSE）
     */
    public Flux<String> chatStream(List<Map<String, String>> messages) {
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            String reply = mockReply(messages);
            return Flux.fromArray(reply.split("(?<=\\G.{8})"));
        }
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", messages,
                "stream", true);
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line.startsWith("data:") && !line.contains("[DONE]"))
                .map(line -> parseDelta(line));
    }

    private String extractContent(Map<?, ?> resp) {
        try {
            List<?> choices = (List<?>) resp.get("choices");
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            return String.valueOf(message.get("content"));
        } catch (Exception e) {
            return "抱歉，我暂时无法回答这个问题。";
        }
    }

    private String parseDelta(String line) {
        try {
            String json = line.substring(line.indexOf('{'));
            // 简化解析：直接截取 content 字段
            int idx = json.indexOf("\"content\"");
            if (idx < 0) {
                return "";
            }
            int start = json.indexOf('"', json.indexOf(':', idx) + 1) + 1;
            int end = json.indexOf('"', start);
            return json.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");
        } catch (Exception e) {
            return "";
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

            Map<?, ?> resp = webClient.post()
                    .uri("/v1/chat/completions")
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
