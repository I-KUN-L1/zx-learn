package com.zhixing.learning.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 签到（Redis BitMap 存储）。
 * 权限：签到为学员端行为，仅学员(2)可操作，管理员/教师一律 403。
 */
@RestController
@RequestMapping("/sign-records")
public class SignRecordController {

    private static final String KEY_PREFIX = "sign:";
    private final StringRedisTemplate redisTemplate;

    public SignRecordController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    @RequireRole(UserRole.STUDENT)
    public R<Void> sign() {
        String key = KEY_PREFIX + UserContext.getUserId() + ":" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        redisTemplate.opsForValue().setBit(key, LocalDate.now().getDayOfMonth() - 1, true);
        return R.ok();
    }

    @GetMapping
    public R<List<Integer>> records() {
        String key = KEY_PREFIX + UserContext.getUserId() + ":" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<Integer> days = new java.util.ArrayList<>();
        for (int i = 0; i < LocalDate.now().getDayOfMonth(); i++) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, i))) {
                days.add(i + 1);
            }
        }
        return R.ok(days);
    }
}
