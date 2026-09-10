package com.zhixing.learning.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 我的课表（持久化）。
 * 权限：课表/学习计划均为学员端行为，仅学员(2)可操作，管理员/教师一律 403；
 * 课程报名人数（/{courseId}/count）为公开聚合信息，不做角色限制。
 */
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final LessonService lessonService;

    @GetMapping("/page")
    @RequireRole(UserRole.STUDENT)
    public R<List<Map<String, Object>>> page() {
        return R.ok(lessonService.page(UserContext.getUserId()));
    }

    @GetMapping("/now")
    @RequireRole(UserRole.STUDENT)
    public R<Map<String, Object>> now() {
        return R.ok(lessonService.now(UserContext.getUserId()));
    }

    @GetMapping("/{courseId}")
    @RequireRole(UserRole.STUDENT)
    public R<Map<String, Object>> getByCourse(@PathVariable Long courseId) {
        return R.ok(lessonService.getByCourse(UserContext.getUserId(), courseId));
    }

    @GetMapping("/{courseId}/count")
    public R<Integer> count(@PathVariable Long courseId) {
        return R.ok(lessonService.countByCourse(courseId));
    }

    @GetMapping("/{courseId}/valid")
    @RequireRole(UserRole.STUDENT)
    public R<Boolean> valid(@PathVariable Long courseId) {
        return R.ok(lessonService.valid(UserContext.getUserId(), courseId));
    }

    @PostMapping("/plans")
    @RequireRole(UserRole.STUDENT)
    public R<Void> createPlan(@RequestBody Map<String, Object> plan) {
        lessonService.createPlan(UserContext.getUserId(), plan);
        return R.ok();
    }

    @GetMapping("/plans")
    @RequireRole(UserRole.STUDENT)
    public R<List<Map<String, Object>>> plans() {
        return R.ok(lessonService.page(UserContext.getUserId()));
    }

    @DeleteMapping("/{courseId}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> delete(@PathVariable Long courseId) {
        lessonService.delete(UserContext.getUserId(), courseId);
        return R.ok();
    }
}
