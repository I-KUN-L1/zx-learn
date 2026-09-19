package com.zhixing.exam.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.exam.domain.po.Question;
import com.zhixing.exam.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 题库管理 / 题库练习。
 * <p>
 * 师生联动约定：
 * <ul>
 *   <li>教师端（role=3）可增删改、发布/撤回、查看全部（含草稿）；</li>
 *   <li>学员端（role=2）只读取 {@code status=1} 的已发布题目，实现"教师发布 → 学员接收"；</li>
 *   <li>管理员（role=1）可读全部，用于运营审查，不具备编辑权限。</li>
 * </ul>
 * 持久化：MySQL {@code zx_exam.question}（原实现为内存 Map，重启即丢）。
 */
@Slf4j
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /* ==================== 教师端：题库管理 ==================== */

    @PostMapping
    @RequireRole(UserRole.TEACHER)
    public R<Long> add(@RequestBody Question question) {
        return R.ok(questionService.create(question));
    }

    @PutMapping("/{id}")
    @RequireRole(UserRole.TEACHER)
    public R<Void> update(@PathVariable Long id, @RequestBody Question question) {
        questionService.update(id, question);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole(UserRole.TEACHER)
    public R<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return R.ok();
    }

    /**
     * 发布 / 撤回题目：控制学员端可见性，是师生联动的开关。
     */
    @PutMapping("/{id}/publish")
    @RequireRole(UserRole.TEACHER)
    public R<Void> publish(@PathVariable Long id,
                           @RequestParam(name = "published", defaultValue = "true") boolean published) {
        questionService.publish(id, published);
        return R.ok();
    }

    /** 教师端题库全量列表（含草稿） */
    @GetMapping("/all")
    @RequireRole(UserRole.TEACHER)
    public R<List<Question>> all() {
        return R.ok(questionService.listByIdsOrAll(null, false));
    }

    /** 教师端题库分页（支持关键词 / 类型 / 课程筛选，含草稿） */
    @GetMapping("/teacher/page")
    @RequireRole(UserRole.TEACHER)
    public R<PageDTO<Question>> teacherPage(PageQuery query,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer type,
                                            @RequestParam(required = false) Long courseId) {
        // teacherId 传 null：教师工作台展示全站题库，便于跨课程复用
        return R.ok(questionService.page(query, keyword, type, courseId, null, false));
    }

    /** 某教师的题目数量 */
    @GetMapping("/numOfTeacher")
    public R<Long> numOfTeacher(@RequestParam(required = false) Long teacherId) {
        return R.ok(questionService.countByTeacher(teacherId));
    }

    /* ==================== 通用：题库查询 ==================== */

    @GetMapping("/{id}")
    public R<Question> getById(@PathVariable Long id) {
        return R.ok(questionService.getRequired(id));
    }

    /**
     * 题库练习列表（学员端"在线答题"数据源）。
     * <p>
     * 修复要点：原实现 {@code @RequestParam("ids") List<Long> ids} 是必填参数，
     * 学员端不带 ids 时直接 400，导致练习页永远空白。现改为可选：
     * 不传 ids 时按角色返回（学员只看已发布，教师/管理员看全部）。
     */
    @GetMapping("/list")
    public R<List<Question>> list(@RequestParam(name = "ids", required = false) List<Long> ids) {
        boolean onlyPublished = !UserContext.hasRole(UserRole.TEACHER.getCode(), UserRole.STAFF.getCode());
        // 显式指定 ids 时不做发布状态过滤，兼容 Feign 内部批量取题
        boolean filterPublished = (ids == null || ids.isEmpty()) && onlyPublished;
        return R.ok(questionService.listByIdsOrAll(ids, filterPublished));
    }

    /** 题库分页（学员端题库练习页数据源：仅已发布） */
    @GetMapping("/page")
    public R<PageDTO<Question>> page(PageQuery query,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Integer type,
                                     @RequestParam(required = false) Long courseId) {
        boolean onlyPublished = !UserContext.hasRole(UserRole.TEACHER.getCode(), UserRole.STAFF.getCode());
        return R.ok(questionService.page(query, keyword, type, courseId, null, onlyPublished));
    }

    @GetMapping("/scores")
    public R<Map<Long, Integer>> scores(@RequestParam(name = "ids", required = false) List<Long> ids) {
        return R.ok(questionService.scores(ids));
    }
}
