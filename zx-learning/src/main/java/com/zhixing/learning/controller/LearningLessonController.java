package com.zhixing.learning.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.utils.OwnerAccessGuard;
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
    public R<PageDTO<Map<String, Object>>> page(PageQuery query) {
        return R.ok(lessonService.page(UserContext.getUserId(), query));
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

    /**
     * 我（当前学员）课表中已拥有的课程 id 集合。
     * <p>
     * 这是前端判定「已拥有」的<b>权威口径</b>：课程列表、课程详情与「我的课表」检索
     * 统一以它为准，课表里有 → 已拥有；课表里没有 → 可购买。三者共用同一份数据源，
     * 因此状态实时联动一致（购买/开课后刷新即可，不会出现"课程界面已拥有、课表里没有"）。
     */
    @GetMapping("/mine/course-ids")
    @RequireRole(UserRole.STUDENT)
    public R<List<Long>> myCourseIds() {
        return R.ok(lessonService.listCourseIds(UserContext.getUserId()));
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
        return R.ok(lessonService.page(UserContext.getUserId(), new PageQuery()).getList());
    }

    @DeleteMapping("/{courseId}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> delete(@PathVariable Long courseId) {
        lessonService.delete(UserContext.getUserId(), courseId);
        return R.ok();
    }

    /**
     * 用户课程中心（课表）课程 id 集合（内部 Feign 接口，不包装）。
     * 「已拥有」权威口径：课表存在即拥有（含学习进度 0、退款后未清课表），
     * 供 zx-trade 下单防重复购买与课程中心已拥有标识使用。
     * 防水平越权：外部用户仅可查询本人，STAFF 可查询任意用户，内部服务调用放行。
     */
    @GetMapping("/users/{userId}/course-ids")
    @NoWrapper
    public List<Long> listCourseIds(@PathVariable("userId") Long userId) {
        OwnerAccessGuard.checkOwnerOrInternal(userId);
        return lessonService.listCourseIds(userId);
    }
}
