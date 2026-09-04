package com.zhixing.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.dto.user.LoginFormDTO;
import com.zhixing.api.dto.user.BootstrapAdminDTO;
import com.zhixing.api.dto.user.PasswordChangeDTO;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.BeanUtils;
import com.zhixing.user.domain.dto.UserFormDTO;
import com.zhixing.user.domain.vo.UserVO;
import com.zhixing.user.mapper.UserMapper;
import com.zhixing.user.domain.po.User;
import com.zhixing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    // ============ 内部接口（Feign 调用，不包装） ============

    @PostMapping("/detail/{isStaff}")
    @NoWrapper
    public UserDTO queryUserDetail(@RequestBody LoginFormDTO loginFormDTO, @PathVariable("isStaff") boolean isStaff) {
        return userService.queryUserDetail(loginFormDTO, isStaff);
    }

    // ============ 首个管理员引导（Feign 内部调用，不包装） ============

    @GetMapping("/bootstrap/admin-exists")
    @NoWrapper
    public Boolean adminExists() {
        return userService.adminExists();
    }

    @PostMapping("/bootstrap/admin")
    @NoWrapper
    public UserDTO createBootstrapAdmin(@RequestBody BootstrapAdminDTO dto) {
        return userService.createBootstrapAdmin(dto.getUsername(), dto.getCellPhone(), dto.getPassword());
    }

    @PutMapping("/bootstrap/password")
    @NoWrapper
    public void changeBootstrapPassword(@RequestBody PasswordChangeDTO dto) {
        userService.changeBootstrapPassword(dto.getCellPhone(), dto.getOldPassword(), dto.getNewPassword());
    }

    @GetMapping("/list")
    @NoWrapper
    public List<UserDTO> queryUserByIds(@RequestParam("ids") List<Long> ids) {
        return userService.queryUserByIds(ids);
    }

    @GetMapping("/{id}/type")
    @NoWrapper
    public Integer queryUserType(@PathVariable("id") Long id) {
        return userService.queryUserType(id);
    }

    @GetMapping("/ids")
    @NoWrapper
    public Map<String, Long> exchangeUserId(@RequestParam("phone") String phone) {
        return userService.exchangeUserId(phone);
    }

    // ============ 对外接口 ============

    @PostMapping
    public R<Void> addUser(@RequestBody UserFormDTO form) {
        userService.saveUser(form);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> updateUser(@PathVariable Long id, @RequestBody UserFormDTO form) {
        userService.updateUser(id, form);
        return R.ok();
    }

    @PutMapping
    public R<Void> updateCurrentUser(@RequestBody UserFormDTO form) {
        userService.updateUser(form.getId(), form);
        return R.ok();
    }

    @PutMapping("/{id}/password/default")
    public R<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return R.ok();
    }

    @PutMapping("/{id}/status/{status}")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.queryMe());
    }

    @GetMapping("/{id}")
    public R<UserVO> getById(@PathVariable Long id) {
        return R.ok(userService.queryById(id));
    }

    @GetMapping("/checkCellphone")
    public R<Boolean> checkCellphone(@RequestParam String cellphone) {
        userService.checkCellPhone(cellphone, null);
        return R.ok(true);
    }

    @GetMapping("/page")
    public R<PageDTO<UserVO>> page(PageQuery query, @RequestParam(required = false) Integer type) {
        Page<User> page = userMapper.selectPage(query.toMpPage("id", false), null);
        return R.ok(PageDTO.of(page, u -> BeanUtils.copyBean(u, UserVO.class)));
    }
}
