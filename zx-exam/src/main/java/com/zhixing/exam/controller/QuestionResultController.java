package com.zhixing.exam.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.OwnerAccessGuard;
import com.zhixing.exam.domain.po.QuestionResult;
import com.zhixing.exam.service.QuestionResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 答题记录管理
 */
@RestController
@RequestMapping("/question-results")
@RequiredArgsConstructor
public class QuestionResultController {

    private final QuestionResultService resultService;

    /**
     * 提交答题结果（学生）
     */
    @PostMapping
    public R<Long> submit(@RequestBody QuestionResult result) {
        return R.ok(resultService.submit(result));
    }

    /**
     * 查询指定用户答题记录（内部，供学情分析聚合）。
     * 防水平越权：外部用户仅可查询本人，STAFF 可查询任意用户，内部服务调用放行。
     */
    @GetMapping("/users/{userId}/all")
    @NoWrapper
    public List<QuestionResult> listByUser(@PathVariable Long userId) {
        OwnerAccessGuard.checkOwnerOrInternal(userId);
        return resultService.listByUser(userId);
    }

    /**
     * 查询指定用户答题统计（内部，供学情分析聚合）。
     * 防水平越权规则同上。
     */
    @GetMapping("/users/{userId}/stats")
    @NoWrapper
    public Map<String, Object> statsByUser(@PathVariable Long userId) {
        OwnerAccessGuard.checkOwnerOrInternal(userId);
        return resultService.statsByUser(userId);
    }
}
