package com.zhixing.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 服务配置（OpenAI 兼容协议，默认对接智谱清言 ChatGLM）
 * <p>
 * 智谱清言官方接口（与 OpenAI 兼容）：
 * 鉴权：Authorization: Bearer {apiKey}
 * 端点：POST {base-url}/{chat-path}  →  https://open.bigmodel.cn/api/paas/v4/chat/completions
 * 请求体：{model, messages, temperature, top_p, max_tokens, stream, tools...}
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "zx.llm")
public class LlmProperties {

    /** OpenAI 兼容接口地址（智谱清言默认） */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /** 智谱开放平台 API Key */
    private String apiKey = "";

    /** 模型名（智谱清言 glm-4.5-air） */
    private String model = "glm-4.5-air";

    /** 相对 base-url 的补全接口路径（智谱为 chat/completions，OpenAI 为 v1/chat/completions） */
    private String chatPath = "chat/completions";

    /** 采样温度（0~1，越高越发散） */
    private Double temperature = 0.8D;

    /** 核采样（0~1，保留头部累计概率达到该值的 token） */
    private Double topP = 0.95D;

    /** 单次回复的最大 token 数 */
    private Integer maxTokens = 1024;

    /** 是否启用（未配置 key 时返回模拟回复） */
    private boolean enabled = false;

    /** Embedding 模型名（如 text-embedding-3-small） */
    private String embeddingModel = "text-embedding-3-small";

    /** Embedding 向量维度，需与 knowledge_chunk.embedding 列（vector 维度）一致 */
    private int embeddingDimension = 1536;
}