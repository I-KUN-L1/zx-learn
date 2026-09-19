package com.zhixing.course.controller;

import com.zhixing.api.dto.course.CourseCatalogueDTO;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程内部调用接口（Feign，不包装）。
 * 权限：仅限服务间调用，外部用户（含管理员）经网关访问一律 403。
 */
@RestController
@RequestMapping("/course")
@RequiredArgsConstructor
public class CourseInfoController {

    private final CourseService courseService;

    @GetMapping("/{id}/searchInfo")
    @NoWrapper
    public CourseSimpleInfoDTO searchInfo(@PathVariable Long id) {
        InternalOnlyGuard.checkInternal();
        return courseService.queryCourseInfoById(id);
    }

    @GetMapping("/name")
    @NoWrapper
    public List<Long> courseIdsByName(@RequestParam("name") String name) {
        InternalOnlyGuard.checkInternal();
        return courseService.queryCourseIdsByName(name);
    }

    /**
     * 门户课程检索（供 zx-search 的 {@code /courses/portal} 与 {@code /recommend/**} 消费）。
     * <p>
     * 只返回<b>已上架</b>课程，与课程上下架状态强一致；排序口径见
     * {@link CourseService#portalQuery(String, String, Integer)}。
     * 走内部通道（{@link InternalOnlyGuard}）：外部用户不得绕过 zx-search 直接命中此端点。
     */
    @GetMapping("/portal")
    @NoWrapper
    public List<CourseSimpleInfoDTO> portal(@RequestParam(value = "keyword", required = false) String keyword,
                                           @RequestParam(value = "sort", required = false) String sort,
                                           @RequestParam(value = "limit", required = false) Integer limit) {
        InternalOnlyGuard.checkInternal();
        return courseService.portalQuery(keyword, sort, limit);
    }

    @GetMapping("/simpleInfo")
    @NoWrapper
    public List<CourseSimpleInfoDTO> simpleInfoList(@RequestParam("ids") List<Long> ids) {
        InternalOnlyGuard.checkInternal();
        return courseService.querySimpleInfoList(ids);
    }

    @GetMapping("/all")
    @NoWrapper
    public List<CourseSimpleInfoDTO> allSimpleInfo() {
        InternalOnlyGuard.checkInternal();
        return courseService.queryAllSimpleInfo();
    }

    /**
     * 批量查询课程目录（章节/小节）信息（供学习服务聚合学习记录展示）
     */
    @GetMapping("/catalogues")
    @NoWrapper
    public List<CourseCatalogueDTO> catalogueList(@RequestParam("ids") List<Long> ids) {
        InternalOnlyGuard.checkInternal();
        return courseService.queryCatalogueList(ids);
    }

    @GetMapping("/{id}")
    public R<Object> getById(@PathVariable Long id) {
        InternalOnlyGuard.checkInternal();
        return R.ok(courseService.getCourseById(id));
    }

    /**
     * 指定课程的全部目录（章节 + 小节，按 index 升序）。
     * 供学习服务判定整门课程是否学完（小节总数 vs 已完成数）并发放课程完成积分。
     */
    @GetMapping("/{id}/catalogues")
    @NoWrapper
    public List<CourseCatalogueDTO> cataloguesByCourse(@PathVariable("id") Long id) {
        InternalOnlyGuard.checkInternal();
        return courseService.queryCataloguesByCourse(id);
    }
}
