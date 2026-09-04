package com.zhixing.aigc.service;

import com.zhixing.aigc.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Embedding 客户端：调用 OpenAI 兼容接口（/v1/embeddings）将文本向量化。
 * <p>
 * 未配置 apiKey 时返回确定性伪向量（哈希 + 归一化），保证本地可运行、可断点调试，
 * 但由于无真实语义，RAG 检索仅具演示意义，生产务必配置真实 Embedding 服务。
 */
@Slf4j
@Service
public class EmbeddingService {

    private final LlmProperties properties;
    private final WebClient webClient;

    public EmbeddingService(LlmProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    /**
     * 将文本编码为 float[] 向量（维度 = zx.llm.embedding-dimension）。
     */
    public float[] embed(String text) {
        if (text == null) {
            text = "";
        }
        int dim = properties.getEmbeddingDimension();
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            return mockEmbedding(text, dim);
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.getEmbeddingModel(),
                    "input", text);
            Map<?, ?> resp = webClient.post()
                    .uri("/v1/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return parse(resp, dim);
        } catch (Exception e) {
            log.warn("调用 Embedding 服务失败，返回降级向量：{}", e.getMessage());
            return mockEmbedding(text, dim);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parse(Map<?, ?> resp, int dim) {
        try {
            List<?> data = (List<?>) resp.get("data");
            Map<?, ?> first = (Map<?, ?>) data.get(0);
            List<Number> nums = (List<Number>) first.get("embedding");
            float[] vec = new float[dimsCheck(nums, dim)];
            for (int i = 0; i < vec.length; i++) {
                vec[i] = nums.get(i).floatValue();
            }
            normalize(vec);
            return vec;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 返回格式异常", e);
        }
    }

    private int dimsCheck(List<Number> nums, int dim) {
        if (nums == null || nums.isEmpty()) {
            throw new IllegalStateException("Embedding 返回为空");
        }
        return nums.size();
    }

    /**
     * 本地降级：基于字符哈希的确定性伪向量（维度与真实模型一致）。
     */
    private float[] mockEmbedding(String text, int dim) {
        float[] vec = new float[dim];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        long h = 1125899906842597L;
        for (byte b : bytes) {
            h = 31L * h + (b & 0xff);
        }
        for (int i = 0; i < dim; i++) {
            h = 31L * h + i;
            vec[i] = (float) (h & 0x7fffffff) / Integer.MAX_VALUE * 2 - 1;
        }
        normalize(vec);
        return vec;
    }

    private void normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        }
    }
}