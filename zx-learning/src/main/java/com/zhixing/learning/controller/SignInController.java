package com.zhixing.learning.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.po.SignIn;
import com.zhixing.learning.service.SignInService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习签到
 */
@RestController
@RequestMapping("/sign-ins")
@RequiredArgsConstructor
public class SignInController {

    private final SignInService signInService;

    @PostMapping
    public R<SignIn> checkIn() {
        return R.ok(signInService.checkIn(UserContext.getUserId()));
    }

    @GetMapping("/today")
    public R<SignIn> today() {
        return R.ok(signInService.today(UserContext.getUserId()));
    }

    @GetMapping
    public R<List<SignIn>> list() {
        return R.ok(signInService.list(UserContext.getUserId()));
    }
}