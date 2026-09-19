package com.zhixing.aigc.controller;

import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.domain.KnowledgeChunk;
import com.zhixing.aigc.service.EmbeddingService;
import com.zhixing.aigc.service.KnowledgeService;
import com.zhixing.aigc.service.KnowledgeVectorRepository;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 向量库接口：文本向量化预览 与 pgvector 检索
 * <p>
 * 权限：与 {@link KnowledgeController} 保持一致 —— 写操作（入库 / 删除）限教师(3)/员工(1)，
 * 只读检索仅要求登录。修复前写接口无任何角色校验，任意学员可写入/删除知识库向量，
 * 污染 RAG 检索结果（数据完整性风险）。
 */
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    /** 单次检索返回的最大切片数（防止 topK 传入极大值导致全表扫描 + 大响应） */
    private static final int MAX_TOP_K = 50;

    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final KnowledgeVectorRepository vectorRepository;

    @PostMapping
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> save(@RequestBody Map<String, String> body) {
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank()) {
            throw new BadRequestException("text 不能为空");
        }
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setTitle(body.get("id"));
        chunk.setContent(text);
        chunk.setEmbedding(embeddingService.embed(text));
        vectorRepository.insert(chunk);
        return R.ok();
    }

    @GetMapping
    public R<float[]> embed(@RequestParam String text) {
        return R.ok(embeddingService.embed(text));
    }

    @DeleteMapping
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> delete(@RequestParam String id) {
        long chunkId;
        try {
            chunkId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            // 修复前：Long.parseLong 直接抛 NumberFormatException → 500（应 400）
            throw new BadRequestException("id 必须为数字");
        }
        vectorRepository.deleteById(chunkId);
        return R.ok();
    }

    @GetMapping("/search")
    public R<List<ChunkHit>> search(@RequestParam String text, @RequestParam(defaultValue = "5") int topK) {
        return R.ok(knowledgeService.search(text, clampTopK(topK)));
    }

    @GetMapping("/search/all")
    public R<List<ChunkHit>> searchAll(@RequestParam String text) {
        // 修复前传 Integer.MAX_VALUE：等价"全表扫描 + 全量回传"，既是注入面也是内存放大面
        return R.ok(knowledgeService.search(text, MAX_TOP_K));
    }

    private int clampTopK(int topK) {
        if (topK <= 0) {
            return 5;
        }
        return Math.min(topK, MAX_TOP_K);
    }
}