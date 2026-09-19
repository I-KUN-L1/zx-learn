package com.zhixing.aigc.controller;

import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.service.KnowledgeService;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（RAG 知识入库 / 检索）
 * <p>
 * 权限：本控制器整体挂在 {@code /admin/knowledge} 下，属于**管理侧**能力。
 * 原先只有写操作（upload）声明了 {@code @RequireRole}，查询接口（检索/预览）仅要求登录，
 * 导致任意已登录学员都能调用 {@code /admin/knowledge/search} 读取内部知识库切片
 * （越权面，上线审查发现）。现统一由 {@code @RequireRole} 声明为教师(3)/员工(1)：
 * 网关从 JWT role claim 解析后经 role-info 头透传，RoleInterceptor 统一校验。
 * <p>
 * 注意：AI 助教的 RAG 检索走 {@code KnowledgeService} 内部直连，不经过本控制器的 HTTP 端点，
 * 因此收紧权限不影响学员侧对话功能。
 */
@Slf4j
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 课程讲义知识入库：切片 + 向量化 + 写入 pgvector（教师权限）
     */
    @PostMapping("/upload")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Map<String, Object>> upload(@RequestBody Map<String, Object> body) {
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
     * 向量检索（管理端联调/排障用）
     */
    @PostMapping("/search")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<List<ChunkHit>> search(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        int topK = Integer.parseInt(body.getOrDefault("topK", "3"));
        return R.ok(knowledgeService.search(query, topK));
    }

    /**
     * 预览切片结果（不落库）
     */
    @PostMapping("/preview")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
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
