package com.zhixing.course.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
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
 * <p>
 * 权限：课程工作台（基本信息/上下架/删除/校验）要求员工(1)或教师(3)；
 * GET /{id} 与 GET /page 为学员端公共接口，不做角色限制。
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/baseInfo/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<CourseDraft> getBaseInfo(@PathVariable Long id) {
        return R.ok(courseService.getBaseInfo(id));
    }

    @PostMapping("/baseInfo/save")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Long> saveBaseInfo(@RequestBody CourseFormDTO form) {
        return R.ok(courseService.saveBaseInfo(form));
    }

    @GetMapping("/{id}")
    public R<CourseVO> getById(@PathVariable Long id) {
        return R.ok(courseService.getCourseById(id));
    }

    @PostMapping("/upShelf")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> upShelf(@RequestBody CourseFormDTO form) {
        courseService.upShelf(form.getId());
        return R.ok();
    }

    @PostMapping("/downShelf")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> downShelf(@RequestBody CourseFormDTO form) {
        courseService.downShelf(form.getId());
        return R.ok();
    }

    @GetMapping("/checkBeforeUpShelf/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> checkBeforeUpShelf(@PathVariable Long id) {
        courseService.checkBeforeUpShelf(id);
        return R.ok();
    }

    @DeleteMapping("/delete/{id}")
    @RequireRole(UserRole.STAFF)
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
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> checkName(@RequestParam String name) {
        courseService.checkName(name);
        return R.ok();
    }
}
