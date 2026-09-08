package com.zhixing.user.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.user.domain.dto.UserFormDTO;
import com.zhixing.user.domain.vo.UserVO;
import com.zhixing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学员管理
 */
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {

    private final UserService userService;

    @GetMapping("/page")
    @RequireRole(UserRole.STAFF)
    public R<List<UserVO>> page() {
        return R.ok(userService.pageQueryUsers(2));
    }

    @PostMapping("/register")
    public R<Void> register(@RequestBody UserFormDTO form) {
        validateRegister(form);
        // 角色由后端强制指定为学员(2)，忽略客户端传入的 type，防止越权注册管理员
        form.setType(2);
        userService.saveUser(form);
        return R.ok();
    }

    /**
     * 注册参数校验：手机号格式、密码强度（与前端规则一致）。
     */
    private void validateRegister(UserFormDTO form) {
        if (StringUtils.isBlank(form.getCellPhone()) || StringUtils.isBlank(form.getPassword())) {
            throw new BadRequestException("手机号或密码不能为空");
        }
        if (!form.getCellPhone().matches("^1\\d{10}$")) {
            throw new BadRequestException("手机号格式不正确");
        }
        if (form.getPassword().length() < 6) {
            throw new BadRequestException("密码至少 6 位");
        }
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        userService.changePassword(body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }
}
