package com.zhixing.course.controller;

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

    @GetMapping("/{id}")
    public R<Object> getById(@PathVariable Long id) {
        InternalOnlyGuard.checkInternal();
        return R.ok(courseService.getCourseById(id));
    }
}
