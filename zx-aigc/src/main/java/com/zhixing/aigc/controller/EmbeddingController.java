package com.zhixing.aigc.controller;

import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.domain.KnowledgeChunk;
import com.zhixing.aigc.service.EmbeddingService;
import com.zhixing.aigc.service.KnowledgeService;
import com.zhixing.aigc.service.KnowledgeVectorRepository;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 向量库接口：文本向量化预览 与 pgvector 检索
 */
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final KnowledgeVectorRepository vectorRepository;

    @PostMapping
    public R<Void> save(@RequestBody Map<String, String> body) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setTitle(body.get("id"));
        chunk.setContent(body.get("text"));
        chunk.setEmbedding(embeddingService.embed(body.get("text")));
        vectorRepository.insert(chunk);
        return R.ok();
    }

    @GetMapping
    public R<float[]> embed(@RequestParam String text) {
        return R.ok(embeddingService.embed(text));
    }

    @DeleteMapping
    public R<Void> delete(@RequestParam String id) {
        vectorRepository.deleteById(Long.parseLong(id));
        return R.ok();
    }

    @GetMapping("/search")
    public R<List<ChunkHit>> search(@RequestParam String text, @RequestParam(defaultValue = "5") int topK) {
        return R.ok(knowledgeService.search(text, topK));
    }

    @GetMapping("/search/all")
    public R<List<ChunkHit>> searchAll(@RequestParam String text) {
        return R.ok(knowledgeService.search(text, Integer.MAX_VALUE));
    }
}