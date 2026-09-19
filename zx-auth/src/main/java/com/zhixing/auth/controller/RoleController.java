package com.zhixing.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.auth.domain.po.Role;
import com.zhixing.auth.mapper.RoleMapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理
 * <p>
 * 权限：RBAC 管理属于系统配置面，类级限定为员工（管理员）。
 * 修复前该类**无任何角色校验**，任意已登录学员/教师均可增删改角色，
 * 是典型的垂直越权（特权提升）—— 现已 fail-closed。
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@RequireRole(UserRole.STAFF)
public class RoleController {

    private final RoleMapper roleMapper;

    @GetMapping("/list")
    public R<List<Role>> listAll() {
        return R.ok(roleMapper.selectList(null));
    }

    @GetMapping
    public R<List<Role>> list() {
        return R.ok(roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getCreateTime)));
    }

    @GetMapping("/{id}")
    public R<Role> getById(@PathVariable Long id) {
        return R.ok(roleMapper.selectById(id));
    }

    @PostMapping
    public R<Void> add(@RequestBody Role role) {
        // role.name 为 NOT NULL 且无默认值；名称缺失时 MyBatis-Plus 生成的 INSERT
        // 不含 name 列 → 数据库报错并落入兜底 500「系统繁忙」。前置校验为 400。
        if (role == null || !StringUtils.hasText(role.getName())) {
            throw new BadRequestException("角色名称不能为空");
        }
        roleMapper.insert(role);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        roleMapper.updateById(role);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleMapper.deleteById(id);
        return R.ok();
    }
}
