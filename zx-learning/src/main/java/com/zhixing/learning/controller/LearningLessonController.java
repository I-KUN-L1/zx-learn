package com.zhixing.learning.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 我的课表（持久化）
 */
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final LessonService lessonService;

    @GetMapping("/page")
    public R<List<Map<String, Object>>> page() {
        return R.ok(lessonService.page(UserContext.getUserId()));
    }

    @GetMapping("/now")
    public R<Map<String, Object>> now() {
        return R.ok(lessonService.now(UserContext.getUserId()));
    }

    @GetMapping("/{courseId}")
    public R<Map<String, Object>> getByCourse(@PathVariable Long courseId) {
        return R.ok(lessonService.getByCourse(UserContext.getUserId(), courseId));
    }

    @GetMapping("/{courseId}/count")
    public R<Integer> count(@PathVariable Long courseId) {
        return R.ok(lessonService.countByCourse(courseId));
    }

    @GetMapping("/{courseId}/valid")
    public R<Boolean> valid(@PathVariable Long courseId) {
        return R.ok(lessonService.valid(UserContext.getUserId(), courseId));
    }

    @PostMapping("/plans")
    public R<Void> createPlan(@RequestBody Map<String, Object> plan) {
        lessonService.createPlan(UserContext.getUserId(), plan);
        return R.ok();
    }

    @GetMapping("/plans")
    public R<List<Map<String, Object>>> plans() {
        return R.ok(lessonService.page(UserContext.getUserId()));
    }

    @DeleteMapping("/{courseId}")
    public R<Void> delete(@PathVariable Long courseId) {
        lessonService.delete(UserContext.getUserId(), courseId);
        return R.ok();
    }
}