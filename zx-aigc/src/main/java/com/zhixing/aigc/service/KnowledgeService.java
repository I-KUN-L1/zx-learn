package com.zhixing.aigc.service;

import com.zhixing.aigc.config.RagProperties;
import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.domain.KnowledgeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务：协调"文本切片 → 向量化 → pgvector 入库/检索"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final KnowledgeVectorRepository vectorRepository;
    private final RagProperties ragProperties;

    /**
     * 知识入库：按配置滑动窗口切分，逐片向量化后写入 pgvector。
     *
     * @return 入库的切片数量
     */
    public int upload(Long courseId, Long lessonId, String title, String content) {
        List<String> chunks = textSplitter.split(content, ragProperties.getChunkSize(),
                ragProperties.getChunkOverlap());
        if (chunks.isEmpty()) {
            return 0;
        }
        for (String chunkText : chunks) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setCourseId(courseId);
            chunk.setLessonId(lessonId);
            chunk.setTitle(title);
            chunk.setContent(chunkText);
            chunk.setEmbedding(embeddingService.embed(chunkText));
            vectorRepository.insert(chunk);
        }
        log.info("知识入库完成：title={}, chunks={}", title, chunks.size());
        return chunks.size();
    }

    /**
     * 检索 TopK 相似片段，并按 minScore 阈值过滤：
     * 低于阈值视为"未命中"（而非硬凑 TopK），交由上层触发礼貌拒答，抑制幻觉。
     */
    public List<ChunkHit> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int k = topK > 0 ? topK : ragProperties.getTopK();
        List<ChunkHit> hits = vectorRepository.searchTopK(embeddingService.embed(query), k);
        double minScore = ragProperties.getMinScore();
        if (minScore <= 0 || hits == null) {
            return hits == null ? List.of() : hits;
        }
        return hits.stream()
                .filter(h -> h.getScore() >= minScore)
                .toList();
    }

    public List<String> previewChunks(String content) {
        return new ArrayList<>(textSplitter.split(content, ragProperties.getChunkSize(),
                ragProperties.getChunkOverlap()));
    }
}