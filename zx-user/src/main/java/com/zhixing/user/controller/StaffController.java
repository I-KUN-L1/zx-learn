package com.zhixing.user.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.user.domain.vo.UserVO;
import com.zhixing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/staffs")
@RequiredArgsConstructor
@RequireRole(UserRole.STAFF)
public class StaffController {

    private final UserService userService;

    @GetMapping("/page")
    public R<List<UserVO>> page() {
        return R.ok(userService.pageQueryUsers(1));
    }
}
