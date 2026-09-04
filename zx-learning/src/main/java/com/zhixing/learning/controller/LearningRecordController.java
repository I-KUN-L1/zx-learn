package com.zhixing.learning.controller;

import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import com.zhixing.learning.domain.dto.LearningProgressDTO;
import com.zhixing.learning.service.LearningRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习记录
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
    public R<Long> submitProgress(@RequestBody LearningProgressDTO form) {
        return R.ok(learningRecordService.submitProgress(form));
    }

    /**
     * 查询指定用户全部学习记录（内部 Feign 接口，不包装）
     */
    @GetMapping("/users/{userId}/all")
    @NoWrapper
    public List<LearningRecordDTO> listAll(@PathVariable("userId") Long userId) {
        return learningRecordService.listRecords(userId);
    }

    /**
     * 查询指定用户学习总时长（秒）（内部 Feign 接口，不包装）
     */
    @GetMapping("/users/{userId}/sum")
    @NoWrapper
    public Long sumDuration(@PathVariable("userId") Long userId) {
        return learningRecordService.sumDuration(userId);
    }
}