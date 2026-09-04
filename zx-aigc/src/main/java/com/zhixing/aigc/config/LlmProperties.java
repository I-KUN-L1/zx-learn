package com.zhixing.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 服务配置（OpenAI 兼容协议）
 */
@Data
@Component
@ConfigurationProperties(prefix = "zx.llm")
public class LlmProperties {

    /** OpenAI 兼容接口地址，例如 https://api.openai.com 或 https://api.deepseek.com */
    private String baseUrl = "https://api.openai.com";
    private String apiKey = "";
    private String model = "gpt-3.5-turbo";
    /** 是否启用（未配置 key 时返回模拟回复） */
    private boolean enabled = false;

    /** Embedding 模型名（如 text-embedding-3-small） */
    private String embeddingModel = "text-embedding-3-small";
    /** Embedding 向量维度，需与 knowledge_chunk.embedding 列（vector 维度）一致 */
    private int embeddingDimension = 1536;
}
