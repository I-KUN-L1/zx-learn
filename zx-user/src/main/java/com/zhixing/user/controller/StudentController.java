package com.zhixing.user.controller;

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
    public R<List<UserVO>> page() {
        return R.ok(userService.pageQueryUsers(2));
    }

    @PostMapping("/register")
    public R<Void> register(@RequestBody UserFormDTO form) {
        if (StringUtils.isBlank(form.getCellPhone()) || StringUtils.isBlank(form.getPassword())) {
            throw new BadRequestException("手机号或密码不能为空");
        }
        form.setType(2);
        userService.saveUser(form);
        return R.ok();
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        userService.changePassword(body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }
}
