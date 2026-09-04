package com.zhixing.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG（检索增强生成）配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "zx.rag")
public class RagProperties {

    /** 知识切片窗口大小（token） */
    private int chunkSize = 500;

    /** 相邻切片重叠（token） */
    private int chunkOverlap = 50;

    /** 检索返回的 TopK 片段数 */
    private int topK = 3;

    /** 无相关片段时的最低相似阈值，低于该值视为"未命中"，触发礼貌拒答 */
    private double minScore = 0.0;

    /** SSE 心跳间隔（秒），0 表示关闭 */
    private int heartbeatSeconds = 15;

    /** 断线重连时可回放的最近事件条数 */
    private int replayCount = 100;

    /** 同时活跃的 SSE 流式连接上限；超限返回降级提示，防止高并发长连接打满连接容量（0=不限制） */
    private int maxConcurrentStreams = 200;
}