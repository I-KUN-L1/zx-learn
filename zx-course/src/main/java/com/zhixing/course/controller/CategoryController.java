package com.zhixing.course.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.course.domain.po.Category;
import com.zhixing.course.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程分类管理
 */
@RestController
@RequestMapping("/categorys")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;

    @GetMapping("/list")
    public R<List<Category>> list(@RequestParam(required = false) Long parentId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(parentId != null, Category::getParentId, parentId)
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSort);
        return R.ok(categoryMapper.selectList(wrapper));
    }

    @GetMapping("/{id}")
    public R<Category> getById(@PathVariable Long id) {
        return R.ok(categoryMapper.selectById(id));
    }

    @GetMapping("/all")
    public R<List<Category>> all() {
        return R.ok(categoryMapper.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort)));
    }

    @GetMapping("/getAllOfOneLevel")
    public R<List<Category>> allOfOneLevel() {
        return R.ok(categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getLevel, 1).orderByAsc(Category::getSort)));
    }

    @PostMapping("/add")
    @RequireRole(UserRole.STAFF)
    public R<Void> add(@RequestBody Category category) {
        // category.name 为 NOT NULL 且无默认值；名称缺失时 MyBatis-Plus 生成的 INSERT
        // 不含 name 列 → 数据库报错并落入兜底 500「系统繁忙」。前置校验为 400。
        if (category == null || !StringUtils.hasText(category.getName())) {
            throw new BadRequestException("分类名称不能为空");
        }
        if (category.getLevel() == null) {
            category.setLevel(1);
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        categoryMapper.insert(category);
        return R.ok();
    }

    @PutMapping("/update")
    @RequireRole(UserRole.STAFF)
    public R<Void> update(@RequestBody Category category) {
        categoryMapper.updateById(category);
        return R.ok();
    }

    @PutMapping("/disableOrEnable")
    @RequireRole(UserRole.STAFF)
    public R<Void> disableOrEnable(@RequestBody Category category) {
        categoryMapper.updateById(category);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole(UserRole.STAFF)
    public R<Void> delete(@PathVariable Long id) {
        categoryMapper.deleteById(id);
        return R.ok();
    }
}
