package com.zhixing.learning.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.learning.service.SignInService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签到内部调用接口（Feign，不包装）。
 * 权限：仅限服务间调用（如学情服务聚合连续打卡天数），外部用户经网关访问一律 403。
 */
@RestController
@RequestMapping("/sign-ins")
@RequiredArgsConstructor
public class SignInInternalController {

    private final SignInService signInService;

    /**
     * 指定用户当前连续签到天数
     */
    @GetMapping("/users/{userId}/streak")
    @NoWrapper
    public Integer streak(@PathVariable("userId") Long userId) {
        InternalOnlyGuard.checkInternal();
        return signInService.currentStreak(userId);
    }
}
