package com.zhixing.learning.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.po.SignIn;
import com.zhixing.learning.service.SignInService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习签到。
 * 权限：签到为学员端行为，仅学员(2)可操作，管理员/教师一律 403。
 */
@RestController
@RequestMapping("/sign-ins")
@RequiredArgsConstructor
@RequireRole(UserRole.STUDENT)
public class SignInController {

    private final SignInService signInService;

    @PostMapping
    public R<SignIn> checkIn() {
        return R.ok(signInService.checkIn(UserContext.getUserId()));
    }

    /** 今日是否已签到 */
    @GetMapping("/today")
    public R<Boolean> today() {
        return R.ok(signInService.today(UserContext.getUserId()) != null);
    }

    /** 签到日期列表（yyyy-MM-dd） */
    @GetMapping
    public R<List<String>> list() {
        return R.ok(signInService.list(UserContext.getUserId()).stream()
                .map(s -> s.getSignDate().toString())
                .toList());
    }
}