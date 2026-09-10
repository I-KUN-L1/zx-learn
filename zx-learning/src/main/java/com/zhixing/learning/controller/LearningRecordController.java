package com.zhixing.learning.controller;

import com.zhixing.api.dto.learning.DailyActiveDTO;
import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.common.utils.OwnerAccessGuard;
import com.zhixing.learning.domain.dto.LearningProgressDTO;
import com.zhixing.learning.service.LearningRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习记录。
 * 权限：提交学习进度为学员端行为，仅学员(2)可操作，管理员/教师一律 403。
 */
@RestController
@RequestMapping("/learning-records")
@RequiredArgsConstructor
public class LearningRecordController {

    private final LearningRecordService learningRecordService;

    /**
     * 提交/更新学习进度
     */
    @PostMapping("/progress")
    @RequireRole(UserRole.STUDENT)
    public R<Long> submitProgress(@RequestBody LearningProgressDTO form) {
        return R.ok(learningRecordService.submitProgress(form));
    }

    /**
     * 查询指定用户全部学习记录（内部 Feign 接口，不包装）。
     * 防水平越权：外部用户仅可查询本人，STAFF 可查询任意用户，内部服务调用放行。
     */
    @GetMapping("/users/{userId}/all")
    @NoWrapper
    public List<LearningRecordDTO> listAll(@PathVariable("userId") Long userId) {
        OwnerAccessGuard.checkOwnerOrInternal(userId);
        return learningRecordService.listRecords(userId);
    }

    /**
     * 查询指定用户学习总时长（秒）（内部 Feign 接口，不包装）。
     * 防水平越权规则同上。
     */
    @GetMapping("/users/{userId}/sum")
    @NoWrapper
    public Long sumDuration(@PathVariable("userId") Long userId) {
        OwnerAccessGuard.checkOwnerOrInternal(userId);
        return learningRecordService.sumDuration(userId);
    }

    /**
     * 近 7 日日活统计（内部 Feign 接口，供管理端看板消费，不包装）。
     * 仅限服务间调用：外部用户（含管理员）经网关访问一律 403。
     */
    @GetMapping("/stats/active")
    @NoWrapper
    public List<DailyActiveDTO> dailyActive() {
        InternalOnlyGuard.checkInternal();
        return learningRecordService.dailyActive();
    }
}
