package com.zhixing.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.auth.domain.po.Menu;
import com.zhixing.auth.domain.po.RoleMenu;
import com.zhixing.auth.mapper.MenuMapper;
import com.zhixing.auth.mapper.RoleMenuMapper;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单管理
 */
@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    @GetMapping("/parent/{pid}")
    public R<List<Menu>> listByParent(@PathVariable Long pid) {
        return R.ok(menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getParentId, pid).orderByAsc(Menu::getSort)));
    }

    @GetMapping("/{id}")
    public R<Menu> getById(@PathVariable Long id) {
        return R.ok(menuMapper.selectById(id));
    }

    @GetMapping
    public R<List<Menu>> tree() {
        List<Menu> all = menuMapper.selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSort));
        return R.ok(buildTree(all, 0L));
    }

    @GetMapping("/me")
    public R<List<Menu>> myTree() {
        // 简化：返回全部菜单（实际应按当前用户角色过滤）
        return tree();
    }

    @PostMapping
    public R<Void> add(@RequestBody Menu menu) {
        menuMapper.insert(menu);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Menu menu) {
        menu.setId(id);
        menuMapper.updateById(menu);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuMapper.deleteById(id);
        return R.ok();
    }

    @PostMapping("/role/{roleId}")
    public R<Void> bindRoleMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
        return R.ok();
    }

    @DeleteMapping("/role/{roleId}")
    public R<Void> unbindRoleMenus(@PathVariable Long roleId) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        return R.ok();
    }

    private List<Menu> buildTree(List<Menu> all, Long parentId) {
        Map<Long, List<Menu>> grouped = all.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(Menu::getParentId));
        List<Menu> roots = grouped.getOrDefault(parentId, new ArrayList<>());
        List<Menu> result = new ArrayList<>();
        for (Menu menu : roots) {
            List<Menu> children = buildTree(all, menu.getId());
            result.add(menu);
            result.addAll(children);
        }
        return result;
    }
}
