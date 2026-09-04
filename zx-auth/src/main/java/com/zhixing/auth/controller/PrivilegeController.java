package com.zhixing.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.auth.domain.po.Privilege;
import com.zhixing.auth.domain.po.RolePrivilege;
import com.zhixing.auth.mapper.PrivilegeMapper;
import com.zhixing.auth.mapper.RolePrivilegeMapper;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理
 */
@RestController
@RequestMapping("/privileges")
@RequiredArgsConstructor
public class PrivilegeController {

    private final PrivilegeMapper privilegeMapper;
    private final RolePrivilegeMapper rolePrivilegeMapper;

    @GetMapping
    public R<PageDTO<Privilege>> page(PageQuery query) {
        Page<Privilege> page = privilegeMapper.selectPage(
                query.toMpPage("id", false), new LambdaQueryWrapper<>());
        return R.ok(PageDTO.of(page));
    }

    @GetMapping("/options/{menuId}")
    public R<List<Privilege>> options(@PathVariable Long menuId) {
        return R.ok(privilegeMapper.selectList(new LambdaQueryWrapper<Privilege>()
                .eq(Privilege::getMenuId, menuId)));
    }

    @PostMapping
    public R<Void> add(@RequestBody Privilege privilege) {
        privilegeMapper.insert(privilege);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Privilege privilege) {
        privilege.setId(id);
        privilegeMapper.updateById(privilege);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        privilegeMapper.deleteById(id);
        return R.ok();
    }

    @PostMapping("/role/{roleId}")
    public R<Void> bindRolePrivileges(@PathVariable Long roleId, @RequestBody List<Long> privilegeIds) {
        rolePrivilegeMapper.delete(new LambdaQueryWrapper<RolePrivilege>().eq(RolePrivilege::getRoleId, roleId));
        for (Long privilegeId : privilegeIds) {
            RolePrivilege rp = new RolePrivilege();
            rp.setRoleId(roleId);
            rp.setPrivilegeId(privilegeId);
            rolePrivilegeMapper.insert(rp);
        }
        return R.ok();
    }

    @DeleteMapping("/role/{roleId}")
    public R<Void> unbindRolePrivileges(@PathVariable Long roleId) {
        rolePrivilegeMapper.delete(new LambdaQueryWrapper<RolePrivilege>().eq(RolePrivilege::getRoleId, roleId));
        return R.ok();
    }
}
