package com.zhixing.insight.service;

import com.zhixing.insight.config.InsightLlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * 学情报告大模型客户端：OpenAI 兼容接口。
 * 未配置 API Key 时使用规则引擎生成总结，保证本地可运行。
 */
@Slf4j
@Service
public class InsightLlmClient {

    private final InsightLlmProperties properties;
    private final WebClient webClient;

    public InsightLlmClient(InsightLlmProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    /**
     * 基于学情数据生成个性化学习报告总结
     *
     * @return 总结文本；若未启用 LLM 则返回 null
     */
    public String generateSummary(Map<String, Integer> dimensions, List<String> weakness, List<String> suggestions) {
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            return null;
        }
        String prompt = buildPrompt(dimensions, weakness, suggestions);
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "你是一位在线教育平台的智能学情分析师。请根据给定的能力维度评分、薄弱点和学习建议，用中文生成一段 80-150 字、鼓励性且具体的个性化学习报告总结，不要使用列表格式。"),
                            Map.of("role", "user", "content", prompt)),
                    "stream", false);
            Map<?, ?> resp = webClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return extractContent(resp);
        } catch (Exception e) {
            log.warn("大模型生成总结失败，回退规则总结: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(Map<String, Integer> dims, List<String> weakness, List<String> suggestions) {
        return "维度评分：" + dims + "\n薄弱点：" + weakness + "\n学习建议：" + suggestions;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> resp) {
        try {
            List<?> choices = (List<?>) resp.get("choices");
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            return String.valueOf(message.get("content")).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
