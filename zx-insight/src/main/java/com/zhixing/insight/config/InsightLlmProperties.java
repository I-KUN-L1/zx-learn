package com.zhixing.insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 学情分析 LLM 与阈值配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "zx.llm")
public class InsightLlmProperties {

    /** 是否启用真实大模型（false 时使用规则引擎生成报告） */
    private boolean enabled = false;

    /** OpenAI 兼容接口地址 */
    private String baseUrl = "https://api.openai.com";

    /** API Key */
    private String apiKey = "";

    /** 模型名称 */
    private String model = "gpt-3.5-turbo";

    /** 学情分析阈值 */
    private Insight insight = new Insight();

    @Data
    public static class Insight {
        /** 答题正确率低于该值判定薄弱 */
        private int weakAccuracy = 60;
        /** 课程平均进度低于该值判定薄弱 */
        private int weakProgress = 40;
        /** 学习路径推荐课程数量 */
        private int recommendLimit = 3;
    }
}
