package com.zhixing.insight.service;

import com.zhixing.insight.config.InsightLlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;

import java.time.Duration;
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

    private static final Duration CONN_TIMEOUT = Duration.ofSeconds(3);
    /**
     * 读取超时 8 秒：前端 axios 超时为 15 秒，学情页面会并发发起
     * profiles/mine 与 reports/latest 两个请求（后者可能触发大模型调用），
     * 若上游大模型不可达，必须保证「连接 3s + 读取 8s」仍在 15s 预算内返回，
     * 否则前端会先超时并表现为接口异常、报告页无数据。
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    /** 解析后的 Chat Completions 完整路径（构造期确定，避免每次请求重复推断） */
    private final String chatPath;

    public InsightLlmClient(InsightLlmProperties properties) {
        this.properties = properties;
        this.chatPath = resolveChatPath(properties);
        this.webClient = WebClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + safe(properties.getApiKey()))
                // 连接/读取超时兜底：防止上游大模型慢或不可达时阻塞请求线程，导致学情报告接口长时间挂起
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(READ_TIMEOUT)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONN_TIMEOUT.toMillis())))
                .build();
    }

    /**
     * 推断 Chat Completions 路径。
     * <p>
     * 历史缺陷（已修复）：原实现固定拼接 {@code /v1/chat/completions}，
     * 而智谱 baseUrl 为 {@code https://open.bigmodel.cn/api/paas/v4}，
     * 拼出 {@code /api/paas/v4/v1/chat/completions} → <b>404</b>，
     * 导致"AI 学情点评"永远降级为规则文案，且每次请求都白跑一次外部 HTTP。
     * 现按版本段智能判断，并支持 {@code zx.llm.chat-path} 显式覆盖。
     */
    private static String resolveChatPath(InsightLlmProperties properties) {
        String configured = properties.getChatPath();
        if (configured != null && !configured.isBlank()) {
            return configured.startsWith("/") ? configured : "/" + configured;
        }
        String base = normalizeBaseUrl(properties.getBaseUrl());
        if (base.endsWith("/chat/completions")) {
            return "";
        }
        // baseUrl 已含版本段（/v1、/v4、/api/paas/v4 等）时不再重复补 /v1
        if (base.matches(".*/v\\d+$") || base.matches(".*/v\\d+\\.[\\d.]+$")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    /** 去掉 baseUrl 末尾斜杠，避免拼出 {@code //} 路径 */
    private static String normalizeBaseUrl(String baseUrl) {
        String base = safe(baseUrl).trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    /**
     * 基于学情数据生成个性化学习报告总结
     *
     * @return 总结文本；若未启用 LLM 或调用失败则返回 null（调用方回退规则总结）
     */
    public String generateSummary(Map<String, Integer> dimensions, List<String> weakness, List<String> suggestions) {
        if (!properties.isEnabled() || safe(properties.getApiKey()).isBlank()) {
            return null;
        }
        String prompt = buildPrompt(dimensions, weakness, suggestions);
        try {
            Map<String, Object> body = Map.of(
                    "model", safe(properties.getModel()),
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "你是一位在线教育平台的智能学情分析师。请根据给定的能力维度评分、薄弱点和学习建议，用中文生成一段 80-150 字、鼓励性且具体的个性化学习报告总结，不要使用列表格式。"),
                            Map.of("role", "user", "content", prompt)),
                    "stream", false);
            Map<?, ?> resp = webClient.post()
                    .uri(chatPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(READ_TIMEOUT.plus(CONN_TIMEOUT));
            return extractContent(resp);
        } catch (Exception e) {
            log.warn("大模型生成总结失败，回退规则总结: path={}, err={}", chatPath, e.getMessage());
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
