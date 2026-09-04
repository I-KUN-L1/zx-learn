package com.zhixing.aigc.controller;

import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.service.KnowledgeService;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库管理接口（RAG 知识入库 / 检索）
 * <p>
 * 权限：写操作（上传）要求教师(3)/员工(1)角色——网关从 JWT role claim 解析后经 role-info 头透传；
 * 查询接口（检索/预览）仅要求登录。
 */
@Slf4j
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    /** 允许管理知识库的角色（user.type）：1=员工 3=教师 */
    private static final Set<String> KNOWLEDGE_MANAGE_ROLES = Set.of("1", "3");

    private final KnowledgeService knowledgeService;

    /**
     * 课程讲义知识入库：切片 + 向量化 + 写入 pgvector（教师权限）
     */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestHeader(value = "role-info", required = false) String role,
                                         @RequestBody Map<String, Object> body) {
        if (!KNOWLEDGE_MANAGE_ROLES.contains(role)) {
            log.warn("知识入库被拒绝：当前角色 {} 无教师/员工权限", role);
            return R.error("仅教师或管理员可上传课程知识");
        }
        Long courseId = toLong(body.get("courseId"));
        Long lessonId = toLong(body.get("lessonId"));
        String title = String.valueOf(body.getOrDefault("title", ""));
        String content = String.valueOf(body.getOrDefault("content", ""));
        if (title.isBlank() || content.isBlank()) {
            return R.error("title 与 content 不能为空");
        }
        int chunks = knowledgeService.upload(courseId, lessonId, title, content);
        return R.ok(Map.of(
                "title", title,
                "chunks", chunks
        ));
    }

    /**
     * 向量检索（演示/联调用）
     */
    @PostMapping("/search")
    public R<List<ChunkHit>> search(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        int topK = Integer.parseInt(body.getOrDefault("topK", "3"));
        return R.ok(knowledgeService.search(query, topK));
    }

    /**
     * 预览切片结果（不落库）
     */
    @PostMapping("/preview")
    public R<List<String>> preview(@RequestBody Map<String, String> body) {
        return R.ok(knowledgeService.previewChunks(body.getOrDefault("content", "")));
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
