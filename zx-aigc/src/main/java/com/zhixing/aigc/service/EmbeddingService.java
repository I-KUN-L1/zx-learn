package com.zhixing.aigc.service;

import com.zhixing.aigc.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedding 客户端：调用 OpenAI 兼容接口将文本向量化。
 *
 * <p>接口路径相对 {@code zx.llm.base-url} 拼接，由 {@code zx.llm.embedding-path}
 * （环境变量 {@code ZX_LLM_EMBEDDING_PATH}）配置。**不要写死路径**：不同厂商的 base-url
 * 是否自带版本段不一致（OpenAI 用 {@code https://api.openai.com} + {@code v1/embeddings}；
 * 智谱用 {@code https://open.bigmodel.cn/api/paas/v4} + {@code embeddings}），
 * 写死会让其中一种组合拼出 404 地址，进而**静默降级**成伪向量（RAG 检索失去语义）。
 *
 * <p>未配置 apiKey 时返回确定性伪向量（哈希 + 归一化），保证本地可运行、可断点调试，
 * 但由于无真实语义，RAG 检索仅具演示意义，生产务必配置真实 Embedding 服务。
 */
@Slf4j
@Service
public class EmbeddingService {

    /** 降级告警只打一次（进程内），避免每条切片都刷 ERROR 把真正的配置问题淹没 */
    private static final AtomicBoolean FALLBACK_WARNED = new AtomicBoolean(false);

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
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", properties.getEmbeddingModel());
            body.put("input", text);
            // 显式声明输出维度：智谱 embedding-3 默认 2048 维、OpenAI text-embedding-3-* 默认更高维，
            // 都必须与本项目的 pgvector 列（knowledge_chunk.embedding）维度严格一致，
            // 否则写入会被 PostgreSQL 拒绝（维度不符）。支持该参数的厂商（智谱/OpenAI 等）会按需截断，
            // 即 MRL 特性：从高维向量前缀截取，语义损失很小。
            if (properties.getEmbeddingDimension() > 0) {
                body.put("dimensions", properties.getEmbeddingDimension());
            }
            Map<?, ?> resp = webClient.post()
                    .uri(embeddingUri())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return parse(resp, dim);
        } catch (Exception e) {
            logFallbackOnce(e);
            return mockEmbedding(text, dim);
        }
    }

    /** 降级只告警一次，避免刷屏；但必须**可见**——静默降级会让 RAG 检索退化成无语义结果却不被察觉。 */
    private void logFallbackOnce(Exception e) {
        if (FALLBACK_WARNED.compareAndSet(false, true)) {
            log.error("Embedding 调用失败，已降级为【伪向量】：RAG 检索结果不具备语义（仅演示可用）。"
                            + "原始错误：{}。请核对 zx.llm.embedding-path / embedding-model 是否与 base-url 配套，"
                            + "并确认 embedding-dimension 与 pgvector 列维度一致（当前 endpoint={}{}, model={}, dim={}）",
                    e.getMessage(), properties.getBaseUrl(), embeddingUri(),
                    properties.getEmbeddingModel(), properties.getEmbeddingDimension());
        } else {
            log.warn("调用 Embedding 服务失败，返回降级向量：{}", e.getMessage());
        }
    }

    /**
     * 拼接向量化接口地址：跳过头尾多余的斜杠，避免形如 {@code v4//embeddings} 的双斜杠路径。
     *
     * <p>路径由 {@code zx.llm.embedding-path}（环境变量 {@code ZX_LLM_EMBEDDING_PATH}）配置，
     * 默认 {@code v1/embeddings}（适配 OpenAI 的 {@code https://api.openai.com} 这种不含版本前缀的 base-url）。
     * <b>使用智谱等 base-url 已含版本段的厂商时必须显式覆盖</b>（如 {@code embeddings}），
     * 否则会拼出 {@code /v4/v1/embeddings} 这类 404 地址，Embedding 恒定失败并静默降级为伪向量。
     */
    private String embeddingUri() {
        String path = properties.getEmbeddingPath() == null || properties.getEmbeddingPath().isBlank()
                ? "v1/embeddings" : properties.getEmbeddingPath();
        return "/" + path.replaceAll("^/+|/+$", "");
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