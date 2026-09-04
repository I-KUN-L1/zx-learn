package com.zhixing.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.auth.domain.po.Role;
import com.zhixing.auth.mapper.RoleMapper;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
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
