package com.zhixing.aigc.service;

import com.zhixing.aigc.config.RagProperties;
import com.zhixing.aigc.domain.ChunkHit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 知识库服务单测：TopK 检索的 minScore 阈值过滤（低于阈值视为未命中，触发上层礼貌拒答）。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private KnowledgeVectorRepository vectorRepository;

    @Spy
    private TextSplitter textSplitter = new TextSplitter(new RagProperties());

    @Spy
    private RagProperties ragProperties = new RagProperties();

    @InjectMocks
    private KnowledgeService knowledgeService;

    private ChunkHit hit(long id, String title, double score) {
        ChunkHit h = new ChunkHit();
        h.setId(id);
        h.setTitle(title);
        h.setContent("内容");
        h.setScore(score);
        return h;
    }

    @Test
    void searchFiltersByMinScore() {
        when(embeddingService.embed("事务")).thenReturn(new float[]{0.1f, 0.2f});
        when(vectorRepository.searchTopK(any(float[].class), eq(3)))
                .thenReturn(List.of(hit(1L, "高相似", 0.87), hit(2L, "低相似", 0.32)));
        ragProperties.setMinScore(0.5);

        List<ChunkHit> hits = knowledgeService.search("事务", 3);

        assertEquals(1, hits.size());
        assertEquals("高相似", hits.get(0).getTitle());
    }

    @Test
    void searchKeepsAllWhenThresholdDisabled() {
        when(embeddingService.embed("事务")).thenReturn(new float[]{0.1f, 0.2f});
        when(vectorRepository.searchTopK(any(float[].class), eq(3)))
                .thenReturn(List.of(hit(1L, "A", 0.87), hit(2L, "B", 0.32)));
        ragProperties.setMinScore(0.0);

        assertEquals(2, knowledgeService.search("事务", 3).size());
    }

    @Test
    void searchAllBelowThresholdReturnsEmpty() {
        when(embeddingService.embed("天气")).thenReturn(new float[]{0.3f});
        when(vectorRepository.searchTopK(any(float[].class), anyInt()))
                .thenReturn(List.of(hit(9L, "不相关", 0.11)));
        ragProperties.setMinScore(0.5);

        // 全部低于阈值 → 未命中 → 空列表，KnowledgeAgent 据此触发礼貌拒答
        assertEquals(List.of(), knowledgeService.search("天气", 3));
    }

    @Test
    void uploadSplitsEmbedsAndInserts() {
        lenient().when(embeddingService.embed(any(String.class))).thenReturn(new float[]{0.5f});
        ragProperties.setChunkSize(20);
        ragProperties.setChunkOverlap(5);

        int chunks = knowledgeService.upload(1L, 2L, "讲义", "重复内容。".repeat(30));

        assertEquals(chunks, org.mockito.Mockito.mockingDetails(vectorRepository).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("insert")).count());
    }
}
