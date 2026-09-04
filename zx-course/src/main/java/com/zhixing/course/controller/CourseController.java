package com.zhixing.course.controller;

import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.course.domain.dto.CourseFormDTO;
import com.zhixing.course.domain.po.CourseDraft;
import com.zhixing.course.domain.vo.CourseVO;
import com.zhixing.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 课程管理
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/baseInfo/{id}")
    public R<CourseDraft> getBaseInfo(@PathVariable Long id) {
        return R.ok(courseService.getBaseInfo(id));
    }

    @PostMapping("/baseInfo/save")
    public R<Long> saveBaseInfo(@RequestBody CourseFormDTO form) {
        return R.ok(courseService.saveBaseInfo(form));
    }

    @GetMapping("/{id}")
    public R<CourseVO> getById(@PathVariable Long id) {
        return R.ok(courseService.getCourseById(id));
    }

    @PostMapping("/upShelf")
    public R<Void> upShelf(@RequestBody CourseFormDTO form) {
        courseService.upShelf(form.getId());
        return R.ok();
    }

    @PostMapping("/downShelf")
    public R<Void> downShelf(@RequestBody CourseFormDTO form) {
        courseService.downShelf(form.getId());
        return R.ok();
    }

    @GetMapping("/checkBeforeUpShelf/{id}")
    public R<Void> checkBeforeUpShelf(@PathVariable Long id) {
        courseService.checkBeforeUpShelf(id);
        return R.ok();
    }

    @DeleteMapping("/delete/{id}")
    public R<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    public R<PageDTO<CourseVO>> page(PageQuery query,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false) Integer status) {
        return R.ok(courseService.pageQuery(query, name, status));
    }

    @GetMapping("/checkName")
    public R<Void> checkName(@RequestParam String name) {
        courseService.checkName(name);
        return R.ok();
    }
}
