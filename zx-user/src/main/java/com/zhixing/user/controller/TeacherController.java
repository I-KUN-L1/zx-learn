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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师管理
 */
@RestController
@RequestMapping("/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final UserService userService;

    /**
     * 教师分页查询（员工专用）。
     * 注意：@RequireRole 从类级移到方法级，避免覆盖 /teachers/register 的匿名放行。
     */
    @GetMapping("/page")
    @RequireRole(UserRole.STAFF)
    public R<List<UserVO>> page() {
        return R.ok(userService.pageQueryUsers(3));
    }

    /**
     * 教师自助注册（网关白名单放行，无需登录）。
     * 管理员账号不开放自助注册，仅允许后端创建。
     */
    @PostMapping("/register")
    public R<Void> register(@RequestBody UserFormDTO form) {
        validateRegister(form);
        // 角色由后端强制指定为教师(3)，忽略客户端传入的 type，防止越权注册管理员
        form.setType(3);
        userService.saveUser(form);
        return R.ok();
    }

    /**
     * 注册参数校验：手机号格式、密码强度（与学员注册保持一致）。
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
}
