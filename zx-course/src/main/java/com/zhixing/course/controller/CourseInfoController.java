package com.zhixing.course.controller;

import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import com.zhixing.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程内部调用接口（Feign，不包装）
 */
@RestController
@RequestMapping("/course")
@RequiredArgsConstructor
public class CourseInfoController {

    private final CourseService courseService;

    @GetMapping("/{id}/searchInfo")
    @NoWrapper
    public CourseSimpleInfoDTO searchInfo(@PathVariable Long id) {
        return courseService.queryCourseInfoById(id);
    }

    @GetMapping("/name")
    @NoWrapper
    public List<Long> courseIdsByName(@RequestParam("name") String name) {
        return courseService.queryCourseIdsByName(name);
    }

    @GetMapping("/simpleInfo")
    @NoWrapper
    public List<CourseSimpleInfoDTO> simpleInfoList(@RequestParam("ids") List<Long> ids) {
        return courseService.querySimpleInfoList(ids);
    }

    @GetMapping("/all")
    @NoWrapper
    public List<CourseSimpleInfoDTO> allSimpleInfo() {
        return courseService.queryAllSimpleInfo();
    }

    @GetMapping("/{id}")
    public R<Object> getById(@PathVariable Long id) {
        return R.ok(courseService.getCourseById(id));
    }
}
